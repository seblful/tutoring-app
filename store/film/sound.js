// The promo video's soundtrack: the music and every effect synthesised here, in an OfflineAudioContext,
// so the sound is the same each run, comes out in step with the frames, and owes no one a licence.
//
// `soundtrack({ duration, bpm, cues, sections })` → an AudioBuffer. The music follows `sections` (an
// opening, the groove, a break, the close) over a I–V–vi–IV in C, a bar every four beats from the start;
// `cues` are the effects film.js placed on the pictures: keys, taps, whooshes, chimes.

"use strict";

window.soundtrack = async function soundtrack({ duration, bpm, cues, sections, sampleRate = 48000 }) {
  const ctx = new OfflineAudioContext(2, Math.ceil(duration * sampleRate), sampleRate);
  const BEAT = 60 / bpm, BAR = 4 * BEAT;
  const random = seeded(11);
  const between = (lo, hi) => lo + (hi - lo) * random();
  const midi = (n) => 440 * 2 ** ((n - 69) / 12);

  // --- The mix -----------------------------------------------------------------------------------
  const master = ctx.createGain();
  const limiter = ctx.createDynamicsCompressor();
  limiter.threshold.value = -8;
  limiter.knee.value = 4;
  limiter.ratio.value = 12;
  limiter.attack.value = 0.003;
  limiter.release.value = 0.15;
  master.connect(limiter).connect(ctx.destination);
  master.gain.setValueAtTime(1, 0);
  master.gain.setValueAtTime(1, duration - 1.4);
  master.gain.linearRampToValueAtTime(0, duration);

  const music = gain(0.42, master);
  const effects = gain(0.8, master);
  const reverb = ctx.createConvolver();
  reverb.buffer = impulse(2.6, 3);
  reverb.connect(gain(0.32, master));
  /** The pad and the bass dip under every kick, which is what makes the groove breathe. */
  const ducked = gain(1, music);
  const echo = ctx.createDelay(1);
  echo.delayTime.value = 0.75 * BEAT;
  const echoTone = filter("lowpass", 2600);
  const echoBack = gain(0.34);
  echo.connect(echoTone).connect(echoBack).connect(echo);
  echoTone.connect(gain(0.28, music));
  const noise = noiseBuffer(2);

  // --- Building blocks ---------------------------------------------------------------------------
  function gain(value, to) {
    const g = ctx.createGain();
    g.gain.value = value;
    if (to) g.connect(to);
    return g;
  }
  function filter(type, frequency, q = 0.7) {
    const f = ctx.createBiquadFilter();
    f.type = type;
    f.frequency.value = frequency;
    f.Q.value = q;
    return f;
  }
  function panned(pan, to) {
    const p = ctx.createStereoPanner();
    p.pan.value = pan;
    p.connect(to);
    return p;
  }
  function tone(type, frequency, t, length, to) {
    const o = ctx.createOscillator();
    o.type = type;
    o.frequency.value = frequency;
    o.connect(to);
    o.start(t);
    o.stop(t + length);
    return o;
  }
  /** A gain that rises to [peak] in [attack] and decays exponentially over [decay]. */
  function hit(t, peak, attack, decay, to) {
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(Math.max(peak, 0.0002), t + attack);
    g.gain.exponentialRampToValueAtTime(0.0001, t + attack + decay);
    g.connect(to);
    return g;
  }
  function noiseAt(t, length, to) {
    const s = ctx.createBufferSource();
    s.buffer = noise;
    s.loop = true;
    s.connect(to);
    s.start(t, random() * 1.5);
    s.stop(t + length);
    return s;
  }

  // --- Instruments -------------------------------------------------------------------------------
  function kick(t, v = 1) {
    const g = hit(t, 0.9 * v, 0.003, 0.4, music);
    const o = tone("sine", 150, t, 0.45, g);
    o.frequency.setValueAtTime(150, t);
    o.frequency.exponentialRampToValueAtTime(46, t + 0.11);
    const click = filter("highpass", 2500);
    click.connect(hit(t, 0.12 * v, 0.001, 0.012, music));
    noiseAt(t, 0.02, click);
    ducked.gain.setValueAtTime(0.3, t);
    ducked.gain.setTargetAtTime(1, t + 0.03, 0.08);
  }
  function clap(t, v = 1) {
    const out = filter("bandpass", 1300, 0.9);
    out.connect(gain(0.9, music));
    out.connect(gain(0.35, reverb));
    for (const [d, peak] of [[0, 0.5], [0.011, 0.45], [0.023, 0.6]]) noiseAt(t + d, 0.25, hit(t + d, peak * v, 0.001, d === 0.023 ? 0.18 : 0.012, out));
  }
  function hat(t, v, open = false) {
    const g = hit(t, 0.16 * v, 0.001, open ? 0.26 : 0.04, panned(0.25, music));
    const hp = filter("highpass", 7600);
    hp.connect(g);
    noiseAt(t, open ? 0.3 : 0.06, hp);
  }
  function bass(t, n, length, level = 1) {
    const lp = filter("lowpass", 1400, 2);
    lp.frequency.setValueAtTime(1500, t);
    lp.frequency.exponentialRampToValueAtTime(380, t + length);
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.32 * level, t + 0.006);
    g.gain.setValueAtTime(0.28 * level, t + length - 0.03);
    g.gain.exponentialRampToValueAtTime(0.0001, t + length);
    lp.connect(g).connect(ducked);
    tone("sawtooth", midi(n), t, length, lp);
    tone("square", midi(n) * 0.5, t, length, gain(0.35, lp));
  }
  function pad(t, notes, length, brightness) {
    const lp = filter("lowpass", brightness, 0.6);
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(1, t + 0.3);
    g.gain.setValueAtTime(1, t + length - 0.1);
    g.gain.exponentialRampToValueAtTime(0.0001, t + length + 0.5);
    lp.connect(g).connect(ducked);
    g.connect(gain(0.25, reverb));
    notes.forEach((n, i) => {
      const side = panned((i / (notes.length - 1)) * 1.2 - 0.6, lp);
      for (const cents of [-8, 8]) {
        const o = tone("sawtooth", midi(n), t, length + 0.6, gain(0.035, side));
        o.detune.value = cents;
      }
      tone("triangle", midi(n + 12), t, length + 0.6, gain(0.02, side));
    });
  }
  function pluck(t, n, v, brightness = 4200) {
    const lp = filter("lowpass", brightness);
    const g = hit(t, 0.17 * v, 0.003, 0.26, lp);
    tone("triangle", midi(n), t, 0.32, g);
    tone("sine", midi(n + 12), t, 0.32, gain(0.4, g));
    lp.connect(panned(between(-0.35, 0.35), music));
    lp.connect(echo);
  }
  function bell(t, n, v = 1, to = music) {
    const f = midi(n);
    const g = hit(t, 0.22 * v, 0.004, 1.5, to);
    g.connect(gain(0.4, reverb));
    // Two-operator FM: a sine bent by another at 3.5× its pitch, the bend dying away — a struck bell.
    const carrier = tone("sine", f, t, 1.6, g);
    const depth = ctx.createGain();
    depth.gain.setValueAtTime(f * 1.4, t);
    depth.gain.exponentialRampToValueAtTime(1, t + 1.2);
    depth.connect(carrier.frequency);
    tone("sine", f * 3.5, t, 1.6, depth);
  }
  /**
   * A key: one soft click — a few milliseconds of noise around [f], and a faint knock under it — the way
   * a phone's keyboard answers a tap, not a desk keyboard's clatter.
   */
  function keyClick(t, v, f, length, to) {
    const bp = filter("bandpass", f, 1.1);
    bp.connect(hit(t, 0.45 * v, 0.0005, length, to));
    noiseAt(t, length * 3, bp);
    tone("sine", f * 0.42, t, length * 4, hit(t, 0.12 * v, 0.0008, length, to));
  }
  function riser(from, to, v = 1) {
    const bp = filter("bandpass", 300, 2.2);
    bp.frequency.setValueAtTime(300, from);
    bp.frequency.exponentialRampToValueAtTime(6000, to);
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, from);
    g.gain.exponentialRampToValueAtTime(0.3 * v, to - 0.02);
    g.gain.linearRampToValueAtTime(0, to);
    bp.connect(g).connect(music);
    g.connect(gain(0.5, reverb));
    noiseAt(from, to - from, bp);
  }

  // --- Effects -----------------------------------------------------------------------------------
  const FX = {
    /** A key, each [c] at a pitch of its own, as no two taps sound quite the same. */
    key(t, { v = 1, c = 0 }) {
      const own = seeded(1000 + c * 7919);
      keyClick(t, v, 2600 * (0.92 + own() * 0.16), 0.012, effects);
    },
    /** The space bar: the same click, lower and a little longer. */
    space(t, { v = 1 }) {
      keyClick(t, v, 1900, 0.016, effects);
    },
    tap(t) {
      const g = hit(t, 0.3, 0.001, 0.05, effects);
      const o = tone("sine", 2200, t, 0.07, g);
      o.frequency.exponentialRampToValueAtTime(900, t + 0.03);
      const hp = filter("highpass", 4000);
      hp.connect(hit(t, 0.1, 0.0005, 0.006, effects));
      noiseAt(t, 0.01, hp);
    },
    pop(t) {
      const g = hit(t, 0.32, 0.002, 0.13, effects);
      g.connect(gain(0.2, reverb));
      const o = tone("sine", 300, t, 0.18, g);
      o.frequency.exponentialRampToValueAtTime(820, t + 0.06);
      const h = tone("triangle", 600, t, 0.12, gain(0.25, g));
      h.frequency.exponentialRampToValueAtTime(1640, t + 0.06);
    },
    /** A figure counting up: a short, dry tick, like a mechanical counter's wheel. */
    tick(t, { v = 1 }) {
      const g = hit(t, 0.1 * v, 0.0005, 0.016, panned(0.15, effects));
      tone("sine", 3300, t, 0.03, g);
      const hp = filter("highpass", 6000);
      hp.connect(hit(t, 0.05 * v, 0.0004, 0.004, effects));
      noiseAt(t, 0.008, hp);
    },
    blip(t, { n = 0 }) {
      const scale = [84, 86, 88, 91, 93, 96, 98];
      const g = hit(t, 0.16, 0.002, 0.18, effects);
      g.connect(gain(0.3, reverb));
      tone("triangle", midi(scale[n % scale.length]), t, 0.22, g);
    },
    swish(t, { v = 1 }) {
      const bp = filter("bandpass", 900, 1.2);
      bp.frequency.setValueAtTime(900, t);
      bp.frequency.exponentialRampToValueAtTime(2800, t + 0.25);
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(0.22 * v, t + 0.1);
      g.gain.exponentialRampToValueAtTime(0.0001, t + 0.32);
      bp.connect(g).connect(effects);
      noiseAt(t, 0.35, bp);
    },
    whoosh(t, { d = 0.6, v = 1 }) {
      const bp = filter("bandpass", 250, 1.1);
      bp.frequency.setValueAtTime(250, t);
      bp.frequency.exponentialRampToValueAtTime(2400, t + d * 0.6);
      bp.frequency.exponentialRampToValueAtTime(700, t + d);
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(0.42 * v, t + d * 0.55);
      g.gain.exponentialRampToValueAtTime(0.0001, t + d);
      const pan = ctx.createStereoPanner();
      pan.pan.setValueAtTime(-0.6, t);
      pan.pan.linearRampToValueAtTime(0.6, t + d);
      bp.connect(g).connect(pan).connect(effects);
      g.connect(gain(0.3, reverb));
      noiseAt(t, d, bp);
    },
    ding(t) {
      for (const [at, n] of [[0, 91], [0.11, 96]]) {
        const f = midi(n);
        for (const [ratio, peak, decay] of [[1, 0.13, 1.3], [2.756, 0.04, 0.6], [5.404, 0.017, 0.25]]) {
          const g = hit(t + at, peak, 0.002, decay, effects);
          g.connect(gain(0.35, reverb));
          tone("sine", f * ratio, t + at, decay + 0.1, g);
        }
      }
    },
    buzz(t) {
      const lp = filter("lowpass", 280, 3);
      lp.connect(effects);
      for (const at of [0, 0.2]) {
        const g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, t + at);
        g.gain.exponentialRampToValueAtTime(0.09, t + at + 0.015);
        g.gain.setValueAtTime(0.09, t + at + 0.13);
        g.gain.exponentialRampToValueAtTime(0.0001, t + at + 0.16);
        g.connect(lp);
        tone("square", 165, t + at, 0.17, g);
      }
    },
    chime(t) {
      [84, 88, 91, 96].forEach((n, i) => bell(t + i * 0.065, n, 0.55, effects));
    },
    switch(t) {
      for (const at of [0, 0.028]) {
        const g = hit(t + at, 0.22, 0.0006, 0.018, effects);
        tone("sine", at ? 1500 : 1900, t + at, 0.03, g);
        const hp = filter("highpass", 3000);
        hp.connect(hit(t + at, 0.08, 0.0005, 0.005, effects));
        noiseAt(t + at, 0.01, hp);
      }
    },
    coin(t) {
      for (const [at, n] of [[0, 95], [0.075, 100]]) {
        const g = hit(t + at, 0.17, 0.002, 0.5, effects);
        g.connect(gain(0.35, reverb));
        tone("triangle", midi(n), t + at, 0.55, g);
        tone("sine", midi(n + 12), t + at, 0.3, gain(0.3, g));
      }
    },
    sparkle(t) {
      const notes = [96, 98, 100, 103, 105, 108];
      for (let i = 0; i < 9; i++) {
        const at = t + i * 0.045 + between(0, 0.02);
        const g = hit(at, 0.07, 0.002, 0.3, panned(between(-0.7, 0.7), effects));
        g.connect(gain(0.5, reverb));
        tone("sine", midi(notes[Math.floor(random() * notes.length)]), at, 0.35, g);
      }
    },
    boom(t) {
      const g = hit(t, 0.55, 0.004, 1.1, effects);
      g.connect(gain(0.5, reverb));
      const o = tone("sine", 90, t, 1.2, g);
      o.frequency.exponentialRampToValueAtTime(42, t + 0.35);
      bell(t + 0.02, 72, 0.45, effects);
      bell(t + 0.02, 79, 0.35, effects);
    },
    impact(t, { v = 1 }) {
      kick(t, 1.1 * v);
      const g = hit(t, 0.5 * v, 0.003, 1.6, effects);
      g.connect(gain(0.4, reverb));
      const o = tone("sine", 70, t, 1.7, g);
      o.frequency.exponentialRampToValueAtTime(38, t + 0.5);
      const hp = filter("highpass", 3500);
      hp.connect(hit(t, 0.22 * v, 0.002, 1.3, effects));
      hp.connect(gain(0.6, reverb));
      noiseAt(t, 1.4, hp);
    },
  };

  // --- The music, bar by bar ---------------------------------------------------------------------
  const CHORDS = [
    { pad: [60, 64, 67, 71], bass: 36 }, // C
    { pad: [59, 62, 67, 69], bass: 35 }, // G/B
    { pad: [60, 64, 67, 69], bass: 33 }, // Am
    { pad: [60, 64, 65, 69], bass: 29 }, // F
  ];
  const ARP = [0, 2, 1, 3, 2, 0, 3, 1, 0, 2, 1, 3, 2, 3, 1, 2];
  /** A four-bar hook over the progression, in eighths (null rests), for the second half's groove. */
  const HOOK = [
    [76, null, 79, null, null, 76, 74, null],
    [74, null, null, 71, null, null, 74, null],
    [72, null, 76, null, null, 74, 72, null],
    [72, null, 69, null, 72, null, 74, null],
  ];
  // Each bar takes the part playing at its middle.
  const sectionAt = (t) => sections.find((s) => t >= s.from && t < s.to);
  const partAt = (t) => sectionAt(t)?.part ?? "outro";
  const grooves = sections.filter((s) => s.part === "groove");
  for (let b = 0; b * BAR < duration - 0.05; b++) {
    const t0 = b * BAR, part = partAt(t0 + BAR / 2), chord = CHORDS[b % 4];
    if (part === "outro") {
      if (Math.abs(t0 - sections.find((s) => s.part === "outro").from) < BAR / 2) {
        pad(t0, [48, 55, 60, 64, 67, 72], duration - t0 - 0.6, 2600);
        bass(t0, 36, 2.5);
        [72, 76, 79, 84].forEach((n, i) => bell(t0 + 0.9 + i * 0.12, n, 0.5));
      }
      continue;
    }
    const open = part === "intro" ? 500 + (t0 / 4) * 1500 : part === "break" ? 1700 : 2400;
    pad(t0, chord.pad, BAR, open);
    for (let s = 0; s < 16; s++) {
      const t = t0 + s * (BEAT / 4);
      const v = s % 4 === 0 ? 1 : 0.62;
      const n = chord.pad[ARP[s]] + 12;
      if (part === "intro") pluck(t, n, v * (0.4 + 0.4 * (t / 4)), 900 + t * 600);
      else if (part === "break") pluck(t, n, v * 0.85, 2800);
      else pluck(t, n, v * 0.7);
    }
    if (part === "groove") {
      const section = sectionAt(t0 + BAR / 2);
      const second = grooves.indexOf(section) >= 1;
      for (let beat = 0; beat < 4; beat++) {
        const t = t0 + beat * BEAT;
        kick(t);
        if (beat % 2 === 1) clap(t, 0.8);
        hat(t + BEAT / 2, 0.9, beat === 3 && b % 2 === 1);
        hat(t + BEAT / 4, 0.32);
        hat(t + (3 * BEAT) / 4, 0.38);
        bass(t + BEAT / 2, chord.bass, BEAT * 0.45);
        if (beat === 0 || beat === 2) bass(t, chord.bass, BEAT * 0.22);
      }
      // The hook over the second groove, all but its last bar, which rolls into the close.
      if (second && t0 + 2 * BAR <= section.to + 0.05) {
        HOOK[b % 4].forEach((n, i) => n && bell(t0 + i * (BEAT / 2), n, 0.32));
      }
      // A roll into the break and into the close.
      const next = partAt(t0 + 1.5 * BAR);
      if (next !== "groove") {
        for (let i = 0; i < 8; i++) clap(t0 + 3 * BEAT + i * (BEAT / 8), 0.25 + i * 0.08);
      }
    }
    // The break keeps its hats and a held bass note, so it dips rather than stops.
    if (part === "break") {
      bass(t0, chord.bass, BAR * 0.95, 0.4);
      for (let beat = 0; beat < 4; beat++) {
        hat(t0 + beat * BEAT + BEAT / 2, 0.6);
        hat(t0 + beat * BEAT + (3 * BEAT) / 4, 0.25);
      }
    }
    if (part === "intro" && b === 1) riser(t0 + 0.4, t0 + BAR, 0.9);
    if (part === "break" && partAt(t0 + 1.5 * BAR) === "groove") riser(t0 + 0.6, t0 + BAR, 0.8);
  }

  for (const c of cues) {
    const play = FX[c.kind];
    if (!play) throw new Error(`No sound for the cue «${c.kind}»`);
    play(c.t, c);
  }

  const rendered = await ctx.startRendering();
  // Brought up to just under full scale, so the video is as loud as the next one on YouTube without clipping.
  let peak = 0;
  for (let ch = 0; ch < rendered.numberOfChannels; ch++) {
    for (const x of rendered.getChannelData(ch)) peak = Math.max(peak, Math.abs(x));
  }
  const scale = peak > 0 ? 0.89 / peak : 1;
  for (let ch = 0; ch < rendered.numberOfChannels; ch++) {
    const data = rendered.getChannelData(ch);
    for (let i = 0; i < data.length; i++) data[i] *= scale;
  }
  return rendered;

  // --- Sources -----------------------------------------------------------------------------------
  function noiseBuffer(seconds) {
    const buffer = ctx.createBuffer(1, seconds * sampleRate, sampleRate);
    const data = buffer.getChannelData(0);
    const white = seeded(5);
    for (let i = 0; i < data.length; i++) data[i] = white() * 2 - 1;
    return buffer;
  }
  /** A room: decaying noise, different in each ear. */
  function impulse(seconds, decay) {
    const buffer = ctx.createBuffer(2, seconds * sampleRate, sampleRate);
    const white = seeded(9);
    for (let ch = 0; ch < 2; ch++) {
      const data = buffer.getChannelData(ch);
      for (let i = 0; i < data.length; i++) data[i] = (white() * 2 - 1) * (1 - i / data.length) ** decay;
    }
    return buffer;
  }
  function seeded(seed) {
    return () => {
      seed = (seed + 0x6d2b79f5) | 0;
      let r = Math.imul(seed ^ (seed >>> 15), 1 | seed);
      r = (r + Math.imul(r ^ (r >>> 7), 61 | r)) ^ r;
      return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
    };
  }
};
