#!/usr/bin/env bash
# Captures genuine D3 evidence into results/, one file per command, each with date, environment,
# the exact command, its unedited output and its exit status. Run from anywhere; it uses the project root.
# Live mode is not run here: it is interactive (private key prompt, operator decision) and saves its own
# transcript under results/live-run-*.txt.
set -uo pipefail
cd -- "$(dirname -- "$0")"
mkdir -p results
env_line="Environment: $(uname -srm) | $(java -version 2>&1 | head -n 1)"

capture() {
  local file="results/$1"; shift
  local out status
  out=$("$@" 2>&1); status=$?
  {
    echo "Date: $(date)"
    echo "$env_line"
    echo "\$ $*"
    echo
    printf '%s\n' "$out"
    echo
    echo "Exit status: $status"
  } > "$file"
  echo "wrote $file (exit status $status)"
}

# D3 evidence. The d2-*.txt files already in results/ are the D2 submission's evidence and are kept as-is.
capture d3-check.txt     bash run.sh check
capture d3-junit.txt     bash run.sh test
capture d3-scripted.txt  bash run.sh run d3-scripted
capture d3-d2-scripted.txt bash run.sh run scripted
capture d3-periodic.txt  bash run.sh run periodic
# D3.3 static analysis. run.sh lint covers src/ only; the second command adds -Xlint:all to the plain and JUnit
# test sources (run.sh compiles JUnit tests without -Xlint). Both write only to the throwaway build/ directory.
capture d3-lint-src.txt  bash run.sh lint
capture d3-lint-tests.txt bash -c 'bash run.sh check >/dev/null && javac --release 21 -encoding UTF-8 -Xlint:all -cp "build:lib/junit-platform-console-standalone-6.0.3.jar" -d build/lint-tests test/junit/*.java && echo TESTS_LINT_OK'
