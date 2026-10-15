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
