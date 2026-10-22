# Weak-range flop coverage study

This offline study checks every declared BTN holding in two contexts: after BB checks and facing BB's half-pot bet. It reuses one physical game, attempts each case once in a fixed order, and retains both successful interval evidence and typed numerical rejections. It helps choose further solver work without selecting only successful examples.

The frozen request contains four holdings and eight cases, in this order: 2c 3c, Ah Kh, As Qs and Qh Th, each after check then bet. It uses the same supplied conditional joint ranges as the qualified weak-hand source, board **8d Kc Kd**, preflop continuation **BB call**, a **6.5 bb** pot and **3.25 bb** bet. The game has two active players at a six-seat table, no rake, one flop bet and exact checkdown. Its supplied ranges differ from the original ten-question collection.

## Actual results

| BTN holding | Facing bet: action within 0.01 bb | Other action: certified minimum loss | After BB checks |
|---|---|---|---|
| 2c 3c | Fold | Call: 2.249499749 bb | Numerical rejection |
| Ah Kh | Call | Fold: 8.516862588 bb | Numerical rejection |
| As Qs | Call | Fold: 3.162948975 bb | Numerical rejection |
| Qh Th | Call | Fold: 1.109022630 bb | Numerical rejection |

All four after-check cases return `CONDITIONAL_REJECTED / NUMERICAL_FAILURE` with `Resolvable refined endpoint witness failed: Invalid behavioral ratio`. They have no conditional report, posterior or action classifications. This is a numerical reconstruction failure, not proof of poker infeasibility, zero reach or an unprofitable action. The study preserves it and proceeds to the next distinct planned case; it never retries the rejected question with another algorithm or tolerance.

The weak fold report is exactly equal to the already committed standalone conditional report. The other three facing-bet results are interval candidates under these changed supplied ranges. **No fresh CFR reference runs occur in this study.** Action separation alone does not qualify a lesson, create a grading handle or extend the eleven-question website collection.

## Implementation and bounds

`SixMaxRefinedFlopCoverageStudy.Request` allows one to four distinct supplied BTN hands and fixes the explicit v8 endpoint mode. It generates both contexts for every hand. Invalid handoff/context selection aborts before any LP, rather than becoming a numerical outcome. Every successful case uses the existing complete conditional study; the survey does not duplicate or relax its optimizer, security face, reach floor, certificates or physical checks.

For four hands, the call reservation is one physical game, eight case attempts, at most sixteen action computations and twenty-four parent solves. Each conditional study may reconstruct its parent once plus once inside each of its two action computations. The original per-LP, compiler and per-action aggregate limits still apply. Rejected internal work counters are unavailable: the reservation is a bound on calls, not a claim that rejected work cost zero or a complete measured arithmetic inventory.

Reports retain the complete request, physical binding, ordered cases, successful conditional reports and all move classifications, or a typed rejection. Counts and classifications are reconstructed during deserialization. The private `Result` constructor is available only through complete solving/replay and provides no trainer capability. Unexpected input, programming and I/O errors abort; only the existing typed LP and conditional rejections become case outcomes.

The CLI supports:

```text
SixMaxRefinedFlopCoverageStudyMain solve <request.json> <new-report.json[.gz]>
SixMaxRefinedFlopCoverageStudyMain replay <request.json> <report.json[.gz]>
SixMaxRefinedFlopCoverageStudyMain inspect <report.json[.gz]>
```

Run from the repository root with solver dependencies on the Java classpath. `solve` refuses an existing output and aliases before expensive work. `replay` reconstructs every case and requires complete report equality. `inspect` performs no solving and prints all eight outcomes with `storedSnapshotOnly=true`; inspecting JSON does not verify its numerical evidence. Both expanded and stored report bytes use the existing 8 MiB cap.

## Evidence and next step

The request, report, prior generation reservation and independent receipt are separate `docs/data/*weak-range*coverage*` files. The generated Java report hash is `8a4a1f7c7c038b6de8d7d7488031e8e6c5c5ec9c228bfff0afd8a446d6560028`. All 279 prior evidence files retain their bytes.

The independent Python checker reconstructs the 24 physical worlds, all 15,792,192 source boards, the literal 1,321-state game, original parent LP witnesses and best responses. It checks all 32 endpoint certificates for the four successes, original full-path reach/posteriors, action values, losses, classifications and successful-case work charges. Its receipt explicitly says the rejected cases are **not independently certified**; full Java replay reproduces those production failures. The corruption controls cover order, omitted cases, admission, reservations, failure relabelling, strong-hand certificates, posteriors, source identity and move classifications.

The next numerical milestone is to capture and explain the common behavioral-ratio reconstruction failure while preserving original-security and posterior checks. Any correction needs its own explicit algorithm/version, original-problem certificates and regressions; clipping a bad conditional ratio or lowering the reach requirement is not a substitute. After that, run fresh-reference qualification for useful candidates before adding another trainer source. Broader ranges, street betting, rake and six-active-player equilibrium remain separate model work.
