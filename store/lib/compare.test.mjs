import assert from "node:assert/strict";
import { test } from "node:test";
import { difference } from "./compare.mjs";

const image = (width, height, channels, at) => {
  const pixels = new Uint8Array(width * height * channels);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) pixels.set(at(x, y), (y * width + x) * channels);
  }
  return { width, height, channels, pixels };
};

test("the same picture differs by nothing, whatever its channels", () => {
  const rgb = image(4, 3, 3, (x, y) => [x * 10, y * 20, 200]);
  const rgba = image(4, 3, 4, (x, y) => [x * 10, y * 20, 200, 255]);
  assert.deepEqual(difference(rgb, rgba), { mean: 0, off: 0 });
});

test("a level along an edge is near, a region gone is far", () => {
  const base = image(10, 10, 3, () => [100, 100, 100]);
  const edge = image(10, 10, 3, (x) => (x === 5 ? [101, 100, 100] : [100, 100, 100]));
  const gone = image(10, 10, 3, (x, y) => (y < 5 ? [0, 0, 0] : [100, 100, 100]));
  const near = difference(base, edge);
  assert.ok(near.mean < 0.1 && near.off === 0);
  const missing = difference(base, gone);
  assert.ok(missing.mean > 30);
  assert.equal(missing.off, 0.5);
});

test("pictures of different sizes cannot be held against each other", () => {
  assert.throws(() => difference(image(2, 2, 3, () => [0, 0, 0]), image(2, 3, 3, () => [0, 0, 0])), /differ in size/);
});
