// node --test store/ — pictures in and out (png.mjs).

import assert from "node:assert/strict";
import { test } from "node:test";
import { blur, decode, write } from "./png.mjs";

/** A [width] × [height] picture of [channels] channels, each pixel from [paint](x, y). */
function picture(width, height, channels, paint) {
  const pixels = Buffer.alloc(width * height * channels);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) pixels.set(paint(x, y), (y * width + x) * channels);
  return { width, height, channels, pixels };
}

test("a picture written out reads back the same, alpha and all", () => {
  const image = picture(3, 2, 4, (x, y) => [x * 80, y * 100, 7, x === 0 ? 0 : 255]);
  const back = decode(write(image));
  assert.deepEqual({ ...back, pixels: [...back.pixels] }, { ...image, pixels: [...image.pixels] });
});

test("blurring spreads an edge and keeps flat colour flat", () => {
  const image = blur(picture(20, 1, 3, (x) => (x < 10 ? [0, 0, 0] : [200, 200, 200])), 1.5);
  const at = (x) => image.pixels[x * 3];
  assert.equal(at(0), 0);
  assert.equal(at(19), 200);
  assert.ok(at(9) > 0 && at(9) < 100 && at(10) > 100 && at(10) < 200, `edge ${at(9)}, ${at(10)}`);
});

test("blurring next to transparency does not darken the colour", () => {
  const image = blur(picture(20, 1, 4, (x) => (x < 10 ? [0, 0, 0, 0] : [240, 240, 240, 255])), 1.5);
  const edge = image.pixels.subarray(10 * 4, 10 * 4 + 4);
  assert.deepEqual([...edge.subarray(0, 3)], [240, 240, 240]);
  assert.ok(edge[3] < 255);
});
