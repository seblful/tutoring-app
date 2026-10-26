// Each language's words for the store, kept in store/text/<locale>/ beside its Play listing texts:
// captions.json holds the brand line, the store button's words and every caption, keyed by the shot they
// stand over in listing.json (store/render.mjs, store/video.mjs, store/youtube.mjs).

import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

/** The languages the listing and video are drawn in: every store/text/<locale>/ with a captions.json. */
export function languages(store) {
  const text = join(store, "text");
  return readdirSync(text, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && existsSync(join(text, entry.name, "captions.json")))
    .map((entry) => entry.name)
    .sort();
}

/**
 * [locale]'s words, checked against [listing]: a tagline, the store button's words, and a caption with a
 * title and subtitle for every shot the listing and the video show.
 */
export function readWords(store, locale, listing) {
  const file = join(store, "text", locale, "captions.json");
  if (!existsSync(file)) throw new Error(`${locale}: no ${file}`);
  const words = JSON.parse(readFileSync(file, "utf8"));
  for (const field of ["tagline", "cta"]) {
    if (typeof words[field] !== "string" || !words[field].trim()) throw new Error(`${file}: no «${field}»`);
  }
  for (const entry of [...listing.screenshots, ...(listing.filmOnly ?? [])]) {
    const caption = words.captions?.[entry.shot];
    for (const copy of [caption, caption?.film].filter((c, i) => i === 0 || c)) {
      if (!copy?.title || !copy?.subtitle) throw new Error(`${file}: no title and subtitle for «${entry.shot}»`);
    }
  }
  return words;
}

/** Which way [locale]'s words run, for an element whose text is typed in and so cannot tell from its own. */
export function direction(locale) {
  return new Intl.Locale(locale).getTextInfo?.().direction ?? new Intl.Locale(locale).textInfo?.direction ?? "ltr";
}
