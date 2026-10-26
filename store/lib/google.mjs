// Signing in to Google's APIs with Node's own crypto and fetch: a service account for Play (store/publish.mjs),
// and the channel owner in a browser for YouTube (store/youtube.mjs) — no npm dependencies.

import { createHash, randomBytes, sign } from "node:crypto";
import { createServer } from "node:http";
import { spawn } from "node:child_process";

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";

const base64url = (data) => Buffer.from(data).toString("base64url");

/** The signed JWT a service account trades for an access token to [scope]; [now] in seconds. */
export function serviceAccountAssertion(account, scope, now = Math.floor(Date.now() / 1000)) {
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: account.client_email, scope, aud: account.token_uri ?? TOKEN_URL, iat: now, exp: now + 3600,
  }));
  const signature = sign("RSA-SHA256", Buffer.from(`${header}.${claims}`), account.private_key);
  return `${header}.${claims}.${base64url(signature)}`;
}

/** An access token for the service account in [json] (its key file's contents). */
export async function serviceAccountToken(json, scope) {
  let account;
  try {
    account = JSON.parse(json);
  } catch {
    throw new Error("The service account key is not JSON; pass the key file's contents");
  }
  if (!account.client_email || !account.private_key) throw new Error("The service account key has no client_email or private_key");
  const token = await tokenRequest({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion: serviceAccountAssertion(account, scope),
  });
  return token.access_token;
}

/** A PKCE pair: the verifier kept here, the challenge sent with the sign-in. */
export function pkce() {
  const verifier = base64url(randomBytes(32));
  return { verifier, challenge: base64url(createHash("sha256").update(verifier).digest()) };
}

/**
 * Signs the user in to [scopes] in their browser, as a desktop app [client] (`{ client_id, client_secret }`)
 * receiving the answer on a loopback port, and returns the token response — with a refresh token, kept to
 * skip the browser next time.
 */
export async function browserSignIn(client, scopes) {
  const { verifier, challenge } = pkce();
  const state = base64url(randomBytes(16));
  const server = createServer();
  await new Promise((listening) => server.listen(0, "127.0.0.1", listening));
  const redirect = `http://127.0.0.1:${server.address().port}`;
  const url = `${AUTH_URL}?${new URLSearchParams({
    client_id: client.client_id, redirect_uri: redirect, response_type: "code", scope: scopes.join(" "),
    code_challenge: challenge, code_challenge_method: "S256", state, access_type: "offline", prompt: "consent",
  })}`;
  console.log(`Sign in to YouTube in the browser; if it does not open, go to:\n${url}`);
  openBrowser(url);
  try {
    const code = await new Promise((answered, failed) => {
      const timeout = setTimeout(() => failed(new Error("No sign-in within 5 minutes")), 5 * 60_000);
      server.on("request", (request, response) => {
        const query = new URL(request.url, redirect).searchParams;
        if (!query.has("code") && !query.has("error")) return response.writeHead(404).end();
        const ok = query.get("state") === state && query.has("code");
        response.writeHead(200, { "Content-Type": "text/plain; charset=utf-8" })
          .end(ok ? "Signed in. You can close this tab." : `Sign-in failed: ${query.get("error") ?? "state mismatch"}`);
        clearTimeout(timeout);
        if (ok) answered(query.get("code"));
        else failed(new Error(`Sign-in failed: ${query.get("error") ?? "state mismatch"}`));
      });
    });
    return await tokenRequest({
      code, client_id: client.client_id, client_secret: client.client_secret, redirect_uri: redirect,
      grant_type: "authorization_code", code_verifier: verifier,
    });
  } finally {
    server.close();
  }
}

/** A fresh access token from a kept [refreshToken], or null once Google no longer honours it. */
export async function refreshedToken(client, refreshToken) {
  try {
    const token = await tokenRequest({
      client_id: client.client_id, client_secret: client.client_secret, refresh_token: refreshToken,
      grant_type: "refresh_token",
    });
    return token.access_token;
  } catch (error) {
    // An app still in testing has its refresh tokens expire after 7 days; sign in again.
    if (/invalid_grant/.test(error.message)) return null;
    throw error;
  }
}

async function tokenRequest(form) {
  const response = await fetch(TOKEN_URL, { method: "POST", body: new URLSearchParams(form) });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`Google sign-in refused (${response.status}): ${body.error ?? ""} ${body.error_description ?? ""}`.trim());
  return body;
}

function openBrowser(url) {
  // rundll32 takes the URL whole; `cmd /c start` would split it at its `&`s.
  const [command, args] = process.platform === "win32" ? ["rundll32", ["url.dll,FileProtocolHandler", url]]
    : process.platform === "darwin" ? ["open", [url]] : ["xdg-open", [url]];
  spawn(command, args, { stdio: "ignore", detached: true }).on("error", () => {}).unref();
}

/**
 * Calls a Google REST API with [token]: JSON in and out, a readable error with Google's own message, and
 * null for a 404 when [missing] is allowed.
 */
export async function call(token, method, url, { json, body, type, missing = false } = {}) {
  const headers = { Authorization: `Bearer ${token}` };
  if (json !== undefined) headers["Content-Type"] = "application/json; charset=utf-8";
  else if (type) headers["Content-Type"] = type;
  const response = await fetch(url, { method, headers, body: json !== undefined ? JSON.stringify(json) : body });
  if (missing && response.status === 404) return null;
  const text = await response.text();
  if (!response.ok) {
    let message = text;
    try { message = JSON.parse(text).error?.message ?? text; } catch {
      // Not JSON: Google's error page as it came.
    }
    throw new GoogleError(response.status, `${method} ${url.split("?")[0]} → ${response.status}: ${message}`);
  }
  return text ? JSON.parse(text) : null;
}

export class GoogleError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
