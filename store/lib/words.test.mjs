// node --test store/ — each language's words for the listing and the video (words.mjs).

import assert from "node:assert/strict";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { direction, languages, readWords } from "./words.mjs";

const STORE = join(import.meta.dirname, "..");
const listing = JSON.parse(readFileSync(join(STORE, "listing.json"), "utf8"));
const LISTING = { screenshots: [{ shot: "today" }, { shot: "lesson-sheet" }], filmOnly: [{ shot: "settings-calendar" }] };
const copy = { title: "A\nB", subtitle: "C" };

function store(t, files) {
  const dir = mkdtempSync(join(tmpdir(), "klas-words-"));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  for (const [locale, words] of Object.entries(files)) {
    mkdirSync(join(dir, "text", locale), { recursive: true });
    if (words) writeFileSync(join(dir, "text", locale, "captions.json"), JSON.stringify(words));
  }
  return dir;
}

test("the languages drawn are those with captions", (t) => {
  const dir = store(t, { "de-DE": { tagline: "x", cta: "y", captions: {} }, "fr-FR": null });
  assert.deepEqual(languages(dir), ["de-DE"]);
});

test("a caption the listing or the video needs, or its film wording when it has one, must be there", (t) => {
  const full = { today: copy, "lesson-sheet": copy, "settings-calendar": copy };
  const dir = store(t, {
    good: { tagline: "x", cta: "y", captions: full },
    missing: { tagline: "x", cta: "y", captions: { today: copy, "lesson-sheet": copy } },
    halfFilm: { tagline: "x", cta: "y", captions: { ...full, "lesson-sheet": { ...copy, film: { title: "T" } } } },
    noCta: { tagline: "x", captions: full },
  });
  assert.equal(readWords(dir, "good", LISTING).captions.today.title, "A\nB");
  assert.throws(() => readWords(dir, "missing", LISTING), /«settings-calendar»/);
  assert.throws(() => readWords(dir, "halfFilm", LISTING), /«lesson-sheet»/);
  assert.throws(() => readWords(dir, "noCta", LISTING), /«cta»/);
});

test("every language in the Play listing has its captions and demo tutor", () => {
  const listed = readdirSync(join(STORE, "text")).filter((l) => existsSync(join(STORE, "text", l, "title.txt"))).sort();
  assert.deepEqual(languages(STORE), listed);
  for (const locale of listed) {
    readWords(STORE, locale, listing);
    assert.ok(existsSync(join(STORE, "text", locale, "demo.json")), `${locale}: no demo.json`);
  }
});

test("Arabic is typed right to left, the rest left to right", () => {
  assert.equal(direction("ar"), "rtl");
  assert.deepEqual(["de-DE", "ja-JP", "hi-IN"].map(direction), ["ltr", "ltr", "ltr"]);
});
