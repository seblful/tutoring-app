// node --test store/ — the film's arithmetic (parts.js), run as the page runs it: a plain script.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { runInNewContext } from "node:vm";

const context = {};
runInNewContext(readFileSync(new URL("./parts.js", import.meta.url), "utf8"), context);
const { exposures, counting, swipe } = context.filmParts;

const plain = (x) => JSON.parse(JSON.stringify(x));
const close = (actual, expected) => assert.ok(Math.abs(actual - expected) < 1e-9, `${actual} ≠ ${expected}`);

test("a still frame is one exposure, a moving one several, inside the film", () => {
  assert.deepEqual(plain(exposures(5, 0.5, { fps: 60 })), [5]);
  const moving = exposures(5, 20, { fps: 60 });
  assert.equal(moving.length, 4);
  close(moving[0], 5 - (0.375 * 0.5) / 60);
  close(moving[3], 5 + (0.375 * 0.5) / 60);
  assert.equal(exposures(5, 6, { fps: 60, scale: 2 }).length, 3);
  assert.ok(exposures(0, 20, { fps: 60, lo: 0, hi: 42 }).every((t) => t >= 0));
});

test("a frame moving faster than its exposures can blend is shot with a shorter shutter", () => {
  const fast = exposures(5, 240, { fps: 60 });
  assert.equal(fast.length, 12);
  // 12 exposures 2.5 px apart: a 30 px smear, not the 120 px a half-open shutter would make.
  const smear = ((fast[11] - fast[0]) * 12) / 11 * 60 * 240;
  close(smear, 30);
});

test("an amount counts up written the app's way", () => {
  const usd = counting("736 USD");
  assert.equal(usd.value, 736);
  assert.equal(usd.at(0), "0 USD");
  assert.equal(usd.at(0.5), "368 USD");
  assert.equal(usd.at(1), "736 USD");

  const grouped = counting("1,250.50 USD");
  assert.equal(grouped.at(1), "1,250.50 USD");
  assert.equal(grouped.at(0.5), "625.25 USD");

  const russian = counting("1 250,5 BYN");
  assert.equal(russian.value, 1250.5);
  assert.equal(russian.at(1), "1 250,5 BYN");
  assert.equal(russian.at(0.1), "125,1 BYN");
  assert.equal(counting("1 250 RUB").at(0.9), "1 125 RUB");
});

test("words with no number are refused", () => {
  assert.throws(() => counting("No income"), /No number/);
});

test("a swiped row goes out over the drag, holds, and is gone on the lift", () => {
  const s = swipe({ unarmed: 30, armed: 50, drag: [1, 2], release: 2.5 });
  assert.equal(s.at(0.5), 0);
  close(s.at(1.5), 25);
  assert.equal(s.at(2.2), 50);
  assert.equal(s.at(2.5), 0);
});

test("a swipe crosses its mark the first moment it is past the shot short of it", () => {
  const s = swipe({ unarmed: 30, armed: 50, drag: [1, 2], release: 2.5 });
  assert.ok(s.at(s.crossing) > 30);
  assert.ok(s.at(s.crossing - 1 / 240) <= 30);
  assert.ok(Math.abs(s.crossing - 1.6) <= 1 / 240, `${s.crossing}`);
});

test("a swipe needs shots short of its mark and past it, and a drag before the lift", () => {
  assert.throws(() => swipe({ unarmed: 50, armed: 30, drag: [1, 2], release: 2.5 }), /short of its mark/);
  assert.throws(() => swipe({ unarmed: 0, armed: 30, drag: [1, 2], release: 2.5 }), /short of its mark/);
  assert.throws(() => swipe({ unarmed: 10, armed: 30, drag: [1, 2], release: 1.5 }), /dragged, then let go/);
});
