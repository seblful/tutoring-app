#!/usr/bin/env node
// Turns the raw captures from StoreScreenshots into the Google Play listing's graphics: framed,
// captioned phone screenshots, the feature graphic and the 512 px icon, for every locale in
// listing.json. No npm dependencies — each image is an HTML page drawn by a headless Chrome, driven over
// the DevTools protocol so a page is captured only once its fonts and images have painted.
//
//   ./gradlew :app:testDebugUnitTest --tests "*StoreScreenshots*" -PstoreScreenshots   # captures
//   node store/render.mjs                                                              # graphics
//
// Writes store/listing/<locale>/{phoneScreenshots/NN-<shot>.png, featureGraphic.png, icon.png} and copies
// the listing's words from store/text/<locale>/ beside them, checked against Play's length limits.
// CHROME=<path> overrides where Chrome is looked for. See docs/store-listing.md.

import { spawn } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { deflateSync, inflateSync } from "node:zlib";

const STORE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(STORE, "..");
const RAW = join(STORE, "build", "raw");
const WORK = join(STORE, "build", "html");
const OUT = join(STORE, "listing");
const TEXT = join(STORE, "text");

/** Play Console's limits, in characters, for each piece of the listing's text. */
const TEXT_LIMITS = { "title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000 };
const RES = join(ROOT, "app", "src", "main", "res");

// Each page is laid out at `width` × `height` and written at `scale` times that, drawn first at
// `supersample` times more and averaged down (see [encode]) so small text and edges come out smooth.
// Screenshots: 9:16 at 2160 × 3840, the largest Play takes (3840 px on the long side), so they stay sharp on a
// tablet or desktop store page. The feature graphic and icon must be exactly the size Play names, so they
// get their detail from supersampling alone.
const SHOT = { width: 1440, height: 2560, scale: 1.5, supersample: 2 };
const FEATURE = { width: 1024, height: 500, scale: 1, supersample: 4 };
const ICON = { width: 512, height: 512, scale: 1, supersample: 4 };
/** Where the phone starts on a screenshot, and the least room left between it and the caption above. */
const PHONE_TOP = 735;
const CAPTION_GAP = 56;
/** Where the feature graphic's phone starts; the brand's words must stop short of it. */
const FEATURE_PHONE_LEFT = 588;

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

/**
 * How much of the icon the K fills. The launcher icon is adaptive: its mark is inset 20.37% into a 108 dp
 * canvas (`ic_launcher_foreground`) of which a phone shows only the middle 72 dp, so on a home screen the
 * mark is 1.5× what the inset alone suggests. Play masks the whole 512 px square instead, so the icon is
 * drawn as the phone shows it.
 */
const ICON_MARK = (1 - 2 * 0.2037) * (108 / 72);

const listing = JSON.parse(readFileSync(join(STORE, "listing.json"), "utf8"));
let browser;
browser = await openChrome(findChrome());

rmSync(WORK, { recursive: true, force: true });
mkdirSync(WORK, { recursive: true });

for (const [locale, words] of Object.entries(listing.locales)) {
  const raw = join(RAW, locale);
  const manifest = join(raw, "shots.json");
  if (!existsSync(manifest)) {
    fail(`No captures for ${locale} in ${raw}. Take them first:\n` +
      `  ./gradlew :app:testDebugUnitTest --tests "*StoreScreenshots*" -PstoreScreenshots`);
  }
  const shots = JSON.parse(readFileSync(manifest, "utf8"));
  const out = join(OUT, locale);
  rmSync(out, { recursive: true, force: true });
  mkdirSync(join(out, "phoneScreenshots"), { recursive: true });

  for (const [i, entry] of listing.screenshots.entries()) {
    const shot = entry.kind === "lockscreen" ? null : shots[entry.shot] ?? fail(`${locale}: no capture named "${entry.shot}"`);
    const copy = entry[locale] ?? fail(`${locale}: no caption for "${entry.shot}"`);
    const name = `${String(i + 1).padStart(2, "0")}-${entry.shot}`;
    const dark = entry.theme === "dark";
    const screen = entry.kind === "lockscreen"
      ? lockScreen(JSON.parse(readFileSync(join(raw, "notifications.json"), "utf8")))
      : appScreen(raw, entry.shot, shot ?? {}, words.clock);
    await draw(screenshotPage({ screen, copy, dark }), SHOT, join(out, "phoneScreenshots", `${name}.png`), `${locale}-${name}`);
  }

  const feature = listing.featureGraphic.shot;
  await draw(featurePage({ raw, shot: feature, colors: shots[feature], tagline: words.tagline, clock: words.clock }),
    FEATURE, join(out, "featureGraphic.png"), `${locale}-feature`);
  await draw(iconPage(), ICON, join(out, "icon.png"), `${locale}-icon`, { opaque: false });
  copyText(locale, out);
  console.log(`${locale}: ${listing.screenshots.length} screenshots, feature graphic, icon, text → ${out}`);
}
browser.close();

/** Copies the listing's words for [locale], refusing any piece Play would reject for length. */
function copyText(locale, out) {
  for (const [file, limit] of Object.entries(TEXT_LIMITS)) {
    const source = join(TEXT, locale, file);
    if (!existsSync(source)) fail(`${locale}: missing ${source}`);
    const words = readFileSync(source, "utf8").trim();
    const length = [...words].length;
    if (length > limit) fail(`${locale}/${file} is ${length} characters; Play allows ${limit}`);
    writeFileSync(join(out, file), `${words}
`);
  }
}

// --- Pages -------------------------------------------------------------------------------------

function screenshotPage({ screen, copy, dark }) {
  return page(SHOT, `
    <div class="backdrop ${dark ? "dark" : ""}">
      <div class="glow one"></div><div class="glow two"></div>
      <header>
        <h1>${lines(copy.title)}</h1>
        <p>${text(copy.subtitle)}</p>
      </header>
      <div class="phone" style="--phone: 1010px; left: 215px; top: ${PHONE_TOP}px;">${screen}</div>
    </div>
    ${fitCaption()}`);
}

/**
 * Shrinks the caption until it is at most two lines and clears the phone — a translation is never measured
 * by whoever writes it, and "Mark a lesson taught" alone wrapped to three lines and ran into the screen.
 * What still does not fit at the floor marks the page, and [draw] refuses it.
 */
function fitCaption() {
  return `<script>
    document.fonts.ready.then(() => {
      const header = document.querySelector("header"), title = header.querySelector("h1"), sub = header.querySelector("p");
      const limit = ${PHONE_TOP - CAPTION_GAP};
      const lines = (el) => Math.round(el.getBoundingClientRect().height / parseFloat(getComputedStyle(el).lineHeight));
      const fits = () => lines(title) <= 2 && lines(sub) <= 2 && header.getBoundingClientRect().bottom <= limit;
      let size = parseFloat(getComputedStyle(title).fontSize);
      while (!fits() && size > 84) title.style.fontSize = (size -= 2) + "px";
      let subSize = parseFloat(getComputedStyle(sub).fontSize);
      while (!fits() && subSize > 40) sub.style.fontSize = (subSize -= 1) + "px";
      document.body.dataset.fits = fits();
    });
  </script>`;
}

function featurePage({ raw, shot, colors, tagline, clock }) {
  return page(FEATURE, `
    <div class="backdrop feature">
      <div class="glow one"></div><div class="glow two"></div>
      <div class="brand">
        <div class="mark"><img src="${url(join(RES, "drawable-nodpi", "ic_logo.png"))}"></div>
        <div>
          <div class="wordmark">Klas</div>
          <div class="tagline">${text(tagline)}</div>
        </div>
      </div>
      <div class="phone" style="--phone: 400px; left: ${FEATURE_PHONE_LEFT}px; top: 62px;">
        ${appScreen(raw, shot, colors, clock)}
      </div>
    </div>
    <script>
      document.fonts.ready.then(() => {
        const words = document.querySelector(".brand").getBoundingClientRect();
        document.body.dataset.fits = [...document.querySelectorAll(".brand *")]
          .every((el) => el.getBoundingClientRect().right <= ${FEATURE_PHONE_LEFT - CAPTION_GAP / 2}) && words.bottom <= 500;
      });
    </script>`);
}

function iconPage() {
  // The launcher icon as Play shows it: the adaptive icon's background and its inset mark.
  return page(ICON, `
    <div class="icon"><img src="${url(join(RES, "drawable-nodpi", "ic_logo.png"))}"></div>`);
}

/** The phone: a dark bezel, a status bar in the colour the screenshot starts with, then the app. */
function appScreen(raw, shot, colors, clock) {
  const ink = colors.dark ? "#e4e1e9" : "#1b1b21";
  return `
    <div class="screen">
      ${statusBar(colors.top, ink, clock)}
      <img class="app" src="${url(join(raw, `${shot}.png`))}">
    </div>`;
}

/** The status bar every screen starts with: the clock, the camera's punch hole and the radio icons. */
function statusBar(background, ink, clock) {
  return `
      <div class="status" style="background:${background}; color:${ink}">
        <span class="clock">${text(clock)}</span>
        <span class="camera"></span>
        <span class="icons">${statusIcons(ink)}</span>
      </div>`;
}

/**
 * The lock screen as the reminder lands on it: the phone's clock and date, then what the app posted — the
 * reminder (newest, with its buttons), the morning agenda and the debt summary. Every word on the cards is
 * the app's own, recorded by StoreScreenshots from what `AndroidNotifier` put in each notification.
 */
function lockScreen(tray) {
  const cards = [...tray.notifications].reverse().map((n, i) => `
        <div class="card">
          <div class="card-head">
            <span class="badge"><img src="${url(join(RES, "drawable-nodpi", "ic_logo.png"))}"></span>
            <span>${text(tray.app)}${i === 0 ? ` · ${text(tray.now)}` : ""}</span>
          </div>
          <div class="card-title">${text(n.title)}</div>
          ${n.text ? `<div class="card-text">${lines(n.text)}</div>` : ""}
          ${n.lines.length ? `<div class="card-lines">${n.lines.map((l) => `<div>${text(l)}</div>`).join("")}</div>` : ""}
          ${n.actions.length ? `<div class="card-actions">${n.actions.map((a) => `<span>${text(a)}</span>`).join("")}</div>` : ""}
        </div>`).join("");
  return `
    <div class="screen">
      <div class="lock">
        ${statusBar("transparent", "#ffffff", "")}
        <div class="lock-clock">${text(tray.clock)}</div>
        <div class="lock-date">${text(tray.date)}</div>
        <div class="cards">${cards}</div>
      </div>
    </div>`;
}

function statusIcons(ink) {
  return `
    <svg viewBox="0 0 24 24" width="1.15em" height="1.15em"><path fill="${ink}" d="M12 21 1 9.6C4 6.9 7.8 5.5 12 5.5s8 1.4 11 4.1L12 21Z"/></svg>
    <svg viewBox="0 0 24 24" width="1.15em" height="1.15em"><path fill="${ink}" d="M2 22 22 2v20H2Z"/></svg>
    <svg viewBox="0 0 24 24" width="1.15em" height="1.15em"><rect x="7" y="3" width="10" height="19" rx="2" fill="${ink}"/><rect x="10" y="1.5" width="4" height="2" rx="0.6" fill="${ink}"/></svg>`;
}

function page(size, body) {
  const font = (file, weight) =>
    `@font-face { font-family: Golos; font-weight: ${weight}; src: url("${url(join(RES, "font", file))}"); }`;
  return `<!doctype html><html><head><meta charset="utf-8"><style>
    ${font("golos_text_regular.ttf", 400)} ${font("golos_text_medium.ttf", 500)}
    ${font("golos_text_semibold.ttf", 600)} ${font("golos_text_bold.ttf", 700)}
    * { box-sizing: border-box; margin: 0; }
    html, body { width: ${size.width}px; height: ${size.height}px; overflow: hidden; }
    body { font-family: Golos, sans-serif; -webkit-font-smoothing: antialiased; }

    .backdrop {
      position: relative; width: 100%; height: 100%; overflow: hidden; color: #fff;
      background: linear-gradient(165deg, #6170dc 0%, #4c5bc4 42%, #2f3a9a 100%);
    }
    .backdrop.dark { background: linear-gradient(165deg, #262b52 0%, #161936 50%, #0b0c1a 100%); }
    .glow { position: absolute; border-radius: 50%; filter: blur(120px); opacity: .55; }
    .glow.one { width: 900px; height: 900px; right: -320px; top: -260px; background: #a9edff; opacity: .35; }
    .glow.two { width: 1100px; height: 1100px; left: -420px; bottom: -380px; background: #006879; }
    .dark .glow.one { background: #4c5bc4; opacity: .5; }
    .dark .glow.two { background: #003640; opacity: .8; }

    header { position: absolute; left: 120px; right: 120px; top: 170px; text-align: center; }
    h1 { font-weight: 700; font-size: 124px; line-height: 1.06; letter-spacing: -2.5px; text-wrap: balance; }
    header p { margin: 44px auto 0; max-width: 1080px; font-weight: 500; font-size: 52px; line-height: 1.3;
      color: rgba(255, 255, 255, .84); text-wrap: balance; }

    .phone {
      position: absolute; width: var(--phone); padding: calc(var(--phone) * .022);
      border-radius: calc(var(--phone) * .125); background: #101116;
      box-shadow: 0 0 0 calc(var(--phone) * .004) #3a3b44 inset,
        0 calc(var(--phone) * .06) calc(var(--phone) * .14) rgba(8, 10, 48, .55);
    }
    .screen { border-radius: calc(var(--phone) * .104); overflow: hidden; background: #000;
      font-size: calc(var(--phone) * .038); }
    .status { position: relative; height: calc(var(--phone) * .092); display: flex; align-items: center;
      justify-content: space-between; padding: 0 calc(var(--phone) * .075); font-weight: 600; }
    .camera { position: absolute; left: 50%; top: 50%; width: calc(var(--phone) * .034);
      height: calc(var(--phone) * .034); transform: translate(-50%, -50%); border-radius: 50%; background: #0b0b0f; }
    .icons { display: flex; gap: .3em; align-items: center; }
    .app { display: block; width: 100%; }

    /* The lock screen fills the same 412 × 915 dp screen the app's captures do. */
    .lock { position: relative; height: calc(var(--phone) * (.956 * 915 / 412 + .092)); overflow: hidden; color: #fff;
      background: radial-gradient(120% 70% at 85% 10%, #8b97ff 0%, transparent 55%),
        radial-gradient(110% 60% at 0% 100%, #0d8aa0 0%, transparent 60%),
        linear-gradient(170deg, #3a46b5 0%, #22296e 55%, #121640 100%); }
    .lock-clock { margin-top: calc(var(--phone) * .1); text-align: center; font-weight: 500;
      font-size: calc(var(--phone) * .21); line-height: 1; letter-spacing: calc(var(--phone) * -.004); }
    .lock-date { margin-top: calc(var(--phone) * .03); text-align: center; font-weight: 500;
      font-size: calc(var(--phone) * .042); color: rgba(255, 255, 255, .9); }
    .cards { margin: calc(var(--phone) * .1) calc(var(--phone) * .035) 0; display: flex; flex-direction: column;
      gap: calc(var(--phone) * .018); }
    .card { background: rgba(251, 248, 255, .95); color: #1b1b21; border-radius: calc(var(--phone) * .06);
      padding: calc(var(--phone) * .042) calc(var(--phone) * .05); box-shadow: 0 calc(var(--phone) * .01) calc(var(--phone) * .04) rgba(8, 10, 48, .25); }
    .card-head { display: flex; align-items: center; gap: calc(var(--phone) * .02);
      font-size: calc(var(--phone) * .032); color: #46464f; }
    .badge { width: calc(var(--phone) * .055); height: calc(var(--phone) * .055); border-radius: 50%; background: #dfe0ff;
      display: grid; place-items: center; }
    .badge img { width: 72%; }
    .card-title { margin-top: calc(var(--phone) * .022); font-weight: 600; font-size: calc(var(--phone) * .042); }
    .card-text { margin-top: calc(var(--phone) * .01); font-size: calc(var(--phone) * .038); line-height: 1.35; color: #303038; }
    .card-lines { margin-top: calc(var(--phone) * .012); font-size: calc(var(--phone) * .035); line-height: 1.45; color: #303038; }
    .card-actions { margin-top: calc(var(--phone) * .03); display: flex; gap: calc(var(--phone) * .06);
      font-weight: 600; font-size: calc(var(--phone) * .037); color: #4c5bc4; }

    .feature .brand { position: absolute; left: 56px; top: 0; bottom: 0; width: 492px;
      display: flex; align-items: center; gap: 30px; }
    .feature .mark { flex: none; width: 150px; height: 150px; border-radius: 40px; background: #dfe0ff;
      display: grid; place-items: center; box-shadow: 0 18px 40px rgba(8, 10, 48, .35); }
    .feature .mark img { width: ${Math.round(150 * ICON_MARK)}px; }
    .feature .wordmark { font-weight: 700; font-size: 104px; line-height: 1; letter-spacing: -2px; }
    .feature .tagline { margin-top: 16px; font-weight: 500; font-size: 28px; line-height: 1.28;
      color: rgba(255, 255, 255, .86); text-wrap: balance; }
    .feature .glow { filter: blur(80px); }
    .feature .glow.one { width: 500px; height: 500px; right: -160px; top: -200px; }
    .feature .glow.two { width: 600px; height: 600px; left: -260px; bottom: -380px; }

    .icon { width: 512px; height: 512px; background: #dfe0ff; display: grid; place-items: center; }
    .icon img { width: ${Math.round(ICON.width * ICON_MARK)}px; }
  </style></head><body>${body}</body></html>`;
}

function url(path) {
  return pathToFileURL(path).href;
}

/** Escapes [s] for HTML, and ties a dash to the word before it so no line starts with «—». */
function text(s) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/ —/g, " —");
}

