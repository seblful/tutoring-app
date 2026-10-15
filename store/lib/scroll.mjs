// A list scrolled in the video from two captures of it: the screen as it opens, and the same screen
// scrolled further down. Where the two overlap they show the same rows, so one picture of the whole
// list can be made by joining them — at a row no text crosses, so a joint a pixel out could not show.

/**
 * How far the list in [list] (left, top, right, bottom, in capture pixels) moved between capture [a]
 * and capture [b], to the pixel — [guess], from where a row on both was recorded, may be a pixel or two
 * out — and the row, counted from the list's top, at which to join them. Throws if the two do not line up.
 */
export function joinScroll(a, b, list, guess) {
  const [left, top, right, bottom] = list;
  if (guess <= 0 || guess >= bottom - top) {
    throw new Error(`The list moved ${guess} px; the two captures must overlap and differ`);
  }
  const at = (image, x, y) => (y * image.width + x) * image.channels;
  let best = null;
  for (let d = Math.max(1, guess - 6); d <= guess + 6 && d < bottom - top; d++) {
    let diff = 0, n = 0;
    for (let y = top + d; y < bottom; y += 3) {
      for (let x = left; x < right; x += 5) {
        const i = at(a, x, y), j = at(b, x, y - d);
        for (let k = 0; k < 3; k++) diff += Math.abs(a.pixels[i + k] - b.pixels[j + k]);
        n++;
      }
    }
    const error = diff / n;
    if (!best || error < best.error) best = { d, error };
  }
  if (best.error > 2) throw new Error(`The two captures of the list do not line up (off by ${best.error.toFixed(1)} a pixel)`);

  // A row no text crosses: the same as the rows either side of it, all the way across — card edges and
  // backgrounds run straight down, words do not.
  const same = (image, y1, y2) => {
    for (let x = left; x < right; x++) {
      const i = at(image, x, y1), j = at(image, x, y2);
      for (let k = 0; k < 3; k++) if (Math.abs(image.pixels[i + k] - image.pixels[j + k]) > 3) return false;
    }
    return true;
  };
  const flat = (image, y) => y >= 2 && y + 2 < image.height && [-2, -1, 1, 2].every((dy) => same(image, y, y + dy));
  // Searched outwards from the middle of the overlap, so the joint is well inside both captures.
  const from = top + best.d, middle = Math.round((from + bottom) / 2);
  for (let step = 0; middle - step >= from || middle + step < bottom; step++) {
    for (const y of [middle - step, middle + step]) {
      if (y >= from && y < bottom && flat(a, y) && flat(b, y - best.d)) return { d: best.d, seam: y - top };
    }
  }
  throw new Error("The two captures of the list share no row free of text to join them at");
}
