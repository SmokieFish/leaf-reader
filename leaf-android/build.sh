#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
sdk_dir="${LEAF_TOOLCHAIN_DIR:-$project_dir/../android-toolchain}"
build_dir="$project_dir/build"
# Always start clean: removed Java classes must not remain inside the APK.
rm -rf "$build_dir/classes" "$build_dir/gen" "$build_dir/dex"
mkdir -p "$build_dir/gen" "$build_dir/classes" "$build_dir/dex"
"$sdk_dir/android-15/aapt2" compile --dir "$project_dir/res" -o "$build_dir/resources.zip"
"$sdk_dir/android-15/aapt2" link -o "$build_dir/base.apk" -I "$sdk_dir/android-35/android.jar" --manifest "$project_dir/AndroidManifest.xml" --java "$build_dir/gen" --min-sdk-version 26 --target-sdk-version 35 "$build_dir/resources.zip"
find "$project_dir/src" "$build_dir/gen" -name '*.java' -print | LC_ALL=C sort > "$build_dir/sources.txt"
java -jar "$sdk_dir/ecj.jar" -8 -warn:none -encoding UTF-8 -classpath "$sdk_dir/android-35/android.jar" -d "$build_dir/classes" @"$build_dir/sources.txt"
python3 - "$build_dir" <<'PY'
from pathlib import Path
import zipfile,sys
p=Path(sys.argv[1])
with zipfile.ZipFile(p/'classes.jar','w') as z:
 for f in sorted((p/'classes').rglob('*.class')):z.write(f,f.relative_to(p/'classes'))
PY
"$sdk_dir/android-15/d8" --release --min-api 26 --lib "$sdk_dir/android-35/android.jar" --output "$build_dir/dex" "$build_dir/classes.jar"
python3 - "$build_dir" <<'PY'
from pathlib import Path
import zipfile,shutil,sys
p=Path(sys.argv[1]);shutil.copy(p/'base.apk',p/'unsigned.apk')
with zipfile.ZipFile(p/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in sorted((p/'dex').glob('*.dex')):z.write(f,f.name)
PY
"$sdk_dir/android-15/zipalign" -f -p 4 "$build_dir/unsigned.apk" "$build_dir/aligned.apk"
printf 'Unsigned aligned APK: %s\n' "$build_dir/aligned.apk"
