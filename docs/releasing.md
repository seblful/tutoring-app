# Releasing

How to build a signed release of Klas, install it on your own phone, and publish it to Google Play.

## Set up signing (once)

The `release` build is **minified and resource-shrunk (R8)** and is signed when a `keystore.properties`
file is present (it is gitignored). Without it, the release build is left unsigned so debug builds, CI,
and contributors are unaffected. With Play App Signing, Google holds the real release key; you sign with
an *upload* key.

1. **Generate an upload key** in `~/keystores/`, outside every repo, named after the app so it can't be
   mixed up with other apps' keys:
   ```bash
   mkdir -p ~/keystores
   keytool -genkeypair -v -keystore ~/keystores/klas-upload-v2.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   `-validity` is in days and must not be shortened: Play rejects a certificate that expires before
   **22 October 2033** ("signed with a certificate that expires too soon"), and a key can't be
   lengthened afterwards. Check any key with `keytool -list -v -keystore <file> -alias upload` (the
   *Valid from … until* line); the release workflow checks it too.

   Back up the keystore file and its passwords *off this machine* (e.g. a password-manager attachment).
   Losing the upload key means a manual reset with Google; losing it before Play App Signing is enabled
   would block all future updates.

2. **Create `keystore.properties`** in the repo root from the template:
   ```bash
   cp keystore.properties.example keystore.properties
   ```
   ```properties
   storeFile=C:/Users/<you>/keystores/klas-upload-v2.jks
   storePassword=<your keystore password>
   keyAlias=upload
   keyPassword=<your key password>
   ```
   Use an absolute path: Gradle doesn't expand `~`, and relative paths resolve against the repo root.
   If you pressed Enter at keytool's key-password prompt, both passwords are the same value.

> ⚠️ **Verify the backup feature on a release build before shipping.** R8 building successfully does
> not prove the reflection-driven backup export/import survives minification. Test the backup
> round-trip on a real device on a release build. R8 keep rules for `@Serializable` types live in
> `app/proguard-rules.pro`. If unsure for a first beta, temporarily set `isMinifyEnabled = false` in the
> `release` block and enable it later once verified.

## Install on your own phone

1. **Build:**
   ```bash
   ./gradlew :app:assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk` (a `-unsigned` suffix means signing was
   skipped — check `keystore.properties`).

2. **Install:**
   ```bash
   adb install "app/build/outputs/apk/release/app-release.apk"
   ```
   Or copy the APK to the phone and tap it (allow "install from unknown sources").

A sideloaded APK is signed with *your* upload key, not Google's. A later Play Store install of the same
app won't update over it — uninstall the sideloaded copy first.

## Publish to Google Play

### Before the first upload

**New personal accounts** must run a **closed test with 12+ testers for 14 continuous days** before
they can publish to production. Start recruiting testers early — this is the usual bottleneck.

### Build the bundle by hand (the first upload only)

Play requires an **App Bundle** (`.aab`), not an APK. After the first upload the
[release workflow](#release-from-a-tag) builds and uploads it.

1. **Bump `versionCode`** in `app/build.gradle.kts` — every upload needs a higher value. `versionName`
   is cosmetic.
2. Build:
   ```bash
   ./gradlew :app:bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`

**Choose the signing key deliberately when you create that first release.** With a Google-generated key,
the Play build is signed with a key you don't hold, so a copy sideloaded from a GitHub release (signed
with the upload key) can't be updated from Play: it has to be uninstalled first, and its data goes with
it — export a backup first. Uploading an existing key to Play App Signing instead keeps those installs
updating in place, but only if that key is valid past October 2033 (Klas's first key was valid to
March 2029, so it could not be used and the sideloaded 0.4.x installs had to be reinstalled).

### Play Console

Create the app with Russian as its default language; the Console walks you through the listing, the
questionnaires and the rollout. The listing's screenshots, feature graphic, icon, text and promo video are
generated — see [Store listing](store-listing.md); the video goes in as a YouTube link. Two answers are specific to Klas:

- **Privacy policy URL** — required: the app stores student names, phones, emails and payment data.
  All data stays on-device, no backend, so the policy is simple.
- **Data safety form** — data stays local and is never transmitted; declare no collection and no
  sharing. This matches `allowBackup="false"` in the manifest — Play's Auto Backup is deliberately
  off so `klas.db` is never copied to Google's servers. Keep the two consistent.

## Release from a tag

Pushing a `vX.Y.Z` tag runs `.github/workflows/release.yml`:

1. **`check`** — the tag equals `versionName` in `app/build.gradle.kts` and is on `main`; with Play
   publishing on, the [Play release notes](#play-release-notes) are filled in, fit, and are new.
2. **`verify`** — the whole of `ci.yml` on the tagged commit.
3. **`device-tests`** — `device-tests.yml`, alongside `verify`: the instrumented suite on an API 35
   emulator, and the data-layer tests (migrations, backup, repositories) on an API 24 one, minSdk. It can also be run by hand on any branch from the Actions tab.
4. **`build`** — restores the upload key, builds the signed APK and AAB, checks both are signed
   (the log prints the APK's certificate), and attaches them to a GitHub release with generated notes.
   Rewrite the notes afterwards with `gh release edit vX.Y.Z --notes-file …`.
5. **`publish`** — uploads the AAB to a Play track with the Play release notes. Skipped until
   `PLAY_PUBLISH` is `true`.

To release: bump `versionCode` and `versionName` and write the Play release notes in a
`chore(release): X.Y.Z` commit on `develop`, fast-forward `main` to it, then tag and push (`git tag -a vX.Y.Z -m "Klas X.Y.Z"`,
`git push origin main vX.Y.Z`). A failed `publish` can be re-run alone from the Actions tab, and a release
can be repeated by running the workflow on its tag (the **Run workflow** ref picker) — that also
re-uploads the files to the existing GitHub release.

### Set up the pipeline (once)

**Signing.** Four repository secrets give the workflow the upload key (run from a terminal with `gh`;
each of the last three prompts for its value):

```bash
base64 -w0 ~/keystores/klas-upload-v2.jks | gh secret set UPLOAD_KEYSTORE_BASE64
gh secret set UPLOAD_KEYSTORE_PASSWORD
gh secret set UPLOAD_KEY_ALIAS
gh secret set UPLOAD_KEY_PASSWORD
```

**Play publishing**, after the first bundle is uploaded by hand and the app exists in the Console:

1. In Google Cloud, create a project, enable the **Google Play Android Developer API**, create a
   **service account** and download a JSON key for it.
2. In Play Console → **Users and permissions**, invite the service account's email and grant it the
   release permissions for Klas (testing tracks and, when ready, production). If the Console still shows
   a Setup → API access page, link the Cloud project there too.
3. Store the key and switch publishing on:
   ```bash
   gh secret set PLAY_SERVICE_ACCOUNT_JSON < service-account.json
   gh variable set PLAY_PUBLISH --body true
   ```
4. Delete the downloaded JSON; the secret is the only copy that needs to exist.

Two optional variables steer where a release goes; both can be changed without touching the workflow:

| Variable | Default | Meaning |
|---|---|---|
| `PLAY_TRACK` | `internal` | `internal`, `alpha` (the default closed-testing track), `beta`, `production`, or a custom closed track's name. The workflow's manual run takes a `track` input that overrides it once. |
| `PLAY_STATUS` | `completed` | Set `draft` until the app's first release has passed Play review — the API only accepts drafts for an unpublished app — and the release is then finished in the Console. |

Without a service account, nothing is lost: the tag still produces the GitHub release, and the AAB
can be uploaded in the Console by hand. Promoting a release between tracks (closed test → production) is
easiest in the Console.

### Play release notes

Play's "What's new" for a release comes from `store/whatsnew/whatsnew-ru-RU` and `whatsnew-en-US`
(plain text, at most 500 characters each; Play's locale codes, as in `store/listing/`). Rewrite both in
every `chore(release)` commit: while `PLAY_PUBLISH` is `true`, `check` refuses a tag whose notes are
empty, too long, or unchanged since the previous tag, so last release's text can't go out again. Write
them for the tutor, from the commit bodies — the GitHub release notes are the place for detail.

A language added to the listing needs its own `whatsnew-<locale>` file and a line in `check`.

### Dependabot

`.github/dependabot.yml` opens one pull request a month into `develop` bumping the workflows' actions,
the commit-pinned ones too; `ci.yml` runs on it. Merge it when green. Gradle dependencies are updated by
hand. The config only takes effect once it is on `main`, GitHub's default branch.

## Per-release checklist

- [ ] Schema change? A migration is shipped, `MigrationChainTest` passes, and its migration test passes on a device
- [ ] `./gradlew :domain:jvmTest :app:verifyRoborazziDebug :app:lintDebug ktlintCheck detekt` pass (CI runs the same)
- [ ] `./gradlew :app:connectedDebugAndroidTest` passes on a phone if the release touches notifications,
      calendar sync or gestures — the emulators in `device-tests` cover the rest
- [ ] Backup round-trip verified on a release build (device)
- [ ] `chore(release)` commit bumps `versionCode` (and `versionName`) and rewrites `store/whatsnew/`;
      `main` fast-forwarded; tag pushed
- [ ] Release workflow green; GitHub release notes rewritten
- [ ] Play track shows the new version (a draft, while `PLAY_STATUS` is `draft`: roll it out in the Console)
