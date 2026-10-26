// One headless Chrome, driven over its DevTools protocol with Node's own WebSocket — the store's pages
// are drawn by it, and the video encoded in it, with no npm dependencies.

import { spawn } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { join } from "node:path";

/**
 * Starts Chrome with its profile in [profile] and returns `send(method, params)` for its page, and
 * `close()`. CHROME=<path> overrides where Chrome is looked for.
 *
 * Chrome draws in software unless [gpu]: the same on every machine, and faster than a GPU for a small page
 * that is read back — a laptop's discrete GPU took 1.3 s to hand a video frame's screenshot over, against
 * 0.1 s in software. With [gpu] it draws on the fastest GPU there is, with html-in-canvas on and no
 * frame-rate cap: for the video, drawn and encoded without leaving it (store/film/gpu.js), and the
 * listing's stills, whose drawing outweighs the read-back.
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

  const pending = new Map();
  const abort = new AbortController();
  let socket, closed = false, failure, rejectOpening;
  const stop = (error) => {
    if (closed) return;
    closed = true;
    failure = error;
    abort.abort(error);
    rejectOpening?.(error);
    for (const call of pending.values()) call.broken(error);
    pending.clear();
    try { socket?.close(); } catch {
      // A broken socket must not prevent the child from being stopped.
    }
    chrome.kill();
  };
  chrome.on("error", stop);
  chrome.on("exit", (code, signal) => stop(new Error(`Chrome exited (${signal ?? code})`)));
  // Bound the whole startup, including discovery and the WebSocket handshake.
  const startup = setTimeout(() => stop(new Error("Chrome did not open its DevTools connection")), 10_000);
  try {
    let port;
    while (!port) {
      await new Promise((wake) => setTimeout(wake, 50));
      abort.signal.throwIfAborted();
      if (existsSync(portFile)) port = readFileSync(portFile, "utf8").split("\n")[0].trim() || undefined;
    }
    const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`, { signal: abort.signal })).json();
    abort.signal.throwIfAborted();
    const page = targets.find((target) => target.type === "page");
    if (!page) throw new Error("Chrome opened no page");
    socket = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise((open, broken) => {
      rejectOpening = broken;
      socket.onopen = open;
      socket.onerror = (event) => stop(event.error ?? new Error("Chrome DevTools connection failed"));
      socket.onclose = () => stop(new Error("Chrome DevTools connection closed"));
    });
    abort.signal.throwIfAborted();
  } catch (error) {
    stop(error);
    throw failure;
  } finally {
    clearTimeout(startup);
    rejectOpening = undefined;
  }

  let next = 0;
  socket.onmessage = ({ data }) => {
    let message;
    try { message = JSON.parse(data); } catch (error) { stop(error); return; }
    const call = pending.get(message.id);
    if (!call) return;
    pending.delete(message.id);
    if (message.error) call.broken(new Error(`${call.method}: ${message.error.message}`));
    else call.done(message.result);
  };
  const send = (method, params = {}) => new Promise((done, broken) => {
    if (closed) return broken(failure);
    if (socket.readyState !== WebSocket.OPEN) {
      stop(new Error("Chrome DevTools connection is not open"));
      return broken(failure);
    }
    const id = ++next;
    pending.set(id, { method, done, broken });
    try { socket.send(JSON.stringify({ id, method, params })); } catch (error) {
      pending.delete(id);
      broken(error);
    }
  });
  return {
    send,
    close: () => stop(new Error("Chrome closed")),
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
