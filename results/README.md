# Evidence

Every file here is unedited command output from my own machine, with the date, environment and command.
Replay and live runs are kept separate and labelled; a plain-Java check run is not a JUnit run.

| File | What it is | How it was produced |
|---|---|---|
| d1.txt | D1 evidence (compile, check, scripted, JUnit) | by hand for D1, 9 Oct 2026 |
| d2-check.txt | Supplied public checks (plain Java, not JUnit) | `bash capture-results.sh` |
| d2-junit.txt | Full JUnit Jupiter 6.0.3 run (supplied tests and mine) | `bash capture-results.sh` |
| d2-scripted.txt | D2 workflow with the ReplayProvider and a scripted operator. REPLAY, not a live model | `bash capture-results.sh` |
| d2-periodic.txt | Periodic trace: ticks 0, 59, 60, a backwards tick, 119, 120 | `bash capture-results.sh` |
| live-run-*.txt | LIVE Gemini runs (gemini-3.5-flash-lite) with a human operator decision; no key included | `bash run.sh run live` |

Live runs kept (none deleted or edited), all on 2026-11-06, Toronto time:
11:50:45 approve (COMMITTED, then DUPLICATE); 11:57:54 defer (left PENDING);
11:58:27 and 11:58:49 reject (NOT_APPROVED, booking unchanged).
