// node --test store/ — the listing read from the repository and sent to Play (play.mjs).

import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { publishListing, readListing } from "./play.mjs";

/** A store folder: [locales] with words, and graphics for those in [drawn]. */
function store(t, locales, { drawn = [], video = {}, words = {} } = {}) {
  const dir = mkdtempSync(join(tmpdir(), "klas-store-"));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  for (const locale of locales) {
    const text = join(dir, "text", locale);
    mkdirSync(text, { recursive: true });
    writeFileSync(join(text, "title.txt"), `${words.title ?? "Klas"}\n`);
    writeFileSync(join(text, "short_description.txt"), `Short ${locale}\n`);
    writeFileSync(join(text, "full_description.txt"), `Full ${locale}\n`);
    if (video[locale]) writeFileSync(join(text, "video.txt"), `${video[locale]}\n`);
  }
  for (const locale of drawn) {
    const shots = join(dir, "listing", locale, "phoneScreenshots");
    mkdirSync(shots, { recursive: true });
    writeFileSync(join(dir, "listing", locale, "icon.png"), `icon ${locale}`);
    writeFileSync(join(dir, "listing", locale, "featureGraphic.png"), `feature ${locale}`);
    for (const name of ["02-people.png", "01-today.png"]) writeFileSync(join(shots, name), `${name} ${locale}`);
  }
  return dir;
}

/** An in-memory Play that records what was sent; [listings] and [images] are what it already shows. */
function fakePlay({ listings = {}, images = {}, commitRefuses = false } = {}) {
  const calls = [];
  const key = (locale, type) => `${locale}/${type}`;
  const api = {
    calls,
    insertEdit: async () => "edit1",
    getListing: async (_, locale) => listings[locale] ?? null,
    putListing: async (_, locale, wanted) => { calls.push(["put", locale, wanted]); },
    listImages: async (_, locale, type) => images[key(locale, type)] ?? [],
    deleteAllImages: async (_, locale, type) => { calls.push(["deleteall", locale, type]); },
    uploadImage: async (_, locale, type, png) => { calls.push(["upload", locale, type, png.toString()]); },
    validate: async () => { calls.push(["validate"]); },
    commit: async (_, notForReview) => {
      calls.push(["commit", notForReview]);
      if (commitRefuses && !notForReview) throw new Error("Please set the query parameter changesNotSentForReview to true.");
    },
    deleteEdit: async () => { calls.push(["delete"]); },
  };
  return api;
}

const hashed = (...contents) => contents.map((content) => ({ sha256: createHash("sha256").update(content).digest("hex") }));
const quiet = { log: () => {} };
const VIDEO = "https://www.youtube.com/watch?v=abcdefghijk";

test("every language gets its words; only drawn ones carry graphics, screenshots in name order", (t) => {
  const listing = readListing(store(t, ["en-US", "de-DE"], { drawn: ["en-US"], video: { "en-US": VIDEO } }));
  const en = listing.find((l) => l.locale === "en-US");
  const de = listing.find((l) => l.locale === "de-DE");
  assert.equal(en.shortDescription, "Short en-US");
  assert.equal(en.video, VIDEO);
  assert.deepEqual(en.graphics.phoneScreenshots.map((p) => p.split(/[\\/]/).pop()), ["01-today.png", "02-people.png"]);
  assert.equal(de.graphics, null);
  assert.equal(de.video, null);
});

test("words longer than Play allows are refused before anything is sent", (t) => {
  assert.throws(() => readListing(store(t, ["en-US"], { words: { title: "x".repeat(31) } })), /31 characters; Play allows 30/);
});

test("a video link Play would not take is refused", (t) => {
  assert.throws(() => readListing(store(t, ["en-US"], { video: { "en-US": "https://youtu.be/abcdefghijk" } })), /video's own link/);
});

test("an unchanged listing sends nothing and throws the edit away", async (t) => {
  const listing = readListing(store(t, ["en-US"], { drawn: ["en-US"], video: { "en-US": VIDEO } }));
  const play = fakePlay({
    listings: { "en-US": { title: "Klas", shortDescription: "Short en-US", fullDescription: "Full en-US", video: VIDEO } },
    images: {
      "en-US/icon": hashed("icon en-US"),
      "en-US/featureGraphic": hashed("feature en-US"),
      "en-US/phoneScreenshots": hashed("01-today.png en-US", "02-people.png en-US"),
    },
  });
  assert.deepEqual(await publishListing(play, listing, quiet), []);
  assert.deepEqual(play.calls, [["delete"]]);
});

test("changed words and a changed image type are sent, the type replaced whole, and committed", async (t) => {
  const listing = readListing(store(t, ["en-US"], { drawn: ["en-US"] }));
  const play = fakePlay({
    listings: { "en-US": { title: "Old", shortDescription: "Short en-US", fullDescription: "Full en-US", video: VIDEO } },
    images: {
      "en-US/icon": hashed("icon en-US"),
      "en-US/featureGraphic": hashed("feature en-US"),
      "en-US/phoneScreenshots": hashed("02-people.png en-US", "01-today.png en-US"), // the order changed
    },
  });
  await publishListing(play, listing, quiet);
  assert.deepEqual(play.calls, [
    // No video.txt: the video Play shows is kept, never cleared.
    ["put", "en-US", { language: "en-US", title: "Klas", shortDescription: "Short en-US", fullDescription: "Full en-US", video: VIDEO }],
    ["deleteall", "en-US", "phoneScreenshots"],
    ["upload", "en-US", "phoneScreenshots", "01-today.png en-US"],
    ["upload", "en-US", "phoneScreenshots", "02-people.png en-US"],
    ["commit", false],
  ]);
});

test("a language Play has no listing for yet is added", async (t) => {
  const listing = readListing(store(t, ["kk"]));
  const play = fakePlay();
  await publishListing(play, listing, quiet);
  assert.deepEqual(play.calls[0], ["put", "kk", { language: "kk", title: "Klas", shortDescription: "Short kk", fullDescription: "Full kk", video: "" }]);
});

test("validating checks the edit with Play and throws it away", async (t) => {
  const play = fakePlay();
  await publishListing(play, readListing(store(t, ["en-US"])), { ...quiet, validate: true });
  assert.deepEqual(play.calls.slice(-2), [["validate"], ["delete"]]);
});

test("when Play will not send the edit for review itself, it is saved for sending from the Console", async (t) => {
  const play = fakePlay({ commitRefuses: true });
  await publishListing(play, readListing(store(t, ["en-US"])), quiet);
  assert.deepEqual(play.calls.slice(-2), [["commit", false], ["commit", true]]);
});

test("a failure part-way throws the edit away", async (t) => {
  const play = fakePlay();
  play.putListing = async () => { throw new Error("quota"); };
  await assert.rejects(publishListing(play, readListing(store(t, ["en-US"])), quiet), /quota/);
  assert.deepEqual(play.calls, [["delete"]]);
});

test("the real listing in the repository is one Play would take", () => {
  const listing = readListing(join(import.meta.dirname, ".."));
  assert.ok(listing.length >= 2);
  for (const graphics of listing.map((l) => l.graphics).filter(Boolean)) {
    for (const path of Object.values(graphics).flat()) assert.ok(readFileSync(path).length > 0, path);
  }
});
