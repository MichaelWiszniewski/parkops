#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
task="compile"
if [ "$#" -gt 0 ]; then task="$1"; shift; fi
case "$task" in compile|run|check|test|lint|smoke) ;; *) echo 'Usage: bash run.sh compile|run|check|test|lint|smoke [program arguments]' >&2; exit 2;; esac
compiler=javac
runner=java
if [ -n "${COURSE_JAVA_HOME:-}" ]; then compiler="$COURSE_JAVA_HOME/bin/javac"; runner="$COURSE_JAVA_HOME/bin/java";
elif [ -n "${JAVA_HOME:-}" ]; then compiler="$JAVA_HOME/bin/javac"; runner="$JAVA_HOME/bin/java"; fi
command -v "$compiler" >/dev/null || { echo 'JDK 21 required. Configure COURSE_JAVA_HOME or PATH.' >&2; exit 2; }
if [ -L build ]; then echo 'Refusing symbolic-link build directory' >&2; exit 2; fi
rm -rf -- ./build
mkdir build
find src -name '*.java' -print | LC_ALL=C sort > build/sources.txt
"$compiler" --release 21 -encoding UTF-8 -Xlint:all -d build @build/sources.txt
if [ "$task" = compile ] || [ "$task" = lint ]; then echo 'COMPILE_OK (warnings, if any, are printed above)'; exit 0; fi
if [ "$task" = run ]; then exec "$runner" -cp build Main "$@"; fi
find test/plain -name '*.java' -print | LC_ALL=C sort > build/plain.txt
"$compiler" --release 21 -encoding UTF-8 -Xlint:all -cp build -d build @build/plain.txt
if [ "$task" = check ]; then exec "$runner" -cp build PublicChecks; fi
if [ "$task" = smoke ]; then exec "$runner" -cp build InfrastructureChecks; fi
jar="${JUNIT_JAR:-$PWD/lib/junit-platform-console-standalone-6.0.3.jar}"
[ -f "$jar" ] || { echo 'JUnit not cached. Run bash setup-junit.sh or set JUNIT_JAR to the TA-provided jar.' >&2; exit 2; }
if command -v sha256sum >/dev/null; then actual=$(sha256sum "$jar" | cut -d ' ' -f 1); else actual=$(shasum -a 256 "$jar" | cut -d ' ' -f 1); fi
[ "$actual" = 3ba0d6150af79214a1411f9ea2fbef864eef68b68c89a17f672c0b89bff9d3a2 ] || { echo 'JUnit checksum mismatch' >&2; exit 2; }
sep=':'
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) sep=';';; esac
find test/junit -name '*.java' -print | LC_ALL=C sort > build/junit.txt
"$compiler" --release 21 -encoding UTF-8 -cp "build$sep$jar" -d build @build/junit.txt
exec "$runner" -jar "$jar" execute --class-path build --scan-class-path --fail-if-no-tests --disable-ansi-colors --details summary
