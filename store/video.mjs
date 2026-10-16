#!/usr/bin/env node
// The promo video: the real app's captures in motion, with music and sound, per locale in listing.json —
// 42 seconds or a 17-second cut, wide (16:9) or tall (9:16). No npm dependencies and no ffmpeg — a
// headless Chrome draws each frame (store/film/film.js), synthesises the soundtrack (store/film/sound.js)
// and encodes both with its own WebCodecs encoders (store/film/encoder.js); store/lib/mp4.mjs writes the file.
//
//   ./gradlew :app:testDebugUnitTest --tests "*StoreScreenshots*" -PstoreScreenshots   # captures
//   node store/video.mjs                     # every format and cut: store/build/video/klas-<locale>….mp4
//   node store/video.mjs --format landscape --cut full     # just the Play listing's one
//   node store/video.mjs --4k                # 2160p instead (slower, and twice the size)
//   node store/video.mjs --locale ru-RU --stills 6.2,7.5   # single frames as PNGs, to check a moment
//
// CHROME=<path> overrides where Chrome is looked for. See docs/store-listing.md.

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { evaluate, openChrome } from "./lib/chrome.mjs";
import { mp4 } from "./lib/mp4.mjs";
import { ICON_MARK, LOGO, PHONE_CSS, RES, fontFaces, lockScreen, statusBar, text, url } from "./lib/phone.mjs";
import { decode } from "./lib/png.mjs";
import { joinScroll } from "./lib/scroll.mjs";

const STORE = dirname(fileURLToPath(import.meta.url));
const RAW = join(STORE, "build", "raw");
const WORK = join(STORE, "build", "film");
const OUT = join(STORE, "build", "video");
const FILM = join(STORE, "film");
/** The frame, wide for the Play listing and YouTube, tall for Shorts, Reels and stories. */
const STAGES = { landscape: { width: 1920, height: 1080 }, vertical: { width: 1080, height: 1920 } };
const CUTS = ["full", "short"];
/** Tall: the captions end above this line, where the phone starts (film.js keeps it there). */
const CAPTION_FLOOR = 640;
const DOWNLOAD_ICON = `<svg viewBox="0 0 24 24"><path fill="#4c5bc4" d="M11 3h2v9.6l3.3-3.3 1.4 1.4L12 16.4l-5.7-5.7 1.4-1.4 3.3 3.3V3ZM5 19h14v2H5v-2Z"/></svg>`;
const CALENDAR_ICON = `<svg viewBox="0 0 24 24"><path fill="#4c5bc4" d="M7 2h2v2h6V2h2v2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2V2Zm12 8H5v10h14V10Z"/></svg>`;

/** The captures on the phone's screen during the film; the lock screen is drawn from notifications.json. */
const SHOTS = ["film-day", "film-sheet", "film-taught", "film-swiped", "people", "reports", "film-homework-list",
  "film-homework-done", "film-homework", "settings-calendar", "film-theme", "film-theme-dark", "schedule-day-dark"];
/** Captures the film takes parts of rather than whole screens: rows shot mid-swipe, short of the mark and past it. */
const PIECES = ["film-swipe-unarmed", "film-swipe-armed", "film-homework-swipe-unarmed", "film-homework-swipe-armed"];
/**
 * The screens shown after the lock screen's reminder, whose status bar reads its time rather than the
 * schedule's: the clock must not run backwards. The schedule's day keeps its own, as its «now» line is
 * drawn at it — but not after dark, which follows the 3:30 of Settings straight on.
 */
const AFTER_REMINDER = ["film-homework-list", "film-homework-done", "film-homework", "settings-calendar", "film-theme", "film-theme-dark", "schedule-day-dark"];
/** Which side of the frame each caption is on, when wide: the phone is on the other. */
const CAPTION_SIDES = {
  "schedule-day": "left", "lesson-sheet": "left", people: "right", reports: "left", reminders: "right",
  homework: "left", "settings-calendar": "right top", "schedule-day-dark": "left",
};

