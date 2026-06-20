# Publishing to Google Play

Step-by-step guide to release this app on the Google Play Store. The build is already configured
for signing + minification — see `app/build.gradle.kts` and `keystore.properties.example`.

---

## 1. One-time prerequisites

### Google Play Developer account
- Register at [play.google.com/console](https://play.google.com/console) and pay the one-time **$25** fee.
- **New personal accounts** must run a **closed test with 12+ testers for 14 continuous days** before
  they can publish to production. Start recruiting testers early — this is the usual bottleneck.

### Upload key (once, never lose it)
Play App Signing means Google holds the real release key; you sign with an *upload* key.

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

- Store `upload-keystore.jks` **outside the repo**.
- Back up the keystore file and its passwords somewhere safe. Losing the upload key means a manual
  reset with Google; losing it before Play App Signing is enabled would block all future updates.

---

## 2. Configure signing locally

1. Copy the template:
   ```bash
   cp keystore.properties.example keystore.properties
   ```
2. Fill in `keystore.properties` (repo root, gitignored) with your key path and passwords.

When `keystore.properties` is absent the release build is left **unsigned** — debug builds, CI, and
contributors without the key are unaffected.

---

## 3. Build the release bundle

Play requires an **App Bundle** (`.aab`), not an APK.

1. **Bump `versionCode`** in `app/build.gradle.kts` — every upload needs a higher value
   (currently `1`). `versionName` (e.g. `0.1.0-beta`) is cosmetic.
2. Build:
   ```bash
   ./gradlew :app:bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`

The release build is **minified + resource-shrunk (R8)**. R8 keep rules for `@Serializable` types
live in `proguard-rules.pro`.

> ⚠️ **Verify the backup feature on a release build before shipping.** R8 building successfully does
> not prove the reflection-driven backup export/import survives minification. Test the backup
> round-trip on a real device on a release build. If unsure for a first beta, temporarily set
> `isMinifyEnabled = false` in the `release` block and enable it later once verified.

---

## 4. Play Console setup

1. **Create app** — name, default language (Russian), app type, free/paid.
2. **Store listing**:
   - Short description + full description
   - App icon (512×512)
   - Feature graphic (1024×500)
   - At least **2 phone screenshots**
3. **Required questionnaires** (must be complete before release):
   - **Privacy policy URL** — required: the app stores student names, phones and payment data.
     (Note: all data stays on-device, no backend, so the policy is simple.)
   - **Data safety form** — data is collected but stays local; declare accordingly.
   - Content rating, target audience, ads declaration, app access.
4. **Upload** the `.aab` to a testing track (start with internal or closed testing).
5. **Roll out** to testers; satisfy the 14-day / 12-tester closed-testing requirement.
6. **Promote to production** once eligible.

---

## Per-release checklist

- [ ] Bump `versionCode` (and `versionName` if user-facing)
- [ ] `keystore.properties` present and correct
- [ ] `./gradlew :app:bundleRelease` succeeds
- [ ] Backup round-trip verified on a release build (device)
- [ ] Upload `.aab` to the chosen track
- [ ] Release notes filled in
