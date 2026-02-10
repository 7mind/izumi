#!/usr/bin/env bash
set -euo pipefail

N="${1:-30}"
TEST="izumi.distage.testkit.distagesuite.interruption.InterruptionTestBlockingZIO_AllEffects"
TS="$(date +%Y%m%d-%H%M%S)"
LOG="/tmp/exchange/interruption-loop-${TS}.log"

if ! [[ "$N" =~ ^[0-9]+$ ]] || [ "$N" -le 0 ]; then
  echo "N must be a positive integer, got: $N" >&2
  exit 2
fi

echo "Running ${N} iterations (fresh sbt per iteration, fail-fast)"
echo "Log: ${LOG}"

for i in $(seq 1 "$N"); do
  ITER_LOG="/tmp/exchange/interruption-loop-${TS}-iter-${i}.log"

  echo "===== ITERATION ${i}/${N} START $(date -Is) =====" | tee -a "$LOG"

  if direnv exec . sbt ";project distage-testkit-scalatestJVM; testOnly ${TEST}" > "$ITER_LOG" 2>&1; then
    cat "$ITER_LOG" >> "$LOG"

    ITER_NOT_INTERRUPTED="$(rg -c "second test was not interrupted" "$ITER_LOG" || true)"
    ITER_FAILED_MARKERS="$(rg -c "\\*\\*\\* [0-9]+ TESTS FAILED \\*\\*\\*" "$ITER_LOG" || true)"

    if [ "$ITER_NOT_INTERRUPTED" -gt 0 ] || [ "$ITER_FAILED_MARKERS" -gt 0 ]; then
      echo "===== ITERATION ${i}/${N} FAIL $(date -Is) =====" | tee -a "$LOG"
      echo "Detected flakiness markers in iteration ${i}: not-interrupted=${ITER_NOT_INTERRUPTED}, failed-markers=${ITER_FAILED_MARKERS}" | tee -a "$LOG"
      rm -f "$ITER_LOG"
      exit 1
    fi

    echo "===== ITERATION ${i}/${N} PASS $(date -Is) =====" | tee -a "$LOG"
  else
    cat "$ITER_LOG" >> "$LOG"
    echo "===== ITERATION ${i}/${N} FAIL $(date -Is) =====" | tee -a "$LOG"
    rm -f "$ITER_LOG"
    exit 1
  fi

  rm -f "$ITER_LOG"
done

PASSED="$(rg -c "===== ITERATION [0-9]+/[0-9]+ PASS" "$LOG" || true)"
FAILED_ITERS="$(rg -c "===== ITERATION [0-9]+/[0-9]+ FAIL" "$LOG" || true)"
FAILED_MARKERS="$(rg -c "\\*\\*\\* [0-9]+ TESTS FAILED \\*\\*\\*" "$LOG" || true)"
NOT_INTERRUPTED="$(rg -c "second test was not interrupted" "$LOG" || true)"

echo
echo "Summary:"
echo "  iterations requested: ${N}"
echo "  iteration pass count: ${PASSED}"
echo "  iteration fail count: ${FAILED_ITERS}"
echo "  failed run markers:   ${FAILED_MARKERS}"
echo "  not-interrupted logs: ${NOT_INTERRUPTED}"
echo "  log file: ${LOG}"

if [ "$FAILED_ITERS" -gt 0 ] || [ "$FAILED_MARKERS" -gt 0 ] || [ "$NOT_INTERRUPTED" -gt 0 ]; then
  echo "Detected failure/flakiness markers." >&2
  exit 1
fi