const options = parseArgs(process.argv.slice(2));
const listing = JSON.parse(readFileSync(join(STORE, "listing.json"), "utf8"));
const locales = options.locale ? [options.locale] : Object.keys(listing.locales);
let browser;
browser = await openChrome(join(STORE, "build", "chrome-profile")).catch((e) => fail(e.message));
const { send } = browser;
mkdirSync(WORK, { recursive: true });
mkdirSync(OUT, { recursive: true });

try {
  for (const locale of locales) {
    for (const layout of options.formats) {
      for (const cut of options.cuts) await film(locale, layout, cut);
    }
  }
} catch (e) {
  fail(e.stack ?? e.message);
}
browser.close();

async function film(locale, layout, cut) {
  const words = listing.locales[locale] ?? fail(`No locale ${locale} in listing.json`);
  const raw = join(RAW, locale);
  for (const file of ["shots.json", "notifications.json", "film.json"]) {
    if (!existsSync(join(raw, file))) {
      fail(`No ${file} for ${locale} in ${raw}. Take the captures first:\n` +
        `  ./gradlew :app:testDebugUnitTest --tests "*StoreScreenshots*" -PstoreScreenshots`);
    }
  }
  const shots = JSON.parse(readFileSync(join(raw, "shots.json"), "utf8"));
  const tray = JSON.parse(readFileSync(join(raw, "notifications.json"), "utf8"));
  const typed = JSON.parse(readFileSync(join(raw, "film.json"), "utf8"));
  const name = `klas-${locale}${layout === "vertical" ? "-vertical" : ""}${cut === "short" ? "-short" : ""}` +
    `${options.scale > 1 ? "-4k" : ""}`;
  const stage = STAGES[layout];

  const source = join(WORK, `${name}.html`);
  writeFileSync(source, page({ locale, layout, cut, stage, words, raw, shots, tray, typed }));
  const scale = options.scale;
  await send("Emulation.setDeviceMetricsOverride",
    { width: stage.width, height: stage.height, deviceScaleFactor: scale, mobile: false });
  await send("Page.navigate", { url: url(source) });
  await evaluate(send, `new Promise((ready, broken) => {
    const loaded = () => location.href === ${JSON.stringify(url(source))} && document.readyState === "complete";
    const wait = () => {
      if (window.pageErrors?.length) return broken(new Error(window.pageErrors.join("; ")));
      loaded() && window.film ? ready() : setTimeout(wait, 20);
    };
    wait();
  })`);
  const info = await evaluate(send, "film.ready");
  const tooWide = await evaluate(send, `[...document.querySelectorAll(".caption .line")]
    .filter((line) => line.scrollWidth > line.closest(".caption").clientWidth).map((line) => line.textContent)`);
  if (tooWide.length) fail(`${locale}: these caption lines do not fit even at the smallest size: ${tooWide.join(" / ")}`);
  if (layout === "vertical") {
    const tooTall = await evaluate(send, `[...document.querySelectorAll(".caption")].filter((c) => {
      c.classList.remove("hidden");
      const low = c.getBoundingClientRect().bottom;
      c.classList.add("hidden");
      return low > ${CAPTION_FLOOR - 16};
    }).map((c) => c.dataset.shot)`);
    if (tooTall.length) fail(`${locale}: these captions run into the phone in the tall frame: ${tooTall.join(", ")}`);
  }

  const fps = options.fps;
  /** The frame at [t], blended from as many exposures as its motion needs, as base64 JPEGs. */
  const exposures = async (t) => {
    const jpegs = [];
    for (const at of await evaluate(send, `film.exposures(${t}, ${fps}, ${scale})`)) {
      await evaluate(send, `film.seek(${at})`);
      // JPEG rather than PNG: a third of the time to make and to read back, and nothing lost that the
      // H.264 encoder would keep — both store colour at a quarter of the resolution of brightness.
      const { data } = await send("Page.captureScreenshot", { format: "jpeg", quality: 95, fromSurface: true, optimizeForSpeed: true });
      jpegs.push(data);
    }
    return jpegs;
  };

  if (options.stills) {
    for (const t of options.stills) {
      const png = await evaluate(send, `encoder.still(${JSON.stringify(await exposures(t))})`);
      const out = join(OUT, "stills", `${name}-${t.toFixed(2)}.png`);
      mkdirSync(dirname(out), { recursive: true });
      writeFileSync(out, Buffer.from(png, "base64"));
      console.log(out);
    }
    return;
  }

  const frames = Math.round(info.duration * fps);
  const width = stage.width * scale, height = stage.height * scale;
  // H.264 High profile at the level the size and rate need; bitrates as YouTube asks of an upload, a
  // little over, since it re-encodes everything it is given.
  const codec = scale > 1 ? (fps > 30 ? "avc1.640034" : "avc1.640033") : fps > 30 ? "avc1.64002a" : "avc1.640028";
  const bitrate = (scale > 1 ? 50e6 : 16e6) * (fps > 30 ? 1 : 0.7);
  await evaluate(send, `encoder.start(${JSON.stringify({ width, height, rate: fps, bitrate, codec })})`);
  const samples = [];
  const started = Date.now();
  let blended = 0;
  for (let i = 0; i < frames; i++) {
    const jpegs = await exposures(i / fps);
    if (jpegs.length > 1) blended++;
    samples.push(...await evaluate(send, `encoder.frame(${JSON.stringify(jpegs)}, ${i})`));
    if (i % (fps * 2) === 0 || i === frames - 1) {
      const done = (i + 1) / frames, spent = (Date.now() - started) / 1000;
      process.stdout.write(`\r${name}: frame ${i + 1}/${frames}, ${Math.round(spent)} s, ` +
        `about ${Math.round(spent / done - spent)} s to go   `);
    }
  }
  const end = await evaluate(send, "encoder.finish()");
  samples.push(...end.chunks);
  process.stdout.write("\n");
  // One sample per frame, in the order shown: an encoder that reordered frames (B-frames) would need an
  // offset table this muxer does not write.
  if (samples.length !== frames) fail(`${name}: ${frames} frames went in, ${samples.length} came out`);
  samples.forEach((s, i) => {
    if (Math.abs(s.timestamp - (i * 1e6) / fps) > 1) fail(`${name}: the encoder reordered frames at ${i}`);
  });

  const sound = await evaluate(send, `soundtrack(${JSON.stringify(info)}).then((buffer) => encoder.audio(buffer, 192000))`);
  const file = mp4({
    video: {
      width, height, fps, avcC: Buffer.from(end.avcC, "base64"), color: end.color,
      samples: samples.map((s) => ({ key: s.key, data: Buffer.from(s.data, "base64") })),
    },
    audio: {
      sampleRate: sound.sampleRate, channels: sound.channels, config: Buffer.from(sound.config, "base64"),
      samples: sound.chunks.map((c) => ({ data: Buffer.from(c, "base64") })),
    },
  });
  const out = join(OUT, `${name}.mp4`);
  writeFileSync(out, file);
  await check(out, info.duration, width, height);
  console.log(`${name}: ${(file.length / 1e6).toFixed(1)} MB, ${width}×${height} at ${fps} fps, ` +
    `${info.duration.toFixed(1)} s, ${blended} frames blurred → ${out}`);
}

