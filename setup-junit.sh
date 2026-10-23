#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
mkdir -p lib
dest=lib/junit-platform-console-standalone-6.0.3.jar
curl --fail --location --proto '=https' --tlsv1.2 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/6.0.3/junit-platform-console-standalone-6.0.3.jar' -o "$dest.download"
if command -v sha256sum >/dev/null; then actual=$(sha256sum "$dest.download" | cut -d ' ' -f 1); else actual=$(shasum -a 256 "$dest.download" | cut -d ' ' -f 1); fi
[ "$actual" = 3ba0d6150af79214a1411f9ea2fbef864eef68b68c89a17f672c0b89bff9d3a2 ] || { echo 'JUnit checksum mismatch; dependency not installed' >&2; exit 2; }
mv -- "$dest.download" "$dest"
echo 'JUnit 6.0.3 cached and SHA-256 verified.'
