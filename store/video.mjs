#!/usr/bin/env node
// The promo video: 40 seconds of the real app's captures in motion, with music and sound, one MP4 per
// locale in listing.json. No npm dependencies and no ffmpeg — a headless Chrome draws each frame
// (store/film/film.js), synthesises the soundtrack (store/film/sound.js) and encodes both with its own
// WebCodecs encoders (store/film/encoder.js); store/lib/mp4.mjs writes the file.
//
//   ./gradlew :app:testDebugUnitTest --tests "*StoreScreenshots*" -PstoreScreenshots   # captures
//   node store/video.mjs                     # store/build/video/klas-<locale>.mp4, 1080p at 60 fps
//   node store/video.mjs --4k                # 2160p instead (slower, and twice the size)
//   node store/video.mjs --locale ru-RU --stills 6.2,7.5   # single frames as PNGs, to check a moment
//
// CHROME=<path> overrides where Chrome is looked for. See docs/store-listing.md.

import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { evaluate, openChrome } from "./lib/chrome.mjs";
import { mp4 } from "./lib/mp4.mjs";
import { ICON_MARK, LOGO, PHONE_CSS, RES, fontFaces, lockScreen, statusBar, text, url } from "./lib/phone.mjs";
import { decode } from "./lib/png.mjs";

const STORE = dirname(fileURLToPath(import.meta.url));
const RAW = join(STORE, "build", "raw");
const WORK = join(STORE, "build", "film");
const OUT = join(STORE, "build", "video");
const FILM = join(STORE, "film");
const STAGE = { width: 1920, height: 1080 };
const CALENDAR_ICON = `<svg viewBox="0 0 24 24"><path fill="#4c5bc4" d="M7 2h2v2h6V2h2v2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2V2Zm12 8H5v10h14V10Z"/></svg>`;
const DOWNLOAD_ICON = `<svg viewBox="0 0 24 24"><path fill="#4c5bc4" d="M11 3h2v9.6l3.3-3.3 1.4 1.4L12 16.4l-5.7-5.7 1.4-1.4 3.3 3.3V3ZM5 19h14v2H5v-2Z"/></svg>`;

/** The captures on the phone's screen during the film; the lock screen is drawn from notifications.json. */
const SHOTS = ["schedule-day", "film-sheet", "film-taught", "people", "reports", "homework", "film-homework",
  "settings-calendar", "schedule-day-dark"];
/** Which side of the frame each caption is on: the phone is on the other. */
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
  for (const locale of locales) await film(locale);
} catch (e) {
  fail(e.stack ?? e.message);
}
browser.close();