/** Plays the file back in Chrome: it must open, run the whole length at the right size, and have sound. */
async function check(file, duration, width, height) {
  const probe = join(WORK, "probe.html");
  writeFileSync(probe, `<!doctype html><video src="${url(file)}" preload="auto" muted></video>`);
  await send("Page.navigate", { url: url(probe) });
  const seen = await evaluate(send, `new Promise((done) => {
    const wait = () => {
      const video = document.querySelector("video");
      if (!video) return setTimeout(wait, 20);
      if (video.error) return done({ error: video.error.message || String(video.error.code) });
      if (video.readyState < 1) return setTimeout(wait, 20);
      fetch(video.src).then((r) => r.arrayBuffer())
        .then((bytes) => new OfflineAudioContext(2, 48000, 48000).decodeAudioData(bytes))
        .then((audio) => {
          let peak = 0;
          for (const x of audio.getChannelData(0)) peak = Math.max(peak, Math.abs(x));
          done({ duration: video.duration, width: video.videoWidth, height: video.videoHeight,
            audio: audio.duration, peak });
        }, (e) => done({ error: "no sound: " + e.message }));
    };
    wait();
  })`);
  if (seen.error) fail(`${file} does not play: ${seen.error}`);
  if (Math.abs(seen.duration - duration) > 0.1 || seen.width !== width || seen.height !== height) {
    fail(`${file} plays as ${seen.width}×${seen.height}, ${seen.duration} s; expected ${width}×${height}, ${duration} s`);
  }
  if (Math.abs(seen.audio - duration) > 0.2 || seen.peak < 0.3) {
    fail(`${file}'s sound runs ${seen.audio} s and peaks at ${seen.peak}; expected ${duration} s, near full scale`);
  }
}

