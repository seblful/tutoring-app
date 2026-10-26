// Uploading the promo video to YouTube through the Data API, resumably, and checking the result is a video
// Play can show (store/youtube.mjs).

import { call } from "./google.mjs";

const UPLOAD_URL = "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status";
const VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos";
/** YouTube's limits: a title in characters, a description in bytes. */
const TITLE_MAX = 100;
const DESCRIPTION_MAX = 5000;
/** Education. */
const CATEGORY = "27";
/** A chunk of the upload; YouTube wants multiples of 256 KiB. */
export const CHUNK = 32 * 256 * 1024;
const ATTEMPTS = 5;

/**
 * The video's title, description and settings for [locale]: unlisted — Play takes unlisted or public, and
 * the video is for the listing — embeddable, and not made for kids, which would turn embedding off.
 */
export function videoResource(locale, { title, tagline, shortDescription }) {
  let words = `${title} — ${tagline}`;
  if ([...words].length > TITLE_MAX) words = [...words].slice(0, TITLE_MAX - 1).join("") + "…";
  const description = Buffer.from(shortDescription).length <= DESCRIPTION_MAX ? shortDescription : title;
  return {
    snippet: { title: words, description, categoryId: CATEGORY, defaultLanguage: locale.split("-")[0] },
    status: {
      privacyStatus: "unlisted", embeddable: true, selfDeclaredMadeForKids: false, containsSyntheticMedia: false,
      license: "youtube",
    },
  };
}

export const watchUrl = (id) => `https://www.youtube.com/watch?v=${id}`;

/**
 * Why Play cannot use a video with this [status], or null when it can. Play shows only public or unlisted
 * videos that may be embedded; YouTube keeps a video private when the uploading app needs Google's
 * compliance audit, whatever the upload asked for.
 */
export function playProblem(status) {
  if (!status) return "YouTube does not show the uploaded video";
  if (["failed", "rejected", "deleted"].includes(status.uploadStatus)) {
    return `YouTube ${status.uploadStatus} the video (${status.failureReason ?? status.rejectionReason ?? "no reason given"})`;
  }
  if (status.privacyStatus === "private") {
    return "YouTube made the video private, which Play does not show. An unverified Google Cloud app's uploads can be " +
      "locked private until the app passes YouTube's API audit; if YouTube Studio lets you, make it unlisted and " +
      "put its link in video.txt yourself";
  }
  if (status.embeddable === false) return "The video cannot be embedded, which Play needs; allow embedding in YouTube Studio";
  return null;
}

/** The byte after the last one YouTube holds, from a resumable upload's `Range: bytes=0-N`. */
export function nextByte(range) {
  const end = /bytes=0-(\d+)/.exec(range ?? "")?.[1];
  return end === undefined ? 0 : Number(end) + 1;
}

/**
 * Uploads [video] (an MP4's bytes) as [resource] with an access [token]; returns YouTube's video. Sent in
 * [CHUNK]s; a chunk that fails is resumed from what YouTube says it holds, up to [ATTEMPTS] times in a row.
 * [put] is fetch, swappable in tests.
 */
export async function upload(token, video, resource, { put = fetch, log = console.log, wait = sleep } = {}) {
  const start = await put(UPLOAD_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`, "Content-Type": "application/json; charset=utf-8",
      "X-Upload-Content-Type": "video/mp4", "X-Upload-Content-Length": String(video.length),
    },
    body: JSON.stringify(resource),
  });
  if (!start.ok) throw new Error(`YouTube refused the upload (${start.status}): ${await start.text()}`);
  const session = start.headers.get("location");
  const total = video.length;
  let from = 0;
  let failures = 0;
  while (true) {
    const to = Math.min(from + CHUNK, total);
    let response;
    try {
      response = await put(session, {
        method: "PUT",
        // A 308 here means "carry on", not a redirect.
        redirect: "manual",
        headers: { Authorization: `Bearer ${token}`, "Content-Range": `bytes ${from}-${to - 1}/${total}` },
        body: video.subarray(from, to),
      });
    } catch (error) {
      response = { status: 0, error };
    }
    if (response.status === 200 || response.status === 201) return response.json();
    if (response.status === 308) {
      from = nextByte(response.headers.get("range"));
      failures = 0;
      log(`  ${Math.round((from / total) * 100)}%`);
      continue;
    }
    if (response.status >= 400 && response.status < 500 && response.status !== 429) {
      throw new Error(`YouTube refused a part of the upload (${response.status}): ${await response.text()}`);
    }
    if (++failures >= ATTEMPTS) throw new Error(`The upload failed ${ATTEMPTS} times in a row (${response.error?.message ?? response.status})`);
    await wait(2 ** failures * 1000);
    from = await held(put, session, token, total);
  }
}

/** Asks YouTube how much of the upload it holds, after a failed chunk. */
async function held(put, session, token, total) {
  try {
    const response = await put(session, {
      method: "PUT", redirect: "manual",
      headers: { Authorization: `Bearer ${token}`, "Content-Range": `bytes */${total}` },
    });
    return response.status === 308 ? nextByte(response.headers.get("range")) : 0;
  } catch {
    return 0;
  }
}

/** The video's status as YouTube now reports it. */
export async function videoStatus(token, id) {
  const found = await call(token, "GET", `${VIDEOS_URL}?part=status&id=${id}`);
  return found.items?.[0]?.status;
}

const sleep = (ms) => new Promise((wake) => setTimeout(wake, ms));
