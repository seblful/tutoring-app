// The promo video's timeline. video.mjs builds the page (the phone with a layer per capture, the captions,
// the brand) and sets `window.FILM`; this script moves all of it as a pure function of time — `film.seek(t)`
// draws the frame at t seconds, in any order — and lists the sounds that go with it (`film.cues`), so the
// frames and the soundtrack are made separately and still land together.
//
// One timeline, 46 seconds, in two shapes: the frame is wide (1920 × 1080) or tall (1080 × 1920, the
// captions above the phone rather than beside it).
//
// Bars of 2 s (120 bpm): every scene starts on a bar, so the screens change on the music's downbeats.

"use strict";

(() => {
  const F = window.FILM;
  const { counting, exposures, swipe: swiping } = window.filmParts;
  const DURATION = 46;
  const SW = F.stage.width, SH = F.stage.height;
  const TALL = F.layout === "vertical";

  // --- Time -------------------------------------------------------------------------------------

  const clamp = (x, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, x));
  const lerp = (a, b, p) => a + (b - a) * p;
  const E = {
    linear: (p) => p,
    inOut: (p) => (p < 0.5 ? 4 * p ** 3 : 1 - (-2 * p + 2) ** 3 / 2),
    out: (p) => 1 - (1 - p) ** 3,
    out5: (p) => 1 - (1 - p) ** 5,
    in: (p) => p ** 3,
    back: (p) => 1 + 2.70158 * (p - 1) ** 3 + 1.70158 * (p - 1) ** 2,
    spring: (p) => (p >= 1 ? 1 : 1 - Math.exp(-7 * p) * Math.cos(11 * p)),
  };
  /** How far t is through [a, b], eased. */
  const span = (t, a, b, ease = E.inOut) => ease(clamp((t - a) / (b - a)));
  /** 0 → 1 from a over [inFor], held, then back to 0 by b over [outFor]. */
  const held = (t, a, b, inFor = 0.3, outFor = inFor) =>
    Math.min(span(t, a, a + inFor, E.out), 1 - span(t, b - outFor, b, E.in));


  /**
   * A value through keyframes `[time, value, ease, bump]`: each value eased in from the one before. A
   * bump adds to the move's middle — `{ ry: 24 }` swings the phone round as it crosses the frame.
   */
  function keys(t, list) {
    if (t <= list[0][0]) return { ...list[0][1] };
    for (let i = 1; i < list.length; i++) {
      const [t1, to, ease = E.inOut, bump] = list[i];
      if (t > t1) continue;
      const [t0, from] = list[i - 1];
      const linear = clamp((t - t0) / (t1 - t0)), p = ease(linear);
      const value = {};
      for (const k in to) value[k] = lerp(from[k], to[k], p);
      if (bump) for (const k in bump) value[k] += bump[k] * Math.sin(Math.PI * linear);
      return value;
    }
    return { ...list[list.length - 1][1] };
  }

  /** The same jitter every run: typing rhythm and the drifting light are part of the film, not noise. */
  function random(seed) {
    return () => {
      seed = (seed + 0x6d2b79f5) | 0;
      let r = Math.imul(seed ^ (seed >>> 15), 1 | seed);
      r = (r + Math.imul(r ^ (r >>> 7), 61 | r)) ^ r;
      return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
    };
  }

  // --- The phone ----------------------------------------------------------------------------------

  /**
   * The phone's width at rest. Wide, it is 904 px tall and fits the frame with room above and below; tall,
   * it fills the frame's lower two thirds, under the captions.
   */
  const W0 = TALL ? 520 : 400;
  /** The width the phone is laid out at: past its largest zoom, so it is only ever scaled down. */
  const WL = TALL ? 780 : 680;
  /** Tall: where the captions end, which a zoomed phone stays below, and how much less it zooms. */
  const CAPTION_FLOOR = 640;
  const TALL_ZOOM = 0.75;
  const CW = F.capture.width, CH = F.capture.height;

  /** The phone's parts at width W: the bezel, the screen, the status bar, and capture px → phone px. */
  function geo(W) {
    const pad = 0.022 * W, sw = 0.956 * W, status = 0.092 * W, k = sw / CW;
    return { W, pad, sw, status, k, H: 2 * pad + status + CH * k };
  }
  /** Where a point of a capture is, from the phone's top-left corner. */
  const local = (g, x, y) => ({ x: g.pad + x * g.k, y: g.pad + g.status + y * g.k });
  const LG = geo(WL);
  /** A lifted row's least corner rounding, for a row with no card of its own: 12 dp in capture pixels. */
  const ROW_CORNER = 12 * 4;
  /** How far inside its mark a lifted copy is cut, in capture pixels: half a dp, too little to see. */
  const LIFT_INSET = 2;
  const mark = (shot, name) => F.shots[shot]?.marks?.[name] ?? fail(`no mark «${name}» on «${shot}»`);

  /** The phone at scale s, placed so the capture point (x, y) sits at (qx, qy) on the stage. */
  function aimAt(x, y, s, qx, qy) {
    if (TALL) return aimTall(x, y, s);
    const g = geo(W0 * s), p = local(g, x, y);
    return { x: qx - (p.x - g.W / 2), y: qy - (p.y - g.H / 2), s, rx: 0, ry: 0, rz: 0 };
  }
  /**
   * Tall: the phone stays in the middle of the frame and closes in less, with the point brought up to
   * the frame's middle only as far as the phone's top stays under the captions — lower on the screen,
   * the phone moves down instead, and past that it zooms less again.
   */
  function aimTall(x, y, s) {
    let z = 1 + (s - 1) * TALL_ZOOM;
    const lowest = SH - 260;
    let p = local(geo(W0 * z), x, y);
    if (CAPTION_FLOOR + p.y > lowest) {
      z *= (lowest - CAPTION_FLOOR) / p.y;
      p = local(geo(W0 * z), x, y);
    }
    const g = geo(W0 * z), qy = clamp(1250, CAPTION_FLOOR + p.y, lowest);
    return { x: SW / 2, y: qy - (p.y - g.H / 2), s: z, rx: 0, ry: 0, rz: 0 };
  }
  function aim(shot, name, s, qx, qy, dy = 0) {
    const m = mark(shot, name);
    return aimAt((m[0] + m[2]) / 2, (m[1] + m[3]) / 2 + dy, s, qx, qy);
  }
  /** Where a mark is on the stage, for a phone at [pose] that is facing the camera. */
  function onStage(pose, shot, name) {
    const g = geo(W0 * pose.s), m = mark(shot, name), a = local(g, m[0], m[1]), b = local(g, m[2], m[3]);
    const ox = pose.x - g.W / 2, oy = pose.y - g.H / 2;
    return { l: ox + a.x, t: oy + a.y, r: ox + b.x, b: oy + b.y };
  }

  // Wide, the phone crosses the frame from scene to scene, opposite its caption; tall, it stays in the
  // middle under the captions and turns from one side to the other.
  const RIGHT = TALL ? { x: SW / 2, y: 1250, s: 1, rx: 4, ry: -12, rz: 0 } : { x: 1390, y: 548, s: 1, rx: 4, ry: -12, rz: 0 };
  const LEFT = TALL ? { ...RIGHT, ry: 12 } : { x: 530, y: 548, s: 1, rx: 4, ry: 12, rz: 0 };
  const FRONT_R = { ...RIGHT, rx: 0, ry: 0 };
  const FRONT_L = { ...LEFT, rx: 0, ry: 0 };
  const SWING_TO_L = { ry: 26, s: -0.07, y: 20 };
  const SWING_TO_R = { ry: -26, s: -0.07, y: 20 };
  const Q_R = [1350, 560];
  const OFF_IN = TALL ? { x: 900, y: 2550, s: 0.9, rx: 18, ry: -40, rz: 14 } : { x: 1720, y: 1650, s: 0.9, rx: 18, ry: -40, rz: 14 };
  const OFF_OUT = TALL ? { x: 540, y: 2800, s: 0.85, rx: 30, ry: -20, rz: -6 } : { x: 1390, y: 1720, s: 0.85, rx: 30, ry: -20, rz: -6 };

  /** A turn away and back to the same side, for a cut between two screens with the phone on one side. */
  const TURN_R = { ry: -22, s: -0.06, y: 16 };
  const TURN_L = { ry: 22, s: -0.06, y: 16 };
  /** Where a mark is aimed with the phone on the left: left of its middle, clear of the caption on the right. */
  const Q_L = [430, 560];

  const POSES = [
    [0, OFF_IN],
    [3.55, OFF_IN],
    [4.5, RIGHT, E.out5],
    [5.0, { ...RIGHT, ry: -6, rx: 2 }, E.linear],
    // Today, which the app opens on: the lesson on now, then the one over and not marked.
    [5.5, aim("today", "next", 1.3, ...Q_R)],
    [6.25, aim("today", "next", 1.32, ...Q_R), E.linear],
    [6.75, aim("today", "toMark", 1.36, ...Q_R)],
    [7.45, aim("today", "toMark", 1.38, ...Q_R), E.linear],
    [7.8, FRONT_R],
    // Over to the schedule, where that lesson is swiped taught.
    [8.35, RIGHT, E.inOut, TURN_R],
    [8.85, aim("film-day", "swipe", 1.42, ...Q_R)],
    [11.0, aim("film-day", "swipe", 1.44, ...Q_R), E.linear],
    [11.75, FRONT_R],
    // Who owes.
    [12.35, LEFT, E.inOut, SWING_TO_L],
    [12.95, { ...FRONT_L, s: 1.05 }],
    [14.95, { ...FRONT_L, s: 1.05 }],
    [15.75, FRONT_L],
    // The month's income, counted up, then the rest of the report scrolled through.
    [16.35, RIGHT, E.inOut, SWING_TO_R],
    [16.95, aim("reports", "income", 1.5, 1330, 470, 150)],
    [17.95, aim("reports", "income", 1.53, 1330, 470, 150), E.linear],
    [18.4, FRONT_R],
    [19.75, FRONT_R],
    // The home screen: the day on Today's widget, and the lesson on now ticked off from there.
    [20.35, LEFT, E.inOut, SWING_TO_L],
    [20.9, { ...FRONT_L, s: 1.05 }],
    [21.4, { ...FRONT_L, s: 1.05 }],
    [21.9, aim("homescreen", "tick", 1.3, ...Q_L)],
    [23.2, aim("homescreen", "tick", 1.32, ...Q_L), E.linear],
    [23.75, FRONT_L],
    // Locked: reminders on the lock screen.
    [24.35, LEFT, E.inOut, TURN_L],
    [25.0, { ...LEFT, ry: 5, rx: 2, s: 1.04 }],
    [27.45, { ...LEFT, ry: 2, rx: 1, s: 1.08 }, E.linear],
    [27.75, FRONT_L],
    // Homework: last time's swiped done, then the next typed and landed on the list.
    [28.35, RIGHT, E.inOut, SWING_TO_R],
    [28.85, aim("film-homework-list", "swipe", 1.3, ...Q_R)],
    [29.6, aim("film-homework-list", "swipe", 1.3, ...Q_R)],
    [30.1, FRONT_R],
    [33.75, FRONT_R],
    // The calendar.
    [34.35, LEFT, E.inOut, SWING_TO_L],
    [34.8, FRONT_L],
    [37.75, FRONT_L],
    // After dark: switched on in Settings, and Today in it. Aimed right of the usual point, so the phone,
    // closing in on the right of its screen, keeps clear of the caption.
    [38.35, RIGHT, E.inOut, SWING_TO_R],
    [38.8, aim("film-theme", "dark", 1.32, 1420, 560)],
    [39.75, aim("film-theme", "dark", 1.36, 1420, 560), E.linear],
    [40.15, { ...RIGHT, ry: -4, rx: 2, s: 1.02 }],
    [41.45, { ...RIGHT, ry: -9, rx: 3, s: 1.07 }, E.linear],
    [42.1, OFF_OUT, E.in],
  ];

  // --- Sounds -------------------------------------------------------------------------------------

  const cues = [];
  const cue = (t, kind, extra = {}) => cues.push({ t, kind, ...extra });

  // --- Elements -----------------------------------------------------------------------------------

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const fade = $("#fade");
  const phone = $("#phone");
  phone.style.setProperty("--phone", `${WL}px`);
  const stack = $(".stack", phone);
  const overlay = $("#overlay");
  const layers = Object.fromEntries($$(".layer", stack).map((el) => [el.dataset.shot, el]));
  const captions = Object.fromEntries($$(".caption").map((el) => [el.dataset.shot, el]));
  const night = $(".bg.night");
  const glows = $$(".glow");
  const dots = $$(".dot");
  const intro = $("#intro");
  const outro = $("#outro");
  const typing = $("#typing");
  const calendar = $("#calendar");

  const scrim = document.createElement("div");
  scrim.className = "scrim";
  stack.append(scrim);

  const callouts = {};

  /**
   * A lifted copy of [name] on [shot], drawn over the phone's screen, raised and shadowed. [whole]: the
   * shot's picture is the lifted thing alone — the home screen's widget — rather than the whole screen.
   */
  function callout(id, shot, name, whole = false) {
    const el = document.createElement("div");
    el.className = "callout";
    el.style.backgroundImage = `url("${F.shots[shot].src}")`;
    overlay.append(el);
    callouts[id] = { el, shot, name, whole };
  }
  callout("next", "today", "next");
  callout("toMark", "today", "toMark");
  callout("swiped", "film-swiped", "swipe");
  callout("debtor", "people", "debtor");
  callout("credit", "people", "credit");
  callout("widget", "homescreen", "widget", true);
  callout("added", "film-homework", "added");
  callout("sync", "settings-calendar", "sync");
  callout("invites", "settings-calendar", "invites");
  /** The dialog's «Save» is a text button: its ripple fills the pill round its word. */
  const saveRipple = (() => {
    const box = document.createElement("span");
    box.className = "pill";
    const ripple = document.createElement("span");
    ripple.className = "ripple";
    box.append(ripple);
    $(".save", typing).append(box);
    return ripple;
  })();

  /**
   * The report as one list to scroll: the capture as it opens, joined below to the capture scrolled down
   * (video.mjs found how far it moved, and a row to join at). The bar above the list takes on the
   * scrolled capture's tint as the list starts to move, as the app's does.
   */
  const report = (() => {
    const { list: [l, t, r, b], d, seam, src, bar } = F.scroll;
    const g = LG, k = g.k, layer = layers.reports;
    const tinted = document.createElement("img");
    tinted.className = "app tinted";
    tinted.src = src;
    const view = document.createElement("div");
    view.className = "scroller";
    Object.assign(view.style, { left: `${l * k}px`, top: `${g.status + t * k}px`, width: `${(r - l) * k}px`, height: `${(b - t) * k}px` });
    const content = document.createElement("div");
    content.className = "content";
    const piece = (image, top, height, from) => {
      const el = document.createElement("div");
      Object.assign(el.style, {
        top: `${top * k}px`, height: `${height * k}px`, backgroundImage: `url("${image}")`,
        backgroundSize: `${CW * k}px auto`, backgroundPosition: `${-l * k}px ${-from * k}px`,
      });
      return el;
    };
    // Overlapping by a couple of rows, which are the same plain rows on both: meeting edge to edge at a
    // fraction of a pixel, they let a hairline of what is under them show through.
    const overlap = 2;
    content.append(piece(F.shots.reports.src, 0, seam + overlap, t),
      piece(src, seam - overlap, b - t + d - seam + overlap, t + seam - overlap - d));
    // As tall as the whole list, so its motion can be measured for the blur.
    content.style.height = `${(b - t + d) * k}px`;

    // The month's total, redrawn in the app's type to count up: the figure the capture shows, covered by
    // its card's colour, with the same words over it at each step on the way.
    const total = F.count;
    const amount = counting(total.text);
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.classList.add("count");
    const margin = 8, width = total.right - total.left + 2 * margin, height = total.bottom - total.top;
    Object.assign(svg.style, { left: `${(total.left - margin - l) * k}px`, top: `${(total.top - t) * k}px`, width: `${width * k}px`, height: `${height * k}px` });
    svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
    svg.innerHTML = `<rect width="${width}" height="${height}" fill="${total.background}"/>` +
      `<text x="${margin}" y="${total.baseline - total.top}" font-family="Golos" font-weight="${total.weight}" ` +
      `font-size="${total.size}" letter-spacing="${total.spacing}" fill="${total.color}"></text>`;
    content.append(svg);
    view.append(content);
    // The tab bar is docked over the list's foot and stays put as the list moves under it: that band of the
    // opening capture, from the bar's hairline down, held over the moving list.
    const held = piece(F.shots.reports.src, 0, b - bar, bar);
    Object.assign(held.style, { position: "absolute", left: `${l * k}px`, top: `${g.status + bar * k}px`,
      width: `${(r - l) * k}px`, backgroundRepeat: "no-repeat" });
    layer.append(tinted, view, held);
    return { tinted, content, svg, words: $("text", svg), amount, d, k };
  })();

  // --- Words ---------------------------------------------------------------------------------------

  /**
   * Each caption's words in masks of their own, and every title at the one size at which all their lines
   * fit their columns — titles changing size from scene to scene would read as a mistake.
   */
  function setUpCaptions() {
    const all = Object.values(captions);
    all.forEach(splitWords);
    const fits = (size) => all.every((el) => {
      $("h1", el).style.fontSize = `${size}px`;
      return $$(".line", el).every((line) => line.scrollWidth <= el.clientWidth);
    });
    let size = 88;
    while (size > 56 && !fits(size)) size -= 2;
  }
  function splitWords(el) {
    for (const line of $$(".line", el)) {
      const words = line.textContent.split(" ");
      line.textContent = "";
      words.forEach((word, i) => {
        const mask = document.createElement("span");
        mask.className = "w";
        const inner = document.createElement("span");
        inner.textContent = word;
        mask.append(inner);
        line.append(mask);
        if (i < words.length - 1) line.append(" ");
      });
    }
  }

  /**
   * The tagline as it is set: on one line if it fits the frame, else broken into two lines of about the
   * same width — a tall frame is narrower than most taglines.
   */
  function taglineLines(text) {
    const ctx = document.createElement("canvas").getContext("2d");
    ctx.font = getComputedStyle($(".tagline", outro)).font;
    const most = SW - 2 * 90;
    if (ctx.measureText(text).width <= most) return text;
    const spaces = [...text.matchAll(/ /g)].map((m) => m.index);
    const widest = (i) => Math.max(ctx.measureText(text.slice(0, i)).width, ctx.measureText(text.slice(i + 1)).width);
    const at = spaces.reduce((best, i) => (widest(i) < widest(best) ? i : best), spaces[0]);
    if (at === undefined || widest(at) > most) fail("The tagline does not fit the frame even on two lines; shorten it in listing.json");
    return `${text.slice(0, at)}\n${text.slice(at + 1)}`;
  }

  // --- The timeline -------------------------------------------------------------------------------

  /**
   * When each caption is on, by the capture it belongs to. The next one starts only once the last word of
   * the one before has left (half a second), or the two titles are on screen over each other.
   */
  const CAPTIONS = [
    ["today", 4.15, 7.6],
    ["lesson-sheet", 8.25, 11.6],
    ["people", 12.25, 15.6],
    ["reports", 16.25, 19.6],
    ["widgets", 20.25, 23.6],
    ["reminders", 24.25, 27.6],
    ["homework", 28.25, 33.6],
    ["settings-calendar", 34.25, 37.6],
    ["today-dark", 38.3, 41.5],
  ];
  /** When each lifted row is up, and how far it grows: a little, so it stays in its place on the screen. */
  const CALLOUTS = [
    ["next", 5.55, 6.3, 1.045],
    ["toMark", 6.8, 7.5, 1.045],
    ["swiped", 10.1, 10.9, 1.045],
    ["debtor", 13.0, 14.95, 1.045],
    ["credit", 13.65, 14.95, 1.045],
    // The whole widget, so a little less, not to run off the screen's edges.
    ["widget", 20.95, 21.55, 1.03],
    ["added", 33.0, 33.65, 1.045],
    // Settings rows sit edge to edge under a section title, so they lift less, not to cover it, and
    // one after the other: up together, the lower one's edge and shadow lie over the upper one.
    ["sync", 34.85, 35.5, 1.03],
    ["invites", 35.4, 36.3, 1.03],
  ];
  /**
   * When each tap lands, and on what. A card or a button standing on its own is pressed in (`lift`); a
   * segment of a control only ripples, as the control is one outline and a segment shrunk out of it
   * breaks it. `end` is a control's last segment, round at its end only — a shape the capture's mark,
   * which keeps one corner radius, does not record; `round` a round button, the widget's tick.
   */
  const TAPS = [
    { at: 22.35, shot: "homescreen", mark: "tick", round: true },
    { at: 29.9, shot: "film-homework-done", mark: "add", lift: true },
    { at: 38.95, shot: "film-theme", mark: "dark", end: true },
  ];
  /** How far a pressed card or button sinks. */
  const PRESSED = 0.965;
  /** How far beside a pressed thing its patch's colour is taken, in capture pixels: clear of its shadow. */
  const BESIDE = 24;
  /**
   * A tap as the app shows one: Material's ripple spreading from the finger inside what is tapped, cut
   * to its shape, and a card or button pressed in and let back — and no dot for the finger, which the
   * phone does not draw. Drawn inside the screen it lands on, so whatever covers that screen next — the
   * dialog, the dark theme — covers the tap as well. What is pressed is a copy of it, over a
   * patch the colour of what is behind it, so it can shrink.
   */
  for (const tap of TAPS) {
    const k = LG.k, [l, top, r, bottom, corner] = mark(tap.shot, tap.mark);
    const w = (r - l) * k, h = (bottom - top) * k;
    const radius = tap.round ? "50%" : tap.end ? `0 ${h / 2}px ${h / 2}px 0` : `${corner * k}px`;
    tap.hole = document.createElement("div");
    tap.hole.className = "press";
    Object.assign(tap.hole.style, { left: `${l * k}px`, top: `${LG.status + top * k}px`, width: `${w}px`, height: `${h}px`, borderRadius: radius });
    tap.target = document.createElement("div");
    tap.target.className = "target";
    tap.target.style.borderRadius = radius;
    if (tap.lift) {
      Object.assign(tap.target.style, { backgroundImage: `url("${F.shots[tap.shot].src}")`, backgroundSize: `${CW * k}px auto`, backgroundPosition: `${-l * k}px ${-top * k}px` });
    }
    tap.ripple = document.createElement("div");
    tap.ripple.className = "ripple";
    tap.target.append(tap.ripple);
    tap.hole.append(tap.target);
    layers[tap.shot].append(tap.hole);
    tap.size = { w, h, reach: Math.hypot(w, h) / 2 };
  }
  /**
   * The rows swiped towards the end of the line, as the app's `SwipeToConfirmBox` has them: dragged off
   * what the swipe promises — the panel behind the row, from captures shot mid-drag just short of the mark
   * and past it, where its glyph has grown — and let go past the mark, which acts. Nothing springs back:
   * a lesson's row is home at once, saying «Taught», as the app redraws it; a piece of homework leaves the
   * list, and the rows below it close up (`close` seconds, video.mjs measured how far).
   */
  const SWIPES = {
    lesson: { before: "film-day", shot: "film-swipe", down: 9.1, drag: [9.15, 9.8], release: 10.0 },
    homework: { before: "film-homework-list", shot: "film-homework-swipe", down: 28.75, drag: [28.8, 29.3], release: 29.45, close: 0.4 },
  };
  /**
   * Moments the picture jumps rather than moves: a swiped row let go is home, or gone, in a frame, as the
   * app has it. Blended across, the frame showed the row in both places.
   */
  const JUMPS = Object.values(SWIPES).map((s) => s.release);
  /** Where the thumb holds the row, across it. */
  const THUMB = 0.3;
  for (const s of Object.values(SWIPES)) {
    const g = LG, k = g.k;
    const m = (s.m = mark(s.before, "swipe"));
    const [l, top, r, bottom, corner] = m;
    s.unarmed = mark(`${s.shot}-unarmed`, "swipe")[0] - l;
    Object.assign(s, swiping({ unarmed: s.unarmed, armed: mark(`${s.shot}-armed`, "swipe")[0] - l, drag: s.drag, release: s.release, ease: E.inOut }));
    const piece = (cls, src, from, style) => {
      const el = document.createElement("div");
      el.className = cls;
      Object.assign(el.style, { backgroundImage: `url("${src}")`, backgroundSize: `${CW * k}px auto`, backgroundPosition: `${-from[0] * k}px ${-from[1] * k}px`, ...style });
      return el;
    };
    s.band = document.createElement("div");
    s.band.className = "swipe";
    Object.assign(s.band.style, { left: `${g.pad}px`, top: `${g.pad + g.status + top * k}px`, width: `${g.sw}px`, height: `${(bottom - top) * k}px` });
    const reveal = { inset: "0" };
    s.armedReveal = piece("reveal", F.shots[`${s.shot}-armed`].src, [0, top], reveal);
    // Short of the mark the capture shows only as much of the panel as the row had uncovered.
    s.unarmedReveal = piece("reveal", F.shots[`${s.shot}-unarmed`].src, [0, top], { ...reveal, clipPath: `inset(0 ${(CW - l - s.unarmed) * k}px 0 0)` });
    s.row = piece("row", F.shots[s.before].src, [l, top], { left: `${l * k}px`, width: `${(r - l) * k}px`, borderRadius: `${Math.max(corner, ROW_CORNER) * k}px` });
    s.band.append(s.armedReveal, s.unarmedReveal, s.row);
    s.finger = document.createElement("div");
    s.finger.className = "tap";
    s.finger.innerHTML = `<div class="finger"></div>`;
    overlay.append(s.band, s.finger);
    if (s.close) {
      // The list below the row, from its capture before the swipe, rising into the gap over the screen's
      // background; under it, the screen's foot fading into the one after, where the message has come up.
      const { pitch, bottom: foot, background } = F.closeUp;
      s.gap = document.createElement("div");
      s.gap.className = "swipe";
      Object.assign(s.gap.style, { left: `${g.pad}px`, top: `${g.pad + g.status + top * k}px`, width: `${g.sw}px`, height: `${(foot - top) * k}px`, background });
      s.below = piece("rows", F.shots[s.before].src, [0, bottom], { left: "0", width: "100%", height: `${(foot - bottom) * k}px` });
      s.gap.append(s.below);
      s.foot = piece("swipe", F.shots[s.before].src, [0, foot], { left: `${g.pad}px`, top: `${g.pad + g.status + foot * k}px`, width: `${g.sw}px`, height: `${(CH - foot) * k}px` });
      s.pitch = pitch;
      overlay.append(s.gap, s.foot);
    }
    cue(s.down, "tap");
    cue(s.crossing, "tick", { v: 1 });
    cue(s.release, "chime");
    if (s.close) cue(s.release + 0.02, "swish", { v: 0.3 });
  }
  /** The widget's tick filled, a beat after the tap lands on it. */
  const TICKED = 22.4;
  /** The month's total counting up as the phone closes in on it, then the list scrolling. */
  const COUNT = [16.9, 17.75];
  const SCROLL = [18.45, 19.5];
  const CARD_ARRIVALS = [24.75, 25.6, 26.45];
  /** «Set homework» opening out of the tapped button, typed into in the time there is, saved, landed. */
  const DIALOG = [29.95, 30.4];
  const TYPING = [30.55, 32.15];
  const SAVE_AT = 32.35;
  const LAND = [32.5, 33.0];
  /** The theme switched to dark, spreading from the tap; then back out of Settings to Today. */
  const DARK = [39.05, 39.7];
  const LEAVE = [40.2, 40.6];

  // Typed at a typist's rhythm, sped up to fit the time there is.
  const typedText = F.film.homework;
  const typedAt = [];
  {
    const next = random(7);
    const chars = [...typedText];
    const gaps = chars.map((ch) => (ch === " " ? 0.11 : 0.062) + next() * 0.04);
    const pace = Math.min(1, (TYPING[1] - TYPING[0]) / gaps.reduce((a, b) => a + b, 0));
    if ((TYPING[1] - TYPING[0]) / chars.length < 0.03) fail("The film's homework is too long to type in time; shorten DemoTutor.typedHomework");
    let t = TYPING[0];
    chars.forEach((ch, i) => {
      typedAt.push(t);
      cue(t, ch === " " ? "space" : "key", { v: 0.75 + next() * 0.25, c: ch.codePointAt(0) });
      t += gaps[i] * pace;
    });
  }
  // The tagline is typed between the name landing and the cut to the phone, as fast as it has to be.
  const taglineAt = [];
  {
    const next = random(3);
    const from = 1.3, until = 3.25, chars = [...F.words.tagline];
    const gaps = chars.map((ch) => (ch === " " ? 0.07 : 0.04) + next() * 0.022);
    const pace = Math.min(1, (until - from) / gaps.reduce((a, b) => a + b, 0));
    if ((until - from) / chars.length < 0.025) fail("The tagline is too long to type in two seconds; shorten it in listing.json");
    let t = from;
    chars.forEach((ch, i) => {
      taglineAt.push(t);
      cue(t, ch === " " ? "space" : "key", { v: 0.6 + next() * 0.3, c: ch.codePointAt(0) });
      t += gaps[i] * pace;
    });
  }
  // The total's count, ticking at each new figure, but no faster than a clock's tick can be told apart.
  {
    let last = -1, lastAt = -1;
    for (let t = COUNT[0]; t < COUNT[1]; t += 1 / 120) {
      const shown = report.amount.at(span(t, COUNT[0], COUNT[1], E.out));
      if (shown !== last && t - lastAt >= 0.045) {
        cue(t, "tick", { v: 0.5 + 0.5 * span(t, COUNT[0], COUNT[1], E.linear) });
        last = shown;
        lastAt = t;
      }
    }
  }

  // The rest of the sound, cut to the pictures.
  cue(0.28, "boom");
  cue(0.55, "whoosh", { d: 0.5, v: 0.5 });
  cue(3.3, "whoosh", { d: 0.7, v: 0.9 });
  cue(4.0, "impact", { v: 0.55 });
  for (const t of [7.82, 11.78, 15.78, 19.78, 23.78, 27.78, 33.78, 37.78]) cue(t, "whoosh", { d: 0.6, v: 0.8 });
  for (const { at } of TAPS) cue(at, "tap");
  cue(TICKED, "chime");
  for (const [id, at] of CALLOUTS) cue(at, id === "sync" || id === "invites" ? "switch" : "pop");
  cue(COUNT[1], "coin");
  cue(SCROLL[0], "swish", { v: 0.3 });
  CARD_ARRIVALS.forEach((t) => {
    cue(t, "ding");
    cue(t, "buzz");
  });
  cue(DIALOG[0] + 0.05, "swish", { v: 0.45 });
  cue(SAVE_AT, "tap");
  cue(LAND[0] + 0.05, "whoosh", { d: 0.45, v: 0.45 });
  cue(DARK[0] - 0.05, "switch");
  cue(DARK[0], "whoosh", { d: 0.9, v: 0.5 });
  cue(LEAVE[0], "swish", { v: 0.35 });
  cue(42.0, "impact", { v: 1 });
  cue(43.2, "sparkle");

  /**
   * The lock screen's cards, each in a wrapper whose height grows as it arrives — from the bottom of the
   * stack up, each pushing the ones below it down. Measured once on a phone at rest.
   */
  const cardWraps = [];
  function setUpCards() {
    phone.style.transform = "none";
    const list = $(".cards", layers.lockscreen);
    const gap = parseFloat(getComputedStyle(list).rowGap) || 0;
    for (const card of $$(".card", list)) {
      const wrap = document.createElement("div");
      card.replaceWith(wrap);
      wrap.append(card);
      cardWraps.push({ wrap, card, height: card.getBoundingClientRect().height + gap });
    }
    list.style.gap = "0";
  }

  // --- Drawing a frame -------------------------------------------------------------------------------

  /** The frame at [t] seconds. */
  function seek(t) {
    background(t);
    brand(t);
    const pose = keys(t, POSES);
    shake(t, pose);
    placePhone(pose);
    screen(t);
    for (const [id, a, b] of CAPTIONS) caption(captions[id], t, a, b);
    for (const [id, a, b, lift] of CALLOUTS) liftRow(callouts[id], t, a, b, lift);
    for (const tap of TAPS) press(tap, t);
    for (const s of Object.values(SWIPES)) swipe(s, t);
    typingCard(t, pose);
    calendarCard(t);
    // The film fades out with the music.
    fade.style.opacity = String(span(t, DURATION - 0.7, DURATION, E.in));
  }

  function background(t) {
    // The night comes with the dark theme.
    const nightness = span(t, DARK[0], DARK[1]) * (1 - span(t, 41.6, 42.8));
    night.style.opacity = String(nightness);
    const fx = SW / 1920, fy = SH / 1080;
    const drift = [[1250, -300, 0.13, 0], [-420, 520, 0.09, 2], [700, 700, 0.11, 4]];
    glows.forEach((g, i) => {
      const [x, y, f, ph] = drift[i];
      g.style.transform = `translate(${x * fx + Math.sin(t * f + ph) * 160}px, ${y * fy + Math.cos(t * f * 1.3 + ph) * 110}px)`;
    });
    dots.forEach((d, i) => {
      const r = random(100 + i);
      const x = r() * SW, speed = 12 + r() * 26, size = 3 + r() * 7, phase = r() * 1000;
      const y = SH + 20 - ((t * speed + phase) % (SH + 120));
      d.style.width = d.style.height = `${size}px`;
      d.style.transform = `translate(${x + Math.sin(t * 0.6 + phase) * 30}px, ${y}px)`;
      d.style.opacity = String((0.12 + 0.18 * Math.sin(t * 1.3 + phase) ** 2) * (1 - 0.5 * nightness));
    });
  }

  let tagline = F.words.tagline;
  function brand(t) {
    // The opening: the K pops in, the name follows, and the tagline is typed.
    const into = span(t, 3.3, 3.85, E.in);
    intro.classList.toggle("hidden", t > 3.9);
    intro.style.opacity = String(1 - into);
    intro.style.transform = `scale(${1 + into * 0.35})`;
    intro.style.filter = into > 0 ? `blur(${into * 14}px)` : "";
    lockup(intro, t, 0.28);
    const shown = taglineAt.filter((at) => t >= at).length;
    $(".typed", intro).textContent = tagline.slice(0, shown);
    const typingNow = shown > 0 && shown < taglineAt.length;
    $(".caret", intro).style.opacity = t < 1.15 ? "0" : typingNow || Math.floor(t * 2.4) % 2 === 0 ? "1" : "0";

    // The close: the same, with the tagline settled and the way to get it.
    outro.classList.toggle("hidden", t < 41.8);
    lockup(outro, t, 42.0);
    const line = span(t, 42.65, 43.15, E.out);
    $(".tagline", outro).style.opacity = String(line);
    $(".tagline", outro).style.transform = `translateY(${(1 - line) * 30}px)`;
    const cta = span(t, 43.2, 43.75, E.back);
    $(".cta", outro).style.opacity = String(span(t, 43.2, 43.4));
    $(".cta", outro).style.transform = `scale(${0.6 + 0.4 * cta})`;
  }

  /** The tile springs in at [at], and the name's letters rise after it. */
  function lockup(root, t, at) {
    const tile = span(t, at, at + 0.75, E.spring);
    $(".tile", root).style.transform = `scale(${tile}) rotate(${(1 - tile) * -14}deg)`;
    $(".tile", root).style.opacity = String(span(t, at, at + 0.08, E.linear));
    $$(".wordmark span", root).forEach((letter, i) => {
      const p = span(t, at + 0.22 + i * 0.06, at + 0.62 + i * 0.06, E.out5);
      letter.style.opacity = String(p);
      letter.style.transform = `translateY(${(1 - p) * 70}px)`;
    });
  }

  /** The phone jolts as each notification arrives, as a phone on vibrate does. */
  function shake(t, pose) {
    for (const at of CARD_ARRIVALS) {
      const d = t - at;
      if (d >= 0 && d < 0.6) {
        const k = Math.exp(-d * 8);
        pose.rz += Math.sin(d * 2 * Math.PI * 17) * 1.6 * k;
        pose.x += Math.sin(d * 2 * Math.PI * 23) * 5 * k;
      }
    }
  }

  /**
   * Puts the phone at [pose] by transform alone. It is laid out once, at [WL], and only ever moved and
   * scaled: re-laid out at each frame's size, as it first was, its text and pictures snapped to whole
   * pixels, so a slow push-in moved them a pixel every other frame and the screen trembled. Scaled in
   * depth too, so its edge keeps its thickness as it shrinks; the light on its glass slides as it turns.
   */
  function placePhone(pose) {
    const s = (pose.s * W0) / WL;
    phone.style.transform = `translate(${pose.x - LG.W / 2}px, ${pose.y - LG.H / 2}px) perspective(2600px) ` +
      `rotateX(${pose.rx}deg) rotateY(${pose.ry}deg) rotateZ(${pose.rz}deg) scale3d(${s}, ${s}, ${s})`;
    phone.style.setProperty("--glare", `${50 - pose.ry * 1.8 + pose.rx * 0.8}%`);
  }

  /** Which captures are on the phone's screen at t, and how they come and go. */
  function screen(t) {
    const g = LG;
    const shown = {};
    const show = (shot, opacity = 1, style = {}) => (shown[shot] = { opacity, style });
    // A cut, not a cross-fade: two screens faded into each other doubled every row that moved. The cuts
    // fall mid-swing, when the phone is turned furthest from the camera.
    const swap = (from, to, at) => show(t < at ? from : to);

    if (t < 8.07) show("today");
    // The swiped row says «Taught» from the moment the swipe lets go of it, as the app redraws it.
    else if (t < 12) show(t < SWIPES.lesson.release ? "film-day" : "film-swiped");
    else if (t < 16) swap("film-swiped", "people", 12.02);
    else if (t < 20) swap("people", "reports", 16.02);
    else if (t < 24) swap("reports", "homescreen", 20.02);
    else if (t < 28) swap("homescreen", "lockscreen", 24.07);
    else if (t < 34) {
      const list = t < SWIPES.homework.release ? "film-homework-list" : t < LAND[0] ? "film-homework-done" : "film-homework";
      swap("lockscreen", list, 28.02);
    } else if (t < 38) swap("film-homework", "settings-calendar", 34.02);
    else {
      // From the tap on «Dark»: the dark settings spread over the light ones, then Today slides in from
      // the left as Settings is left for it.
      const wipe = span(t, DARK[0], DARK[1], E.inOut), leave = span(t, LEAVE[0], LEAVE[1], E.inOut);
      if (leave >= 1) show("film-today-dark");
      else {
        if (wipe < 1) swap("settings-calendar", "film-theme", 38.02);
        if (leave > 0) show("film-today-dark", 1, { app: `translateX(${(leave - 1) * 30}%)` });
        if (wipe > 0) {
          const m = mark("film-theme", "dark"), x = ((m[0] + m[2]) / 2) * g.k, y = g.status + ((m[1] + m[3]) / 2) * g.k;
          const h = g.status + CH * g.k, reach = Math.hypot(Math.max(x, g.sw - x), Math.max(y, h - y));
          show("film-theme-dark", 1, {
            clipPath: wipe < 1 ? `circle(${wipe * reach}px at ${x}px ${y}px)` : "",
            app: leave > 0 ? `translateX(${leave * 100}%)` : "",
          });
        }
      }
    }

    for (const [shot, el] of Object.entries(layers)) {
      const on = shown[shot];
      el.classList.toggle("hidden", !on);
      if (!on) continue;
      el.style.opacity = String(on.opacity);
      el.style.clipPath = on.style.clipPath ?? "";
      el.style.transform = on.style.transform ?? "";
      const app = $(".app", el);
      if (app) app.style.transform = on.style.app ?? "";
    }
    // Draw order: whatever came last in [shown] on top, and the scrim over the screens.
    Object.keys(shown).forEach((shot, i) => {
      layers[shot].style.zIndex = String(i + 1);
    });
    scrim.style.zIndex = "50";

    // The widget's tick, filled once tapped: the same widget with that lesson taught, faded in over it.
    $(".widget-face.ticked", layers.homescreen).style.opacity = String(span(t, TICKED, TICKED + 0.15, E.linear));

    // The lock screen's cards.
    if (shown.lockscreen) {
      cardWraps.forEach((c, i) => {
        const at = CARD_ARRIVALS[cardWraps.length - 1 - i];
        const p = span(t, at, at + 0.45, E.out5);
        c.wrap.style.height = `${c.height * p}px`;
        c.card.style.opacity = String(span(t, at, at + 0.25, E.linear));
        c.card.style.transform = `translateY(${(1 - p) * -40 * (WL / W0)}px) scale(${0.94 + 0.06 * p})`;
      });
    }
    if (shown.reports) reports(t);

    // The typing card's scrim.
    scrim.style.opacity = String(0.45 * held(t, DIALOG[0] + 0.02, LAND[1] - 0.1, 0.25, 0.35));
  }

  /** The report: its total counting up from nothing, then the list scrolled down to its last breakdowns. */
  function reports(t) {
    const p = span(t, COUNT[0], COUNT[1], E.out);
    report.words.textContent = report.amount.at(p);
    report.svg.style.opacity = String(1 - span(t, COUNT[1] + 0.05, COUNT[1] + 0.25, E.linear));
    const scroll = span(t, SCROLL[0], SCROLL[1], E.inOut);
    report.content.style.transform = `translateY(${-scroll * report.d * report.k}px)`;
    report.tinted.style.opacity = String(span(t, SCROLL[0], SCROLL[0] + 0.25, E.linear));
  }

  function caption(el, t, a, b) {
    const on = t >= a - 0.05 && t <= b + 0.6;
    el.classList.toggle("hidden", !on);
    if (!on) return;
    $$(".w > span", el).forEach((word, i) => {
      const rise = span(t, a + i * 0.055, a + i * 0.055 + 0.55, E.out5);
      const leave = span(t, b + i * 0.03, b + i * 0.03 + 0.32, E.in);
      word.style.transform = `translateY(${(1 - rise) * 110 - leave * 110}%)`;
    });
    const sub = $("p", el);
    const subIn = span(t, a + 0.35, a + 0.85, E.out5), subOut = span(t, b, b + 0.3, E.in);
    sub.style.opacity = String(subIn * (1 - subOut));
    sub.style.transform = `translateY(${(1 - subIn) * 26 - subOut * 20}px)`;
    const bar = $(".bar", el);
    bar.style.transform = `scaleX(${span(t, a, a + 0.5, E.out5) * (1 - span(t, b, b + 0.25, E.in))})`;
  }

  function liftRow(c, t, a, b, lift) {
    const p = held(t, a, b, 0.32, 0.28);
    const visible = p > 0.001;
    c.el.classList.toggle("hidden", !visible);
    if (!visible) return;
    // Cut a little inside the mark: scaled, the copy's outermost pixels blend with what lies just outside
    // the card, and a lighter screen behind it drew a pale rim round it.
    const g = LG, whole = mark(c.shot, c.name);
    const m = [whole[0] + LIFT_INSET, whole[1] + LIFT_INSET, whole[2] - LIFT_INSET, whole[3] - LIFT_INSET,
      Math.max(0, (whole[4] ?? 0) - LIFT_INSET)];
    const pos = local(g, m[0], m[1]);
    const w = (m[2] - m[0]) * g.k, h = (m[3] - m[1]) * g.k;
    const pop = span(t, a, a + 0.45, E.back) * (1 - span(t, b - 0.28, b, E.in));
    const scale = 1 + (lift - 1) * pop;
    // Cut to the card's own corners (the app's shape, recorded with the mark), so no corner of the card
    // shows inside a rounder or squarer copy of it; a row with no card of its own gets a gentle rounding.
    const round = Math.max(m[4] ?? 0, ROW_CORNER) * g.k;
    Object.assign(c.el.style, {
      left: `${pos.x}px`, top: `${pos.y}px`, width: `${w}px`, height: `${h}px`,
      borderRadius: `${round}px`, transform: `scale(${scale})`,
    });
    c.el.style.backgroundSize = c.whole ? `${(whole[2] - whole[0]) * g.k}px ${(whole[3] - whole[1]) * g.k}px` : `${CW * g.k}px auto`;
    c.el.style.backgroundPosition = c.whole ? `${-LIFT_INSET * g.k}px ${-LIFT_INSET * g.k}px` : `${-m[0] * g.k}px ${-m[1] * g.k}px`;
    // Lifted, not outlined: its shadow alone sets it off the screen.
    c.el.style.boxShadow = `0 ${18 * p}px ${46 * p}px rgba(8, 10, 48, ${0.35 * p})`;
    c.el.style.opacity = "1";
  }

  function press(tap, t) {
    const d = t - tap.at;
    const on = d > -0.25 && d < 0.6;
    tap.hole.classList.toggle("hidden", !on);
    if (!on) return;
    // In as the finger lands, and back with a little give.
    const sink = span(d, -0.1, 0, E.out) * (1 - span(d, 0.04, 0.4, E.spring));
    tap.target.style.transform = tap.lift ? `scale(${1 - (1 - PRESSED) * sink})` : "";
    ripple(tap.ripple, d, tap.size.w / 2, tap.size.h / 2, tap.size.reach);
  }

  /**
   * Material's ripple, [d] seconds after the finger lands: out from (x, y) until it fills what it is
   * cut to ([reach], the farthest corner), and faded once the finger is up.
   */
  function ripple(el, d, x, y, reach) {
    const r = reach * (0.2 + 0.8 * span(d, -0.03, 0.35, E.out));
    Object.assign(el.style, { left: `${x - r}px`, top: `${y - r}px`, width: `${2 * r}px`, height: `${2 * r}px` });
    el.style.opacity = String(span(d, -0.03, 0.03, E.linear) * (1 - span(d, 0.25, 0.55, E.linear)));
  }

  /** A row being swiped: the thumb down on it, the row dragged off its panel, and what the lift leaves. */
  function swipe(s, t) {
    const [l, top, r, bottom] = s.m;
    const x = s.at(t);
    const on = t > s.down - 0.2 && t < s.release;
    s.band.classList.toggle("hidden", !on);
    if (on) {
      s.row.style.transform = `translateX(${x * LG.k}px)`;
      // Only what the row has uncovered: right of it, the capture shot past the mark has the row itself.
      s.armedReveal.style.clipPath = `inset(0 ${(CW - l - x) * LG.k}px 0 0)`;
      // The glyph grows at the mark: the panel short of it fades into the one past it.
      s.unarmedReveal.style.opacity = String(1 - span(t, s.crossing, s.crossing + 0.15, E.linear));
    }
    const thumb = t > s.down - 0.2 && t < s.release + 0.25;
    s.finger.classList.toggle("hidden", !thumb);
    if (thumb) {
      const p = local(LG, l + THUMB * (r - l) + x, (top + bottom) / 2);
      const down = span(t, s.down - 0.2, s.down, E.out), lift = span(t, s.release, s.release + 0.25, E.linear);
      Object.assign(s.finger.style, { left: `${p.x}px`, top: `${p.y}px` });
      $(".finger", s.finger).style.opacity = String(down * (1 - lift));
      $(".finger", s.finger).style.transform = `scale(${1.25 - 0.25 * down + 0.2 * lift})`;
    }
    if (!s.close) return;
    const closing = t >= s.release && t < s.release + s.close;
    s.gap.classList.toggle("hidden", !closing);
    s.foot.classList.toggle("hidden", !closing);
    if (closing) {
      const p = span(t, s.release, s.release + s.close, E.out);
      s.below.style.top = `${(bottom - top - p * s.pitch) * LG.k}px`;
      s.foot.style.opacity = String(1 - span(t, s.release, s.release + 0.25, E.linear));
    }
  }

  /** «Set homework»: out of the tapped button, typed into, saved, and landed on the list as its new row. */
  function typingCard(t, pose) {
    const on = t > DIALOG[0] - 0.02 && t < LAND[1] + 0.02;
    typing.classList.toggle("hidden", !on);
    const rest = TALL ? { x: SW / 2, y: 1150 } : { x: 1340, y: 540 };
    const width = typing.offsetWidth, height = typing.offsetHeight;
    if (on) {
      const add = onStage(pose, "film-homework-done", "add");
      const open = span(t, DIALOG[0], DIALOG[1], E.out5);
      let x = lerp((add.l + add.r) / 2, rest.x, open), y = lerp((add.t + add.b) / 2, rest.y, open);
      let sx = lerp(0.12, 1, open), sy = sx, alpha = span(t, DIALOG[0], DIALOG[0] + 0.1, E.linear);
      const land = span(t, LAND[0], LAND[1], E.inOut);
      if (land > 0) {
        const row = onStage(pose, "film-homework", "added");
        // Shrunk evenly to the row's width — squeezed to its height as well, the words went flat — and
        // faded into the real row as it gets there.
        x = lerp(rest.x, (row.l + row.r) / 2, land);
        y = lerp(rest.y, (row.t + row.b) / 2, land);
        sx = sy = lerp(1, (row.r - row.l) / width, land);
        alpha = 1 - span(t, lerp(LAND[0], LAND[1], 0.45), LAND[1], E.inOut);
      }
      typing.style.transform = `translate(${x - width / 2}px, ${y - height / 2}px) scale(${sx}, ${sy})`;
      typing.style.opacity = String(alpha);
      $(".field.task", typing).classList.toggle("focus", t >= DIALOG[1] + 0.05);
      const shown = typedAt.filter((at) => t >= at).length;
      $(".typed", typing).textContent = typedText.slice(0, shown);
      const blink = shown === typedAt.length && Math.floor((t - typedAt.at(-1)) * 2.2) % 2 === 1;
      $(".caret", typing).style.opacity = t >= DIALOG[1] + 0.05 && !blink && t < SAVE_AT ? "1" : "0";
    }
    const pill = saveRipple.parentElement;
    ripple(saveRipple, t - SAVE_AT, pill.offsetWidth / 2, pill.offsetHeight / 2, Math.hypot(pill.offsetWidth, pill.offsetHeight) / 2);
  }

  /** The day as the phone's calendar shows it once the lessons are synced, beside the switch for it. */
  function calendarCard(t) {
    const p = held(t, 35.85, 37.75, 0.5, 0.3);
    calendar.classList.toggle("hidden", p <= 0);
    if (p <= 0) return;
    const rise = span(t, 35.85, 36.35, E.out5);
    calendar.style.opacity = String(p);
    calendar.style.transform = `translateY(${(1 - rise) * 60}px) scale(${0.96 + 0.04 * rise})`;
    $$(".event", calendar).forEach((ev, i) => {
      const at = 36.0 + i * 0.16;
      const q = span(t, at, at + 0.35, E.back);
      ev.style.opacity = String(span(t, at, at + 0.12, E.linear));
      ev.style.transform = `translateX(${(1 - q) * 50}px)`;
    });
  }

  // --- Motion blur ---------------------------------------------------------------------------------

  /**
   * The patch under each card or button pressed in: the colour just beside it on its capture, what it
   * stands on, so the ring it uncovers as it sinks is the screen behind and not its own edge.
   */
  function setUpTaps() {
    const canvas = document.createElement("canvas");
    canvas.width = canvas.height = 1;
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    for (const tap of TAPS.filter((x) => x.lift)) {
      const [l, top, , bottom] = mark(tap.shot, tap.mark);
      ctx.drawImage($(".app", layers[tap.shot]), l - BESIDE, (top + bottom) / 2, 1, 1, 0, 0, 1, 1);
      const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data;
      tap.hole.style.background = `rgb(${r}, ${g}, ${b})`;
    }
  }

  /** Everything that moves on its own, whose edges tell how fast the frame is changing. */
  let moving = [];
  function setUpMotion() {
    moving = [phone, typing, calendar, intro, outro, report.content,
      ...$$(".caption .w > span, .caption p, .caption .bar"), ...$$(".brand .tile, .brand .wordmark span, .brand .tagline, .brand .cta"),
      ...Object.values(callouts).map((c) => c.el), ...TAPS.flatMap((tap) => [tap.target, tap.ripple]),
      saveRipple, ...cardWraps.map((c) => c.card),
      ...Object.values(SWIPES).flatMap((s) => [s.row, s.finger, s.below].filter(Boolean)),
      ...$$(".event", calendar), ...$$(".layer .app")];
  }
  /**
   * The moments to blend frame [t] from: how far the fastest edge on the stage moves in one frame, found
   * by drawing the frame half a frame either side, decides how many (parts.js). Kept between the jumps
   * ([JUMPS]), so a frame just after one is not blended with the moment before it.
   */
  function exposuresAt(t, fps, scale) {
    const lo = Math.max(0, ...JUMPS.filter((j) => j <= t));
    const hi = Math.min(DURATION, ...JUMPS.filter((j) => j > t)) - 1e-4;
    const a = clamp(t - 0.5 / fps, lo, hi), b = clamp(t + 0.5 / fps, lo, hi);
    if (b <= a) return [t];
    const edges = () => moving.map((el) => {
      const r = el.getBoundingClientRect();
      return r.width > 0 && r.height > 0 ? r : null;
    });
    seek(a);
    const before = edges();
    seek(b);
    const after = edges();
    let fastest = 0;
    before.forEach((r, i) => {
      const s = after[i];
      if (!r || !s) return;
      fastest = Math.max(fastest, Math.abs(r.left - s.left), Math.abs(r.top - s.top), Math.abs(r.right - s.right), Math.abs(r.bottom - s.bottom));
    });
    return exposures(t, (fastest * (1 / fps)) / (b - a), { fps, scale, lo, hi });
  }

  function fail(message) {
    throw new Error(message);
  }

  // --- Ready ---------------------------------------------------------------------------------------

  $$(".event", calendar).forEach((_, i) => cue(36.0 + i * 0.16, "blip", { n: i }));

  const ready = (async () => {
    await document.fonts.ready;
    await Promise.all($$("img").map((img) => img.decode().catch(() => fail(`could not load ${img.src}`))));
    setUpCaptions();
    setUpCards();
    setUpTaps();
    tagline = taglineLines(F.words.tagline);
    $(".tagline", outro).textContent = tagline;
    // Typed from the left of where the whole line will end up, not grown out of the middle.
    const typed = $(".tagline", intro);
    $(".typed", typed).textContent = tagline;
    const box = typed.getBoundingClientRect();
    Object.assign(typed.style, { width: `${box.width}px`, height: `${box.height}px`, textAlign: "left" });
    $(".typed", typed).textContent = "";
    setUpMotion();
    // The music's shape: an opening, the groove, a quieter bar pair under the reminders' chimes, the
    // groove again, and the close.
    const sections = [
      { from: 0, to: 4, part: "intro" },
      { from: 4, to: 24, part: "groove" },
      { from: 24, to: 28, part: "break" },
      { from: 28, to: 42, part: "groove" },
      { from: 42, to: DURATION, part: "outro" },
    ];
    const placed = cues.filter((c) => c.t >= 0 && c.t < DURATION).sort((a, b) => a.t - b.t);
    return { duration: DURATION, bpm: 120, cues: placed, sections };
  })();

  window.film = {
    ready,
    seek: (t) => new Promise((done) => {
      seek(t);
      requestAnimationFrame(() => done());
    }),
    /** Sets the page to frame [t] without waiting for it to be drawn: gpu.js waits for its own paint. */
    set: (t) => seek(t),
    /** The moments frame [t] is blended from, at [fps] and the output's [scale]. */
    exposures: (t, fps, scale) => exposuresAt(t, fps, scale),
  };
})();