// --- The page --------------------------------------------------------------------------------------

function page({ locale, layout, cut, stage, words, raw, shots, tray, typed }) {
  const strings = appStrings(locale);
  const caption = (entry) => {
    const still = entry[locale] ?? fail(`${locale}: no caption for «${entry.shot}» in listing.json`);
    // The video's own wording where it shows more than the still: the lesson sheet's scene swipes too.
    const copy = still.film ?? still;
    const title = copy.title.split("\n").map((line) => `<span class="line">${text(line)}</span>`).join("");
    return `
    <div class="caption ${CAPTION_SIDES[entry.shot] ?? "left"}" data-shot="${entry.shot}">
      <div class="bar"></div><h1>${title}</h1><p>${text(copy.subtitle)}</p>
    </div>`;
  };
  const used = listing.screenshots.filter((entry) => entry.shot in CAPTION_SIDES);
  const shot = (name) => shots[name] ?? fail(`${locale}: no capture named «${name}»; take the captures again`);
  const layer = (name) => {
    const s = shot(name);
    return `
        <div class="layer" data-shot="${name}">
          ${statusBar(s.top, s.dark ? "#e4e1e9" : "#1b1b21", AFTER_REMINDER.includes(name) ? tray.clock : words.clock)}
          <img class="app" src="${url(join(raw, `${name}.png`))}">
        </div>`;
  };
  const agenda = tray.notifications.find((n) => n.lines.length) ?? fail(`${locale}: no agenda in notifications.json`);
  const events = agenda.lines.map((line) => {
    const [time, ...rest] = line.split(" · ");
    const who = rest.pop();
    return `<div class="event"><b>${text(who)}</b>${text([time, ...rest].join(" · "))}</div>`;
  }).join("");
  const lockup = `
      <div class="lockup">
        <div class="tile"><img src="${url(LOGO)}" style="width: ${Math.round(210 * ICON_MARK)}px"></div>
        <div class="wordmark">${[..."Klas"].map((c) => `<span>${c}</span>`).join("")}</div>
      </div>`;
  // The edge of the phone: layers stacked behind its face, down to the back, which casts the shadow.
  const sides = Array.from({ length: 7 }, (_, i) =>
    `<div class="side${i === 6 ? " back" : ""}" style="--z: ${((i + 1) * 0.012).toFixed(3)}"></div>`).join("");

  const sheet = decode(readFileSync(join(raw, "film-sheet.png")));
  const data = {
    layout, cut, stage,
    capture: { width: sheet.width, height: sheet.height },
    shots: Object.fromEntries([...SHOTS, ...PIECES].map((name) => [name, { src: url(join(raw, `${name}.png`)), marks: shot(name).marks }])),
    words: { tagline: words.tagline },
    film: typed,
    sheetTop: sheetTop(sheet, shot("film-sheet").bottom),
    ...reportParts(raw, shot("reports"), shot("film-reports-end")),
    closeUp: closeUp(raw, shot("film-homework-list")),
  };
  // Beside the settings when wide; over the phone's lower half, under the rows it calls out, when tall.
  const calendarAt = layout === "vertical" ? "left: 280px; top: 1240px;" : "left: 920px; top: 440px;";
  const dots = Array.from({ length: layout === "vertical" ? 18 : 16 }, () => `<div class="dot"></div>`).join("");
  return `<!doctype html><html lang="${locale}" class="${layout}"><head><meta charset="utf-8">
  <script>window.pageErrors = []; addEventListener("error", (e) => pageErrors.push(e.message));</script>
  <style>${fontFaces()}${PHONE_CSS}</style>
  <link rel="stylesheet" href="${url(join(FILM, "film.css"))}">
</head><body>
  <div id="stage" style="width: ${stage.width}px; height: ${stage.height}px">
    <div class="bg"></div><div class="bg night"></div>
    <div class="glow one"></div><div class="glow two"></div><div class="glow three"></div>
    ${dots}
    ${used.map(caption).join("")}
    <div class="phone" id="phone">
      ${sides}
      <div class="key power"></div><div class="key volume"></div>
      <div class="screen"><div class="stack">
        ${SHOTS.map(layer).join("")}
        <div class="layer" data-shot="lockscreen">${lockScreen(tray)}</div>
      </div></div>
      <div class="glass"></div>
      <div id="overlay"></div>
    </div>
    <div id="typing" class="hidden">
      <h2>${text(strings.homework_add)}</h2>
      <div class="field"><span class="label">${text(strings.homework_assignee_label)}</span>${text(typed.student)}</div>
      <div class="field task"><span class="label">${text(strings.homework_text_label)}</span>
        <div class="text"><span class="typed"></span><span class="caret"></span></div></div>
      <div class="buttons"><span>${text(strings.action_cancel)}</span><span class="save">${text(strings.action_save)}</span></div>
    </div>
    <div id="calendar" class="hidden" style="${calendarAt}">
      <div class="head">${CALENDAR_ICON}<span class="date">${text(tray.date)}</span></div>
      <div class="events">${events}</div>
    </div>
    <div class="brand" id="intro">${lockup}
      <div class="tagline"><span class="typed"></span><span class="caret"></span></div>
    </div>
    <div class="brand hidden" id="outro">${lockup}
      <div class="tagline">${text(words.tagline)}</div>
      <div class="cta">${DOWNLOAD_ICON}<span>${text(words.cta ?? fail(`${locale}: no «cta» in listing.json`))}</span></div>
    </div>
  </div>
  <script>window.FILM = ${JSON.stringify(data)};</script>
  <script src="${url(join(FILM, "parts.js"))}"></script>
  <script src="${url(join(FILM, "film.js"))}"></script>
  <script src="${url(join(FILM, "sound.js"))}"></script>
  <script src="${url(join(FILM, "encoder.js"))}"></script>
</body></html>`;
}

