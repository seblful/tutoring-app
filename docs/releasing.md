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
   keytool -genkeypair -v -keystore ~/keystores/klas-upload.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   Back up the keystore file and its passwords *off this machine* (e.g. a password-manager attachment).
   Losing the upload key means a manual reset with Google; losing it before Play App Signing is enabled
   would block all future updates.

2. **Create `keystore.properties`** in the repo root from the template:
   ```bash
   cp keystore.properties.example keystore.properties
   ```
   ```properties
   storeFile=C:/Users/<you>/keystores/klas-upload.jks
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

### Build the bundle

Play requires an **App Bundle** (`.aab`), not an APK.

1. **Bump `versionCode`** in `app/build.gradle.kts` — every upload needs a higher value. `versionName`
   is cosmetic.
2. Build:
   ```bash
   ./gradlew :app:bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`

### Play Console

Create the app with Russian as its default language; the Console walks you through the listing, the
questionnaires and the rollout. Two answers are specific to Klas:

- **Privacy policy URL** — required: the app stores student names, phones, emails and payment data.
  All data stays on-device, no backend, so the policy is simple.
- **Data safety form** — data stays local and is never transmitted; declare no collection and no
  sharing. This matches `allowBackup="false"` in the manifest — Play's Auto Backup is deliberately
  off so `klas.db` is never copied to Google's servers. Keep the two consistent.

## Per-release checklist

- [ ] Bump `versionCode` (and `versionName` if user-facing)
- [ ] Schema change? A migration is shipped and `MigrationChainTest` passes
- [ ] `keystore.properties` present and correct
- [ ] `./gradlew :domain:test :app:testDebugUnitTest` and `:app:lintDebug` pass
- [ ] `./gradlew :app:bundleRelease` succeeds
- [ ] Backup round-trip verified on a release build (device)
- [ ] Upload `.aab` to the chosen track
- [ ] Release notes filled in
