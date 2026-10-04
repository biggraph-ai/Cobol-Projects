#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash java/build.sh
build_dir=${JAVA_BUILD_DIR:-/workspace/cobol-java-build}
runtime_dir=${COBOL4J_HOME:-/workspace/toolchains/cobol4j}
if [ -n "${JAVA_HOME:-}" ]; then
    compiler="$JAVA_HOME/bin/javac"
elif command -v javac >/dev/null 2>&1; then
    compiler=javac
else
    compiler=/workspace/toolchains/java/usr/lib/jvm/java-21-openjdk-amd64/bin/javac
fi
"$compiler" --release 21 --add-modules jdk.httpserver -cp "$build_dir/classes:$runtime_dir/lib/*" -d "$build_dir/classes" java/src/test/java/org/biggraph/cobol/IntegrationChecks.java
java --add-modules jdk.httpserver -cp "$build_dir/classes:$runtime_dir/lib/*" org.biggraph.cobol.IntegrationChecks
