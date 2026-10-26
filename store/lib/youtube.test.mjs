// node --test store/ — the promo video's upload to YouTube (youtube.mjs).

import assert from "node:assert/strict";
import { test } from "node:test";
import { CHUNK, nextByte, playProblem, upload, videoResource } from "./youtube.mjs";

const reply = (status, { headers = {}, json } = {}) => ({
  status, ok: status >= 200 && status < 300,
  headers: new Headers(headers), json: async () => json, text: async () => JSON.stringify(json ?? {}),
});

/** A YouTube upload session that holds what it is sent, and fails the [failing]th chunk once. */
function fakeYouTube(total, { failing = -1 } = {}) {
  let held = 0;
  let chunks = 0;
  const ranges = [];
  const put = async (url, { method, headers }) => {
    if (method === "POST") return reply(200, { headers: { location: "https://upload/session" } });
    const range = headers["Content-Range"];
    ranges.push(range);
    if (range.startsWith("bytes */")) return reply(308, held ? { headers: { range: `bytes=0-${held - 1}` } } : {});
    if (chunks++ === failing) throw new Error("connection reset");
    const [, from, to] = /bytes (\d+)-(\d+)\//.exec(range).map(Number);
    assert.equal(from, held, "a chunk starts where YouTube's copy ends");
    held = to + 1;
    return held === total ? reply(201, { json: { id: "abcdefghijk" } }) : reply(308, { headers: { range: `bytes=0-${held - 1}` } });
  };
  return { put, ranges };
}

const quiet = { log: () => {}, wait: async () => {} };

test("a video is sent in chunks, each from where YouTube's copy ends", async () => {
  const youtube = fakeYouTube(CHUNK * 2 + 5);
  const video = await upload("token", Buffer.alloc(CHUNK * 2 + 5), {}, { put: youtube.put, ...quiet });
  assert.equal(video.id, "abcdefghijk");
  assert.deepEqual(youtube.ranges, [
    `bytes 0-${CHUNK - 1}/${CHUNK * 2 + 5}`, `bytes ${CHUNK}-${CHUNK * 2 - 1}/${CHUNK * 2 + 5}`,
    `bytes ${CHUNK * 2}-${CHUNK * 2 + 4}/${CHUNK * 2 + 5}`,
  ]);
});

test("a chunk lost on the way is resumed from what YouTube holds", async () => {
  const youtube = fakeYouTube(CHUNK * 2, { failing: 1 });
  const video = await upload("token", Buffer.alloc(CHUNK * 2), {}, { put: youtube.put, ...quiet });
  assert.equal(video.id, "abcdefghijk");
  assert.deepEqual(youtube.ranges.slice(1), [`bytes ${CHUNK}-${CHUNK * 2 - 1}/${CHUNK * 2}`, `bytes */${CHUNK * 2}`, `bytes ${CHUNK}-${CHUNK * 2 - 1}/${CHUNK * 2}`]);
});

test("YouTube's Range header says where to carry on", () => {
  assert.equal(nextByte("bytes=0-262143"), 262144);
  assert.equal(nextByte(null), 0);
});

test("the video is unlisted, embeddable and not for kids, titled within YouTube's 100 characters", () => {
  const resource = videoResource("ru-RU", { title: "Klas", tagline: "т".repeat(120), shortDescription: "Short" });
  assert.equal([...resource.snippet.title].length, 100);
  assert.ok(resource.snippet.title.startsWith("Klas — "));
  assert.equal(resource.snippet.defaultLanguage, "ru");
  assert.deepEqual(
    [resource.status.privacyStatus, resource.status.embeddable, resource.status.selfDeclaredMadeForKids],
    ["unlisted", true, false],
  );
});

test("Play's needs are checked on the uploaded video", () => {
  assert.equal(playProblem({ uploadStatus: "uploaded", privacyStatus: "unlisted", embeddable: true }), null);
  assert.match(playProblem({ uploadStatus: "uploaded", privacyStatus: "private", embeddable: true }), /private/);
  assert.match(playProblem({ uploadStatus: "uploaded", privacyStatus: "unlisted", embeddable: false }), /embedded/);
  assert.match(playProblem({ uploadStatus: "rejected", rejectionReason: "duplicate" }), /rejected the video \(duplicate\)/);
});