/**
 * What the video needs to scroll the report and count its total up: the two captures of the list joined
 * (how far it moved, found to the pixel, and where to join them), and the total's type with its card's
 * colour behind it, to cover the figure the capture shows.
 */
function reportParts(raw, opened, scrolled) {
  const top = decode(readFileSync(join(raw, "reports.png")));
  const bottom = decode(readFileSync(join(raw, "film-reports-end.png")));
  const list = opened.marks.list;
  const guess = opened.marks.anchor[1] - scrolled.marks.anchor[1];
  const { d, seam } = joinScroll(top, bottom, list, guess);
  const total = opened.texts?.total ?? fail("No «total» text recorded on «reports»; take the captures again");
  const x = Math.round(total.left) - 12, y = Math.round((total.top + total.bottom) / 2);
  const at = (y * top.width + x) * top.channels;
  const background = `#${[0, 1, 2].map((k) => top.pixels[at + k].toString(16).padStart(2, "0")).join("")}`;
  return {
    scroll: { list, d, seam, src: url(join(raw, "film-reports-end.png")) },
    count: { ...total, background },
  };
}

/**
 * What the video needs to close the homework list up over the row swiped off it: how far the rows below
 * rose, found to the pixel between the lists before and after; where the rows end, just under the last
 * card — the message the swipe brings up comes in below it, over the screen's foot; and the screen's
 * background, for the gap they rise into.
 */
