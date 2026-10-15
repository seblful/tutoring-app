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

import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { openChrome } from "./lib/chrome.mjs";
import { ICON_MARK, LOGO, PHONE_CSS, appScreen, fontFaces, lines, lockScreen, text, url } from "./lib/phone.mjs";
import { decode, encode } from "./lib/png.mjs";

const STORE = dirname(fileURLToPath(import.meta.url));
const RAW = join(STORE, "build", "raw");
const WORK = join(STORE, "build", "html");
const OUT = join(STORE, "listing");
const TEXT = join(STORE, "text");

/** Play Console's limits, in characters, for each piece of the listing's text. */
const TEXT_LIMITS = { "title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000 };

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

const listing = JSON.parse(readFileSync(join(STORE, "listing.json"), "utf8"));
let browser;
browser = await openChrome(join(STORE, "build", "chrome-profile")).catch((e) => fail(e.message));

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
        <div class="mark"><img src="${url(LOGO)}"></div>
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
    <div class="icon"><img src="${url(LOGO)}"></div>`);
}

function page(size, body) {
  return `<!doctype html><html><head><meta charset="utf-8"><style>
    ${fontFaces()}
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
    ${PHONE_CSS}

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

function fail(message) {
  console.error(message);
  browser?.close();
  process.exit(1);
}
