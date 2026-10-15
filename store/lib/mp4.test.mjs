// node --test store/
import assert from "node:assert/strict";
import { test } from "node:test";
import { mp4 } from "./mp4.mjs";

const bytes = (...values) => Buffer.from(values);

/** Three frames at 30 fps (the first a key frame) and two AAC frames at 48 kHz. */
function sample() {
  return mp4({
    video: {
      width: 64, height: 32, fps: 30, avcC: bytes(1, 0x64, 0, 0x28), color: null,
      samples: [{ data: bytes(1, 1, 1), key: true }, { data: bytes(2, 2), key: false }, { data: bytes(3), key: true }],
    },
    audio: { sampleRate: 48000, channels: 2, config: bytes(0x11, 0x90), samples: [{ data: bytes(9, 9) }, { data: bytes(8) }] },
  });
}

/** The boxes in [buf] between [from] and [to]: type, where its body starts, and its size. */
function boxes(buf, from = 0, to = buf.length) {
  const out = [];
  for (let at = from; at < to;) {
    const size = buf.readUInt32BE(at);
    out.push({ type: buf.toString("latin1", at + 4, at + 8), body: at + 8, end: at + size });
    at += size;
  }
  return out;
}
const child = (buf, box, type) => boxes(buf, box.body, box.end).find((b) => b.type === type);
const path = (buf, box, ...types) => types.reduce((b, type) => child(buf, b, type), box);
/** A full box's entries, as unsigned 32-bit numbers after its version, flags and count. */
const table = (buf, box) => {
  const count = buf.readUInt32BE(box.body + 4);
  return Array.from({ length: count }, (_, i) => buf.readUInt32BE(box.body + 8 + i * 4));
};

test("the index comes before the data and points at every sample, in the order they play", () => {
  const file = sample();
  const top = boxes(file);
  assert.deepEqual(top.map((b) => b.type), ["ftyp", "moov", "mdat"]);
  const [video, audio] = boxes(file, top[1].body, top[1].end).filter((b) => b.type === "trak");
  const read = (trak) => {
    const stbl = path(file, trak, "mdia", "minf", "stbl");
    const sizes = (() => {
      const stsz = child(file, stbl, "stsz");
      const count = file.readUInt32BE(stsz.body + 8);
      return Array.from({ length: count }, (_, i) => file.readUInt32BE(stsz.body + 12 + i * 4));
    })();
    return table(file, child(file, stbl, "stco")).map((at, i) => [...file.subarray(at, at + sizes[i])]);
  };
  assert.deepEqual(read(video), [[1, 1, 1], [2, 2], [3]]);
  assert.deepEqual(read(audio), [[9, 9], [8]]);
  // By time: frame 0 and sound 0 at 0 s, sound 1 at 21 ms, frames 1 and 2 at 33 and 67 ms.
  assert.deepEqual([...file.subarray(top[2].body, top[2].end)], [1, 1, 1, 9, 9, 8, 2, 2, 3]);
});

test("durations and key frames are those of the samples given", () => {
  const file = sample();
  const moov = boxes(file)[1];
  const mvhd = child(file, moov, "mvhd");
  assert.equal(file.readUInt32BE(mvhd.body + 12), 1000); // timescale: milliseconds
  assert.equal(file.readUInt32BE(mvhd.body + 16), 100); // three frames at 30 fps outlast 2048 samples
  const [video, audio] = boxes(file, moov.body, moov.end).filter((b) => b.type === "trak");
  const mdhd = (trak) => path(file, trak, "mdia", "mdhd");
  assert.equal(file.readUInt32BE(mdhd(video).body + 12), 30000);
  assert.equal(file.readUInt32BE(mdhd(video).body + 16), 3000);
  assert.equal(file.readUInt32BE(mdhd(audio).body + 12), 48000);
  assert.equal(file.readUInt32BE(mdhd(audio).body + 16), 2048);
  assert.deepEqual(table(file, path(file, video, "mdia", "minf", "stbl", "stss")), [1, 3]);
  assert.equal(path(file, audio, "mdia", "minf", "stbl", "stss"), undefined);
});
