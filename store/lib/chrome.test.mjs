import assert from "node:assert/strict";
import childProcess from "node:child_process";
import { EventEmitter } from "node:events";
import fs from "node:fs";
import { syncBuiltinESMExports } from "node:module";
import { test } from "node:test";
import { openChrome } from "./chrome.mjs";

function fakeChrome(t, { fetch, connection = "open", spawnError } = {}) {
  const child = new EventEmitter();
  child.kills = 0;
  child.kill = () => { child.kills++; };
  let socket, browser;
  let created;
  const socketCreated = new Promise((ready) => { created = ready; });
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, "WebSocket");
  class Socket {
    static CONNECTING = 0;
    static OPEN = 1;
    static CLOSING = 2;
    static CLOSED = 3;
    readyState = Socket.CONNECTING;
    sent = [];
    closes = 0;
    constructor() {
      if (connection === "throws") throw new Error("invalid debugger URL");
      socket = this;
      created();
      if (connection === "waiting") return;
      queueMicrotask(() => {
        if (connection === "error") this.onerror?.({ error: new Error("handshake failed") });
        else if (connection === "closed") this.disconnect();
        else {
          this.readyState = Socket.OPEN;
          this.onopen?.();
        }
      });
    }
    send(message) { this.sent.push(JSON.parse(message)); }
    close() { this.closes++; this.readyState = Socket.CLOSED; }
    disconnect() {
      this.readyState = Socket.CLOSED;
      this.onclose?.({ code: 1006, reason: "connection lost" });
    }
    reply(message) { this.onmessage?.({ data: JSON.stringify(message) }); }
  }
  Object.defineProperty(globalThis, "WebSocket", {
    configurable: true, writable: true, enumerable: descriptor.enumerable, value: Socket,
  });
  t.mock.method(childProcess, "spawn", () => {
    if (spawnError) queueMicrotask(() => child.emit("error", spawnError));
    return child;
  });
  t.mock.method(fs, "existsSync", () => true);
  t.mock.method(fs, "readFileSync", () => "12345\n");
  t.mock.method(fs, "rmSync", () => {});
  t.mock.method(globalThis, "fetch", fetch ?? (async () => ({
    json: async () => [{ type: "page", webSocketDebuggerUrl: "ws://debugger" }],
  })));
  // The module under test keeps its real imports; synchronize the mocked built-in exports.
  syncBuiltinESMExports();
  t.after(() => {
    browser?.close();
    t.mock.restoreAll();
    syncBuiltinESMExports();
    Object.defineProperty(globalThis, "WebSocket", descriptor);
  });
  return {
    child,
    socketCreated,
    get socket() { return socket; },
    async open() { browser = await openChrome("fake-profile"); return browser; },
  };
}

async function within(promise) {
  let timer;
  try {
    return await Promise.race([
      promise,
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error("operation stayed pending")), 1000); }),
    ]);
  } finally { clearTimeout(timer); }
}

test("Chrome commands return their own response and preserve protocol errors", async (t) => {
  const fake = fakeChrome(t);
  const browser = await fake.open();
  const first = browser.send("Runtime.evaluate", { expression: "42" });
  const second = browser.send("Page.captureScreenshot");
  fake.socket.reply({ id: 2, result: { data: "pixels" } });
  fake.socket.reply({ id: 1, error: { message: "evaluation failed" } });
  await assert.rejects(first, /Runtime.evaluate: evaluation failed/);
  assert.deepEqual(await second, { data: "pixels" });
  assert.deepEqual(fake.socket.sent[0], { id: 1, method: "Runtime.evaluate", params: { expression: "42" } });
});

for (const stage of ["fetch", "JSON", "missing page", "socket constructor", "socket handshake", "socket closed"]) {
  test(`a ${stage} startup failure releases Chrome`, async (t) => {
    const failure = new Error(`${stage} failed`);
    const fake = fakeChrome(t, {
      fetch: stage === "fetch" ? async () => { throw failure; }
        : stage === "JSON" ? async () => ({ json: async () => { throw failure; } })
        : stage === "missing page" ? async () => ({ json: async () => [] }) : undefined,
      connection: stage === "socket constructor" ? "throws"
        : stage === "socket handshake" ? "error" : stage === "socket closed" ? "closed" : "open",
    });
    await within(assert.rejects(fake.open()));
    assert.equal(fake.child.kills, 1);
    if (fake.socket) assert.equal(fake.socket.closes, 1);
  });
}