function closeUp(raw, before) {
  const list = decode(readFileSync(join(raw, "film-homework-list.png")));
  const done = decode(readFileSync(join(raw, "film-homework-done.png")));
  const [, top, , bottom] = before.marks.swipe;
  const px = (x, y) => [0, 1, 2].map((k) => list.pixels[(y * list.width + x) * list.channels + k]);
  const ground = px(8, Math.round((top + bottom) / 2));
  const plain = (y) => {
    for (let x = 0; x < list.width; x += 2) {
      if (px(x, y).some((v, k) => Math.abs(v - ground[k]) > 6)) return false;
    }
    return true;
  };
  let last = before.marks.add[1] - 1;
  while (last > bottom && plain(last)) last--;
  const foot = last + 8;
  const { d: pitch } = joinScroll(list, done, [0, top, list.width, foot], bottom - top + 48);
  const background = `#${ground.map((v) => v.toString(16).padStart(2, "0")).join("")}`;
  return { pitch, bottom: foot, background };
}

/**
 * Where the lesson sheet's top edge is on its capture: the first run of rows, straight down the middle,
 * in the sheet's own colour — the scrim above it darkens everything it covers.
 */
function sheetTop({ width, height, channels, pixels }, color) {
  const want = [1, 3, 5].map((i) => parseInt(color.slice(i, i + 2), 16));
  const x = Math.floor(width / 2);
  let run = 0;
  for (let y = 0; y < height; y++) {
    const at = (y * width + x) * channels;
    const off = want.reduce((sum, v, k) => sum + Math.abs(pixels[at + k] - v), 0);
    run = off < 12 ? run + 1 : 0;
    if (run === 24) return y - run + 1;
  }
  return fail("Could not find the lesson sheet's top edge on film-sheet.png");
}

/** The app's own words for what the film draws of it (the homework dialog), in [locale]'s language. */
function appStrings(locale) {
  const read = (folder) => {
    const file = join(RES, folder, "strings.xml");
    if (!existsSync(file)) return {};
    const xml = readFileSync(file, "utf8");
    return Object.fromEntries([...xml.matchAll(/<string name="([^"]+)">([\s\S]*?)<\/string>/g)]
      .map(([, name, value]) => [name, value.replace(/\\'/g, "'").replace(/\\"/g, '"').replace(/&amp;/g, "&")]));
  };
  const language = locale.split("-")[0];
  return { ...read("values"), ...(language === "en" ? {} : read(`values-${language}`)) };
}

function parseArgs(args) {
  const options = { fps: 60, scale: 1, locale: null, stills: null, formats: Object.keys(STAGES), cuts: CUTS };
  const list = (value, known, what) => {
    const picked = value.split(",");
    const unknown = picked.filter((v) => !known.includes(v));
    if (unknown.length) fail(`Unknown ${what} ${unknown.join(", ")}; known: ${known.join(", ")}`);
    return picked;
  };
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === "--4k") options.scale = 2;
    else if (arg === "--fps") options.fps = Number(args[++i]);
    else if (arg === "--locale") options.locale = args[++i];
    else if (arg === "--stills") options.stills = args[++i].split(",").map(Number);
    else if (arg === "--format") options.formats = list(args[++i], Object.keys(STAGES), "format");
    else if (arg === "--cut") options.cuts = list(args[++i], CUTS, "cut");
    else fail(`Unknown option ${arg}`);
  }
  if (![24, 25, 30, 50, 60].includes(options.fps)) fail("--fps takes 24, 25, 30, 50 or 60");
  return options;
}

function fail(message) {
  console.error(`\n${message}`);
  browser?.close();
  process.exit(1);
}
