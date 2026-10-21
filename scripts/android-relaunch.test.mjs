import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkInstrumentation, runRelaunch } from './android-relaunch.mjs';

function fakeAdb({ fails, survivesStop = false, reusesPid = false } = {}) {
  const calls = [];
  let running = false;
  let pid = 100;
  return {
    calls,
    command(args) {
      calls.push(args);
      const shell = args.slice(args.indexOf('shell') + 1);
      if (shell[1] === 'instrument') {
        const phase = shell[shell.indexOf('relaunchPhase') + 1];
        return phase === fails ? 'FAILURES!!!\nTests run: 1, Failures: 1' : 'OK (1 test)\n';
      }
      if (shell[1] === 'start') {
        running = true;
        if (!reusesPid) pid++;
      }
      if (shell[1] === 'force-stop' && !survivesStop) running = false;
      if (shell[0] === 'sh') return running ? `${pid}\n` : '';
      return '';
    },
  };
}

test('a fresh process is checked after the stop and the selected device is used for every call', () => {
  const fake = fakeAdb();
  assert.deepEqual(runRelaunch('adb', fake.command, 'device-1'), { before: '101', after: '102' });
  assert.ok(fake.calls.every((args) => args[0] === '-s' && args[1] === 'device-1'));
  const phases = fake.calls.filter((args) => args.includes('relaunchPhase'))
    .map((args) => args[args.indexOf('relaunchPhase') + 1]);
  assert.deepEqual(phases, ['seed', 'verify', 'cleanup']);
  assert.ok(fake.calls.at(-1).includes('force-stop'));
});

test('adb returning success does not turn a failed instrumentation result into a pass', () => {
  for (const output of ['OK (0 tests)', 'INSTRUMENTATION_FAILED', 'Process crashed', 'OK (1 test)\nFAILURES!!!',
    'INSTRUMENTATION_STATUS_CODE: -3\nOK (1 test)', 'INSTRUMENTATION_STATUS_CODE: -4\nOK (1 test)']) {
    assert.throws(() => checkInstrumentation(output), /did not pass/);
  }
});

test('a verification failure is reported and still cleans up test records and stops the app', () => {
  const fake = fakeAdb({ fails: 'verify' });
  assert.throws(() => runRelaunch('adb', fake.command), /FAILURES!!!/);
  assert.ok(fake.calls.at(-2).includes('cleanup'));
  assert.ok(fake.calls.at(-1).includes('force-stop'));
});

test('a process that survived the stop cannot satisfy persistence after relaunch', () => {
  const fake = fakeAdb({ survivesStop: true });
  assert.throws(() => runRelaunch('adb', fake.command), /survived force-stop/);
});

test('reusing the old process cannot satisfy the relaunch check', () => {
  const fake = fakeAdb({ reusesPid: true });
  assert.throws(() => runRelaunch('adb', fake.command), /reused the old process/);
});

test('cleanup errors keep the original failure visible and do not prevent force-stop', () => {
  const fake = fakeAdb({ fails: 'verify' });
  const command = (args) => {
    const result = fake.command(args);
    if (args.includes('cleanup')) throw new Error('cleanup error');
    return result;
  };
  assert.throws(() => runRelaunch('adb', command), (error) =>
    error instanceof AggregateError && error.errors.length === 2 &&
    /FAILURES!!!/.test(error.errors[0].message) && /cleanup error/.test(error.errors[1].message));
  assert.ok(fake.calls.at(-1).includes('force-stop'));
});
