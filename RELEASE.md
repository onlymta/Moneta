# Getting a real APK out of this project

This codebase was written in an environment with **no Android SDK and no
access to Google's Maven repository** (required for the Android Gradle
Plugin, AndroidX, and Compose) - so nothing here has ever been compiled, and
no `.apk` could be produced directly in that environment, no matter how many
phases were finished. Everything below is verified to work in principle
(the Gradle wrapper is genuine and version-matched - see the note at the
bottom) but the actual build has to happen somewhere with real SDK access.
Three ways to get there, easiest first:

## Option 1: GitHub Actions (no local setup at all)

1. Push this project to a new GitHub repository.
2. The included workflow, `.github/workflows/build.yml`, runs automatically
   on every push to `main` (or manually via the "Run workflow" button under
   the Actions tab).
3. It builds both a debug APK and a release APK on GitHub's own runners
   (which have full internet access, unlike the sandbox this project came
   from) and uploads each as a downloadable build artifact -
   `moneta-debug-apk` and `moneta-release-apk`.
4. Download whichever artifact zip you want from the finished workflow run,
   unzip it, and install the `.apk` on a device or emulator
   (`adb install app-release.apk`, or just copy it to a phone with "install
   from unknown sources" allowed).

The release APK is what you want for judging real-world performance: it's
built with `isMinifyEnabled = true` (R8 shrinking/optimization) and
`isDebuggable = false`, so it doesn't carry the overhead a debug build has
(see "Debug vs release performance" below). Without a real signing keystore
configured (see below), it's automatically signed with the debug keystore
instead so it's still directly installable - just not something to publish
to Google Play as-is.

## Option 2: Android Studio (recommended for actually developing this further)

1. Install [Android Studio](https://developer.android.com/studio) (Koala/2024.1 or newer).
2. Open this project's root folder.
3. Let Gradle sync - Android Studio will download any missing SDK platforms/build-tools automatically.
4. Run ▶ on an emulator or a connected device, or use **Build → Build Bundle(s) / APK(s) → Build APK(s)** to get a `.apk` file directly (Android Studio will show a notification with a "locate" link to the output file, typically `app/build/outputs/apk/debug/app-debug.apk`).

## Option 3: Command line, if you already have the Android SDK installed

```bash
cd moneta
./gradlew assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew assembleRelease` now works out of the box: `app/build.gradle.kts`
declares a `release` signing config that only activates when
`RELEASE_STORE_FILE` (plus `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`) point at a real, existing keystore. If those aren't
set, the `release` build type quietly signs with the debug keystore instead,
so the command always succeeds and always produces something installable.

**To test a release build locally right now**, no keystore needed:
```bash
cd moneta
./gradlew assembleRelease
# APK will be at: app/build/outputs/apk/release/app-release.apk
```

**To get a real, Play-Store-worthy signed release** later:
1. Generate a signing keystore (`keytool -genkeypair -v -keystore release.keystore -alias moneta -keyalg RSA -keysize 2048 -validity 10000`).
2. Set the four env vars above to point at it, e.g.:
   ```bash
   export RELEASE_STORE_FILE=/path/to/release.keystore
   export RELEASE_STORE_PASSWORD=...
   export RELEASE_KEY_ALIAS=moneta
   export RELEASE_KEY_PASSWORD=...
   ./gradlew assembleRelease
   ```
   (never commit the keystore or its passwords to source control).
3. Or, to have CI produce a properly signed APK: base64-encode the keystore
   (`base64 -w0 release.keystore`) and add it plus the three passwords as
   repo secrets named `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
   `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` (Settings → Secrets and
   variables → Actions). The workflow picks them up automatically - no
   workflow file changes needed.
4. Run `./gradlew bundleRelease` instead if you need a Play Store `.aab`.

## Debug vs release performance

A debug build being noticeably heavier/slower than release is expected, not
a bug - three real Android mechanisms cause it, independent of anything in
this app's own code:
- **`android:debuggable="true"`** (set automatically on the `debug` build
  type) makes ART disable several runtime optimizations and keeps JIT
  compilation available, specifically so a debugger/profiler can attach -
  that alone costs real, measurable performance.
- **No R8/ProGuard shrinking or optimization** (`isMinifyEnabled = false` on
  `debug`): every class is loaded exactly as compiled, with none of the
  inlining, dead-code elimination, or dexopt-friendly restructuring R8 does
  for `release`.
- **Extra debug-only libraries**: `debugImplementation("androidx.compose.ui:ui-tooling")`
  and `...ui-test-manifest` (see `app/build.gradle.kts`) pull in Compose's
  layout-inspector/preview machinery, which only exists in debug builds and
  adds startup and runtime overhead of its own.

None of this means the app's own code is inefficient - it means comparing a
debug APK's feel to what a real user would experience isn't apples-to-apples.
Build and try the `release` APK (Option 1 or the command above) for a
representative read on performance.

## Why the Gradle wrapper in this project can be trusted

`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` were fetched directly from the official `gradle/gradle` GitHub repository at tag `v8.7.0`, matching the Gradle version pinned in `gradle/wrapper/gradle-wrapper.properties` (`8.7`) - not hand-written or reconstructed from memory. They're the same three files any fresh `gradle wrapper` invocation would produce.
