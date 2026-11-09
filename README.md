# edbfi ReVanced patches

[![CI](https://github.com/edbfi/revanced-patches/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/edbfi/revanced-patches/actions/workflows/ci.yml)

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
