// The Play store listing as the repository holds it, and sending it to Play through the Publishing API —
// only what differs, so an unchanged listing is not put through review again (store/publish.mjs).

import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { call } from "./google.mjs";

/** Play's limits on the listing's words, in characters. */
export const TEXT_LIMITS = { title: 30, shortDescription: 80, fullDescription: 4000 };
const TEXT_FILES = { title: "title.txt", shortDescription: "short_description.txt", fullDescription: "full_description.txt" };
/** The one form of YouTube link Play takes: the video's own, with no playlist or time. */
const VIDEO_URL = /^https:\/\/www\.youtube\.com\/watch\?v=[\w-]{11}$/;
/** The graphics per language, in the order uploaded, by the API's image types. */
export const IMAGE_TYPES = ["icon", "featureGraphic", "phoneScreenshots"];
const MAX_SCREENSHOTS = 8;

/**
 * Every language's listing: words (and a video, where `video.txt` names one) from `text/<locale>/`, and
 * graphics from `listing/<locale>/` for the languages render.mjs draws them for — `graphics` is null for the
 * rest, whose graphics Play takes from the default language. Throws on anything Play would refuse.
 */
export function readListing(store) {
  const textDir = join(store, "text");
  return readdirSync(textDir, { withFileTypes: true }).filter((entry) => entry.isDirectory()).map(({ name: locale }) => {
    const texts = {};
    for (const [field, file] of Object.entries(TEXT_FILES)) {
      const source = join(textDir, locale, file);
      if (!existsSync(source)) throw new Error(`${locale}: missing ${source}`);
      const words = readFileSync(source, "utf8").trim();
      if (!words) throw new Error(`${locale}/${file} is empty`);
      const length = [...words].length;
      if (length > TEXT_LIMITS[field]) throw new Error(`${locale}/${file} is ${length} characters; Play allows ${TEXT_LIMITS[field]}`);
      texts[field] = words;
    }
    const videoFile = join(textDir, locale, "video.txt");
    const video = existsSync(videoFile) ? readFileSync(videoFile, "utf8").trim() : null;
    if (video !== null && !VIDEO_URL.test(video)) {
      throw new Error(`${locale}/video.txt is "${video}"; Play takes a video's own link, https://www.youtube.com/watch?v=<id>`);
    }
    return { locale, ...texts, video, graphics: readGraphics(join(store, "listing", locale), locale) };
  });
}

function readGraphics(dir, locale) {
  if (!existsSync(dir)) return null;
  const file = (name) => {
    const path = join(dir, name);
    if (!existsSync(path)) throw new Error(`${locale}: missing ${path}; run node store/render.mjs`);
    return path;
  };
  const shotsDir = join(dir, "phoneScreenshots");
  const shots = existsSync(shotsDir) ? readdirSync(shotsDir).filter((name) => name.endsWith(".png")).sort() : [];
  if (shots.length < 2 || shots.length > MAX_SCREENSHOTS) {
    throw new Error(`${locale}: ${shots.length} phone screenshots; Play takes 2 to ${MAX_SCREENSHOTS}`);
  }
  return {
    icon: [file("icon.png")],
    featureGraphic: [file("featureGraphic.png")],
    phoneScreenshots: shots.map((name) => join(shotsDir, name)),
  };
}

/**
 * The listing Play should show for a language: the repository's words, and its video — or, where the
 * repository names none, the one Play already shows, which this never removes.
 */
export function wantedListing(local, remote) {
  return {
    language: local.locale,
    title: local.title,
    shortDescription: local.shortDescription,
    fullDescription: local.fullDescription,
    video: local.video ?? remote?.video ?? "",
  };
}

const sameListing = (a, b) => b !== null &&
  ["title", "shortDescription", "fullDescription", "video"].every((field) => (a[field] ?? "") === (b[field] ?? "").trim());

const sha256 = (path) => createHash("sha256").update(readFileSync(path)).digest("hex");

