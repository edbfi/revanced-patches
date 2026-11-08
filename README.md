# edbfi ReVanced patches

[ReVanced](https://revanced.app) patches for Android apps.

## Use

**ReVanced Manager:** add a patches source with this URL:

```text
https://github.com/edbfi/revanced-patches/releases/latest/download/patches-bundle.json
```

Manager doesn't verify signatures. To check a bundle, use the CLI.

**ReVanced CLI:** download `patches-X.Y.Z.rvp`, `.rvp.asc` and `.rvp.sigstore.json` from a
[release](https://github.com/edbfi/revanced-patches/releases) and the [public key](keys/release-signing.asc), then:

```sh
java -jar revanced-cli.jar patch \
  -p patches-X.Y.Z.rvp -k release-signing.asc -s patches-X.Y.Z.rvp.asc \
  -a patches-X.Y.Z.rvp.sigstore.json -r edbfi/revanced-patches \
  app.apk
```

The key's fingerprint is `7B40 A51F C15C 376F 09D1  6979 893C 92F2 3E64 7FC8`.

## Patches

### Sync for Reddit

Tested on `com.laurencedawson.reddit_sync` `v23.06.30-13:39`.

| Patch | What it does |
| --- | --- |
| Spoof client | Logs in and browses with another app's Reddit OAuth client; Reddit no longer accepts Sync's. |

Spoof client defaults to [RedReader](https://github.com/QuantumBadger/RedReader)'s client. If Reddit stops accepting
it, set all three options to another "installed app" client: `OAuth client ID`, `Redirect URI` (the one registered
for that ID) and `User-Agent` (the one that app sends). Then remove your accounts in Sync and add them again: their
tokens belong to the old client.

## Contribute

See [AGENTS.md](AGENTS.md).

## License

[AGPL-3.0-only](LICENSE).
