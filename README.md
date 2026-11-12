<p align="center">
  <img src="assets/revanced-patches.svg" alt="revanced-patches logo" width="256" height="256">
</p>
<h1 align="center">revanced-patches</h1>
<p align="center"><strong>Signed ReVanced patches for Android apps.</strong></p>
<p align="center">
  <a href="https://github.com/edbfi/revanced-patches/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/license-AGPL--3.0--only-blue?style=flat" alt="License: AGPL-3.0-only">
  </a>
  <a href="https://github.com/JetBrains/kotlin" title="Kotlin source">
    <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat&amp;logo=kotlin&amp;logoColor=white" alt="Kotlin">
  </a>
  <a href="https://github.com/ReVanced/revanced-patcher" title="ReVanced Patcher framework source">
    <img src="https://img.shields.io/badge/ReVanced-0076FF?style=flat" alt="ReVanced">
  </a>
  <a href="https://android.googlesource.com/platform/manifest" title="Android Open Source Project source manifest">
    <img src="https://img.shields.io/badge/Android-3DDC84?style=flat&amp;logo=android&amp;logoColor=black" alt="Android">
  </a>
</p>

<p align="center">
  <a href="https://github.com/edbfi/revanced-patches/actions/workflows/ci.yml">
    <img src="https://github.com/edbfi/revanced-patches/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI">
  </a>
</p>

[ReVanced](https://revanced.app) patches for Android apps, in one signed bundle.

## Supported apps

| App | Package | Patches |
| --- | --- | --- |
| [Sync for Reddit](docs/syncforreddit.md) | `com.laurencedawson.reddit_sync` | 15 |

Each app's page lists its patches, their options and known issues, and the app version they were tested on. Patch
that version: ReVanced Manager and the CLI skip these patches on other versions unless you force them, and other
versions are untested.

## Install

### ReVanced Manager

Add a patches source with this URL:

```text
https://github.com/edbfi/revanced-patches/releases/latest/download/patches-bundle.json
```

Manager also selects the patches its built-in ReVanced Patches has for the same app, and some of them change the same
code as ours. Before you patch, turn on Settings → Advanced → **Allow changing patch selection and options**, then in
**Select patches** untick **ReVanced Patches**.

Manager doesn't verify signatures. To check a bundle, use the CLI.

### ReVanced CLI

Download `patches-X.Y.Z.rvp`, `.rvp.asc` and `.rvp.sigstore.json` from a
[release](https://github.com/edbfi/revanced-patches/releases) and the [public key](keys/release-signing.asc), then:

```sh
java -jar revanced-cli.jar patch \
  -p patches-X.Y.Z.rvp -k release-signing.asc -s patches-X.Y.Z.rvp.asc \
  -a patches-X.Y.Z.rvp.sigstore.json -r edbfi/revanced-patches \
  app.apk
```

The key's fingerprint is `7B40 A51F C15C 376F 09D1  6979 893C 92F2 3E64 7FC8`.

## Contribute

Build commands, patch rules and commit conventions are in [AGENTS.md](AGENTS.md).

## License

[AGPL-3.0-only](LICENSE).
