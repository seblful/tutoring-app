// Writes an MP4 from what Chrome's WebCodecs encoders hand back — H.264 video and AAC audio, each sample
// already compressed — so the video needs no ffmpeg. Only what a player and YouTube need: one video and
// one audio track, every sample its own chunk, interleaved by time, and the index (`moov`) ahead of the
// data so the file starts playing before it has finished loading.

/**
 * @param video `{ width, height, fps, avcC, color, samples: [{ data, key }] }` in decode order, one per
 *   frame; `color` is the VideoColorSpaceInit the encoder reported, or null.
 * @param audio `{ sampleRate, channels, config, samples: [{ data }] }`, AAC frames of 1024 samples each;
 *   `config` is the AudioSpecificConfig the encoder described its stream with.
 */
export function mp4({ video, audio }) {
  const TIMESCALE = 1000;
  const frameTicks = 1000; // the video track counts fps × 1000 ticks a second, a frame being 1000
  const videoScale = video.fps * frameTicks;
  const videoDuration = video.samples.length * frameTicks;
  const audioDuration = audio.samples.length * 1024;
  const movieDuration = Math.max(
    Math.round(videoDuration / videoScale * TIMESCALE),
    Math.round(audioDuration / audio.sampleRate * TIMESCALE),
  );

  // Both tracks' samples in the order they play, so a player reading from the start finds each frame's
  // sound beside it.
  const order = [
    ...video.samples.map((s, i) => ({ s, track: "video", at: i / video.fps })),
    ...audio.samples.map((s, i) => ({ s, track: "audio", at: (i * 1024) / audio.sampleRate })),
  ].sort((a, b) => a.at - b.at || (a.track === "video" ? -1 : 1));

  const build = (base) => {
    const offsets = { video: [], audio: [] };
    let at = base;
    for (const { s, track } of order) {
      offsets[track].push(at);
      at += s.data.length;
    }
    return box("moov",
      fullBox("mvhd", 0, 0, u32(0), u32(0), u32(TIMESCALE), u32(movieDuration), u32(0x00010000), u16(0x0100),
        zeros(10), MATRIX, zeros(24), u32(3)),
      track({
        id: 1, duration: Math.round(videoDuration / videoScale * TIMESCALE), width: video.width, height: video.height,
        handler: "vide", name: "Klas video", timescale: videoScale, mediaDuration: videoDuration,
        header: fullBox("vmhd", 0, 1, zeros(8)),
        entry: box("avc1", zeros(6), u16(1), zeros(16), u16(video.width), u16(video.height), u32(0x00480000),
          u32(0x00480000), u32(0), u16(1), zeros(32), u16(0x0018), u16(0xffff),
          box("avcC", video.avcC),
          colr(video.color),
          box("pasp", u32(1), u32(1))),
        deltas: [[video.samples.length, frameTicks]],
        sizes: video.samples.map((s) => s.data.length),
        offsets: offsets.video,
        keys: video.samples.flatMap((s, i) => (s.key ? [i + 1] : [])),
      }),
      track({
        id: 2, duration: Math.round(audioDuration / audio.sampleRate * TIMESCALE), volume: 0x0100,
        handler: "soun", name: "Klas audio", timescale: audio.sampleRate, mediaDuration: audioDuration,
        header: fullBox("smhd", 0, 0, u16(0), u16(0)),
        entry: box("mp4a", zeros(6), u16(1), zeros(8), u16(audio.channels), u16(16), u16(0), u16(0),
          u32(audio.sampleRate * 0x10000), esds(audio.config)),
        deltas: [[audio.samples.length, 1024]],
        sizes: audio.samples.map((s) => s.data.length),
        offsets: offsets.audio,
      }),
    );
  };

  const ftyp = box("ftyp", ascii("isom"), u32(0x200), ascii("isom"), ascii("iso2"), ascii("avc1"), ascii("mp41"));
  const dataSize = order.reduce((sum, { s }) => sum + s.data.length, 0);
  // The index's size does not depend on the offsets it holds, so it is built once to measure it and once
  // with the offsets that measurement gives.
  const placeholder = build(0);
  const mdatHeader = Buffer.concat([u32(dataSize + 8), ascii("mdat")]);
  const moov = build(ftyp.length + placeholder.length + mdatHeader.length);
  return Buffer.concat([ftyp, moov, mdatHeader, ...order.map(({ s }) => Buffer.from(s.data))]);
}

