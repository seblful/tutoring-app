import { execFileSync } from 'node:child_process';
import { parseArgs } from 'node:util';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const app = 'com.seblful.klas.debug';
const runner = `${app}.test/com.seblful.klas.KlasTestRunner`;
const probe = 'com.seblful.klas.persistence.ColdRelaunchTest';

export function checkInstrumentation(output) {
  if (!/\bOK \(1 test\)/.test(output) ||
      /FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_STATUS_CODE:\s*-[1-4]\b/.test(output)) {
    throw new Error(`Relaunch instrumentation did not pass:\n${output}`);
  }
}

export function runRelaunch(adb, command = (args) => execFileSync(adb, args, {
  encoding: 'utf8', timeout: 120_000, stdio: ['ignore', 'pipe', 'pipe'],
}), serial) {
  const shell = (...args) => command([...(serial ? ['-s', serial] : []), 'shell', ...args]).trim();
  const phase = (name) => {
    const output = shell('am', 'instrument', '-w', '-r', '-e', 'class', probe,
      '-e', 'relaunchPhase', name, runner);
    checkInstrumentation(output);
  };
  const stop = () => shell('am', 'force-stop', app);
  const launch = () => {
    shell('am', 'start', '-W', '-n', `${app}/com.seblful.klas.MainActivity`);
    // pidof exits 1 for an absent process. A shell conditional lets us assert absence explicitly.
    const pid = shell('sh', '-c', `'pidof ${app} || true'`);
    if (!/^\d+$/.test(pid)) throw new Error(`App did not start: ${pid}`);
    return pid;
  };
  const errors = [];
  let result;
  try {
    phase('seed');
    stop();
    const before = launch();
    stop();
    if (shell('sh', '-c', `'pidof ${app} || true'`)) throw new Error('App survived force-stop');
    const after = launch();
    if (before === after) throw new Error('Relaunch reused the old process');
    phase('verify');
    result = { before, after };
  } catch (error) {
    errors.push(error);
  } finally {
    for (const cleanup of [() => phase('cleanup'), stop]) {
      try { cleanup(); } catch (error) { errors.push(error); }
    }
  }
  if (errors.length === 1) throw errors[0];
  if (errors.length > 1) throw new AggregateError(errors, 'Relaunch and cleanup failed');
  return result;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { values } = parseArgs({ options: { adb: { type: 'string' }, serial: { type: 'string' } } });
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  const adb = values.adb || (sdk ? join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb') : 'adb');
  const { before, after } = runRelaunch(adb, undefined, values.serial);
  console.log(`Android persistence passed across process ${before} → ${after}`);
}
