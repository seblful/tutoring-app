// Chrome's own encoders, through WebCodecs: each frame is encoded to H.264, the soundtrack to AAC. A frame
// comes from the GPU's canvas as it is (gpu.js), or, where Chrome cannot draw the page on the GPU, as the
// JPEGs video.mjs screenshotted — one, or several exposures of a moving frame to blend into one. What they produce goes back to Node in pieces, as base64,
// to be written into an MP4 (store/lib/mp4.mjs).

"use strict";

window.encoder = (() => {
  let video, fps, chunks = [], avcC = null, color = null, failure = null;
  let canvas = null;

  /**
   * The exposures of one frame, averaged: what a camera's open shutter records of something moving, a
   * blur along its path. One exposure is the frame as it is.
   */
  async function blend(jpegs) {
    const bitmaps = await Promise.all(jpegs.map((jpeg) =>
      createImageBitmap(new Blob([Uint8Array.fromBase64(jpeg)], { type: "image/jpeg" }))));
    if (bitmaps.length === 1) return bitmaps[0];
    const { width, height } = bitmaps[0];
    if (!canvas || canvas.width !== width || canvas.height !== height) canvas = new OffscreenCanvas(width, height);
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    const sum = new Uint16Array(width * height * 4);
    for (const bitmap of bitmaps) {
      ctx.drawImage(bitmap, 0, 0);
      bitmap.close();
      const { data } = ctx.getImageData(0, 0, width, height);
      for (let i = 0; i < data.length; i++) sum[i] += data[i];
    }
    // A clamped array rounds to the nearest on its own.
    const out = ctx.createImageData(width, height), n = bitmaps.length;
    for (let i = 0; i < sum.length; i++) out.data[i] = sum[i] / n;
    ctx.putImageData(out, 0, 0);
    return canvas.transferToImageBitmap();
  }

  const take = () => {
    if (failure) throw failure;
    const out = chunks.map((c) => ({ key: c.key, timestamp: c.timestamp, data: c.data.toBase64() }));
    chunks = [];
    return out;
  };

  /** Encodes [source] (an image or a canvas) as frame [index]. */
  async function encode(source, index) {
    const frame = new VideoFrame(source, { timestamp: Math.round((index * 1e6) / fps), duration: Math.round(1e6 / fps) });
    // A key frame every two seconds, so YouTube and a scrubbing player can start anywhere.
    video.encode(frame, { keyFrame: index % (2 * fps) === 0 });
    frame.close();
    while (video.encodeQueueSize > 4) await new Promise((r) => video.addEventListener("dequeue", r, { once: true }));
    return take();
  }

  return {
    /**
     * Opens the video encoder — the GPU's own when [hardware], Chrome's software one otherwise; throws if
     * Chrome cannot encode [codec] at this size.
     */
    async start({ width, height, rate, bitrate, codec, hardware = false }) {
      fps = rate;
      const config = {
        codec, width, height, bitrate, framerate: fps,
        avc: { format: "avc" }, latencyMode: "quality", bitrateMode: "variable",
        hardwareAcceleration: hardware ? "prefer-hardware" : "no-preference",
      };
      const { supported } = await VideoEncoder.isConfigSupported(config);
      if (!supported) throw new Error(`Chrome cannot encode ${codec} at ${width}×${height}`);
      video = new VideoEncoder({
        output: (chunk, meta) => {
          if (meta?.decoderConfig?.description) avcC = new Uint8Array(meta.decoderConfig.description);
          if (meta?.decoderConfig?.colorSpace) color = meta.decoderConfig.colorSpace;
          const data = new Uint8Array(chunk.byteLength);
          chunk.copyTo(data);
          chunks.push({ key: chunk.type === "key", data, timestamp: chunk.timestamp });
        },
        error: (e) => (failure = e),
      });
      video.configure(config);
    },

    /** Encodes frame [index] from its exposures, base64 JPEGs, and returns what the encoder has finished so far. */
    async frame(jpegs, index) {
      const bitmap = await blend(jpegs);
      const out = await encode(bitmap, index);
      bitmap.close();
      return out;
    },

    /** Encodes frame [index] from what [canvas] shows now, and returns what the encoder has finished so far. */
    canvas: (canvas, index) => encode(canvas, index),

    /** A frame's exposures blended, as a base64 PNG: a still of the moment as the video shows it. */
    async still(jpegs) {
      const bitmap = await blend(jpegs);
      const still = new OffscreenCanvas(bitmap.width, bitmap.height);
      still.getContext("2d").drawImage(bitmap, 0, 0);
      bitmap.close();
      const blob = await still.convertToBlob({ type: "image/png" });
      return new Uint8Array(await blob.arrayBuffer()).toBase64();
    },

    /** Finishes the video: the last frames, and how the stream describes itself. */
    async finish() {
      await video.flush();
      const rest = take();
      video.close();
      if (!avcC) throw new Error("The encoder never described its stream");
      return { chunks: rest, avcC: avcC.toBase64(), color };
    },

    /** The soundtrack as AAC: its AudioSpecificConfig and every frame, each 1024 samples. */
    async audio(buffer, bitrate) {
      const out = [];
      let config = null, error = null;
      const encoder = new AudioEncoder({
        output: (chunk, meta) => {
          if (meta?.decoderConfig?.description) config = new Uint8Array(meta.decoderConfig.description);
          const data = new Uint8Array(chunk.byteLength);
          chunk.copyTo(data);
          out.push(data.toBase64());
        },
        error: (e) => (error = e),
      });
      const settings = { codec: "mp4a.40.2", sampleRate: buffer.sampleRate, numberOfChannels: buffer.numberOfChannels, bitrate };
      const { supported } = await AudioEncoder.isConfigSupported(settings);
      if (!supported) throw new Error("Chrome cannot encode AAC here");
      encoder.configure(settings);
      const step = 4800;
      for (let at = 0; at < buffer.length; at += step) {
        const frames = Math.min(step, buffer.length - at);
        const planar = new Float32Array(frames * buffer.numberOfChannels);
        for (let ch = 0; ch < buffer.numberOfChannels; ch++) {
          planar.set(buffer.getChannelData(ch).subarray(at, at + frames), ch * frames);
        }
        encoder.encode(new AudioData({
          format: "f32-planar", sampleRate: buffer.sampleRate, numberOfFrames: frames,
          numberOfChannels: buffer.numberOfChannels, timestamp: Math.round((at * 1e6) / buffer.sampleRate), data: planar,
        }));
      }
      await encoder.flush();
      encoder.close();
      if (error) throw error;
      if (!config) throw new Error("The AAC encoder never described its stream");
      return { config: config.toBase64(), chunks: out, sampleRate: buffer.sampleRate, channels: buffer.numberOfChannels };
    },
  };
})();
