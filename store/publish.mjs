#!/usr/bin/env node
// Sends the store listing to Google Play: every language's words from store/text/<locale>/, its YouTube
// video where store/text/<locale>/video.txt names one, and the graphics render.mjs drew in
// store/listing/<locale>/. Only what differs from Play's listing is sent; graphics are uploaded per type,
// all of a type at once, when any of them changed.
//
//   PLAY_SERVICE_ACCOUNT_JSON="$(cat key.json)" node store/publish.mjs              # send
//   PLAY_SERVICE_ACCOUNT_JSON="$(cat key.json)" node store/publish.mjs --validate   # check only
//
// CI runs it from .github/workflows/listing.yml. See docs/store-listing.md.

import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { serviceAccountToken } from "./lib/google.mjs";
import { playApi, publishListing, readListing } from "./lib/play.mjs";

const STORE = dirname(fileURLToPath(import.meta.url));
const PACKAGE = "com.seblful.klas";

const key = process.env.PLAY_SERVICE_ACCOUNT_JSON;
if (!key) {
  console.error("PLAY_SERVICE_ACCOUNT_JSON is not set: the Play service account key's contents (docs/releasing.md)");
  process.exit(1);
}
try {
  const listing = readListing(STORE);
  console.log(`${listing.length} languages; graphics for ${listing.filter((l) => l.graphics).map((l) => l.locale).join(", ")}`);
  const token = await serviceAccountToken(key, "https://www.googleapis.com/auth/androidpublisher");
  await publishListing(playApi(token, PACKAGE), listing, { validate: process.argv.includes("--validate") });
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
