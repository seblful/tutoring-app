// node --test store/ — signing in to Google (google.mjs).

import assert from "node:assert/strict";
import { createHash, generateKeyPairSync, verify } from "node:crypto";
import { test } from "node:test";
import { pkce, serviceAccountAssertion } from "./google.mjs";

test("a service account's assertion is signed by its key and asks for the scope, for an hour", () => {
  const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const account = { client_email: "ci@klas.iam.gserviceaccount.com", private_key: privateKey.export({ type: "pkcs8", format: "pem" }) };
  const [header, claims, signature] = serviceAccountAssertion(account, "scope-x", 1000).split(".");
  const decoded = (part) => JSON.parse(Buffer.from(part, "base64url").toString());
  assert.deepEqual(decoded(header), { alg: "RS256", typ: "JWT" });
  assert.deepEqual(decoded(claims), {
    iss: "ci@klas.iam.gserviceaccount.com", scope: "scope-x", aud: "https://oauth2.googleapis.com/token", iat: 1000, exp: 4600,
  });
  assert.ok(verify("RSA-SHA256", Buffer.from(`${header}.${claims}`), publicKey, Buffer.from(signature, "base64url")));
});

test("a PKCE challenge is the verifier's SHA-256, base64url", () => {
  const { verifier, challenge } = pkce();
  assert.match(verifier, /^[\w-]{43}$/);
  assert.equal(challenge, createHash("sha256").update(verifier).digest("base64url"));
});
