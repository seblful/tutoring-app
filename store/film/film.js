// The promo video's timeline. video.mjs builds the page (the phone with a layer per capture, the captions,
// the brand) and sets `window.FILM`; this script moves all of it as a pure function of time — `film.seek(t)`
// draws the frame at t seconds, in any order — and lists the sounds that go with it (`film.cues`), so the
// frames and the soundtrack are made separately and still land together.
//
// Bars of 2 s (120 bpm): every scene starts on a bar, so the cuts fall on the music's downbeats.

"use strict";

(() => {
  const F = window.FILM;
  const DURATION = 40;

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

  /** The same jitter every run: typing rhythm and the drifting light are part of the cut, not noise. */
  function random(seed) {
    return () => {
      seed = (seed + 0x6d2b79f5) | 0;
      let r = Math.imul(seed ^ (seed >>> 15), 1 | seed);
      r = (r + Math.imul(r ^ (r >>> 7), 61 | r)) ^ r;
      return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
    };
  }

  // --- The phone ----------------------------------------------------------------------------------

  const W0 = 400; // the phone's width at rest; it is 904 px tall, so it fits the frame with room above and below
  const CW = F.capture.width, CH = F.capture.height;

  /** The phone's parts at width W: the bezel, the screen, the status bar, and capture px → phone px. */
  function geo(W) {
    const pad = 0.022 * W, sw = 0.956 * W, status = 0.092 * W, k = sw / CW;
    return { W, pad, sw, status, k, H: 2 * pad + status + CH * k };
  }
  /** Where a point of a capture is, from the phone's top-left corner. */
  const local = (g, x, y) => ({ x: g.pad + x * g.k, y: g.pad + g.status + y * g.k });
  const mark = (shot, name) => F.shots[shot]?.marks?.[name] ?? fail(`no mark «${name}» on «${shot}»`);

  /** The phone at scale s, placed so the capture point (x, y) sits at (qx, qy) on the stage. */
  function aimAt(x, y, s, qx, qy) {
    const g = geo(W0 * s), p = local(g, x, y);
    return { x: qx - (p.x - g.W / 2), y: qy - (p.y - g.H / 2), s, rx: 0, ry: 0, rz: 0 };
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

  const RIGHT = { x: 1390, y: 548, s: 1, rx: 4, ry: -12, rz: 0 };
  const LEFT = { x: 530, y: 548, s: 1, rx: 4, ry: 12, rz: 0 };
  const FRONT_R = { ...RIGHT, rx: 0, ry: 0 };
  const FRONT_L = { ...LEFT, rx: 0, ry: 0 };
  const SWING_TO_L = { ry: 26, s: -0.07, y: 20 };
  const SWING_TO_R = { ry: -26, s: -0.07, y: 20 };
  const Q_R = [1350, 560];
  const Q_L = [570, 560];

  const POSES = [
    [0, { x: 1720, y: 1650, s: 0.9, rx: 18, ry: -40, rz: 14 }],
    [3.55, { x: 1720, y: 1650, s: 0.9, rx: 18, ry: -40, rz: 14 }],
    [4.5, RIGHT, E.out5],
    [5.45, { ...RIGHT, ry: -6, rx: 2 }, E.linear],
    // The lesson on now, tapped: its sheet comes up, «Taught» is tapped, and the row says so.
    [6.0, aim("schedule-day", "onNow", 1.38, ...Q_R)],
    [6.8, aim("schedule-day", "onNow", 1.38, ...Q_R)],
    [7.25, aim("film-sheet", "taught", 1.62, ...Q_R)],
    [7.9, aim("film-sheet", "taught", 1.62, ...Q_R)],
    [8.45, aim("film-taught", "row", 1.42, ...Q_R)],
    [9.3, aim("film-taught", "row", 1.42, ...Q_R)],
    [9.75, FRONT_R],
    // Who owes.
    [10.35, LEFT, E.inOut, SWING_TO_L],
    [10.95, { ...FRONT_L, s: 1.05 }],
    [12.95, { ...FRONT_L, s: 1.05 }],
    [13.75, FRONT_L],
    // The month's income.
    [14.35, RIGHT, E.inOut, SWING_TO_R],
    [14.95, aim("reports", "income", 1.5, 1330, 470, 150)],
    [16.55, aim("reports", "income", 1.56, 1330, 470, 150), E.linear],
    [17.1, FRONT_R],
    [17.75, FRONT_R],
    // Reminders on the lock screen.
    [18.35, LEFT, E.inOut, SWING_TO_L],
    [19.0, { ...LEFT, ry: 5, rx: 2, s: 1.04 }],
    [21.45, { ...LEFT, ry: 2, rx: 1, s: 1.08 }, E.linear],
    [21.75, FRONT_L],
    // Homework, typed and landed on the list.
    [22.35, RIGHT, E.inOut, SWING_TO_R],
    [22.85, FRONT_R],
    [27.75, FRONT_R],
    // The calendar.
    [28.35, LEFT, E.inOut, SWING_TO_L],
    [28.8, FRONT_L],
    [31.75, FRONT_L],
    // After dark, and nothing leaves the phone.
    [32.35, RIGHT, E.inOut, SWING_TO_R],
    [33.0, { ...RIGHT, ry: -4, rx: 2, s: 1.02 }],
    [35.45, { ...RIGHT, ry: -9, rx: 3, s: 1.07 }, E.linear],
    [36.1, { x: 1390, y: 1720, s: 0.85, rx: 30, ry: -20, rz: -6 }, E.in],
  ];

  // --- Sounds -------------------------------------------------------------------------------------

  const cues = [];
  const cue = (t, kind, extra = {}) => cues.push({ t, kind, ...extra });

  // --- Elements -----------------------------------------------------------------------------------

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const phone = $("#phone");
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

  /** The sheet slides up over the schedule: the dimmed screen and the sheet are the same capture, split. */
  const sheet = layers["film-sheet"];
  const sheetPart = sheet.cloneNode(true);
  sheetPart.dataset.shot = "film-sheet-part";
  stack.append(sheetPart);
  const scrim = document.createElement("div");
  scrim.className = "scrim";
  stack.append(scrim);

  const callouts = {};
  const taps = {};

  /** A lifted copy of [name] on [shot], drawn over the phone's screen. */
  function callout(id, shot, name, color) {
    const el = document.createElement("div");
    el.className = "callout";
    el.style.backgroundImage = `url("${F.shots[shot].src}")`;
    const ring = document.createElement("div");
    ring.className = "ring";
    ring.style.borderColor = color;
    overlay.append(el, ring);
    callouts[id] = { el, ring, shot, name };
  }
  function tap(id) {
    const el = document.createElement("div");
    el.className = "tap";
    el.innerHTML = `<div class="wave"></div><div class="finger"></div>`;
    overlay.append(el);
    taps[id] = el;
  }
  callout("row", "film-taught", "row", "#a6f2b5");
  callout("debtor", "people", "debtor", "#ffb4ab");
  callout("credit", "people", "credit", "#a6f2b5");
  callout("added", "film-homework", "added", "#a9edff");
  callout("sync", "settings-calendar", "sync", "#a9edff");
  callout("invites", "settings-calendar", "invites", "#a9edff");
  for (const id of ["onNow", "taught", "add"]) tap(id);
  const saveTap = document.createElement("div");
  saveTap.className = "tap";
  saveTap.style.setProperty("--phone", "560px");
  saveTap.innerHTML = `<div class="wave"></div><div class="finger"></div>`;
  $("#stage").append(saveTap);

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

  // --- The cut -------------------------------------------------------------------------------------

  /**
   * When each caption is on, by the capture it belongs to. The next one starts only once the last word of
   * the one before has left (half a second), or the two titles are on screen over each other.
   */
  const CAPTIONS = [
    ["schedule-day", 4.15, 6.15],
    ["lesson-sheet", 6.75, 9.6],
    ["people", 10.25, 13.6],
    ["reports", 14.25, 17.6],
    ["reminders", 18.25, 21.6],
    ["homework", 22.25, 27.6],
    ["settings-calendar", 28.25, 31.6],
    ["schedule-day-dark", 32.3, 35.5],
  ];
  /** When each lifted row is up. */
  const CALLOUTS = [
    ["row", 8.48, 9.35, 1.1],
    ["debtor", 11.0, 12.95, 1.1],
    ["credit", 11.65, 12.95, 1.1],
    ["added", 26.95, 27.65, 1.1],
    ["sync", 28.9, 30.2, 1.06],
    ["invites", 29.35, 30.2, 1.06],
  ];
  /** When each tap lands, and on what. */
  const TAPS = [
    ["onNow", 6.12, "schedule-day", "onNow"],
    ["taught", 7.42, "film-sheet", "taught"],
    ["add", 23.05, "homework", "add"],
  ];
  const SHEET_UP = [6.28, 6.66];
  /** Material's scrim behind a modal sheet: black at 32%, what the capture's dimmed top measures. */
  const SCRIM = 0.32;
  /** The sheet's top corners: Material's extra-large shape, 28 dp, in the capture's 4× pixels. */
  const SHEET_CORNER = 28 * 4;
  const TAUGHT_AT = 7.47;
  const SHEET_DOWN = [7.6, 7.92];
  const CARD_ARRIVALS = [18.75, 19.6, 20.45];
  const TYPE_FROM = 23.75;
  const SAVE_AT = 26.3;
  const LAND = [26.45, 26.95];
  const DARK = [32.9, 33.6];

  const typedText = F.film.homework;
  const typedAt = [];
  {
    const next = random(7);
    let t = TYPE_FROM;
    for (const ch of typedText) {
      typedAt.push(t);
      cue(t, ch === " " ? "space" : "key", { v: 0.75 + next() * 0.25 });
      t += (ch === " " ? 0.11 : 0.062) + next() * 0.04;
    }
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
      cue(t, ch === " " ? "space" : "key", { v: 0.6 + next() * 0.3 });
      t += gaps[i] * pace;
    });
  }

  // The rest of the sound, cut to the pictures.
  cue(0.28, "boom");
  cue(0.55, "whoosh", { d: 0.5, v: 0.5 });
  cue(3.3, "whoosh", { d: 0.7, v: 0.9 });
  cue(4.0, "impact", { v: 0.55 });
  for (const t of [9.78, 13.78, 17.78, 21.78, 27.78, 31.78]) cue(t, "whoosh", { d: 0.6, v: 0.8 });
  for (const [, at] of TAPS) cue(at, "tap");
  cue(SHEET_UP[0], "swish", { v: 0.5 });
  cue(7.47, "chime");
  cue(SHEET_DOWN[0], "swish", { v: 0.4 });
  for (const [id, at] of CALLOUTS) cue(at, id === "sync" || id === "invites" ? "switch" : "pop");
  cue(15.0, "coin");
  CARD_ARRIVALS.forEach((t) => {
    cue(t, "ding");
    cue(t, "buzz");
  });
  cue(23.15, "swish", { v: 0.45 });
  cue(SAVE_AT, "tap");
  cue(LAND[0] + 0.05, "whoosh", { d: 0.45, v: 0.45 });
  cue(DARK[0], "switch");
  cue(DARK[0] + 0.05, "whoosh", { d: 0.9, v: 0.5 });
  cue(36.0, "impact", { v: 1 });
  cue(37.2, "sparkle");

  /**
   * The lock screen's cards, each in a wrapper whose height grows as it arrives — from the bottom of the
   * stack up, each pushing the ones below it down. Measured once on a phone at rest.
   */
  const cardWraps = [];
  function setUpCards() {
    placePhone({ x: 960, y: 540, s: 1, rx: 0, ry: 0, rz: 0 });
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

  function seek(t) {
    background(t);
    brand(t);
    const pose = keys(t, POSES);
    shake(t, pose);
    placePhone(pose);
    screen(t, pose);
    for (const [id, a, b] of CAPTIONS) caption(captions[id], t, a, b);
    for (const [id, a, b, lift] of CALLOUTS) liftRow(callouts[id], t, a, b, lift, pose);
    for (const [id, at, shot, name] of TAPS) press(taps[id], t, at, shot, name, pose);
    typingCard(t, pose);
    calendarCard(t);
    // The film fades out with the music.
    $("#stage").style.opacity = String(1 - span(t, DURATION - 0.7, DURATION, E.in));
  }

  function background(t) {
    const nightness = Math.max(span(t, DARK[0], DARK[1]), 0) * (1 - span(t, 35.6, 36.8));
    night.style.opacity = String(nightness);
    const drift = [[1250, -300, 0.13, 0], [-420, 520, 0.09, 2], [700, 700, 0.11, 4]];
    glows.forEach((g, i) => {
      const [x, y, f, ph] = drift[i];
      g.style.transform = `translate(${x + Math.sin(t * f + ph) * 160}px, ${y + Math.cos(t * f * 1.3 + ph) * 110}px)`;
    });
    dots.forEach((d, i) => {
      const r = random(100 + i);
      const x = r() * 1920, speed = 12 + r() * 26, size = 3 + r() * 7, phase = r() * 1000;
      const y = 1100 - ((t * speed + phase) % 1200);
      d.style.width = d.style.height = `${size}px`;
      d.style.transform = `translate(${x + Math.sin(t * 0.6 + phase) * 30}px, ${y}px)`;
      d.style.opacity = String((0.12 + 0.18 * Math.sin(t * 1.3 + phase) ** 2) * (1 - 0.5 * nightness));
    });
  }

  function brand(t) {
    // The opening: the K pops in, the name follows, and the tagline is typed.
    const into = span(t, 3.3, 3.85, E.in);
    intro.classList.toggle("hidden", t > 3.9);
    intro.style.opacity = String(1 - into);
    intro.style.transform = `scale(${1 + into * 0.35})`;
    intro.style.filter = into > 0 ? `blur(${into * 14}px)` : "";
    lockup(intro, t, 0.28);
    const shown = taglineAt.filter((at) => t >= at).length;
    $(".typed", intro).textContent = F.words.tagline.slice(0, shown);
    const typingNow = shown > 0 && shown < taglineAt.length;
    $(".caret", intro).style.opacity = t < 1.15 ? "0" : typingNow || Math.floor(t * 2.4) % 2 === 0 ? "1" : "0";

    // The close: the same, with the tagline settled and the way to get it.
    outro.classList.toggle("hidden", t < 35.8);
    lockup(outro, t, 36.0);
    const line = span(t, 36.65, 37.15, E.out);
    $(".tagline", outro).style.opacity = String(line);
    $(".tagline", outro).style.transform = `translateY(${(1 - line) * 30}px)`;
    const cta = span(t, 37.2, 37.75, E.back);
    $(".cta", outro).style.opacity = String(span(t, 37.2, 37.4));
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

  function placePhone(pose) {
    const g = geo(W0 * pose.s);
    phone.style.setProperty("--phone", `${g.W}px`);
    phone.style.left = `${pose.x - g.W / 2}px`;
    phone.style.top = `${pose.y - g.H / 2}px`;
    phone.style.transform =
      `perspective(2600px) rotateX(${pose.rx}deg) rotateY(${pose.ry}deg) rotateZ(${pose.rz}deg)`;
    overlay.style.setProperty("--phone", `${g.W}px`);
  }

  /** Which captures are on the phone's screen at t, and how they come and go. */
  function screen(t, pose) {
    const g = geo(W0 * pose.s);
    const shown = {};
    const show = (shot, opacity = 1, style = {}) => (shown[shot] = { opacity, style });
    // A cut, not a cross-fade: two screens faded into each other doubled every row that moved. The cuts
    // fall mid-swing, when the phone is turned furthest from the camera.
    const swap = (from, to, at) => show(t < at ? from : to);

    let dim = 0;
    if (t < 9.9) {
      // The row says «Taught» from the moment it is tapped, under the sheet, as it does in the app.
      show(t < TAUGHT_AT ? "schedule-day" : "film-taught");
      const up = span(t, SHEET_UP[0], SHEET_UP[1], E.out) * (1 - span(t, SHEET_DOWN[0], SHEET_DOWN[1], E.in));
      if (up > 0) {
        // Only the sheet comes from its capture, cut to its own rounded top. The dimming behind it is
        // Material's scrim drawn here, over whichever schedule is underneath: taken from the capture as
        // well, it left an undimmed band between its edge and the moving sheet.
        const top = g.status + F.sheetTop * g.k, height = g.status + CH * g.k, round = SHEET_CORNER * g.k;
        show("film-sheet-part", 1, {
          clipPath: `inset(${top}px 0 0 0 round ${round}px ${round}px 0 0)`,
          transform: `translateY(${(1 - up) * (height - top)}px)`,
        });
        dim = SCRIM * up;
      }
    } else if (t < 14) swap("film-taught", "people", 10.02);
    else if (t < 18) swap("people", "reports", 14.02);
    else if (t < 22) swap("reports", "lockscreen", 18.02);
    else if (t < 28) {
      swap("lockscreen", t < LAND[0] ? "homework" : "film-homework", 22.02);
    } else if (t < 32) swap("film-homework", "settings-calendar", 28.02);
    else {
      swap("settings-calendar", "schedule-day", 32.02);
      const wipe = span(t, DARK[0], DARK[1], E.inOut);
      // From the middle: started in a corner, the dark app met the black bezel and read as a blot.
      if (wipe > 0) show("schedule-day-dark", 1, { clipPath: `circle(${wipe * 125}% at 50% 42%)` });
    }

    for (const [shot, el] of Object.entries(layers).concat([["film-sheet-part", sheetPart]])) {
      const on = shown[shot];
      el.classList.toggle("hidden", !on);
      if (!on) continue;
      el.style.opacity = String(on.opacity);
      el.style.clipPath = on.style.clipPath ?? "";
      el.style.transform = on.style.transform ?? "";
    }
    // Draw order: whatever came last in [shown] on top; the scrim over the screens, the sheet over it.
    Object.keys(shown).forEach((shot, i) => {
      (shot === "film-sheet-part" ? sheetPart : layers[shot]).style.zIndex = String(i + 1);
    });
    sheetPart.style.zIndex = "60";
    scrim.style.zIndex = "50";

    // The lock screen's cards.
    if (shown.lockscreen) {
      cardWraps.forEach((c, i) => {
        const at = CARD_ARRIVALS[cardWraps.length - 1 - i];
        const p = span(t, at, at + 0.45, E.out5);
        const w = g.W / W0;
        c.wrap.style.height = `${c.height * w * p}px`;
        c.card.style.opacity = String(span(t, at, at + 0.25, E.linear));
        c.card.style.transform = `translateY(${(1 - p) * -40 * w}px) scale(${0.94 + 0.06 * p})`;
      });
    }

    // The sheet's scrim, or the typing card's.
    scrim.style.opacity = String(Math.max(dim, 0.45 * held(t, 23.12, 26.85, 0.25, 0.35)));
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

  function liftRow(c, t, a, b, lift, pose) {
    const p = held(t, a, b, 0.32, 0.28);
    const visible = p > 0.001;
    c.el.classList.toggle("hidden", !visible);
    c.ring.classList.toggle("hidden", !visible);
    if (!visible) return;
    const g = geo(W0 * pose.s), m = mark(c.shot, c.name);
    const pos = local(g, m[0], m[1]);
    const w = (m[2] - m[0]) * g.k, h = (m[3] - m[1]) * g.k;
    const pop = span(t, a, a + 0.45, E.back) * (1 - span(t, b - 0.28, b, E.in));
    const scale = 1 + (lift - 1) * pop;
    for (const el of [c.el, c.ring]) {
      el.style.left = `${pos.x}px`;
      el.style.top = `${pos.y}px`;
      el.style.width = `${w}px`;
      el.style.height = `${h}px`;
      el.style.transform = `scale(${scale})`;
    }
    c.el.style.backgroundSize = `${CW * g.k}px auto`;
    c.el.style.backgroundPosition = `${-m[0] * g.k}px ${-m[1] * g.k}px`;
    c.el.style.boxShadow = `0 ${18 * p}px ${46 * p}px rgba(8, 10, 48, ${0.4 * p})`;
    c.el.style.opacity = "1";
    c.ring.style.opacity = String(p);
  }

  function press(el, t, at, shot, name, pose) {
    const d = t - at;
    const on = d > -0.25 && d < 0.5;
    el.classList.toggle("hidden", !on);
    if (!on) return;
    const g = geo(W0 * pose.s), m = mark(shot, name);
    const p = local(g, (m[0] + m[2]) / 2, (m[1] + m[3]) / 2);
    drawTap(el, d, p.x, p.y);
  }

  function drawTap(el, d, x, y) {
    el.style.left = `${x}px`;
    el.style.top = `${y}px`;
    const down = span(d, -0.25, 0, E.out), up = span(d, 0.05, 0.4, E.out);
    $(".finger", el).style.opacity = String(down * (1 - span(d, 0.1, 0.35, E.linear)));
    $(".finger", el).style.transform = `scale(${1.25 - 0.25 * down})`;
    $(".wave", el).style.opacity = String(d > 0 ? 1 - up : 0);
    $(".wave", el).style.transform = `scale(${0.6 + up * 1.4})`;
  }

  /** «Set homework»: out of the tapped button, typed into, saved, and landed on the list as its new row. */
  function typingCard(t, pose) {
    const on = t > 23.08 && t < LAND[1] + 0.02;
    typing.classList.toggle("hidden", !on);
    saveTap.classList.toggle("hidden", !(t > SAVE_AT - 0.25 && t < SAVE_AT + 0.5));
    const rest = { x: 1340, y: 540 };
    const width = typing.offsetWidth, height = typing.offsetHeight;
    if (on) {
      const add = onStage(pose, "homework", "add");
      const open = span(t, 23.1, 23.55, E.out5);
      let x = lerp((add.l + add.r) / 2, rest.x, open), y = lerp((add.t + add.b) / 2, rest.y, open);
      let sx = lerp(0.12, 1, open), sy = sx, alpha = span(t, 23.1, 23.2, E.linear);
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
      typing.style.left = `${x - width / 2}px`;
      typing.style.top = `${y - height / 2}px`;
      typing.style.transform = `scale(${sx}, ${sy})`;
      typing.style.opacity = String(alpha);
      $(".field.task", typing).classList.toggle("focus", t >= 23.6);
      const shown = typedAt.filter((at) => t >= at).length;
      $(".typed", typing).textContent = typedText.slice(0, shown);
      const blink = shown === typedAt.length && Math.floor((t - typedAt.at(-1)) * 2.2) % 2 === 1;
      $(".caret", typing).style.opacity = t >= 23.6 && !blink && t < SAVE_AT ? "1" : "0";
    }
    if (t > SAVE_AT - 0.25 && t < SAVE_AT + 0.5) {
      const save = $(".save", typing).getBoundingClientRect();
      drawTap(saveTap, t - SAVE_AT, (save.left + save.right) / 2, (save.top + save.bottom) / 2);
    }
  }

  /** The day as the phone's calendar shows it once the lessons are synced, beside the switch for it. */
  function calendarCard(t) {
    const p = held(t, 29.85, 31.75, 0.5, 0.3);
    calendar.classList.toggle("hidden", p <= 0);
    if (p <= 0) return;
    const rise = span(t, 29.85, 30.35, E.out5);
    calendar.style.opacity = String(p);
    calendar.style.transform = `translateY(${(1 - rise) * 60}px) scale(${0.96 + 0.04 * rise})`;
    $$(".event", calendar).forEach((ev, i) => {
      const at = 30.0 + i * 0.16;
      const q = span(t, at, at + 0.35, E.back);
      ev.style.opacity = String(span(t, at, at + 0.12, E.linear));
      ev.style.transform = `translateX(${(1 - q) * 50}px)`;
    });
  }

  function fail(message) {
    throw new Error(message);
  }

  // --- Ready ---------------------------------------------------------------------------------------

  $$(".event", calendar).forEach((_, i) => cue(30.0 + i * 0.16, "blip", { n: i }));
  cues.sort((a, b) => a.t - b.t);

  const ready = (async () => {
    await document.fonts.ready;
    await Promise.all($$("img").map((img) => img.decode().catch(() => fail(`could not load ${img.src}`))));
    setUpCaptions();
    setUpCards();
    // Typed from the left of where the whole line will end up, not grown out of the middle.
    const tagline = $(".tagline", intro);
    $(".typed", tagline).textContent = F.words.tagline;
    tagline.style.width = `${tagline.getBoundingClientRect().width}px`;
    $(".typed", tagline).textContent = "";
    // The music's shape: an opening, the groove, a quieter bar pair under the reminders' chimes, the
    // groove again, and the close.
    const sections = [
      { from: 0, to: 4, part: "intro" },
      { from: 4, to: 18, part: "groove" },
      { from: 18, to: 22, part: "break" },
      { from: 22, to: 36, part: "groove" },
      { from: 36, to: DURATION, part: "outro" },
    ];
    return { duration: DURATION, bpm: 120, cues, sections };
  })();

  window.film = {
    ready,
    seek: (t) => new Promise((done) => {
      seek(t);
      requestAnimationFrame(() => done());
    }),
  };
})();
