# Agent instructions

ReVanced patches by edbfi: one bundle, `patches-<version>.rvp`, with patches for several Android apps.
CI comes from [edbfi-ci](https://github.com/edbfi/edbfi-ci); its rules apply here.

## Layout

- A patch lives in `patches/src/main/kotlin/app/edbfi/patches/<app>/<area>/<feature>/`: `<Feature>Patch.kt`
  and its method matchers in `Matchers.kt`. `<app>` is a short lowercase app name (`syncforreddit`), `<area>`
  groups features (`ads`, `api`, `layout`, `misc`). Resources go in `patches/src/main/resources/<app>/<feature>/`;
  code several apps share goes in `patches/…/patches/shared/`.
- Java that runs inside an app goes in an extension module, `extensions/<app>/`, package
  `app.edbfi.extension.<app>`, with compile-only stubs of the app's classes in its `stub/` subproject. Add it with
  the first patch that needs it, and keep kotlin-stdlib out of its runtime classpath. The first extension module
  also adds `android.library apply false` to the root `build.gradle.kts`, `extensions/proguard-rules.pro` and an
  Android SDK on the machine.
- Never commit APKs, bundles, keystores or app code. Samples and patched output go in the ignored `tmp/`.

## Patches

- Patcher v22 API only: `bytecodePatch`/`resourcePatch` with `apply {}` and declarative method matchers, never
  `execute {}` or `fingerprint {}`.
- The name says what the patch does ("Remove ads"); the description says it in third person ("Removes ads.").
  Every named patch has a description and a `compatibleWith` naming the package and the versions it was tested
  on. `PatchMetadataTest` checks these and that names are unique per app.
- Match on stable facts: strings, types, access flags, opcodes. Use an obfuscated name only when nothing stable
  exists, and say so in a comment.
- More than a few injected instructions belong in extension Java; the smali only calls into it.
- Fail at patch time: throw `PatchException` when a match is missing or ambiguous rather than risk a
  `VerifyError` in the app.
- Helpers are `internal`; only patches are public.

## Commands

- `just check` runs what CI runs, prek hooks included; `just build` builds the bundle; `just patch <apk>` applies it to an APK with
  the pinned revanced-cli, into `tmp/out/`, signed with the main checkout's `tmp/test-signing.keystore` so every
  worktree's build installs over the last with `adb install -r` and keeps the app's data.
- Gradle needs `githubPackagesUsername` and `githubPackagesPassword` (a classic PAT with only `read:packages`)
  in `~/.gradle/gradle.properties`. Its daemon runs on JDK 25 (`gradle/gradle-daemon-jvm.properties`); point
  `org.gradle.java.installations.paths` there at a JDK 25 that Gradle doesn't find by itself, such as Homebrew's.

## Test device

- `just device` starts Android 15 in Docker ([Redroid](https://github.com/remote-android/redroid-doc), no VM or
  KVM) and connects adb on loopback; use `ANDROID_SERIAL=127.0.0.1:5600`. `just device-reset` deletes it and its
  data, including the owner's login: agents run it only when the owner asks. Agents share the one device: take
  turns.
- The host kernel needs binderfs and PSI; where PSI is off by default, boot with `psi=1`. On arm64 Linux, use a
  native `adb` on `PATH`, not the SDK's x86_64 one.
- It renders in software, and playing a video crashes its SurfaceFlinger
  ([redroid-doc#920](https://github.com/remote-android/redroid-doc/issues/920)): turn autoplay off and check
  videos through downloaded files and logs.
- Reddit's login page rejects Redroid's WebView (Chromium 128), so `just device` installs a pinned Chromium WebView
  over it, re-signed with AOSP's public test key; it needs `ANDROID_HOME` for `apksigner`.
- Agents never type account credentials. The owner logs in through scrcpy; the agent then keeps the login by
  updating with `adb install -r`, never uninstalling.

## rea

- `.mcp.json`, `.codex/config.toml` and `opencode.json` start `scripts/rea.sh`, which runs rea from the main
  checkout's ignored `.rea/install/`, shared by every worktree. `just setup-rea` installs the current checkout's
  pin (`.rea/package.json`) there, fetches its JADX bridge, re-syncs `.agents/skills/reverse-engineer-anything`
  from the installed package, and checks Ghidra and JADX.
- Machine tools: Homebrew `openjdk@25` and `apktool`, and `adb`. rea 6.8.0 rejects Ghidra 12.2, Homebrew's
  version, so Ghidra 12.1.x comes from NSA's release zip into `~/.local/opt/ghidra_12.1.<n>_PUBLIC` (or set
  `GHIDRA_INSTALL_DIR`); on arm64, build its decompiler with `support/gradle/gradlew buildNatives`.
- Bump rea by hand: change `.rea/package.json`, refresh the lock with
  `npm install --package-lock-only --prefix .rea`, run `just setup-rea`, and commit the skill changes with it.
- On arm64 Linux, x86_64 Android tools such as aapt2 run through `qemu-user-binfmt` with `libc6-amd64-cross`.

## Docs

- Docs hold only what the code can't say: this file, `README.md` for users, and short comments next to the code
  they explain. No reports, plans or notes in the repo: evidence goes in the PR body.
- When a fact changes, fix or delete every other statement of it; never append history.
- `CLAUDE.md` is a symlink to this file.

## Commits and releases

- Conventional Commits scoped by app (`feat(syncforreddit): …`) or by area (`ci`, `rea`), with a DCO sign-off
  (`git commit -s`), through a PR.
- New source files carry `SPDX-License-Identifier: AGPL-3.0-only`. Other patch repos are reference only: nothing
  is copied from them.
- The bundle has one [SemVer](https://semver.org/) version. Release by publishing a GitHub release from main
  tagged `vX.Y.Z`; `release.yml` builds, signs and attaches the assets. Never move, delete or reuse a tag; fix a
  bad release with a new one.
- The first release is `0.1.0`. After that, bump from the commits since the last tag: breaking → major (minor
  while the version is `0.y.z`), `feat` → minor, `fix` or `perf` → patch. A change is breaking when users must
  act on it: a removed or renamed patch or option, or a dropped app version. Mark it with `!`.