function lines(s) {
  return s.split("\n").map(text).join("<br>");
}

// --- Drawing -----------------------------------------------------------------------------------

async function draw(html, size, out, name, { opaque = true } = {}) {
  const source = join(WORK, `${name}.html`);
  writeFileSync(source, html);
  const { send } = browser;
  const factor = size.scale * size.supersample;
  await send("Emulation.setDeviceMetricsOverride",
    { width: size.width, height: size.height, deviceScaleFactor: factor, mobile: false });
  await send("Page.navigate", { url: url(source) });
  // Captured only once the page has loaded, its fonts and images are decoded and two frames have been
  // drawn: Chrome's own `--screenshot` took some pages before they had finished painting and left the
  // bottom rows black.
  const { result } = await send("Runtime.evaluate", {
    awaitPromise: true,
    returnByValue: true,
    expression: `new Promise((ready) => {
      const settle = () => document.fonts.ready
        .then(() => Promise.all([...document.images].map((image) => image.decode().catch(() => {}))))
        .then(() => requestAnimationFrame(() => requestAnimationFrame(() => ready(document.body.dataset.fits ?? "n/a"))));
      const loaded = () => location.href === ${JSON.stringify(url(source))} && document.readyState === "complete";
      const wait = () => (loaded() ? settle() : setTimeout(wait, 20));
      wait();
    })`,
  });
  if (result.value === "false") fail(`${name}: the words run into the phone; shorten the caption or tagline in listing.json`);

  const { data } = await send("Page.captureScreenshot", { format: "png", fromSurface: true });
  const image = decode(Buffer.from(data, "base64"));
  if (image.width !== size.width * factor || image.height !== size.height * factor) {
    fail(`${name}: Chrome drew ${image.width}×${image.height}, not ${size.width * factor}×${size.height * factor}`);
  }
  // Play takes a "24-bit PNG (no alpha)" for screenshots and the feature graphic, but a "32-bit PNG" for
  // the icon.
  writeFileSync(out, encode(image, size.supersample, opaque ? 3 : 4));
}

