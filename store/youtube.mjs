#!/usr/bin/env node
// Uploads each language's promo video (node store/video.mjs, the wide one) to YouTube, unlisted, and writes its
// link to store/text/<locale>/video.txt, where store/publish.mjs sends it to Play. Run on your computer:
// YouTube needs the channel owner signed in, in a browser.
//
//   node store/youtube.mjs                    # every language without a video.txt
//   node store/youtube.mjs --locale ru-RU     # just one
//   node store/youtube.mjs --replace          # upload again over existing links (the old videos stay)
//
// YOUTUBE_CLIENT=<path> overrides the OAuth client file, by default ~/keystores/youtube-client.json; the
// sign-in is kept beside it in youtube-token.json. See docs/store-listing.md.

import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { browserSignIn, refreshedToken } from "./lib/google.mjs";
import { languages, readWords } from "./lib/words.mjs";
import { playProblem, upload, videoResource, videoStatus, watchUrl } from "./lib/youtube.mjs";

const STORE = dirname(fileURLToPath(import.meta.url));
const VIDEOS = join(STORE, "build", "video");
const SCOPES = ["https://www.googleapis.com/auth/youtube.upload", "https://www.googleapis.com/auth/youtube.readonly"];
const CLIENT_FILE = process.env.YOUTUBE_CLIENT ?? join(homedir(), "keystores", "youtube-client.json");
const TOKEN_FILE = join(dirname(CLIENT_FILE), "youtube-token.json");

const args = process.argv.slice(2);
const only = args.includes("--locale") ? args[args.indexOf("--locale") + 1] : null;
const replace = args.includes("--replace");
const listing = JSON.parse(readFileSync(join(STORE, "listing.json"), "utf8"));
const locales = languages(STORE).filter((locale) => !only || locale === only);
if (!locales.length) fail(`No store/text/${only}/captions.json`);
/** The wide video, the one Play shows. */
const videoFile = (locale) => join(VIDEOS, `klas-${locale}.mp4`);

const todo = locales.filter((locale) => {
  const link = join(STORE, "text", locale, "video.txt");
  if (existsSync(link) && !replace) {
    console.log(`${locale}: already ${readFileSync(link, "utf8").trim()} (--replace to upload again)`);
    return false;
  }
  if (!existsSync(videoFile(locale))) fail(`${locale}: no ${videoFile(locale)}; run node store/video.mjs`);
  return true;
});
if (!todo.length) process.exit(0);

const token = await signIn().catch((error) => fail(error.message));
for (const locale of todo) {
  const text = (file) => readFileSync(join(STORE, "text", locale, file), "utf8").trim();
  const resource = videoResource(locale, {
    title: text("title.txt"), tagline: readWords(STORE, locale, listing).tagline, shortDescription: text("short_description.txt"),
  });
  const file = videoFile(locale);
  console.log(`${locale}: uploading ${file} as «${resource.snippet.title}»`);
  const video = await upload(token, readFileSync(file), resource).catch((error) => fail(`${locale}: ${error.message}`));
  const status = await videoStatus(token, video.id).catch((error) => fail(`${locale}: ${watchUrl(video.id)} uploaded, but ${error.message}`));
  const problem = playProblem(status);
  if (problem) fail(`${locale}: ${watchUrl(video.id)} — ${problem}`);
  const link = join(STORE, "text", locale, "video.txt");
  const old = existsSync(link) ? readFileSync(link, "utf8").trim() : null;
  writeFileSync(link, `${watchUrl(video.id)}\n`);
  console.log(`${locale}: ${watchUrl(video.id)} → ${link}`);
  if (old) console.log(`${locale}: the previous video, ${old}, is still on the channel; delete it in YouTube Studio once Play shows the new one`);
}
console.log("Play needs the video without ads: if the channel is monetised, turn ads off for it in YouTube Studio.");
console.log("Commit the video.txt files, then send the listing: node store/publish.mjs, or the «Store listing» workflow.");

/** An access token: from the kept sign-in, or from signing in again in the browser. */
async function signIn() {
  if (!existsSync(CLIENT_FILE)) {
    fail(`No OAuth client at ${CLIENT_FILE}: create a Desktop app client in Google Cloud and save its JSON there (docs/store-listing.md)`);
  }
  const file = JSON.parse(readFileSync(CLIENT_FILE, "utf8"));
  const client = file.installed ?? file.web;
  if (!client?.client_id) fail(`${CLIENT_FILE} is not an OAuth client's JSON`);
  if (existsSync(TOKEN_FILE)) {
    const kept = await refreshedToken(client, JSON.parse(readFileSync(TOKEN_FILE, "utf8")).refresh_token);
    if (kept) return kept;
    console.log("The kept YouTube sign-in has expired.");
  }
  const token = await browserSignIn(client, SCOPES);
  if (token.refresh_token) {
    mkdirSync(dirname(TOKEN_FILE), { recursive: true });
    writeFileSync(TOKEN_FILE, JSON.stringify({ refresh_token: token.refresh_token }));
    try { chmodSync(TOKEN_FILE, 0o600); } catch {
      // Windows keeps a file in the profile to its owner already.
    }
  }
  return token.access_token;
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
