# Revised preflop models with exact payoff reuse

The retained-coverage study identified a model limitation: the 100bb `[3,100]` menu makes all-in play common and its improved connected policies retain very little useful heads-up content. This follow-up changes the declared betting menu, solves each new game from scratch, and measures its reached content separately from strategy quality.

`SixMaxPreflopPayoffReuse` reuses **exact showdown shares**, not strategies or continuation values. With the same six physical hands and active-seat subset, the undealt deck and showdown shares are unchanged by preflop bet sizes, starting stacks, rake or range weights. The target game recomputes root probabilities, chip commitments, rake deductions, terminal utilities, information sets, policy and its own best-response score.

The source must pass the existing strict pack validation and declare exact enumeration: every subset has 658,008 board trials and zero sampling error. Target support must use exactly the same physical six-hand deals, including folded hands. Missing or extra worlds fail before solving. Range weights can change, but pruning a world or adding a new card combination requires a new payoff calculation. Synthetic exact fixtures in unit tests are deliberately test-only inputs; the saved research packs use the previously enumerated physical payoffs.

No source policy rows or connected checkpoint transfer to the target. Source/target spot hashes, source pack hash, rule menus, reused entry count and independent scores are saved in `six-max-preflop-payoff-reuse/v1`. The target is a normal, strictly reloadable preflop pack. Historical schemas and hashes are unchanged. All artifacts remain `VALIDATION_ONLY`.

## Measured source models

All three sources have the same twelve correlated physical deals, the same six seat ranges, 100bb stacks, 0.5bb small blind and no rake. Every seat acts. Each uses 500 exhaustive CFR+ iterations in its own mandatory-checkdown game.

| Global raise targets | Public states | NashConv in its own game (bb) | Heads-up continuation mass | All-in mass |
| --- | ---: | ---: | ---: | ---: |
| Original `[3,100]` | 12,832 | 0.0453440071 | 0.0254253173 | 0.6442526472 |
| Revised `[3,9]` | 12,832 | 0.0003349793 | 0.4136305768 | 0 |
| Control `[3]` | 1,360 | 0.0000943110 | 0.7496589378 | 0 |

These scores measure different games; the table is not evidence that one policy improves another or that a solver algorithm converges faster. The smaller game is easier. All non-all-in source pots still check down after preflop. `[3,9]` permits an open to 9bb as well as a raise from 3bb to 9bb: these are global targets, not position/history-dependent sizing. `[3]` permits one raise to 3bb, with no 3-bet or preflop shove. Neither is a realistic complete cash game.

Adding `[3,9,100]` exceeds the existing 20,000-public-state cap before payoffs or solving. This rejection is tested. No cap was raised and no private worlds or legal branches were silently removed. The two revised sources each reuse all **684** exact deal/subset entries. The original source remains unchanged.

## Reach is insufficient on its own

Both new sources pass the optimistic public-history reach feasibility bounds for two histories. Sixteen seeds, 711–726, nevertheless find no qualifying two-history menu under the existing **16 compatible deal/flop pairs / 2,000,000 complete states** budget and unchanged content thresholds. The search preserves every counterfactual root; it does not prune private deals to fit a board.

The `[3,9]` two-history search records fifteen budget rejections and one missing diverse menu. Its separate one-history search fits all sixteen candidates within budget, but each fails retained-content criteria: the histories with sufficiently varied active hands are too rare and cover too little heads-up mass. Moving all-in probability into checkdown pots alone does not repair useful decisions.

The `[3]` two-history search records thirteen budget rejections and three content rejections. A narrower **one-history** menu passes at seed 711 without lowering any content cutoff:

- UTG folds, HJ folds, CO raises to 3bb, BTN folds, SB folds, BB calls.
- BB acts first postflop against CO on `2h 3c Ks`, with a 6.5bb pot and 97bb remaining stacks.
- Connected bet sizes are 3.25bb, 6.5bb and 13bb; selected flop/turn/river betting remains heads-up.
- All twelve root worlds and twelve board-compatible worlds remain. Folded HJ and BTN uncertainty is preserved.
- The history reaches **0.3379070893**, covering **45.0748%** of heads-up history mass.
- BB retains approximately 50/50 `77` / `AJ`; CO retains approximately 43/57 `AK` / `QT`. Both players have two material exact combos.
- Cost is **12 compatible pairs / 1,323,133 complete states**. Selected absolute physical-flop reach is **0.0000342011**; a single exact flop still covers only 1/9,880 physical boards given this history.

This pass describes the source policy and selected board. It does not establish the final connected policy's quality, stability or trainer admission. Fresh connected solves must recompute those checks after preflop and postflop learning; no old checkpoint can be resumed across the new source/menu identity.

## Reproduce the model comparison

Build first, then use separate output paths. Commands write local outputs; the saved packs and reports in `docs/data` are the recorded inputs/evidence.

