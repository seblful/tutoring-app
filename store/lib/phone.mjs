// The phone the app is shown in — its frame, status bar, lock screen and home screen — and the app's font, shared by
// the listing's stills (render.mjs) and the promo video (video.mjs), so both show the same phone.

import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
export const RES = join(ROOT, "app", "src", "main", "res");
/** The text catalog's Android-format string files, one `values…` folder per language. */
export const STRINGS = join(ROOT, "ui", "src", "commonMain", "strings");
export const LOGO = join(RES, "drawable-nodpi", "ic_logo.png");

/**
 * How much of the icon the K fills. The launcher icon is adaptive: its mark is inset 20.37% into a 108 dp
 * canvas (`ic_launcher_foreground`) of which a phone shows only the middle 72 dp, so on a home screen the
 * mark is 1.5× what the inset alone suggests. Play masks the whole 512 px square instead, so the icon is
 * drawn as the phone shows it.
 */
export const ICON_MARK = (1 - 2 * 0.2037) * (108 / 72);

/** The app's own font, Golos Text, in the four weights it ships. */
export function fontFaces() {
  const font = (file, weight) =>
    `@font-face { font-family: Golos; font-weight: ${weight}; src: url("${url(join(RES, "font", file))}"); }`;
  return `${font("golos_text_regular.ttf", 400)} ${font("golos_text_medium.ttf", 500)}
    ${font("golos_text_semibold.ttf", 600)} ${font("golos_text_bold.ttf", 700)}`;
}

/** The phone's styles. Every length is a share of `--phone`, the phone's width, so it draws at any size. */
export const PHONE_CSS = `
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

    /* The home screen (homeScreen): the lock screen's wallpaper, with the app's widgets laid on it. */
    .home > :not(.status) { position: absolute; }
    .home-date { font-weight: 500; font-size: calc(var(--phone) * .052); color: #fff; }
    /* The widgets' captures have their own clear 24 dp corners; the radius only shapes the shadow. Clipped
       too, an edge was antialiased twice on some frames of a zoom, and blinked. */
    .widget { border-radius: calc(var(--phone) * ${(0.956 * 24 / 412).toFixed(5)});
      box-shadow: 0 calc(var(--phone) * .008) calc(var(--phone) * .03) rgba(8, 10, 48, .28); }
    .widget-face { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
    .widget-face.ticked { opacity: 0; }
    .home-search { border-radius: 999px; background: rgba(251, 248, 255, .92); display: flex; align-items: center;
      padding-left: calc(var(--phone) * .045); }
    .home-search svg { width: calc(var(--phone) * .06); }`;

/** The phone's screen: a status bar in the colour the capture starts with, then the app. */
export function appScreen(raw, shot, colors, clock) {
  const ink = colors.dark ? "#e4e1e9" : "#1b1b21";
  return `
    <div class="screen">
      ${statusBar(colors.top, ink, clock)}
      <img class="app" src="${url(join(raw, `${shot}.png`))}">
    </div>`;
}

/**
 * The status bar every screen starts with: the clock, the camera's punch hole and the radio icons. Its colour
 * runs a little over the capture below, which starts in that colour: edge to edge, both edges were
 * antialiased against the screen's black, and the seam blinked as the video's phone moved.
 */
export function statusBar(background, ink, clock) {
  return `
      <div class="status" style="background:${background}; color:${ink};
        box-shadow: 0 calc(var(--phone) * .003) 0 ${background}">
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
export function lockScreen(tray) {
  const cards = [...tray.notifications].reverse().map((n, i) => `
        <div class="card">
          <div class="card-head">
            <span class="badge"><img src="${url(LOGO)}"></span>
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

/**
 * The home screen, in the 412 × 915 dp grid of the app's captures under the status bar (4 capture pixels a
 * dp): the date, the Today widget and the shortcuts bar under it, and a search pill. The
 * widgets are the app's own, drawn by StoreWidgets; boxes here are where they sit, so a mark on a widget's
 * capture lands on the screen at its box's offset, as one on an app capture does.
 */
export const HOME = {
  today: { left: 16, top: 64, width: 380, height: 400 },
  shortcuts: { left: 16, top: 480, width: 380, height: 76 },
};

export function homeScreen(raw, tray) {
  const dp = (n, from = 0) => `calc(var(--phone) * ${(from + 0.956 * n / 412).toFixed(5)})`;
  // Down from under the status bar, as the captures are.
  const down = (n) => dp(n, 0.092);
  const box = ({ left, top, width, height }) =>
    `left: ${dp(left)}; top: ${down(top)}; width: ${dp(width)}; height: ${dp(height)};`;
  return `
    <div class="screen">
      <div class="lock home">
        ${statusBar("transparent", "#ffffff", tray.homeClock ?? "")}
        <div class="home-date" style="left: ${dp(24)}; top: ${down(14)};">${text(tray.date)}</div>
        <div class="widget today" style="${box(HOME.today)}">
          <img class="widget-face" src="${url(join(raw, "widget-today.png"))}">
          <img class="widget-face ticked" src="${url(join(raw, "widget-today-ticked.png"))}">
        </div>
        <div class="widget" style="${box(HOME.shortcuts)}">
          <img class="widget-face" src="${url(join(raw, "widget-shortcuts.png"))}">
        </div>
        <div class="home-search" style="left: ${dp(24)}; top: ${down(812)}; width: ${dp(364)}; height: ${dp(56)};">
          <svg viewBox="0 0 24 24"><path fill="#46464f" d="M9.5 3a6.5 6.5 0 0 1 5.2 10.4l5.4 5.4-1.4 1.4-5.4-5.4A6.5 6.5 0 1 1 9.5 3Zm0 2a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z"/></svg>
        </div>
      </div>
    </div>`;
}

function statusIcons(ink) {
  return `
    <svg viewBox="0 0 24 24" width="1.15em" height="1.15em"><path fill="${ink}" d="M12 21 1 9.6C4 6.9 7.8 5.5 12 5.5s8 1.4 11 4.1L12 21Z"/></svg>
    <svg viewBox="0 0 24 24" width="1.15em" height="1.15em"><path fill="${ink}" d="M2 22 22 2v20H2Z"/></svg>
    <svg viewBox="0 0 24 24" width="1.15em" height="1.15em"><rect x="7" y="3" width="10" height="19" rx="2" fill="${ink}"/><rect x="10" y="1.5" width="4" height="2" rx="0.6" fill="${ink}"/></svg>`;
}

export function url(path) {
  return pathToFileURL(path).href;
}

/** Escapes [s] for HTML, and ties a dash to the word before it so no line starts with «—». */
export function text(s) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/ —/g, " —");
}

export function lines(s) {
  return s.split("\n").map(text).join("<br>");
}