/**
 * Starts one headless Chrome for the whole run and returns `send(method, params)` over its DevTools
 * protocol — Node's own WebSocket, so still no dependencies.
 */
async function openChrome(executable) {
  const profile = join(STORE, "build", "chrome-profile");
  const portFile = join(profile, "DevToolsActivePort");
  rmSync(portFile, { force: true });
  const chrome = spawn(executable, [
    "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run", "--no-default-browser-check",
    "--allow-file-access-from-files", "--remote-debugging-port=0", `--user-data-dir=${profile}`, "about:blank",
  ], { stdio: "ignore" });

  let port;
  for (let tries = 0; !port && tries < 200; tries++) {
    await new Promise((wake) => setTimeout(wake, 50));
    if (existsSync(portFile)) port = readFileSync(portFile, "utf8").split("\n")[0].trim() || undefined;
  }
  if (!port) {
    chrome.kill();
    fail("Chrome did not open its DevTools port");
  }
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  const page = targets.find((target) => target.type === "page");
  if (!page) {
    chrome.kill();
    fail("Chrome opened no page");
  }

  const socket = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((open, broken) => {
    socket.onopen = open;
    socket.onerror = broken;
  });
  const pending = new Map();
  let next = 0;
  socket.onmessage = ({ data }) => {
    const message = JSON.parse(data);
    const call = pending.get(message.id);
    if (!call) return;
    pending.delete(message.id);
    if (message.error) call.broken(new Error(`${call.method}: ${message.error.message}`));
    else call.done(message.result);
  };
  const send = (method, params = {}) => new Promise((done, broken) => {
    const id = ++next;
    pending.set(id, { method, done, broken });
    socket.send(JSON.stringify({ id, method, params }));
  });
  return {
    send,
    close: () => {
      socket.close();
      chrome.kill();
    },
  };
}