function track({ id, duration, width = 0, height = 0, volume = 0, handler, name, timescale, mediaDuration,
  header, entry, deltas, sizes, offsets, keys }) {
  const offsetBox = offsets.at(-1) > 0xffffffff
    ? fullBox("co64", 0, 0, u32(offsets.length), ...offsets.map(u64))
    : fullBox("stco", 0, 0, u32(offsets.length), ...offsets.map(u32));
  return box("trak",
    fullBox("tkhd", 0, 3, u32(0), u32(0), u32(id), u32(0), u32(duration), zeros(8), u16(0), u16(0), u16(volume),
      u16(0), MATRIX, u32(width * 0x10000), u32(height * 0x10000)),
    box("mdia",
      fullBox("mdhd", 0, 0, u32(0), u32(0), u32(timescale), u32(mediaDuration), u16(0x55c4), u16(0)), // «und»
      fullBox("hdlr", 0, 0, u32(0), ascii(handler), zeros(12), Buffer.from(`${name}\0`, "utf8")),
      box("minf",
        header,
        box("dinf", fullBox("dref", 0, 0, u32(1), fullBox("url ", 0, 1))),
        box("stbl",
          fullBox("stsd", 0, 0, u32(1), entry),
          fullBox("stts", 0, 0, u32(deltas.length), ...deltas.flatMap(([count, delta]) => [u32(count), u32(delta)])),
          ...(keys ? [fullBox("stss", 0, 0, u32(keys.length), ...keys.map(u32))] : []),
          fullBox("stsc", 0, 0, u32(1), u32(1), u32(1), u32(1)),
          fullBox("stsz", 0, 0, u32(0), u32(sizes.length), ...sizes.map(u32)),
          offsetBox))));
}

/**
 * How the frames' YUV maps back to colour, as the encoder reported it — a player guessing wrong shifts every
 * colour slightly. Without a report, BT.709 in limited range: what YouTube assumes of HD.
 */
function colr(color) {
  const code = (table, name, fallback) => table[name] ?? fallback;
  const primaries = code({ bt709: 1, bt470bg: 5, smpte170m: 6, bt2020: 9, smpte432: 12 }, color?.primaries, 1);
  const transfer = code({ bt709: 1, smpte170m: 6, linear: 8, "iec61966-2-1": 13, pq: 16, hlg: 18 }, color?.transfer, 1);
  const matrix = code({ rgb: 0, bt709: 1, bt470bg: 5, smpte170m: 6, "bt2020-ncl": 9 }, color?.matrix, 1);
  return box("colr", ascii("nclx"), u16(primaries), u16(transfer), u16(matrix), u8(color?.fullRange ? 0x80 : 0));
}

/** The MPEG-4 audio descriptor an `mp4a` entry carries, around the encoder's AudioSpecificConfig. */
function esds(config) {
  const descriptor = (tag, ...parts) => {
    const body = Buffer.concat(parts);
    return Buffer.concat([u8(tag), Buffer.from([0x80, 0x80, 0x80, body.length]), body]);
  };
  return fullBox("esds", 0, 0,
    descriptor(0x03, u16(0), u8(0),
      descriptor(0x04, u8(0x40), u8(0x15), Buffer.from([0, 0, 0]), u32(0), u32(0),
        descriptor(0x05, Buffer.from(config))),
      descriptor(0x06, u8(0x02))));
}

const MATRIX = Buffer.concat([0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000].map(u32));

function box(type, ...parts) {
  const body = Buffer.concat(parts);
  return Buffer.concat([u32(body.length + 8), ascii(type), body]);
}

function fullBox(type, version, flags, ...parts) {
  return box(type, Buffer.from([version, flags >> 16 & 0xff, flags >> 8 & 0xff, flags & 0xff]), ...parts);
}

function u8(n) {
  return Buffer.from([n]);
}

function u16(n) {
  const b = Buffer.alloc(2);
  b.writeUInt16BE(n);
  return b;
}

function u32(n) {
  const b = Buffer.alloc(4);
  b.writeUInt32BE(n >>> 0);
  return b;
}

function u64(n) {
  const b = Buffer.alloc(8);
  b.writeBigUInt64BE(BigInt(n));
  return b;
}

function zeros(n) {
  return Buffer.alloc(n);
}

function ascii(s) {
  return Buffer.from(s, "latin1");
}
