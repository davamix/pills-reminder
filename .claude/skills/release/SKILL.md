---
name: release
description: Release a version of Pills Reminder from main, as P8 released 1.0.0. Build the signed release APK on main, check its signature (the same key as every earlier release) and its manifest, take its SHA-256, write the release notes and show them to the user, tag vX.Y.Z, create the GitHub release with the APK, check the published asset, and install it on the phone over the previous version, keeping the treatments. Use when the user asks to release, tag or publish a version, once its changes are merged into main and the user has given the go-ahead.
---

# Release a version

Input: the version, `X.Y.Z`. **Only with the user's go-ahead** (CLAUDE.md, "Git"): a tag and a GitHub release are public.

## Before

- The version's changes are merged into `main`, and CI passed there.
- `app/build.gradle.kts` has the new `versionName` (`X.Y.Z`) and a `versionCode` one higher than the last release's (1.0.0 was 1). Android refuses an update with a lower `versionCode`. If it isn't raised yet, that change goes through a pull request first.
- The signing properties are there: `grep -c '^pillsReminder\.release\.' ~/.gradle/gradle.properties` prints 4. **Never print their values**, or put the password in a command line or a file in the repo.
- The phone is connected, for the last step (`phone` skill).

## Steps

Work in the session's scratchpad for every file that isn't in the repo: `<scratchpad>/release/`.

1. **Build on `main`:** `git switch main && git pull --ff-only`, and note the commit (`git rev-parse HEAD`). Then `./gradlew assembleRelease --console=plain -q > build/gradle.log 2>&1; echo "exit=$?"`. The APK is `app/build/outputs/apk/release/app-release.apk`.
2. **Check the signature:** `D:\Android\Sdk\build-tools\37.0.0\apksigner.bat verify --verbose --print-certs <apk>`. It must say `Verifies`, v2 (or later) `true`, one signer, `CN=Pills Reminder`, and the certificate SHA-256 digest **`d28df13a9531debbba7c7d73eb2305121cea03bce885157ae533b4c4a263a5e7`**. Another digest means another key: the phone would refuse the update, and the only way round, uninstalling, deletes every treatment. Stop and tell the user.
3. **Check the manifest:** `D:\Android\Sdk\cmdline-tools\latest\bin\apkanalyzer.bat manifest print <apk>`, parsed with Python's `xml.etree` (one attribute per line, so grep doesn't work). It must have the new `versionName` and `versionCode`, no `android:debuggable`, `allowBackup="false"`, exactly the permissions of plan §3 (plus AndroidX's `<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`), no `INTERNET`, and only `MainActivity` exported. A new component or permission since the last release must be explained, as the `handover` skill's manifest comparison does.
4. **Copy and hash:** copy the APK to `<scratchpad>/release/pills-reminder-X.Y.Z.apk`, and take its SHA-256 (`sha256sum`). A fresh build doesn't give the same bytes as an earlier build of the same code, so the hash is this file's.
5. **Release notes:** write them to `<scratchpad>/release/notes.md` from the template below, and **show them to the user before anything is published**, for example as the preview of an `AskUserQuestion` option ("Publish as shown" / "Change the notes first"). The repo is public: no personal data, no real treatments.
6. **Tag and publish,** once the user agrees:

   ```bash
   git tag -a vX.Y.Z -m "Pills Reminder X.Y.Z" <commit> && git push origin vX.Y.Z
   gh release create vX.Y.Z <scratchpad>/release/pills-reminder-X.Y.Z.apk --title "Pills Reminder X.Y.Z" --notes-file <scratchpad>/release/notes.md --verify-tag
   ```

   Pass Windows paths to `gh` (`cygpath -w`).
7. **Check what was published:** `gh release download vX.Y.Z -p 'pills-reminder-X.Y.Z.apk' -D <scratchpad>/release/download --clobber`, and compare its SHA-256 with the notes. `gh release view vX.Y.Z --json isDraft,isPrerelease,assets` must say not a draft, not a pre-release, one asset.
8. **Install it on the phone** over the previous version, keeping the data: `$adb install -r <the downloaded APK>` (`phone` skill). Then check: the installed APK's SHA-256 (`$adb shell pm path io.github.davamix.pillsreminder`, then `$adb exec-out cat <path> | sha256sum`), the `versionName` in `dumpsys package`, and `pkg=$release_pkg app_events` showing the update: the process killed for `installPackageLI`, `MY_PACKAGE_REPLACED` starting `SystemEventReceiver`, and the reminders posted again within a second or two (with HyperOS's Autostart on). The treatments must all be there.
9. **Record it:** CLAUDE.md's status line, and whatever phase or document asked for the release.

## Release notes template

Keep each part short. "What's new" replaces "What it does" after 1.0.0; the last two parts stay.

```markdown
<One or two sentences: what this release is.>

## What's new

- <each change, as the user sees it>

## Installing

Android 13 or later. With USB debugging on (and, on a Xiaomi phone, *Install via USB*):

    adb install -r pills-reminder-X.Y.Z.apk

`-r` installs over an earlier version and keeps your treatments. <Anything the first launch, or this update, asks for.>

## Verifying the APK

- SHA-256 of `pills-reminder-X.Y.Z.apk`: `<sha256>`
- Signing certificate: `CN=Pills Reminder`, SHA-256 digest `d28df13a9531debbba7c7d73eb2305121cea03bce885157ae533b4c4a263a5e7`, the same key as every release since 1.0.0.

## How it was checked

<The checks behind this release, with links pinned to the tag: https://github.com/davamix/pills-reminder/blob/vX.Y.Z/…>
```

1.0.0's notes are on its [GitHub release](https://github.com/davamix/pills-reminder/releases/tag/v1.0.0).
