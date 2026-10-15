// node --test store/ — joining two captures of a scrolled list (scroll.mjs).

import assert from "node:assert/strict";
import { test } from "node:test";
import { joinScroll } from "./scroll.mjs";

/**
 * A tall list of noisy rows with gaps between them, seen through a window [height] tall from [offset].
 * The gaps are not one colour across — a card's edges run down them, as on the app's screens.
 */
function list(width, height, offset) {
  const pixels = new Uint8Array(width * height * 3);
  for (let y = 0; y < height; y++) {
    const row = y + offset;
    const plain = row % 40 >= 30;
    for (let x = 0; x < width; x++) {
      const v = plain ? (x < 5 || x >= width - 5 ? 240 : 200) : (row * 31 + x * 17 + ((row * x) % 13)) % 256;
      pixels.set([v, v, v], (y * width + x) * 3);
    }
  }
  return { width, height, channels: 3, pixels };
}

test("finds how far the list moved, from a guess a pixel or two out", () => {
  const a = list(60, 300, 0), b = list(60, 300, 137);
  const { d, seam } = joinScroll(a, b, [0, 0, 60, 300], 135);
  assert.equal(d, 137);
  assert.ok(seam >= d && seam < 300, `joint at ${seam}`);
  assert.ok(seam % 40 >= 32 && seam % 40 < 38, `the joint, at ${seam}, is inside a gap`);
});

test("refuses captures that do not overlap", () => {
  assert.throws(() => joinScroll(list(60, 300, 0), list(60, 300, 400), [0, 0, 60, 300], 400), /overlap/);
});

test("refuses captures that are not the same list", () => {
  const other = list(60, 300, 0);
  other.pixels.reverse();
  assert.throws(() => joinScroll(list(60, 300, 0), other, [0, 0, 60, 300], 100), /line up/);
});
