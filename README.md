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

For `com.laurencedawson.reddit_sync` version `v23.06.30-13:39`.

| Patch | What it does |
| --- | --- |
| Spoof client | Logs in and browses with another app's Reddit OAuth client, since Reddit no longer accepts Sync's own. |

**Spoof client** uses [RedReader](https://github.com/QuantumBadger/RedReader)'s public client by default: client ID
`yH0aTnJEt6qUgGn835B4vg`, redirect URI `redreader://rr_oauth_redir` and User-Agent
`org.quantumbadger.redreader/1.27`. Reddit may stop accepting that client one day. Then set all three options to
another app's client of Reddit's "installed app" type (one without a secret): its client ID, the redirect URI
registered for it, and the User-Agent that app sends. Reddit now approves new API clients by hand, so you can't simply create your own.

## Contribute

See [AGENTS.md](AGENTS.md).

## License

[AGPL-3.0-only](LICENSE).