/** Whether Play's [remote] images are [paths], in order, by content. */
export function sameImages(paths, remote) {
  return paths.length === remote.length && paths.every((path, i) => sha256(path) === remote[i].sha256?.toLowerCase());
}

/**
 * Sends [listing] to Play in one edit through [api] (see [playApi]): words where they differ, and a type's
 * images, all replaced, where any differ. With [validate] Play checks the edit and it is thrown away.
 * Returns what changed; an edit with no change is thrown away rather than committed.
 */
export async function publishListing(api, listing, { validate = false, log = console.log } = {}) {
  const edit = await api.insertEdit();
  const changed = [];
  try {
    for (const [i, local] of listing.entries()) {
      const before = changed.length;
      const remote = await api.getListing(edit, local.locale);
      const wanted = wantedListing(local, remote);
      if (!sameListing(wanted, remote)) {
        await api.putListing(edit, local.locale, wanted);
        changed.push(`${local.locale} words${wanted.video !== (remote?.video ?? "") ? " and video" : ""}`);
      }
      for (const type of local.graphics ? IMAGE_TYPES : []) {
        const paths = local.graphics[type];
        if (sameImages(paths, await api.listImages(edit, local.locale, type))) continue;
        await api.deleteAllImages(edit, local.locale, type);
        for (const path of paths) await api.uploadImage(edit, local.locale, type, readFileSync(path));
        changed.push(`${local.locale} ${type}`);
      }
      // Uploading every language's graphics takes many minutes: a line per language shows it moving.
      const sent = changed.slice(before).map((what) => what.slice(local.locale.length + 1));
      log(`${i + 1}/${listing.length} ${local.locale}: ${sent.length ? sent.join(", ") : "unchanged"}`);
    }
    if (!changed.length) {
      log("Play already shows this listing; nothing to send.");
      await api.deleteEdit(edit);
      return changed;
    }
    log(`Changed: ${changed.join(", ")}`);
    if (validate) {
      await api.validate(edit);
      log("Play accepts these changes; nothing was published (--validate).");
      await api.deleteEdit(edit);
      return changed;
    }
    await commit(api, edit, log);
    return changed;
  } catch (error) {
    await api.deleteEdit(edit).catch(() => {});
    throw error;
  }
}

async function commit(api, edit, log) {
  try {
    await api.commit(edit, false);
    log("Sent to Play; the listing changes go through review.");
  } catch (error) {
    // With managed publishing, or changes already waiting, Play will not send an edit for review itself.
    if (!/changesNotSentForReview/.test(error.message)) throw error;
    await api.commit(edit, true);
    log("Saved in Play, not sent for review: send it from Play Console → Publishing overview.");
  }
}

/** The Publishing API calls [publishListing] makes, for [packageName] with an access [token]. */
export function playApi(token, packageName) {
  const base = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/edits`;
  const upload = `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${packageName}/edits`;
  const listing = (edit, locale) => `${base}/${edit}/listings/${encodeURIComponent(locale)}`;
  return {
    insertEdit: async () => (await call(token, "POST", base, { json: {} })).id,
    getListing: (edit, locale) => call(token, "GET", listing(edit, locale), { missing: true }),
    putListing: (edit, locale, wanted) => call(token, "PUT", listing(edit, locale), { json: wanted }),
    listImages: async (edit, locale, type) => (await call(token, "GET", `${listing(edit, locale)}/${type}`))?.images ?? [],
    deleteAllImages: (edit, locale, type) => call(token, "DELETE", `${listing(edit, locale)}/${type}`),
    uploadImage: (edit, locale, type, png) => call(token, "POST",
      `${upload}/${edit}/listings/${encodeURIComponent(locale)}/${type}?uploadType=media`, { body: png, type: "image/png" }),
    validate: (edit) => call(token, "POST", `${base}/${edit}:validate`),
    commit: (edit, notForReview) => call(token, "POST", `${base}/${edit}:commit${notForReview ? "?changesNotSentForReview=true" : ""}`),
    deleteEdit: (edit) => call(token, "DELETE", `${base}/${edit}`),
  };
}
