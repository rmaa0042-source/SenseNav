# builds/

The most recent APK for each build variant lands here automatically.

| File | Produced by |
| --- | --- |
| `sensenav-debug-latest.apk` | `./gradlew assembleDebug` |
| `sensenav-release-latest.apk` | `./gradlew assembleRelease` |

Each file is overwritten on every build, so it is always the newest one. Unlike
`app/build/outputs/`, this folder survives `./gradlew clean`.

## Producing a build

```bash
./gradlew assembleDebug
```

The copy runs automatically as part of `assemble`. To refresh the folder without
a full build, run the copy task on its own:

```bash
./gradlew copyDebugApkToBuilds
```

## Installing on a connected device

```bash
adb install -r builds/sensenav-debug-latest.apk
```

## A note on version control

The `.apk` files here are **gitignored**. They are ~12 MB each, and git keeps
every version of a file forever - committing one per build would permanently
bloat the repository, and removing them later requires rewriting history.

If the team wants to share builds through the repo anyway, delete the
`/builds/*.apk` line from `.gitignore`. For wider distribution, attaching the
APK to a GitHub Release is usually the better option: releases are stored
outside git history, so they can be deleted later without consequence.

## Debug builds are not shippable

`sensenav-debug-latest.apk` is signed with the local debug keystore, has
`isMinifyEnabled = false`, and embeds whatever `MAPS_API_KEY` is in your
`local.properties`. It is for testing only - a Play Store build needs a release
keystore and its own restricted API key.
