# SenseNav

FIT5120 TP07

An Android app for finding sensory-friendly places and walking routes around
Melbourne. Routes are scored against City of Melbourne live pedestrian sensor
data, so you can pick a quieter path rather than just the fastest one.

---

## Setup after cloning or pulling

Everything builds from a fresh clone **except one file**: `local.properties` is
gitignored (it holds secrets), so you have to create your own.

### 1. Open the project in Android Studio

Requires Android Studio with **JDK 21**. Opening the project generates
`local.properties` with your `sdk.dir` automatically.

### 2. Add the Maps API key

The map is blank grey without this. Open `local.properties` and add:

```properties
MAPS_API_KEY=paste_the_key_here
```

Ask the team for the key — **never commit it or paste it into a PR, issue, or
public channel.** `local.properties` is gitignored precisely so it cannot be
committed by accident.

### 3. Sync Gradle and run

The key is read at build time, so **you must rebuild after adding it** — a
Gradle sync alone is not enough.

---

## Important: the key is locked to each developer's machine

The Maps key is restricted to our Android package plus a list of approved
signing fingerprints. Every developer's debug keystore is different and
generated locally, so **your fingerprint has to be added to the key before the
map will render for you**. Until then you get a blank grey map even though the
key is correct.

To get added, run:

```bash
./gradlew signingReport
```

Copy the **SHA1** value from the `debug` variant and send it to whoever owns the
Google Cloud project. Adding it takes about 30 seconds — a single key accepts
many fingerprints, so everyone can share one key.

If the app shows the "Map needs an API key" card instead of a grey map, the key
is missing from `local.properties` entirely, not a fingerprint problem.

---

## The routing API

Directions come from a FastAPI service, not from this repo:

```
http://34.172.95.142:8000
```

**This IP is not static.** If it changes, you do not need to edit any source —
override it in `local.properties`:

```properties
ROUTING_API_BASE_URL=http://new-address:8000/
```

Check it is alive by opening `http://34.172.95.142:8000/docs` in a browser. If
routes fail to load but the map renders, the API is the likely cause. Ping
Elijah, who has SSH access to the VM.

> The `backend/` folder is a Spring Boot service that the app does **not** call.
> It holds hardcoded sample data and is not wired into anything.

---

## Building

```bash
./gradlew assembleDebug
```

The newest APK is mirrored to `builds/sensenav-debug-latest.apk` on every build
— see [builds/README.md](builds/README.md). Install it on a connected device:

```bash
adb install -r builds/sensenav-debug-latest.apk
```

---

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| Blank grey map | Your signing fingerprint is not on the API key yet — run `./gradlew signingReport` and send your debug SHA1 |
| "Map needs an API key" card | `MAPS_API_KEY` missing from `local.properties`, or you didn't rebuild after adding it |
| "Can't reach the routing service" | API is down or its IP changed — check `/docs` in a browser |
| "Couldn't find <place>" | The typed start/end could not be geocoded — try a fuller address |
| Routes load but no blue "my location" dot | Location permission denied; the app falls back to the CBD as the start point |

---

## Project layout

| Path | What it is |
| --- | --- |
| `app/src/main/java/.../ui/screens/` | All screens (single-file Compose UI) |
| `app/src/main/java/.../ui/map/` | Google Maps surface, markers, polylines |
| `app/src/main/java/.../data/` | Routing API client, device location, geocoding |
| `app/src/main/java/.../api/` | Retrofit interface and DTOs for the routing API |
| `documentation/` | API guide and reference screenshots |
| `builds/` | Latest built APK per variant (gitignored binaries) |
