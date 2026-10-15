// node --test store/ — finding unmarked things on a capture (find.mjs).

import assert from "node:assert/strict";
import { test } from "node:test";
import { cardAround, outlinedRow } from "./find.mjs";

/** A light screen [width] × [height], with [paint] filling rectangles `[l, t, r, b, rgb]` in order. */
function screen(width, height, ...paint) {
  const pixels = new Uint8Array(width * height * 3).fill(250);
  for (const [l, t, r, b, rgb] of paint) {
    for (let y = t; y < b; y++) for (let x = l; x < r; x++) pixels.set(rgb, (y * width + x) * 3);
  }
  return { width, height, channels: 3, pixels };
}

test("finds a card from a point in its padding, words and all", () => {
  const image = screen(400, 600, [20, 100, 380, 300, [223, 224, 255]], [60, 180, 300, 210, [20, 20, 90]]);
  assert.deepEqual(cardAround(image, 30, 195, 64), [20, 100, 380, 300, 64]);
});

test("refuses a point that is not in a card", () => {
  const image = screen(400, 600, [60, 150, 70, 160, [20, 20, 90]]);
  assert.throws(() => cardAround(image, 65, 155, 64), /No card/);
});

test("finds the row of outlined segments, not the divider or the words above it", () => {
  const outline = [119, 118, 128];
  const image = screen(400, 600,
    [0, 200, 400, 202, [199, 197, 208]], // a divider: lighter
    [60, 230, 200, 240, [30, 30, 30]], // words
    [16, 300, 384, 302, outline], [16, 360, 384, 362, outline], // the row's top and bottom edges
    [16, 300, 18, 362, outline], [382, 300, 384, 362, outline], // and its ends
    [150, 325, 250, 337, [30, 30, 30]]); // a segment's label
  assert.deepEqual(outlinedRow(image, 150), [16, 300, 384, 362, 31]);
});

test("refuses a screen with no outlined row", () => {
  assert.throws(() => outlinedRow(screen(400, 600, [0, 200, 400, 202, [199, 197, 208]]), 100), /No row/);
});
