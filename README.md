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

## Contribute

See [AGENTS.md](AGENTS.md).

## License

[AGPL-3.0-only](LICENSE).