```powershell
mvn -q -pl solver -am install -DskipTests
foreach ($model in 'three-nine', 'three-open') {
    $timestamp = if ($model -eq 'three-nine') { '2026-10-06T12:15:00Z' } else { '2026-10-06T12:16:00Z' }
    foreach ($histories in 2, 1) {
        $report = if ($histories -eq 2) { 'payoff-reuse' } else { 'single-history-search' }
        mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxPreflopPayoffReuseMain' "-Dexec.args=docs/data/sixmax-correlated-source-pack.json docs/data/sixmax-$model-spot.json .local/sixmax-$model-source-pack.json .local/sixmax-$model-$report.json 500 $timestamp --search 711 16 $histories 1"
        if ($LASTEXITCODE -ne 0) { throw "Model comparison failed: $model / $histories" }
    }
}
```

The optional `--search` takes first seed, seed count (1–16), histories (1–4) and physical flops per history (1–4). Omit it for the fresh pack and terminal-reach audit only. The CLI's existing reach/menu audits require zero applied rake in both models; it rejects raked inputs before training. Use the `buildExact` API for raked-game rebuilds, which are separately tested. The CLI bounds training to 1–3,000 iterations and each input to 16 MiB. All four paths must be distinct, including normalized aliases and existing hard links. It builds and serializes all results before replacing either output. Each output uses an atomic rename and cleans temporary files; the two output replacements are not a filesystem-wide transaction if an I/O failure interrupts the second rename. Validation failures preserve both old outputs and both inputs.

The saved reports bind both model hashes, record full source/target reach, and retain every finite search attempt and rejection. Tests reload exact packs, compare all reused share arrays and masks with the original source, recompute same-game quality, reproduce menu coverage/cost, and enforce the existing tree cap. Small fixtures separately verify changed chip utilities and rake, changed root weights, source immutability, invalid metadata, sampled-source rejection, physical support changes, path aliases, input limits and output preservation.

## Fresh connected workflow

The one-history control uses the existing alternating v5 workflow, whose report includes independent strategy-quality and retained-content decisions. Two fresh seeds use 500 joint iterations, 300 initial conditional iterations, one round of 1,000 linear-vanilla preflop iterations and 300 postflop iterations. The conditional target is 0.05bb and required parent improvement is 0.000001bb. This is the same quality criterion for both seeds, fixed before running.

```powershell
foreach ($trialSeed in 711, 712) {
    mvn -q -pl solver exec:java '-Dexec.mainClass=com.pokerlab.solver.SixMaxAlternatingContinuationStudyMain' "-Dexec.args=docs/data/sixmax-three-open-source-pack.json .local/sixmax-three-open-alternating-$trialSeed.json .local/sixmax-three-open-policy-$trialSeed.json $trialSeed 500 300 1000 300 1 1 711 1 0.05 0.000001 --diverse-pairs 0.05 --preflop-algorithm LINEAR_VANILLA"
    if ($LASTEXITCODE -ne 0) { throw "Fresh connected solve failed: $trialSeed" }
}
```

Both fresh trials accept the complete round and pass the unchanged **retained** content screen:

| Fresh seed | Initial parent NashConv (bb) | Retained parent NashConv (bb) | Retained worst conditional gap (bb) | Retained selected history reach | Retained content |
| --- | ---: | ---: | ---: | ---: | --- |
| 711 | 0.0002107952 | 0.0000794667 | 0.0121469907 | 0.4996717706 | Pass |
| 712 | 0.0002126442 | 0.0000794694 | 0.0121689556 | 0.4996717734 | Pass |

The initial/retained comparison is within the same connected game for each seed. Both retained policies cover about **66.63%** of reached heads-up history mass. All twelve private worlds remain counterfactually available and positively reached on the selected board. BB retains approximately 50/50 `77` / `AJ`; CO retains approximately 33/67 `AK` / `QT`. Folded HJ and BTN uncertainty remains. Selected absolute physical-flop probability is approximately **0.0000505741**, still a single-board continuation model.

The intermediate preflop-only update is not acceptable on its own: its changed private ranges raise the conditional postflop gap to **4.8449bb / 5.0018bb**, despite the initial conditional gaps being about 0.0032bb. Re-solving all 126,200 postflop rows at the changed ranges restores the target and permits the complete round to pass. This illustrates why the whole-round gate audits the final connected policy instead of assuming that frozen continuation play remains accurate after preflop learning.

Each saved policy has **127,318 complete rows**. The exhaustive preflop stage visits 97,926,000 nodes and 50,112,000 terminals. Its frozen continuation table includes a six-seat utility vector for every one of the twelve original private deals. Reports [711](data/sixmax-three-open-alternating-seed-711.json) and [712](data/sixmax-three-open-alternating-seed-712.json) bind the fresh training budgets, source, menu, whole-round decision, retained policy hash and both source/retained coverage. The large policy checkpoints remain ignored local files.

An [independent-policy comparison](sixmax-connected-policy-stability.md) now reloads those checkpoints and recomputes quality, content and stage-normalized action-frequency disagreement. Two passing seeds are useful finite-game evidence, not a broad stability certificate or trainer admission. General 6-max cash play still needs wider physical hand support, history-dependent raise menus, multiway postflop betting, broader physical-board coverage and independent tests across more seeds and budgets. High public-history reach cannot substitute for those model gates.
