# Exact row scaling for later flop LPs

The existing qualified initial-flop report uses `solveResolvableCosts`. Its solver identity,
limits and saved certificates are unchanged. Later physical-flop questions exposed three
different LP failures under that representation: one work-limit rejection and two singular
bases. Those failures do not establish that the original problems are infeasible.

`solveExactPowerOfTwoRows` is a separately selected representation of the same bounded LP.
For each row it chooses a positive power of two from the row's coefficient L1 norm plus the
absolute right-hand side, multiplies the entire inequality by that factor, then invokes the
existing ordered, resolvable-cost solver. It refuses a transformation if any finite original
coefficient cannot be recovered bit for bit, including signed zero and nonzero underflow.
It maps the resulting duals back to the original row order and units. Before returning a
result, it independently checks primal feasibility, dual feasibility and the duality gap
against the copied original LP, using the unchanged `1e-8` certificate tolerance and the
original problem hash. Preparation, nested solve and certificate work share the unchanged
four-billion-operation and ten-thousand-pivot caps. Failure returns no result.

The four checked-in physical LP fixtures came from the owned full-parent flop action-bound
calculation. LP 3 (81 variables, 80 constraints) previously exhausted work; LPs 11 and 16
(80/85 and 82/83) previously failed at singular bases; LP 6 (79/82) is a low-reach control
whose optimum remains zero. Their independent research-oracle objectives are stored with
the original matrices in `solver/src/test/resources/bounded-lp-flop-equilibration-controls.json.gz`.
The production test checks the returned certificates on the original matrices; it does
not rely on the oracle solver at runtime. A further 292 independent LPs under four row
representations cover ordinary optima, row permutations and positive scaling.

This method is deliberately not an automatic fallback. No current action-bound path calls
it, so the existing six-action report and trainer drill remain exactly reproducible. The
next later-decision milestone must select this mode before optimization, version the
request/report and demonstrate full physical replay, fresh full-parent reference inclusion,
actual and counterfactual path reach, and all legal action bounds. A successful individual
LP certificate alone is not trainer admission.
