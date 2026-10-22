#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_dir="${LEAF_TOOLCHAIN_DIR:-$project_dir/../android-toolchain}"
: "${LEAF_KEYSTORE:?Set LEAF_KEYSTORE to a private keystore outside the repository}"
: "${LEAF_KEY_ALIAS:?Set LEAF_KEY_ALIAS}"
: "${LEAF_KEYSTORE_PASSWORD:?Set LEAF_KEYSTORE_PASSWORD privately}"
export LEAF_KEY_PASSWORD="${LEAF_KEY_PASSWORD:-$LEAF_KEYSTORE_PASSWORD}"
version="$(python3 - "$project_dir/AndroidManifest.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
print(ET.parse(sys.argv[1]).getroot().attrib['{http://schemas.android.com/apk/res/android}versionName'])
PY
)"
mkdir -p "$project_dir/../dist"
output="$project_dir/../dist/LeafReader-Android-$version.apk"
"$sdk_dir/android-15/apksigner" sign --ks "$LEAF_KEYSTORE" --ks-key-alias "$LEAF_KEY_ALIAS" --ks-pass env:LEAF_KEYSTORE_PASSWORD --key-pass env:LEAF_KEY_PASSWORD --out "$output" "$project_dir/build/aligned.apk"
"$sdk_dir/android-15/apksigner" verify --verbose "$output"
printf 'Signed APK: %s\n' "$output"