/** Decodes an 8-bit RGB or RGBA PNG — all Chrome writes — into its raw pixels. */
function decode(png) {
  const width = png.readUInt32BE(16), height = png.readUInt32BE(20), depth = png[24], type = png[25];
  if (depth !== 8 || (type !== 2 && type !== 6)) fail(`Unexpected PNG colour type ${type}, depth ${depth}`);
  const channels = type === 6 ? 4 : 3;
  const idat = [];
  for (let at = 8; at < png.length;) {
    const length = png.readUInt32BE(at), kind = png.toString("latin1", at + 4, at + 8);
    if (kind === "IDAT") idat.push(png.subarray(at + 8, at + 8 + length));
    at += 12 + length;
  }
  const data = inflateSync(Buffer.concat(idat));
  const stride = width * channels, pixels = Buffer.alloc(height * stride);
  // One loop per filter rather than a choice per byte: the screenshots' captures are 30 million pixels.
  for (let y = 0; y < height; y++) {
    const filter = data[y * (stride + 1)], from = y * (stride + 1) + 1, row = y * stride, up = row - stride;
    for (let x = 0; x < stride; x++) {
      const raw = data[from + x];
      const a = x >= channels ? pixels[row + x - channels] : 0;
      const b = y > 0 ? pixels[up + x] : 0;
      let value;
      switch (filter) {
        case 0: value = raw; break;
        case 1: value = raw + a; break;
        case 2: value = raw + b; break;
        case 3: value = raw + ((a + b) >> 1); break;
        default: value = raw + paeth(a, b, x >= channels && y > 0 ? pixels[up + x - channels] : 0);
      }
      pixels[row + x] = value & 0xff;
    }
  }
  return { width, height, channels, pixels };
}

