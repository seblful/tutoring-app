// PNG in and out, with no dependencies: Chrome's screenshots and the app's captures decoded to raw pixels,
// and the listing's graphics encoded back at the size Play asks for.

import { deflateSync, inflateSync } from "node:zlib";

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

/** Decodes an 8-bit RGB or RGBA PNG — all Chrome writes — into its raw pixels. */
export function decode(png) {
  const width = png.readUInt32BE(16), height = png.readUInt32BE(20), depth = png[24], type = png[25];
  if (depth !== 8 || (type !== 2 && type !== 6)) throw new Error(`Unexpected PNG colour type ${type}, depth ${depth}`);
  const channels = type === 6 ? 4 : 3;
  const idat = [];
  for (let at = 8; at < png.length;) {
    const length = png.readUInt32BE(at), kind = png.toString("latin1", at + 4, at + 8);
    if (kind === "IDAT") idat.push(png.subarray(at + 8, at + 8 + length));
    at += 12 + length;
  }
  const data = inflateSync(Buffer.concat(idat));
  const stride = width * channels, pixels = Buffer.alloc(height * stride);
  // One loop per filter rather than a choice per byte: the screenshots' captures are 30 million pixels.
  for (let y = 0; y < height; y++) {
    const filter = data[y * (stride + 1)], from = y * (stride + 1) + 1, row = y * stride, up = row - stride;
    for (let x = 0; x < stride; x++) {
      const raw = data[from + x];
      const a = x >= channels ? pixels[row + x - channels] : 0;
      const b = y > 0 ? pixels[up + x] : 0;
      let value;
      switch (filter) {
        case 0: value = raw; break;
        case 1: value = raw + a; break;
        case 2: value = raw + b; break;
        case 3: value = raw + ((a + b) >> 1); break;
        default: value = raw + paeth(a, b, x >= channels && y > 0 ? pixels[up + x - channels] : 0);
      }
      pixels[row + x] = value & 0xff;
    }
  }
  return { width, height, channels, pixels };
}

/**
 * Averages every [factor] × [factor] block into one pixel and writes [channels] per pixel: 3 (RGB,
 * Play's "24-bit, no alpha") or 4 (RGBA, its "32-bit"). The pages are drawn at [factor]× and brought down
 * here, so a phone shrunk to a third of its size still has smooth text and edges — at 1× Chrome samples
 * it once per pixel, and the feature graphic's small phone came out soft and jagged.
 */
export function encode({ width, height, channels: from, pixels }, factor, channels) {
  const w = width / factor, h = height / factor, area = factor * factor;
  const stride = w * channels + 1, out = Buffer.alloc(h * stride);
  for (let y = 0; y < h; y++) {
    out[y * stride] = 0; // no filter
    for (let x = 0; x < w; x++) {
      for (let k = 0; k < 3; k++) {
        let sum = 0;
        for (let dy = 0; dy < factor; dy++) {
          for (let dx = 0; dx < factor; dx++) sum += pixels[((y * factor + dy) * width + x * factor + dx) * from + k];
        }
        out[y * stride + 1 + x * channels + k] = Math.round(sum / area);
      }
      if (channels === 4) out[y * stride + 1 + x * 4 + 3] = 0xff; // every page here is opaque
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(w, 0); header.writeUInt32BE(h, 4);
  header[8] = 8; header[9] = channels === 4 ? 6 : 2; // 8-bit truecolour, with or without alpha
  return Buffer.concat([PNG_SIGNATURE, chunk("IHDR", header), chunk("IDAT", deflateSync(out, { level: 9 })),
    chunk("IEND", Buffer.alloc(0))]);
}

/**
 * [image] blurred by a Gaussian of [sigma] pixels — near enough: three box blurs, each a running sum, so
 * a whole capture takes a moment at any [sigma] — its colour weighted by its alpha so a transparent edge
 * does not darken what it borders.
 */
export function blur({ width, height, channels, pixels }, sigma) {
  // The three boxes whose sizes give the Gaussian's spread (Kutskir's "fastest Gaussian blur").
  const ideal = Math.sqrt(4 * sigma * sigma + 1);
  let lower = Math.floor(ideal);
  if (lower % 2 === 0) lower--;
  const upper = lower + 2;
  const smaller = Math.round((12 * sigma * sigma - 3 * lower * lower - 12 * lower - 9) / (-4 * lower - 4));
  const radii = [0, 1, 2].map((i) => ((i < smaller ? lower : upper) - 1) / 2);

  const n = width * height, alpha = channels === 4;
  // Premultiplied, in floats, one channel plane at a time.
  const planes = Array.from({ length: channels }, () => new Float32Array(n));
  for (let i = 0; i < n; i++) {
    const a = alpha ? pixels[i * 4 + 3] / 255 : 1;
    for (let k = 0; k < channels; k++) planes[k][i] = k === 3 ? a : pixels[i * channels + k] * a;
  }
  /** One box of [r] along [lines] lines of [length], [step] apart within a line and [stride] between them. */
  const box = (from, to, r, length, lines, step, stride) => {
    const width = 2 * r + 1, last = length - 1;
    for (let line = 0; line < lines; line++) {
      const base = line * stride, at = (p) => from[base + Math.min(last, Math.max(0, p)) * step];
      let sum = 0;
      for (let j = -r; j <= r; j++) sum += at(j);
      for (let p = 0; p < length; p++) {
        to[base + p * step] = sum / width;
        sum += at(p + r + 1) - at(p - r);
      }
    }
  };
  const spare = new Float32Array(n);
  for (const plane of planes) {
    for (const r of radii) {
      box(plane, spare, r, width, height, 1, width);
      box(spare, plane, r, height, width, width, 1);
    }
  }
  const out = Buffer.alloc(n * channels);
  for (let i = 0; i < n; i++) {
    const a = alpha ? planes[3][i] : 1;
    for (let k = 0; k < channels; k++) {
      const v = k === 3 ? a * 255 : a > 0 ? planes[k][i] / a : 0;
      out[i * channels + k] = Math.min(255, Math.max(0, Math.round(v)));
    }
  }
  return { width, height, channels, pixels: out };
}

/** [image] as a PNG at its own size, with its alpha if it has one; quickly compressed, for scratch files. */
export function write({ width, height, channels, pixels }) {
  const stride = width * channels + 1, out = Buffer.alloc(height * stride);
  for (let y = 0; y < height; y++) pixels.copy(out, y * stride + 1, y * width * channels, (y + 1) * width * channels);
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0); header.writeUInt32BE(height, 4);
  header[8] = 8; header[9] = channels === 4 ? 6 : 2;
  return Buffer.concat([PNG_SIGNATURE, chunk("IHDR", header), chunk("IDAT", deflateSync(out, { level: 1 })),
    chunk("IEND", Buffer.alloc(0))]);
}

function paeth(a, b, c) {
  const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}

function chunk(kind, data) {
  const head = Buffer.alloc(8);
  head.writeUInt32BE(data.length, 0);
  head.write(kind, 4, "latin1");
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), data])), 0);
  return Buffer.concat([head, data, crc]);
}

function crc32(bytes) {
  let crc = ~0;
  for (const byte of bytes) {
    crc ^= byte;
    for (let k = 0; k < 8; k++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return ~crc >>> 0;
}
