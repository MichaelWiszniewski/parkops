#!/usr/bin/env bash
# Captures genuine D2 evidence into results/, one file per command, each with date, environment,
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

capture d2-check.txt     bash run.sh check
capture d2-junit.txt     bash run.sh test
capture d2-scripted.txt  bash run.sh run scripted
capture d2-periodic.txt  bash run.sh run periodic
