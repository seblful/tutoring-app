// The film's arithmetic that does not touch the page: how a cut lays pieces of the timeline end to end,
// how many exposures a moving frame is blended from, and how an amount reads as it counts up. A plain
// script, so the page loads it before film.js and store/film/parts.test.mjs runs it in Node.

"use strict";

(function (root) {
  /**
   * A cut of the film: [segments] of the full timeline, `[from, to)` in seconds, played one after another.
   * The full film is one segment; the short one is three, each starting where the one before ends.
   */
  function cut(segments) {
    let at = 0;
    const parts = segments.map(([from, to]) => {
      if (!(to > from)) throw new Error(`A cut's segment must run forwards: ${from} → ${to}`);
      const part = { from, to, start: at, end: at + to - from };
      at = part.end;
      return part;
    });
    const duration = at;
    const partAt = (t) => parts.find((p) => t < p.end) ?? parts[parts.length - 1];
    return {
      duration,
      parts,
      /** The full timeline's time at this cut's [t], and the segment it falls in. */
      source(t) {
        const part = partAt(t);
        return { t: part.from + t - part.start, part };
      },
      /** Where a moment of the full timeline plays in this cut, or null if it is cut out. */
      place(t) {
        const part = parts.find((p) => t >= p.from && t < p.to);
        return part ? part.start + t - part.from : null;
      },
      /**
       * The music's sections in this cut's time: each of the full film's that a segment plays, the same
       * part merged across a join. Every segment must keep the full film's bars where they were — its
       * start moved by whole bars — or the cuts stop falling on downbeats; [downbeat] is where the first
       * bar of this cut starts.
       */
      music(sections, bar) {
        const shifts = parts.map((p) => ((((p.start - p.from) % bar) + bar) % bar));
        if (shifts.some((s) => Math.abs(s - shifts[0]) > 1e-6 && Math.abs(Math.abs(s - shifts[0]) - bar) > 1e-6)) {
          throw new Error("The cut's segments are not whole bars apart; its cuts would miss the beat");
        }
        const out = [];
        for (const p of parts) {
          for (const s of sections) {
            const from = Math.max(p.from, s.from), to = Math.min(p.to, s.to);
            if (to <= from) continue;
            const section = { from: p.start + from - p.from, to: p.start + to - p.from, part: s.part };
            const last = out[out.length - 1];
            if (last && last.part === section.part && Math.abs(last.to - section.from) < 1e-6) last.to = section.to;
            else out.push(section);
          }
        }
        return { sections: out, downbeat: shifts[0] };
      },
    };
  }

  /** The share of a frame's time the virtual shutter is open: 180°, as a film camera's. */
  const SHUTTER = 0.5;
  /** How far apart, in output pixels, the blended copies of a moving edge may be before they read as steps. */
  const STEP = 2.5;
  const MAX_EXPOSURES = 12;

  /**
   * The moments a frame at [t] is blended from, for something on it moving [motion] stage pixels a frame:
   * one for a still frame, more the faster it moves, spread over the open shutter and kept inside
   * `[lo, hi]` — a segment of a cut, so no frame blends across a jump. Past what [MAX_EXPOSURES] can
   * blend smoothly, the shutter closes down instead, as a camera's would for a fast pan: a shorter
   * smear, rather than copies far enough apart to be seen.
   */
  function exposures(t, motion, { fps, scale = 1, lo = -Infinity, hi = Infinity }) {
    const smear = motion * scale * SHUTTER;
    const n = Math.min(MAX_EXPOSURES, Math.max(1, Math.ceil(smear / STEP)));
    if (n === 1) return [t];
    const open = (SHUTTER * Math.min(1, (MAX_EXPOSURES * STEP) / smear)) / fps;
    return Array.from({ length: n }, (_, k) => Math.min(hi, Math.max(lo, t + ((k + 0.5) / n - 0.5) * open)));
  }

  /**
   * An amount as the app wrote it — «736 USD», «1 250,50 BYN» — that can be shown on its way up from
   * zero: `at(p)` is the same words with the number at share p of its value, written the same way (its
   * grouping and its decimals).
   */
  function counting(text) {
    const match = /\d(?:[\d\s.,  ]*\d)?/.exec(text);
    if (!match) throw new Error(`No number to count up in «${text}»`);
    const run = match[0];
    // A separator with one or two digits after it, at the end, is the decimal one; three is grouping.
    const tail = /([.,])(\d{1,2})$/.exec(run);
    const whole = tail ? run.slice(0, tail.index) : run;
    const decimals = tail ? tail[2].length : 0;
    const group = /\D/.exec(whole)?.[0] ?? "";
    const value = Number(whole.replace(/\D/g, "") + (tail ? `.${tail[2]}` : ""));
    const write = (v) => {
      const [int, frac] = v.toFixed(decimals).split(".");
      const grouped = group ? int.replace(/\B(?=(\d{3})+(?!\d))/g, group) : int;
      return frac ? grouped + tail[1] + frac : grouped;
    };
    if (write(value) !== run) throw new Error(`Cannot write «${run}» back the way the app did`);
    const before = text.slice(0, match.index), after = text.slice(match.index + run.length);
    return {
      value,
      at: (p) => before + write(Math.round(value * Math.min(1, Math.max(0, p)) * 10 ** decimals) / 10 ** decimals) + after,
    };
  }

  root.filmParts = { cut, exposures, counting, SHUTTER };
})(globalThis);
