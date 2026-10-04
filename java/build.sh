#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
build_dir=${JAVA_BUILD_DIR:-/workspace/cobol-java-build}
runtime_dir=${COBOL4J_HOME:-/workspace/toolchains/cobol4j}
mkdir -p "$build_dir/classes"
if [ ! -f "$runtime_dir/lib/libcobj.jar" ]; then
    python3 tools/bootstrap.py
fi
cp conversion.json "$build_dir/classes/conversion.json"
find src/main/java generated -name '*.java' -print > "$build_dir/sources.txt"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    "$JAVA_HOME/bin/javac" --release 21 -encoding UTF-8 -cp "$runtime_dir/lib/*" -d "$build_dir/classes" @"$build_dir/sources.txt"
elif command -v javac >/dev/null 2>&1; then
    javac --release 21 -encoding UTF-8 -cp "$runtime_dir/lib/*" -d "$build_dir/classes" @"$build_dir/sources.txt"
elif [ -x /workspace/toolchains/java/usr/lib/jvm/java-21-openjdk-amd64/bin/javac ]; then
    /workspace/toolchains/java/usr/lib/jvm/java-21-openjdk-amd64/bin/javac --release 21 -encoding UTF-8 -cp "$runtime_dir/lib/*" -d "$build_dir/classes" @"$build_dir/sources.txt"
else
    java com.sun.tools.javac.Main -source 21 -target 21 -encoding UTF-8 -cp "$runtime_dir/lib/*" -d "$build_dir/classes" @"$build_dir/sources.txt"
fi
