// The film's arithmetic that does not touch the page: how many exposures a moving frame is blended from,
// how an amount reads as it counts up, and where a swiped row is. A plain script, so the page loads it
// before film.js and store/film/parts.test.mjs runs it in Node.

"use strict";

(function (root) {
  /** The share of a frame's time the virtual shutter is open: 180°, as a film camera's. */
  const SHUTTER = 0.5;
  /** How far apart, in output pixels, the blended copies of a moving edge may be before they read as steps. */
  const STEP = 2.5;
  const MAX_EXPOSURES = 12;

  /**
   * The moments a frame at [t] is blended from, for something on it moving [motion] stage pixels a frame:
   * one for a still frame, more the faster it moves, spread over the open shutter and kept inside
   * `[lo, hi]` — the film, so no frame blends in a moment before it starts or after it ends. Past what [MAX_EXPOSURES] can
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

  /**
   * A row swiped as the app's `SwipeToConfirmBox` has it: dragged out to [armed] pixels over [drag], eased,
   * held there, and gone on the lift at [release] — nothing springs back. `at(t)` is how far it is;
   * `crossing` the first moment it is past [unarmed], where it was shot just short of the mark: when the
   * phone ticks and the glyph behind it grows.
   */
  function swipe({ unarmed, armed, drag: [from, to], release, ease = (p) => p }) {
    if (!(unarmed > 0 && unarmed < armed)) throw new Error("A swipe's shots must be short of its mark and past it");
    if (!(from < to && to <= release)) throw new Error("A swipe must be dragged, then let go");
    const at = (t) => (t < release ? armed * ease(Math.min(1, Math.max(0, (t - from) / (to - from)))) : 0);
    let crossing = from;
    while (at(crossing) <= unarmed && crossing < to) crossing += 1 / 240;
    return { at, crossing };
  }

  root.filmParts = { exposures, counting, swipe, SHUTTER };
})(globalThis);
