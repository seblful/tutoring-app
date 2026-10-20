# Run the iOS pilot

On an Apple Silicon Mac, generate the Xcode project and run the `KlasPilot` scheme:

```bash
brew install xcodegen
xcodegen generate --spec iosApp/project.yml
open iosApp/KlasPilot.xcodeproj
```

Use Xcode with an installed iOS simulator, JDK 21 and the Android SDK configured as in the README.
The Xcode build invokes Gradle to build and link `KlasShared`; its source is `:shared` with exported
domain types. `iosApp/project.yml` is the project source of truth; regenerate after changing it.
The generated project and build results are ignored by Git.

Add a student, book a lesson, terminate the app and reopen it. The student and lesson must remain.
The host uses `createKlasStore` and the same repository wiring as Android. It keeps `klas-pilot.db`
in its own Application Support directory. This is a local persistence pilot: English, Math at
30 USD per hour, with calendar, reminders, sharing and backup UI outside its scope.

## Run the persistence test

Choose an available iPhone simulator UDID from `xcrun simctl list devices available`, then run:

```bash
xcodebuild test -project iosApp/KlasPilot.xcodeproj -scheme KlasPilot \
  -destination 'platform=iOS Simulator,id=<UDID>' \
  -derivedDataPath iosApp/DerivedData CODE_SIGNING_ALLOWED=NO
```

The test creates a unique database within the test app's sandbox, creates a student and booking,
then terminates and relaunches the app to verify both. It does not reset an existing pilot database.
The macOS job in [CI](../.github/workflows/ci.yml) generates the project and runs this test alongside
the shared/domain simulator suites. Windows can compile the native Kotlin libraries; Xcode building,
Swift compilation and simulator execution require macOS.
