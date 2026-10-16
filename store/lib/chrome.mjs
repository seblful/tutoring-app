// One headless Chrome, driven over its DevTools protocol with Node's own WebSocket — the store's pages
// are drawn by it, and the video encoded in it, with no npm dependencies.

import { spawn } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { join } from "node:path";

/**
 * Starts Chrome with its profile in [profile] and returns `send(method, params)` for its page, and
 * `close()`. CHROME=<path> overrides where Chrome is looked for.
 *
 * Chrome draws in software unless [gpu]: the same on every machine, and faster than a GPU for a page that
 * is read back — a laptop's discrete GPU took 1.3 s a frame to hand a screenshot over, against 0.1 s in
 * software. With [gpu] it draws on the fastest GPU there is, with html-in-canvas on and no frame-rate cap,
 * for the video drawn and encoded without leaving it (store/film/gpu.js).
 */
export async function openChrome(profile, { gpu = false } = {}) {
  const portFile = join(profile, "DevToolsActivePort");
  rmSync(portFile, { force: true });
  const graphics = gpu
    ? ["--enable-gpu", ...(process.platform === "win32" ? ["--use-angle=d3d11"] : []), "--force_high_performance_gpu",
      "--enable-blink-features=CanvasDrawElement", "--disable-frame-rate-limit", "--disable-gpu-vsync"]
    : ["--disable-gpu"];
  const chrome = spawn(findChrome(), [
    "--headless=new", ...graphics, "--hide-scrollbars", "--no-first-run", "--no-default-browser-check",
    "--allow-file-access-from-files", "--remote-debugging-port=0", `--user-data-dir=${profile}`, "about:blank",
  ], { stdio: "ignore" });

  let port;
  for (let tries = 0; !port && tries < 200; tries++) {
    await new Promise((wake) => setTimeout(wake, 50));
    if (existsSync(portFile)) port = readFileSync(portFile, "utf8").split("\n")[0].trim() || undefined;
  }
  if (!port) {
    chrome.kill();
    throw new Error("Chrome did not open its DevTools port");
  }
  const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  const page = targets.find((target) => target.type === "page");
  if (!page) {
    chrome.kill();
    throw new Error("Chrome opened no page");
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

/**
 * Evaluates [expression] in the page, waits for it if it is a promise, and returns its value — throwing
 * what the page threw, so a mistake in a page's script stops the run with the page's own message.
 */
export async function evaluate(send, expression) {
  const { result, exceptionDetails } = await send("Runtime.evaluate",
    { expression, awaitPromise: true, returnByValue: true });
  if (exceptionDetails) {
    const thrown = exceptionDetails.exception?.description ?? exceptionDetails.text;
    throw new Error(`The page threw: ${thrown}`);
  }
  return result.value;
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
  const found = candidates.find((path) => path && existsSync(path));
  if (!found) throw new Error("Chrome not found; set CHROME=<path to chrome>");
  return found;
}