/**
 * Averages every [factor] × [factor] block into one pixel and writes [channels] per pixel: 3 (RGB,
 * Play's "24-bit, no alpha") or 4 (RGBA, its "32-bit"). The pages are drawn at [factor]× and brought down
 * here, so a phone shrunk to a third of its size still has smooth text and edges — at 1× Chrome samples
 * it once per pixel, and the feature graphic's small phone came out soft and jagged.
 */
function encode({ width, height, channels: from, pixels }, factor, channels) {
  const w = width / factor, h = height / factor, area = factor * factor;
  const stride = w * channels + 1, out = Buffer.alloc(h * stride);
  for (let y = 0; y < h; y++) {
    out[y * stride] = 0; // no filter
    for (let x = 0; x < w; x++) {
      for (let k = 0; k < 3; k++) {
        let sum = 0;
        for (let dy = 0; dy < factor; dy++) {
          for (let dx = 0; dx < factor; dx++) sum += pixels[((y * factor + dy) * width + x * factor + dx) * from + k];
        }
        out[y * stride + 1 + x * channels + k] = Math.round(sum / area);
      }
      if (channels === 4) out[y * stride + 1 + x * 4 + 3] = 0xff; // every page here is opaque
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(w, 0); header.writeUInt32BE(h, 4);
  header[8] = 8; header[9] = channels === 4 ? 6 : 2; // 8-bit truecolour, with or without alpha
  return Buffer.concat([PNG_SIGNATURE, chunk("IHDR", header), chunk("IDAT", deflateSync(out, { level: 9 })),
    chunk("IEND", Buffer.alloc(0))]);
}

function paeth(a, b, c) {
  const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}

function chunk(kind, data) {
  const head = Buffer.alloc(8);
  head.writeUInt32BE(data.length, 0);
  head.write(kind, 4, "latin1");
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), data])), 0);
  return Buffer.concat([head, data, crc]);
}

function crc32(bytes) {
  let crc = ~0;
  for (const byte of bytes) {
    crc ^= byte;
    for (let k = 0; k < 8; k++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return ~crc >>> 0;
}

function findChrome() {
  const candidates = [
    process.env.CHROME,
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
    `${process.env.LOCALAPPDATA}/Google/Chrome/Application/chrome.exe`,
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser",
  ];
  return candidates.find((path) => path && existsSync(path)) ?? fail("Chrome not found; set CHROME=<path to chrome>");
}

function fail(message) {
  console.error(message);
  browser?.close();
  process.exit(1);
}
