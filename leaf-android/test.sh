#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_dir="${LEAF_TOOLCHAIN_DIR:-$project_dir/../android-toolchain}"
tests_dir="$project_dir/build/tests"
mkdir -p "$tests_dir"
find "$project_dir/tests" -name '*.java' > "$project_dir/build/test-sources.txt"
for source in Core Book Importer ReaderGesture ReadAhead SecretCodec; do
 printf '%s\n' "$project_dir/src/com/leafreader/app/$source.java" >> "$project_dir/build/test-sources.txt"
done
java -jar "$sdk_dir/ecj.jar" -8 -warn:none -encoding UTF-8 -classpath "$sdk_dir/android-35/android.jar" -d "$tests_dir" @"$project_dir/build/test-sources.txt"
classpath="$tests_dir:$sdk_dir/android-35/android.jar"
java -cp "$classpath" CoreTest
java -cp "$classpath" ReaderGestureTest
java -cp "$classpath" ReadAheadTest
java -cp "$classpath" SecretCodecTest
java -cp "$classpath" com.leafreader.app.ImporterTest "$@"
