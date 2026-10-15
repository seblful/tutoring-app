// Things on a capture that StoreScreenshots did not mark, found in its pixels instead: a card from a
// point inside it, and a row of outlined segments. Each returns [left, top, right, bottom, corner] in the
// capture's pixels, as a mark does, and throws rather than guess.

const at = (image, x, y) => (y * image.width + x) * image.channels;
const color = (image, x, y) => [0, 1, 2].map((k) => image.pixels[at(image, x, y) + k]);
const near = (a, b, tolerance = 6) => a.every((v, k) => Math.abs(v - b[k]) <= tolerance);

/**
 * The card around (x, y): the run of the card's own colour from that point out to each edge. [x] should
 * sit in the card's padding, clear of its words, so the run up and down is not cut short by them; the
 * run across is taken a corner's depth below the card's top, past its rounding and above its words. The
 * corner is the card's shape, which the pixels do not tell, so it is passed in.
 */
export function cardAround(image, x, y, corner) {
  const start = [x, y];
  const inside = color(image, x, y);
  const run = (dx, dy) => {
    let px = x, py = y;
    while (px + dx >= 0 && px + dx < image.width && py + dy >= 0 && py + dy < image.height &&
      near(color(image, px + dx, py + dy), inside)) {
      px += dx;
      py += dy;
    }
    return dx ? px : py;
  };
  const top = run(0, -1), bottom = run(0, 1) + 1;
  y = top + Math.min(corner, Math.floor((bottom - top) / 2));
  const left = run(-1, 0), right = run(1, 0) + 1;
  if (right - left < 0.3 * image.width || bottom - top < 40) {
    throw new Error(`No card around (${start}): only ${right - left} × ${bottom - top} px of its colour`);
  }
  return [left, top, right, bottom, corner];
}

/**
 * The first row of outlined segments at or below [from]: two dark lines straight across most of the
 * screen — its top and bottom edges — with the segments between. Dividers are lighter and single, and
 * a line of words never runs dark all the way across, so neither is mistaken for one.
 */
export function outlinedRow(image, from) {
  const x0 = Math.round(image.width * 0.1), x1 = Math.round(image.width * 0.9);
  const line = (y) => {
    const first = color(image, x0, y);
    if (0.3 * first[0] + 0.59 * first[1] + 0.11 * first[2] > 160) return false;
    let same = 0;
    for (let x = x0; x < x1; x += 2) if (near(color(image, x, y), first, 24)) same++;
    return same >= 0.8 * ((x1 - x0) / 2);
  };
  const lines = [];
  for (let y = from; y < image.height && lines.length < 2; y++) {
    if (!line(y)) continue;
    const start = y;
    while (y + 1 < image.height && line(y + 1)) y++;
    lines.push([start, y + 1]);
  }
  if (lines.length < 2) throw new Error(`No row of outlined segments below y = ${from}`);
  const [[top], [, bottom]] = lines;
  if (bottom - top < 40 || bottom - top > 400) throw new Error(`The outlined row found is ${bottom - top} px tall`);
  // Out along the middle to the row's ends, and through the outline there.
  const edge = color(image, x0, top);
  const mid = Math.round((top + bottom) / 2);
  const out = (x, dx) => {
    while (x + dx >= 0 && x + dx < image.width && !near(color(image, x + dx, mid), edge, 24)) x += dx;
    while (x + dx >= 0 && x + dx < image.width && near(color(image, x + dx, mid), edge, 24)) x += dx;
    return x;
  };
  return [out(x0, -1), top, out(x1, 1) + 1, bottom, (bottom - top) / 2];
}
