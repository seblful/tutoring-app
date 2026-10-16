// How far apart two pictures of the same moment are: the video's GPU frame and a screenshot of it.

/**
 * The difference between images [a] and [b] (decoded PNGs of one size): the mean difference of a colour
 * channel, in 8-bit levels, and the share of pixels off by more than [far] levels in some channel. Two
 * renderers' anti-aliasing differs by a level or so along edges; a missing or misplaced element differs
 * by tens over a whole region.
 */
export function difference(a, b, far = 48) {
  if (a.width !== b.width || a.height !== b.height) {
    throw new Error(`The pictures differ in size: ${a.width}×${a.height} and ${b.width}×${b.height}`);
  }
  let sum = 0, off = 0;
  const n = a.width * a.height;
  for (let i = 0; i < n; i++) {
    let most = 0;
    for (let k = 0; k < 3; k++) {
      const d = Math.abs(a.pixels[i * a.channels + k] - b.pixels[i * b.channels + k]);
      sum += d;
      if (d > most) most = d;
    }
    if (most > far) off++;
  }
  return { mean: sum / (n * 3), off: off / n };
}
