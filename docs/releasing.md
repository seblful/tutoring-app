# Releasing

Build signed APKs for sideloading and AABs for Google Play. For normal releases, follow
[Release from a tag](#release-from-a-tag); signing/pipeline setup is needed only once.

## Release from a tag

1. On `develop`, bump `versionCode` and `versionName` in `app/build.gradle.kts`, rewrite both
   `store/whatsnew/whatsnew-ru-RU` and `whatsnew-en-US`, and commit as `chore(release): X.Y.Z`.
2. Complete the [checklist](#per-release-checklist), then fast-forward `main` to the release commit.
3. Tag and push:
   ```bash
   git tag -a vX.Y.Z -m "Klas X.Y.Z"
   git push origin main vX.Y.Z
   ```
4. Check the workflow, rewrite GitHub notes with `gh release edit vX.Y.Z --notes-file …`, and confirm
   the Play track. If `PLAY_STATUS` is `draft`, finish rollout in the Console.

The [release workflow](../.github/workflows/release.yml) checks that the tag matches `versionName` and
is on `main`; runs CI and device tests; builds/checks signed APK/AAB artifacts; attaches them to a
GitHub release; and publishes to Play only when `PLAY_PUBLISH=true`. Device tests cover the full suite
on API 35 and data tests on API 24. A failed publish can be rerun alone. A manual run **on the tag**
repeats the release and replaces existing GitHub artifacts; its `track` input overrides `PLAY_TRACK`.

Play notes are plain text, at most **500 characters per locale**. With publishing enabled, the workflow
refuses missing, blank, oversized or unchanged notes. Write for the tutor; GitHub notes hold technical
detail. Adding a listing language requires `whatsnew-<locale>` and a corresponding workflow check.

## Set up signing (once)

Release builds use R8 minification/resource shrinking. `keystore.properties` enables signing; without
it, release builds remain unsigned. Keep the upload key outside the repo and back up its file/passwords.

1. Generate a long-lived key:
   ```bash
   mkdir -p ~/keystores
   keytool -genkeypair -v -keystore ~/keystores/klas-upload-v2.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   `-validity` is days. The workflow requires expiry past **22 October 2033**; do not shorten it.
   Check with `keytool -list -v -keystore <file> -alias upload`. Existing key validity cannot be extended.
   Losing the upload key requires a Google reset; losing the signing key before Play App Signing
   can prevent future updates. Back up the keystore and passwords off this machine.
2. Copy the gitignored configuration template:
   ```bash
   cp keystore.properties.example keystore.properties
   ```
   ```properties
   storeFile=C:/Users/<you>/keystores/klas-upload-v2.jks
   storePassword=<your keystore password>
   keyAlias=upload
   keyPassword=<your key password>
   ```
   Use an absolute path: Gradle does not expand `~`; relative paths resolve from the repo root.
   Enter at keytool's key-password prompt makes both passwords equal.

**Verify backup export/import on a real device with a release build before shipping.** R8 success does
not prove the round-trip survives minification; keep rules are in `app/proguard-rules.pro`.

## Build and sideload

```bash
./gradlew :app:assembleRelease
adb install "app/build/outputs/apk/release/app-release.apk"
```

An `app-release-unsigned.apk` means signing was skipped: check `keystore.properties`. Alternatively,
copy the signed APK to the phone and allow installation from unknown sources.

**A Play build signed with Google's key cannot update an APK signed with a different upload key.**
Export a backup outside the app **before uninstalling** the sideloaded version; restore it after
installing from Play. Uninstalling removes the local database. Choosing an existing signing key when
first configuring Play App Signing can preserve compatibility, provided Play accepts that key.

## First Play upload

Personal developer accounts created after 13 November 2023 need a closed test with at least 12 testers
opted in continuously for 14 days before applying for production access; see
[Google's testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).
The [tester instructions](../store/tester-instructions.md) currently link to the internal test;
that track is separate from the required closed test.

1. Create the app in Play Console with Russian as default language. Prepare the
   [store listing](store-listing.md), questionnaires and a public privacy-policy URL.
2. Build the first bundle manually; each upload needs a higher `versionCode`:
   ```bash
   ./gradlew :app:bundleRelease
   ```
   Upload `app/build/outputs/bundle/release/app-release.aab` in the Console.
3. Complete Data safety using [the privacy policy](../store/privacy-policy.md) and
   [Google's definitions](https://support.google.com/googleplay/android-developer/answer/10787469).
   Review optional calendar sync, invitations and user-selected exports/sharing, including applicable
   exemptions. `allowBackup="false"` disables Android backup; it does not mean data is never transferred.

## Set up the pipeline (once)

Store signing secrets with `gh`; the last three commands prompt for values:

```bash
base64 -w0 ~/keystores/klas-upload-v2.jks | gh secret set UPLOAD_KEYSTORE_BASE64
gh secret set UPLOAD_KEYSTORE_PASSWORD
gh secret set UPLOAD_KEY_ALIAS
gh secret set UPLOAD_KEY_PASSWORD
```

After the first bundle exists in Play Console:

1. Create a Google Cloud project, enable the Google Play Android Developer API, and create a service
   account with a downloaded JSON key.
2. Invite its email in Play Console → Users and permissions; grant Klas testing/production release
   permissions as needed. Link the project under Setup → API access if the Console shows that page.
3. Store the key and enable publishing:
   ```bash
   gh secret set PLAY_SERVICE_ACCOUNT_JSON < service-account.json
   gh variable set PLAY_PUBLISH --body true
   ```
   Delete the downloaded JSON after storing the secret.

| Variable | Default | Use |
|---|---|---|
| `PLAY_TRACK` | `internal` | `internal`, `alpha` (default closed track), `beta`, `production` or custom closed-track name. |
| `PLAY_STATUS` | `completed` | Use `draft` until first-release review allows publishing; finish drafts in the Console. |

Without Play setup, tags still produce GitHub artifacts for manual upload. Promote releases between
tracks in the Console. [Dependabot](../.github/dependabot.yml) maintains workflow actions; Gradle
versions are updated manually.

## Per-release checklist

- [ ] Schema change: migration shipped, `MigrationChainTest` and device migration tests pass.
- [ ] `./gradlew :domain:jvmTest :app:verifyRoborazziDebug :app:lintDebug ktlintCheck detekt` passes.
- [ ] `./gradlew :app:connectedDebugAndroidTest` passes on a phone for notification/calendar/gesture
      changes; release emulators cover the remaining instrumented tests.
- [ ] Backup round-trip verified on a release build on a device.
- [ ] Version and both Play notes updated; `main` fast-forwarded; matching tag pushed.
- [ ] Release workflow green, GitHub notes rewritten, correct Play track/version rolled out.
