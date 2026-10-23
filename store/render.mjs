#!/usr/bin/env node
// Turns the raw captures from StoreScreenshots into the Google Play listing's graphics: framed,
// captioned phone screenshots, the feature graphic and the 512 px icon, for every locale in
// listing.json. No npm dependencies — each image is an HTML page drawn by a headless Chrome, driven over
// the DevTools protocol so a page is captured only once its fonts and images have painted.
//
//   ./gradlew :app:testDebugUnitTest --tests "*StoreScreenshots*" -PstoreScreenshots   # captures
//   node store/render.mjs                                                              # graphics
//   node store/render.mjs --gpu                                                        # drawn on the GPU
//
// Writes store/listing/<locale>/{phoneScreenshots/NN-<shot>.png, featureGraphic.png, icon.png} and copies
// the listing's words from store/text/<locale>/ beside them, checked against Play's length limits.
// CHROME=<path> overrides where Chrome is looked for. See docs/store-listing.md.

import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { evaluate, openChrome } from "./lib/chrome.mjs";
import { ICON_MARK, LOGO, PHONE_CSS, appScreen, fontFaces, homeScreen, lines, lockScreen, text, url } from "./lib/phone.mjs";
import { cardAround, outlinedRow } from "./lib/find.mjs";
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
/** The phone on a screenshot: its width and left edge, laid out at [SHOT]'s size. */
const PHONE = { width: 1010, left: 215 };
/**
 * How much a called-out row grows as it lifts off the screen: enough to stand clear of the phone's edge,
 * so the one thing a screenshot is about can be read at the size Play shows it in a list.
 */
const LIFT = 1.12;
/** A row lifted off a screen with no card of its own still gets a gentle rounding: 12 dp. */
const ROW_CORNER = 12 * 4;
/** How far inside its mark a lifted copy is cut, in capture pixels: half a dp, too little to see. */
const LIFT_INSET = 2;
/**
 * Things a screenshot can call out that StoreScreenshots did not mark, found on the capture instead: the
 * lesson sheet's row of statuses, and the report's whole income card around its label.
 */
const FOUND = {
  status: (image) => outlinedRow(image, Math.floor(image.height / 2)),
  "income-card": (image, marks) => {
    const label = marks.income ?? fail("no «income» mark on «reports»");
    // In the card's padding, left of its label: 12 dp of the 20 there are.
    return cardAround(image, label[0] - 48, Math.round((label[1] + label[3]) / 2), 16 * 4);
  },
};
/**
 * What a drawn screen can call out, by the name `callout` gives it: on the lock screen the reminder — the
 * card with the buttons; on the home screen the Today widget.
 */
const DRAWN_CALLOUTS = {
  reminder: `[...phone.querySelectorAll(".card")].find((c) => c.querySelector(".card-actions"))`,
  widget: `phone.querySelector(".widget.today")`,
};

/**
 * A caption's least contrast with what is behind it — WCAG's for body text, though the captions are large:
 * Play shows a screenshot at a fraction of its size, where large text is small again.
 */
const MIN_CONTRAST = 4.5;
/** Where the feature graphic's phone starts; the brand's words must stop short of it. */
const FEATURE_PHONE_LEFT = 588;
/**
 * The middle of the feature graphic, where Play draws its play button once the listing has a video: no
 * words of the brand may fall inside it.
 */
const PLAY_BUTTON = { x: 512, y: 250, r: 96 };

