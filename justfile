# SPDX-License-Identifier: AGPL-3.0-only
set shell := ["bash", "-euo", "pipefail", "-c"]

cli_version := "6.0.0"
cli_sha256 := "c25549bc17d59d2eb94fa5f86e60e9b77a02772ca88f7050f8f1276f923a9958"
jadx_sha256 := "6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75"

# List the recipes
default:
    @just --list

# Run what CI runs: the prek hooks, then lint, test and build the bundle
check:
    prek run --all-files --hook-stage manual
    ./gradlew check :patches:buildAndroid

# Build patches/build/libs/patches-<version>.rvp
build:
    ./gradlew :patches:buildAndroid

# Apply the locally built bundle to an APK; the result lands in tmp/out/
[positional-arguments]
patch apk *args: build
    #!/usr/bin/env bash
    set -euo pipefail
    apk=$1
    shift
    cli=.tools/revanced-cli-{{ cli_version }}-all.jar
    just _fetch "https://github.com/ReVanced/revanced-cli/releases/download/v{{ cli_version }}/revanced-cli-{{ cli_version }}-all.jar" "$cli" {{ cli_sha256 }}
    # The aapt2 inside the CLI is x86_64-only: on arm64 Linux it runs under qemu-user-binfmt.
    if [[ "$(uname -sm)" == "Linux aarch64" ]]; then export QEMU_LD_PREFIX=/usr/x86_64-linux-gnu; fi
    version=$(sed -n 's/^version=//p' gradle.properties)
    mkdir -p tmp/out
    # -b: a local build is unsigned, so skip signature verification.
    "${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar "$cli" patch -b -p "patches/build/libs/patches-$version.rvp" \
        -o "tmp/out/$(basename "$apk" .apk)-patched.apk" "$@" "$apk"

# Install this checkout's rea pin where every worktree finds it, vendor its skill and check Ghidra and JADX
setup-rea:
    #!/usr/bin/env bash
    set -euo pipefail
    install=$(cd "$(git rev-parse --path-format=absolute --git-common-dir)/.." && pwd)/.rea/install
    mkdir -p "$install"
    cp .rea/package.json .rea/package-lock.json "$install/"
    npm ci --prefix "$install" --no-audit --no-fund
    just _fetch https://github.com/1013503897/jadx-headless-mcp/releases/download/v0.7.1/jadx-headless-mcp-0.7.1-all.jar "$install/jadx-headless-mcp-0.7.1-all.jar" {{ jadx_sha256 }}
    # The skill must match the installed server, so it comes from the package, never from rea's main branch.
    skills=.agents/skills
    rm -rf "$skills/reverse-engineer-anything"
    mkdir -p "$skills"
    cp -R "$install/node_modules/rea-agents/skills/reverse-engineer-anything" "$skills/"
    cp "$install/node_modules/rea-agents/LICENSE" "$skills/reverse-engineer-anything.LICENSE"
    scripts/rea.sh doctor --provider ghidra --provider jadx --json \
        | jq -e '[.scope_checks[] | select(.ok | not)] as $failed | if $failed == [] then "rea: Ghidra and JADX ready" else ($failed | error) end'

# Download a pinned file unless it is already there with the right SHA-256
[positional-arguments]
_fetch url path sha256:
    #!/usr/bin/env bash
    set -euo pipefail
    url=$1 path=$2 sha256=$3
    if [[ -f "$path" ]] && echo "$sha256  $path" | sha256sum -c --status -; then exit 0; fi
    mkdir -p "$(dirname "$path")"
    curl -fsSL --retry 3 -o "$path.part" "$url"
    echo "$sha256  $path.part" | sha256sum -c --quiet -
    mv "$path.part" "$path"