async function film(locale) {
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
  const sheet = decode(readFileSync(join(raw, "film-sheet.png")));

  const source = join(WORK, `${locale}.html`);
  writeFileSync(source, page({ locale, words, raw, shots, tray, typed, sheet }));
  const scale = options.scale;
  await send("Emulation.setDeviceMetricsOverride",
    { width: STAGE.width, height: STAGE.height, deviceScaleFactor: scale, mobile: false });
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

  if (options.stills) {
    for (const t of options.stills) {
      await evaluate(send, `film.seek(${t})`);
      const { data } = await send("Page.captureScreenshot", { format: "png", fromSurface: true });
      const out = join(OUT, "stills", `${locale}-${t.toFixed(2)}.png`);
      mkdirSync(dirname(out), { recursive: true });
      writeFileSync(out, Buffer.from(data, "base64"));
      console.log(out);
    }
    return;
  }

  const fps = options.fps, frames = Math.round(info.duration * fps);
  const width = STAGE.width * scale, height = STAGE.height * scale;
  // H.264 High profile at the level the size and rate need; bitrates as YouTube asks of an upload, a
  // little over, since it re-encodes everything it is given.
  const codec = scale > 1 ? (fps > 30 ? "avc1.640034" : "avc1.640033") : fps > 30 ? "avc1.64002a" : "avc1.640028";
  const bitrate = (scale > 1 ? 50e6 : 16e6) * (fps > 30 ? 1 : 0.7);
  await evaluate(send, `encoder.start(${JSON.stringify({ width, height, rate: fps, bitrate, codec })})`);
  const samples = [];
  const started = Date.now();
  for (let i = 0; i < frames; i++) {
    await evaluate(send, `film.seek(${i / fps})`);
    // JPEG rather than PNG: a third of the time to make and to read back, and nothing lost that the
    // H.264 encoder would keep — both store colour at a quarter of the resolution of brightness.
    const { data } = await send("Page.captureScreenshot", { format: "jpeg", quality: 95, fromSurface: true, optimizeForSpeed: true });
    samples.push(...await evaluate(send, `encoder.frame(${JSON.stringify(data)}, ${i})`));
    if (i % (fps * 2) === 0 || i === frames - 1) {
      const done = (i + 1) / frames, spent = (Date.now() - started) / 1000;
      process.stdout.write(`\r${locale}: frame ${i + 1}/${frames}, ${Math.round(spent)} s, ` +
        `about ${Math.round(spent / done - spent)} s to go   `);
    }
  }
  const end = await evaluate(send, "encoder.finish()");
  samples.push(...end.chunks);
  process.stdout.write("\n");
  // One sample per frame, in the order shown: an encoder that reordered frames (B-frames) would need an
  // offset table this muxer does not write.
  if (samples.length !== frames) fail(`${locale}: ${frames} frames went in, ${samples.length} came out`);
  samples.forEach((s, i) => {
    if (Math.abs(s.timestamp - (i * 1e6) / fps) > 1) fail(`${locale}: the encoder reordered frames at ${i}`);
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
  const out = join(OUT, `klas-${locale}${scale > 1 ? "-4k" : ""}.mp4`);
  writeFileSync(out, file);
  await check(out, info.duration, width, height);
  console.log(`${locale}: ${(file.length / 1e6).toFixed(1)} MB, ${width}×${height} at ${fps} fps → ${out}`);
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

function page({ locale, words, raw, shots, tray, typed, sheet }) {
  const strings = appStrings(locale);
  const caption = (entry) => {
    const copy = entry[locale] ?? fail(`${locale}: no caption for «${entry.shot}» in listing.json`);
    const title = copy.title.split("\n").map((line) => `<span class="line">${text(line)}</span>`).join("");
    return `
    <div class="caption ${CAPTION_SIDES[entry.shot] ?? "left"}" data-shot="${entry.shot}">
      <div class="bar"></div><h1>${title}</h1><p>${text(copy.subtitle)}</p>
    </div>`;
  };
  const used = listing.screenshots.filter((entry) => entry.shot in CAPTION_SIDES);
  const layer = (shot) => {
    const s = shots[shot] ?? fail(`${locale}: no capture named «${shot}»`);
    return `
        <div class="layer" data-shot="${shot}">
          ${statusBar(s.top, s.dark ? "#e4e1e9" : "#1b1b21", words.clock)}
          <img class="app" src="${url(join(raw, `${shot}.png`))}">
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

  const data = {
    capture: { width: sheet.width, height: sheet.height },
    shots: Object.fromEntries(SHOTS.map((shot) => [shot, { src: url(join(raw, `${shot}.png`)), marks: shots[shot].marks }])),
    words: { tagline: words.tagline },
    film: typed,
    sheetTop: sheetTop(sheet, shots["film-sheet"].bottom),
  };
  const dots = Array.from({ length: 16 }, () => `<div class="dot"></div>`).join("");
  return `<!doctype html><html lang="${locale}"><head><meta charset="utf-8">
  <script>window.pageErrors = []; addEventListener("error", (e) => pageErrors.push(e.message));</script>
  <style>${fontFaces()}${PHONE_CSS}</style>
  <link rel="stylesheet" href="${url(join(FILM, "film.css"))}">
</head><body>
  <div id="stage">
    <div class="bg"></div><div class="bg night"></div>
    <div class="glow one"></div><div class="glow two"></div><div class="glow three"></div>
    ${dots}
    ${used.map(caption).join("")}
    <div class="phone" id="phone">
      <div class="screen"><div class="stack">
        ${SHOTS.map(layer).join("")}
        <div class="layer" data-shot="lockscreen">${lockScreen(tray)}</div>
      </div></div>
      <div id="overlay"></div>
    </div>
    <div id="typing" class="hidden">
      <h2>${text(strings.homework_add)}</h2>
      <div class="field"><span class="label">${text(strings.homework_assignee_label)}</span>${text(typed.student)}</div>
      <div class="field task"><span class="label">${text(strings.homework_text_label)}</span>
        <div class="text"><span class="typed"></span><span class="caret"></span></div></div>
      <div class="buttons"><span>${text(strings.action_cancel)}</span><span class="save">${text(strings.action_save)}</span></div>
    </div>
    <div id="calendar" class="hidden" style="left: 920px; top: 440px;">
      <div class="head">${CALENDAR_ICON}<span class="date">${text(tray.date)}</span></div>
      <div class="events">${events}</div>
    </div>
    <div class="brand" id="intro">${lockup}
      <div class="tagline"><span class="typed"></span><span class="caret"></span></div>
    </div>
    <div class="brand hidden" id="outro">${lockup}
      <div class="tagline">${text(words.tagline)}</div>
      <div class="cta">${DOWNLOAD_ICON}<span>${text(words.cta)}</span></div>
    </div>
  </div>
  <script>window.FILM = ${JSON.stringify(data)};</script>
  <script src="${url(join(FILM, "film.js"))}"></script>
  <script src="${url(join(FILM, "sound.js"))}"></script>
  <script src="${url(join(FILM, "encoder.js"))}"></script>
</body></html>`;
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
  const options = { fps: 60, scale: 1, locale: null, stills: null };
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === "--4k") options.scale = 2;
    else if (arg === "--fps") options.fps = Number(args[++i]);
    else if (arg === "--locale") options.locale = args[++i];
    else if (arg === "--stills") options.stills = args[++i].split(",").map(Number);
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
