// Chrome's own encoders, through WebCodecs: each frame video.mjs screenshots comes back here as a PNG and
// is encoded to H.264, the soundtrack to AAC. What they produce goes back to Node in pieces, as base64,
// to be written into an MP4 (store/lib/mp4.mjs).

"use strict";

window.encoder = (() => {
  let video, fps, chunks = [], avcC = null, color = null, failure = null;

  const take = () => {
    if (failure) throw failure;
    const out = chunks.map((c) => ({ key: c.key, timestamp: c.timestamp, data: c.data.toBase64() }));
    chunks = [];
    return out;
  };

  return {
    /** Opens the video encoder; throws if Chrome cannot encode [codec] at this size. */
    async start({ width, height, rate, bitrate, codec }) {
      fps = rate;
      const config = {
        codec, width, height, bitrate, framerate: fps,
        avc: { format: "avc" }, latencyMode: "quality", bitrateMode: "variable",
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

    /** Encodes frame [index] from a base64 JPEG, and returns what the encoder has finished so far. */
    async frame(jpeg, index) {
      const bitmap = await createImageBitmap(new Blob([Uint8Array.fromBase64(jpeg)], { type: "image/jpeg" }));
      const frame = new VideoFrame(bitmap, { timestamp: Math.round((index * 1e6) / fps), duration: Math.round(1e6 / fps) });
      bitmap.close();
      // A key frame every two seconds, so YouTube and a scrubbing player can start anywhere.
      video.encode(frame, { keyFrame: index % (2 * fps) === 0 });
      frame.close();
      while (video.encodeQueueSize > 4) await new Promise((r) => video.addEventListener("dequeue", r, { once: true }));
      return take();
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