const listing = JSON.parse(readFileSync(join(STORE, "listing.json"), "utf8"));
let browser;
// A profile of its own, so the listing can be drawn while video.mjs encodes in its own Chrome.
browser = await openChrome(join(STORE, "build", "chrome-profile-listing"), { gpu: process.argv.includes("--gpu") })
  .catch((e) => fail(e.message));

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

  const themes = listing.screenshots.map((entry) => entry.theme ?? "light");
  for (const [i, entry] of listing.screenshots.entries()) {
    const drawn = isDrawn(entry);
    const shot = drawn ? null : shots[entry.shot] ?? fail(`${locale}: no capture named "${entry.shot}"`);
    const copy = entry[locale] ?? fail(`${locale}: no caption for "${entry.shot}"`);
    const name = `${String(i + 1).padStart(2, "0")}-${entry.shot}`;
    const tray = () => JSON.parse(readFileSync(join(raw, "notifications.json"), "utf8"));
    const screen = entry.kind === "lockscreen" ? lockScreen(tray())
      : entry.kind === "homescreen" ? homeScreen(raw, { ...tray(), homeClock: words.clock })
      : appScreen(raw, entry.shot, shot ?? {}, words.clock);
    const behind = entry.behind &&
      appScreen(raw, entry.behind, shots[entry.behind] ?? fail(`${locale}: no capture named "${entry.behind}"`), words.clock);
    const lifts = drawn ? "" : callouts(raw, entry, shot, `${locale} ${entry.shot}`);
    await draw(screenshotPage({ screen, behind, lifts, entry, copy, index: i, themes }), SHOT,
      join(out, "phoneScreenshots", `${name}.png`), `${locale}-${name}`);
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

// --- Callouts -----------------------------------------------------------------------------------

/**
 * The rows an entry calls out (`callout`, by mark name), lifted off the phone's screen as one card —
 * several rows that sit together, as settings' switches do, lift as one. In the phone's own pixels, so it
 * sits where the row does and turns with the phone.
 */
function callouts(raw, entry, shot, name) {
  if (!entry.callout?.length) return "";
  const image = decode(readFileSync(join(raw, `${entry.shot}.png`)));
  const boxes = entry.callout.map((mark) => {
    const box = FOUND[mark]?.(image, shot.marks) ?? shot.marks[mark] ?? fail(`${name}: no mark «${mark}» to call out`);
    return [...box.slice(0, 4), box[4] ?? 0];
  });
  // Cut a little inside the marks: scaled, the copy's outermost pixels blend with what lies just outside
  // the card, and a lighter screen behind it drew a pale rim round it.
  const [l, t, r, b] = [0, 1, 2, 3].map((k) => (k < 2 ? Math.min : Math.max)(...boxes.map((box) => box[k])) +
    (k < 2 ? LIFT_INSET : -LIFT_INSET));
  const corner = Math.max(ROW_CORNER, ...boxes.map((box) => box[4] - LIFT_INSET));
  // As shares of the phone's width, as PHONE_CSS draws it — a 2.2% bezel, a 95.6% screen, a 9.2% status
  // bar — so the lift fits a phone of any size.
  const k = 0.956 / image.width, of = (share) => `calc(var(--phone) * ${share})`;
  return `
      <div class="lift" style="left: ${of(0.022 + l * k)}; top: ${of(0.022 + 0.092 + t * k)}; width: ${of((r - l) * k)};
        height: ${of((b - t) * k)}; border-radius: ${of(corner * k)};
        background-image: url(&quot;${url(join(raw, `${entry.shot}.png`))}&quot;);
        background-size: ${of(0.956)} auto; background-position: ${of(-l * k)} ${of(-t * k)};"></div>`;
}

// --- Pages -------------------------------------------------------------------------------------

/**
 * A screenshot: the caption, and under it the phone — straight on; or `layout: "tilted"`, turned so its
 * edge shows; or `layout: "pair"`, in front of a second phone showing `behind`. The backdrop is one long
 * picture across all the screenshots, each showing its own stretch of it ([panorama]).
 */
function screenshotPage({ screen, behind, lifts, entry, copy, index, themes }) {
  const layout = entry.layout ?? "straight";
  const phones = {
    straight: `
      <div class="phone" style="--phone: ${PHONE.width}px; left: ${PHONE.left}px; top: ${PHONE_TOP}px;">${phoneBody()}${screen}${lifts}</div>`,
    tilted: `
      <div class="phone tilted" style="--phone: 860px; left: 300px; top: ${PHONE_TOP + 20}px;">${phoneBody(true)}${screen}${lifts}</div>`,
    pair: `
      <div class="phone behind" style="--phone: 900px; left: 15px; top: ${PHONE_TOP + 110}px;">${phoneBody()}${behind}</div>
      <div class="phone front" style="--phone: 940px; left: 420px; top: ${PHONE_TOP + 25}px;">${phoneBody()}${screen}${lifts}</div>`,
  };
  return page(SHOT, `
    <div class="backdrop">
      ${panorama(themes, index)}
      <header>
        <h1>${lines(copy.title)}</h1>
        <p>${text(copy.subtitle)}</p>
      </header>
      ${phones[layout] ?? fail(`No screenshot layout «${layout}»`)}
    </div>
    ${isDrawn(entry) ? (entry.callout ?? []).map((name) => liftDrawn(DRAWN_CALLOUTS[name] ?? fail(`No drawn callout «${name}»`))).join("") : ""}
    ${fitCaption()}`);
}

/** Whether [entry]'s screen is drawn here — the lock screen, the home screen — rather than captured. */
function isDrawn(entry) {
  return entry.kind === "lockscreen" || entry.kind === "homescreen";
}

/**
 * A part of a drawn screen lifted off it, as an app screen's row is: a copy of it over it, grown, standing
 * out from the phone. [find] is the script that picks it out of `phone`.
 */
function liftDrawn(find) {
  return `<script>
    document.fonts.ready.then(() => {
      const phone = document.querySelector(".phone");
      const card = ${find};
      let x = 0, y = 0;
      for (let el = card; el && el !== phone; el = el.offsetParent) { x += el.offsetLeft; y += el.offsetTop; }
      const lift = card.cloneNode(true);
      lift.classList.add("lift", "card-lift");
      Object.assign(lift.style, { left: x + "px", top: y + "px", width: card.offsetWidth + "px", height: card.offsetHeight + "px", margin: "0" });
      phone.append(lift);
    });
  </script>`;
}

/**
 * The phone's body around its face: the buttons on its side and light lying across the glass — and, for a
 * phone turned in [depth], its edge, in layers down to the back, which casts the shadow. A phone facing
 * the camera cannot show its edge, so it is not drawn: every layer is a whole phone's worth of pixels,
 * and two phones of them at the screenshots' scale were more than Chrome would draw.
 */
function phoneBody(depth = false) {
  const sides = depth
    ? Array.from({ length: 7 }, (_, i) =>
      `<div class="side${i === 6 ? " back" : ""}" style="--z: ${((i + 1) * 0.012).toFixed(3)}"></div>`).join("")
    : "";
  return `${sides}<div class="key power"></div><div class="key volume"></div><div class="glass"></div>`;
}

/**
 * The backdrop as one picture across every screenshot, of which screenshot [index] shows its own stretch:
 * a gradient that drifts in tone from one to the next — into night under a dark-theme one — and glows
 * sitting on the joins, half on each side, so that side by side in Play's gallery the screenshots run on
 * into each other. The glows sit low, behind the phones, never behind the captions.
 */
function panorama(themes, index) {
  const w = SHOT.width, tones = ["#5261cd", "#4a58c2", "#5866d2", "#4553bf"];
  const stops = themes.map((theme, i) => `${theme === "dark" ? "#14172f" : tones[i % tones.length]} ${i * w + w / 2}px`);
  const glows = themes.slice(1).map((theme, i) => {
    const dark = theme === "dark" || themes[i] === "dark";
    const light = i % 2 === 0;
    const size = light ? 900 : 1100, y = light ? 1500 : 2050;
    const color = dark ? (light ? "#4c5bc4" : "#003640") : light ? "#a9edff" : "#006879";
    const opacity = dark ? 0.45 : light ? 0.3 : 0.55;
    return `<div class="glow" style="left: ${(i + 1) * w - size / 2}px; top: ${y - size / 2}px; width: ${size}px;
      height: ${size}px; background: ${color}; opacity: ${opacity}"></div>`;
  }).join("");
  return `
      <div class="pano" style="left: ${-index * w}px; width: ${themes.length * w}px;
        background: linear-gradient(180deg, rgba(10, 12, 60, .1) 0%, rgba(10, 12, 60, 0) 30%, rgba(10, 12, 60, .45) 100%),
          linear-gradient(90deg, ${stops.join(", ")});">${glows}</div>`;
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
      const limit = Math.min(...[...document.querySelectorAll(".phone")].map((p) => p.getBoundingClientRect().top)) - ${CAPTION_GAP};
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

/**
 * The feature graphic: the brand on the left — its name, then the tagline under it — and the phone on the
 * right. The words keep out of the phone's way, and out of the middle, where Play's play button goes.
 */
function featurePage({ raw, shot, colors, tagline, clock }) {
  return page(FEATURE, `
    <div class="backdrop feature">
      <div class="glow one"></div><div class="glow two"></div>
      <div class="brand">
        <div class="lockup">
          <div class="mark"><img src="${url(LOGO)}"></div>
          <div class="wordmark">Klas</div>
        </div>
        <div class="tagline">${text(tagline)}</div>
      </div>
      <div class="phone" style="--phone: 400px; left: ${FEATURE_PHONE_LEFT}px; top: 62px;">
        ${phoneBody()}${appScreen(raw, shot, colors, clock)}
      </div>
    </div>
    <script>
      document.fonts.ready.then(() => {
        const words = document.querySelector(".brand").getBoundingClientRect();
        const { x, y, r } = ${JSON.stringify(PLAY_BUTTON)};
        const clear = (box) => Math.hypot(Math.max(box.left - x, 0, x - box.right), Math.max(box.top - y, 0, y - box.bottom)) > r;
        document.body.dataset.fits = [...document.querySelectorAll(".brand .mark, .brand .wordmark, .brand .tagline")]
          .every((el) => {
            const box = el.getBoundingClientRect();
            return box.right <= ${FEATURE_PHONE_LEFT - CAPTION_GAP / 2} && clear(box);
          }) && words.bottom <= 500;
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
    .pano { position: absolute; top: 0; height: 100%; }
    .glow { position: absolute; border-radius: 50%; filter: blur(120px); opacity: .55; }
    .glow.one { width: 900px; height: 900px; right: -320px; top: -260px; background: #a9edff; opacity: .35; }
    .glow.two { width: 1100px; height: 1100px; left: -420px; bottom: -380px; background: #006879; }

    header { position: absolute; left: 120px; right: 120px; top: 170px; text-align: center; }
    h1 { font-weight: 700; font-size: 124px; line-height: 1.06; letter-spacing: -2.5px; text-wrap: balance; }
    header p { margin: 44px auto 0; max-width: 1080px; font-weight: 500; font-size: 52px; line-height: 1.3;
      color: rgba(255, 255, 255, .92); text-wrap: balance; }
    ${PHONE_CSS}

    /* The phone's body (phoneBody): the face's rim catching the light, the side buttons, the glare — and on
       a turned phone the edge stacked behind it, whose back casts the shadow, so it does not cross the edge. */
    .phone { background: #0c0d11;
      box-shadow: 0 0 0 calc(var(--phone) * .004) #5e616c inset, 0 0 0 calc(var(--phone) * .008) #1b1c22 inset,
        0 calc(var(--phone) * .06) calc(var(--phone) * .14) rgba(8, 10, 48, .55); }
    .phone .side { position: absolute; inset: 0; border-radius: inherit;
      background: linear-gradient(90deg, #474954, #24252c 10%, #18191e 50%, #24252c 90%, #474954);
      transform: translateZ(calc(var(--phone) * var(--z) * -1)); }
    .phone .side.back { box-shadow: 0 calc(var(--phone) * .06) calc(var(--phone) * .14) rgba(8, 10, 48, .55); }
    .phone .key { position: absolute; right: calc(var(--phone) * -.011); width: calc(var(--phone) * .011);
      border-radius: 0 calc(var(--phone) * .006) calc(var(--phone) * .006) 0;
      background: linear-gradient(90deg, #26272d, #5a5c67 60%, #33343b); }
    .phone .key.power { top: 25%; height: 8%; }
    .phone .key.volume { top: 36%; height: 14%; }
    .phone .glass { position: absolute; inset: calc(var(--phone) * .022); border-radius: calc(var(--phone) * .104);
      z-index: 2; pointer-events: none;
      background: linear-gradient(112deg, rgba(255, 255, 255, 0) 30%, rgba(255, 255, 255, .06) 42%,
        rgba(255, 255, 255, .02) 50%, rgba(255, 255, 255, 0) 58%); }
    .phone.tilted { transform-origin: 50% 30%; transform-style: preserve-3d;
      transform: perspective(3200px) rotateY(-17deg) rotateX(5deg) rotateZ(-2.5deg);
      box-shadow: 0 0 0 calc(var(--phone) * .004) #5e616c inset, 0 0 0 calc(var(--phone) * .008) #1b1c22 inset; }
    .phone.tilted .key { transform: translateZ(calc(var(--phone) * -.042)); }
    .phone.behind { transform-origin: 50% 30%; transform: rotate(-6deg); }
    .phone.front { transform-origin: 50% 30%; transform: rotate(3deg); }

    /* A row lifted off the screen (callouts): bigger, and shadowed so it stands off the phone. */
    .lift { position: absolute; z-index: 3; background-repeat: no-repeat; transform: scale(${LIFT});
      box-shadow: 0 calc(var(--phone) * .03) calc(var(--phone) * .07) rgba(8, 10, 48, .45); }
    .tilted .lift { transform: translateZ(calc(var(--phone) * .06)) scale(1.08); }
    /* Opaque, unlike the lock screen's own frosted cards, or the card it lifts off shows through it. */
    .lift.card-lift { background: #fbf8ff; }

    .feature .brand { position: absolute; left: 52px; top: 0; bottom: 0; width: 340px;
      display: flex; flex-direction: column; justify-content: center; }
    .feature .lockup { display: flex; align-items: center; gap: 24px; }
    .feature .mark { flex: none; width: 112px; height: 112px; border-radius: 30px; background: #dfe0ff;
      display: grid; place-items: center; box-shadow: 0 18px 40px rgba(8, 10, 48, .35); }
    .feature .mark img { width: ${Math.round(112 * ICON_MARK)}px; }
    .feature .wordmark { font-weight: 700; font-size: 92px; line-height: 1; letter-spacing: -2px; }
    .feature .tagline { margin-top: 26px; font-weight: 500; font-size: 28px; line-height: 1.28;
      color: rgba(255, 255, 255, .9); text-wrap: balance; }
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
  await checkContrast(name);

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
 * Fails a page whose caption is hard to read: each block of words against the backdrop behind it,
 * measured with the words taken away, at the lightest that backdrop gets under them (its 98th
 * percentile, so a stray bright pixel of a glow does not count).
 */
async function checkContrast(name) {
  const { send } = browser;
  const blocks = await evaluate(send, `[...document.querySelectorAll("header h1, header p")].map((el) => {
    const box = el.getBoundingClientRect(), style = getComputedStyle(el);
    el.style.visibility = "hidden";
    return { x: box.left, y: box.top, width: box.width, height: box.height, color: style.color, what: el.tagName };
  })`);
  if (!blocks.length) return;
  const settled = "new Promise((done) => requestAnimationFrame(() => requestAnimationFrame(done)))";
  await evaluate(send, settled);
  for (const block of blocks) {
    const { data } = await send("Page.captureScreenshot",
      { format: "png", fromSurface: true, clip: { x: block.x, y: block.y, width: block.width, height: block.height, scale: 0.25 } });
    const behind = decode(Buffer.from(data, "base64"));
    const [r, g, b, a = 1] = block.color.match(/[\d.]+/g).map(Number);
    const ratios = [];
    for (let i = 0; i < behind.width * behind.height; i++) {
      const at = i * behind.channels, px = [0, 1, 2].map((k) => behind.pixels[at + k]);
      ratios.push(contrast(luminance([r, g, b].map((v, k) => a * v + (1 - a) * px[k])), luminance(px)));
    }
    ratios.sort((p, q) => p - q);
    const worst = ratios[Math.floor(ratios.length * 0.02)];
    if (worst < MIN_CONTRAST) {
      fail(`${name}: the caption's ${block.what === "H1" ? "title" : "subtitle"} is ${worst.toFixed(2)}:1 against ` +
        `the backdrop; it needs ${MIN_CONTRAST}:1`);
    }
  }
  await evaluate(send, `document.querySelectorAll("header h1, header p").forEach((el) => (el.style.visibility = ""))`);
  await evaluate(send, settled);
}

/** WCAG's relative luminance of an sRGB colour, and the contrast between two. */
function luminance(rgb) {
  const [r, g, b] = rgb.map((v) => {
    const c = v / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
function contrast(a, b) {
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

function fail(message) {
  console.error(message);
  browser?.close();
  process.exit(1);
}