test("a spawn error rejects startup and releases Chrome", async (t) => {
  const failure = new Error("Chrome executable could not start");
  const fake = fakeChrome(t, { spawnError: failure });
  await within(assert.rejects(fake.open(), (error) => error === failure));
  assert.equal(fake.child.kills, 1);
  assert.equal(fake.socket, undefined);
});

test("a child exit interrupts discovery and aborts the request", async (t) => {
  let aborted = false;
  const fake = fakeChrome(t, {
    fetch: (_, { signal }) => new Promise((_, reject) => {
      signal.addEventListener("abort", () => { aborted = true; reject(signal.reason); }, { once: true });
      fake.child.emit("exit", 1, null);
    }),
  });
  await within(assert.rejects(fake.open(), /Chrome exited/));
  assert.equal(aborted, true);
  assert.equal(fake.child.kills, 1);
  assert.equal(fake.socket, undefined);
});

test("the startup deadline also releases a socket whose handshake never finishes", async (t) => {
  const fake = fakeChrome(t, { connection: "waiting" });
  const setTimer = globalThis.setTimeout;
  let expire;
  t.mock.method(globalThis, "setTimeout", (wake, milliseconds, ...args) => {
    if (milliseconds > 1000) expire = wake;
    return setTimer(wake, milliseconds, ...args);
  });
  const opening = fake.open();
  await within(fake.socketCreated);
  expire();
  await within(assert.rejects(opening, /DevTools connection/));
  assert.equal(fake.child.kills, 1);
  assert.equal(fake.socket.closes, 1);
});

for (const event of ["socket close", "socket error", "child exit", "child error"]) {
  test(`${event} rejects every outstanding command and future calls`, async (t) => {
    const fake = fakeChrome(t);
    const browser = await fake.open();
    const commands = [browser.send("Runtime.evaluate"), browser.send("Page.captureScreenshot")];
    const rejected = commands.map((command) => assert.rejects(command));
    if (event === "socket close") fake.socket.disconnect();
    else if (event === "socket error") fake.socket.onerror?.({ error: new Error("transport failed") });
    else if (event === "child exit") fake.child.emit("exit", 1, null);
    else fake.child.emit("error", new Error("child process failed"));
    await within(Promise.all(rejected));
    await within(assert.rejects(browser.send("Page.navigate")));
    assert.equal(fake.socket.sent.length, 2);
    assert.equal(fake.child.kills, 1);
    assert.equal(fake.socket.closes, 1);
  });
}

test("close rejects pending commands and releases each resource only once", async (t) => {
  const fake = fakeChrome(t);
  const browser = await fake.open();
  const rejected = assert.rejects(browser.send("Runtime.evaluate"));
  browser.close();
  browser.close();
  fake.child.emit("exit", 0, null);
  fake.socket.disconnect();
  await within(rejected);
  await within(assert.rejects(browser.send("Page.navigate")));
  assert.equal(fake.child.kills, 1);
  assert.equal(fake.socket.closes, 1);
  assert.equal(fake.socket.sent.length, 1);
});

test("a send failure rejects that command without breaking later commands", async (t) => {
  const fake = fakeChrome(t);
  const browser = await fake.open();
  const cyclic = {};
  cyclic.self = cyclic;
  await assert.rejects(browser.send("Runtime.evaluate", cyclic), /circular/i);
  const sender = t.mock.method(fake.socket, "send", () => { throw new Error("send failed"); });
  await assert.rejects(browser.send("Runtime.evaluate"), /send failed/);
  sender.mock.restore();
  const command = browser.send("Runtime.evaluate", { expression: "42" });
  fake.socket.reply({ id: fake.socket.sent.at(-1).id, result: { value: 42 } });
  assert.deepEqual(await within(command), { value: 42 });
});

test("a closing socket rejects commands before its close event arrives", async (t) => {
  const fake = fakeChrome(t);
  const browser = await fake.open();
  fake.socket.readyState = WebSocket.CLOSING;
  await within(assert.rejects(browser.send("Runtime.evaluate"), /not open/));
  assert.equal(fake.socket.sent.length, 0);
  assert.equal(fake.child.kills, 1);
});

test("a failed socket close still stops Chrome and preserves the original failure", async (t) => {
  const fake = fakeChrome(t);
  const browser = await fake.open();
  const failure = new Error("transport failed");
  const rejected = assert.rejects(browser.send("Runtime.evaluate"), (error) => error === failure);
  t.mock.method(fake.socket, "close", () => { throw new Error("close failed"); });
  fake.socket.onerror({ error: failure });
  await within(rejected);
  assert.equal(fake.child.kills, 1);
});
