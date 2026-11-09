# edbfi ReVanced patches

[![CI](https://github.com/edbfi/revanced-patches/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/edbfi/revanced-patches/actions/workflows/ci.yml)

[ReVanced](https://revanced.app) patches for Android apps, in one signed bundle.

## Supported apps

| App | Package | Tested version |
| --- | --- | --- |
| [Sync for Reddit](#sync-for-reddit) | `com.laurencedawson.reddit_sync` | `v23.06.30-13:39` |

Patch the tested version. ReVanced Manager and the CLI skip these patches on other versions unless you force them, and
other versions are untested.

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

## App details

### Sync for Reddit

#### Patches

| Patch | What it does | Default |
| --- | --- | --- |
| Spoof client | Restores login and browsing by using another app's Reddit OAuth client. | On |
| Disable ads | Removes ads and the ad consent form, and keeps the ad SDKs from starting. | On |
| Disable Sync for Lemmy bottom sheet | Removes the Sync for Lemmy promotion that shows at every start. | On |
| Fix /s/ links | Opens Reddit share links (/s/) as the post they point to, from the app and from other apps. | On |
| Fix Redgifs | Fixes Redgifs videos not loading. | On |
| Fix post thumbnails | Fixes post thumbnails not loading. | On |
| Fix video downloads | Fixes downloaded Reddit videos having only audio. | On |
| Fix View more comments | Fixes "View more" not loading further comments. | On |
| Fix galleries | Fixes Reddit galleries failing to open. | On |
| Fix Other discussions | Fixes "Other discussions" failing to load, by searching Reddit for the link instead. | On |
| Fix Imgur | Fixes Imgur albums failing to open, album previews in comments and posts staying blank, and Imgur uploads failing. | On |
| Use /user/ endpoint | Requests user profiles from /user/ instead of /u/. Not needed today: Reddit redirects /u/ to /user/, and the app follows the redirect. | Off |

#### Options

- **Spoof client** uses [RedReader](https://github.com/QuantumBadger/RedReader)'s public Reddit client by default,
  whose rate limit every RedReader user shares. If Reddit stops accepting it, set all three options to another
  "installed app" client: `OAuth client ID`, `Redirect URI` (the one registered for that ID) and `User-Agent` (the one
  that app sends). Then remove your accounts in Sync and add them again: their tokens belong to the old client.
- **Fix Imgur** uses RedReader's public Imgur client ID by default, whose quota is shared too. If Imgur stops
  accepting it, set `Imgur client ID` to another.

#### Known issues

- **Previews of Tenor, Gfycat and other website links in comments and posts stay blank.** They came from Sync's own
  image server, which no longer answers. Without Sync Ultra, Sync never showed website previews.
- **Enhanced autoplay doesn't play Redgifs or Gfycat videos.** It plays them through Sync's image server too. Leave it off (the
  default).
- **"Other discussions" can miss a post of the same link, or show a post of a different one.** It searches Reddit for the link.

## Contribute

Build commands, patch rules and commit conventions are in [AGENTS.md](AGENTS.md).

## License

[AGPL-3.0-only](LICENSE).
