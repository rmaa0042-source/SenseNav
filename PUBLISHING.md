# Publishing SenseNav

Everything here is one-time setup unless marked otherwise. The build is already
configured for it; what remains is the parts that need accounts and secrets.

## 1. Create the signing key

Play and Firebase both identify an app by its signing certificate. **Losing this
key means you can never update the listing again** — back it up somewhere other
than this machine.

```bash
keytool -genkeypair -v -keystore sensenav-release.jks -alias sensenav -keyalg RSA -keysize 4096 -validity 10000
```

Keep the `.jks` outside the repository. `*.jks` and `*.keystore` are gitignored,
but the safest place is not in the tree at all.

Then copy the template and fill it in:

```bash
cp keystore.properties.example keystore.properties
```

`keystore.properties` is gitignored. On CI, set the same four names
(`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`) as environment variables instead — the build reads either.

## 2. Restrict the Maps API key

`MAPS_API_KEY` comes from `local.properties` or the environment. Before shipping,
restrict it in the Google Cloud console to this app's package name **and** the
release signing certificate's SHA-1. An unrestricted key extracted from an APK
can be used by anyone, billed to you.

Get the SHA-1 with:

```bash
keytool -list -v -keystore sensenav-release.jks -alias sensenav
```

## 3. Check the release is publishable

```bash
./gradlew verifyReleaseConfig
```

This runs automatically before `assembleRelease` and `bundleRelease`. It fails
with a list of what is missing rather than producing an artifact that cannot be
shipped. It checks:

- a release signing key is configured and the keystore file exists
- `MAPS_API_KEY` is set, so the build will not ship without maps
- if the API base URL is cleartext `http://`, that its host has a `<domain>` entry
  in `app/src/main/res/xml/network_security_config.xml`

That last one matters because nothing at runtime reports the mismatch — a release
build blocks cleartext by default, so the calls would simply all fail. **If the
API address ever changes, update both `ROUTING_API_BASE_URL` and that XML file.**

## 4. Build

```bash
./gradlew bundleRelease
```

`app/build/outputs/bundle/release/app-release.aab` — this is what Play wants.
For Firebase App Distribution or sideloading, `./gradlew assembleRelease` gives an
APK, which is also mirrored to `builds/sensenav-release-latest.apk`.

Release builds are minified and resource-shrunk: roughly 1.9 MB against 13 MB for
debug. Keep `app/build/outputs/mapping/release/mapping.txt` for every build you
publish, or release crash reports will be unreadable.

## 5. Firebase App Distribution

The Gradle plugins are already declared and pinned, but **they only apply when
`app/google-services.json` exists**. The Google Services plugin hard-fails when
that file is missing, which would make the project unbuildable on a fresh clone,
so it is deliberately conditional.

1. Create a Firebase project at <https://console.firebase.google.com>.
2. Add an Android app with package name `com.flip6.sensenav`.
3. Download `google-services.json` into `app/`. It is gitignored — each developer
   or CI runner fetches their own.
4. Confirm the plugins picked it up:

   ```bash
   ./gradlew :app:tasks --all | grep appDistribution
   ```

   You should see `appDistributionUploadRelease`.

5. Upload a build:

   ```bash
   ./gradlew assembleRelease appDistributionUploadRelease
   ```

   Authenticate with either `firebase login:ci` and a `FIREBASE_TOKEN` env var, or
   a service account via `GOOGLE_APPLICATION_CREDENTIALS`.

To set tester groups and release notes permanently, make the plugin application
unconditional in `app/build.gradle.kts` (delete the `if (hasFirebaseConfig)`
guard and move the aliases into the `plugins {}` block) — that gives you the typed
`firebaseAppDistribution { }` accessor inside `buildTypes.release`. Until then,
pass them per-invocation:

```bash
./gradlew appDistributionUploadRelease -PappDistributionGroups=testers
```

### Crashlytics

`firebase-crashlytics` is included when Firebase is configured. **Analytics is
deliberately not** — this app keeps the user's location and history on the device,
and a general analytics SDK would start sending behavioural data off it. A crash
stack trace is a narrower trade, and without it a published build gives no way to
learn why it broke for someone.

To make release crash reports readable, upload the R8 mapping after each build:

```bash
./gradlew uploadCrashlyticsMappingFileRelease
```

## Known gaps before a public listing

- **The API is plain HTTP.** Route requests, including the user's coordinates,
  travel unencrypted and are readable by anything on the network path. Cleartext
  is scoped to that one host and denied everywhere else, but TLS on the API is the
  real fix.
- **No privacy policy.** Play requires one for any app requesting location. This
  app also needs a Data Safety declaration; the honest version is that location is
  used on-device and not shared, and that saved places and history stay local —
  `allowBackup` is off and both backup rule files exclude every stored file.
- **`versionCode` is 1.** It must increase for every upload.
