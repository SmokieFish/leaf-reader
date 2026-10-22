#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_dir="${LEAF_TOOLCHAIN_DIR:-$project_dir/../android-toolchain}"
mkdir -p "$project_dir/build/tests"
java -jar "$sdk_dir/ecj.jar" -8 -warn:none -encoding UTF-8 -d "$project_dir/build/tests" "$project_dir/src/com/leafreader/app/Core.java" "$project_dir/tests/CoreTest.java"
java -cp "$project_dir/build/tests" CoreTest
