# Engine contracts

The framework-free `engine` module preserves the original hand evaluator and CLI. Requests validate 2–9 distinct named players, exact hole cards and 0–5 known board cards. All known cards must be unique. Missing board cards are sampled uniformly without replacement. One/two-card boards represent partial information rather than a normal betting street.

`SimulationConfiguration` caps a request at 100 million iterations, one million iterations per batch and ten thousand batches. The API may impose tighter limits. `BatchPlanner` includes a final partial batch, deriving reproducible seeds using a bijective SplitMix64 mixer. Reproducibility requires the same engine version, parent seed, scenario and batch size. Runtime measurements are not deterministic.

Wins mean outright wins; ties count participation in a split pot; losses equal trials minus wins minus ties. Equity shares count fractional pots, divided by trials for equity. Shares use IEEE 754 double precision; aggregation accepts a relative conservation tolerance of 1e-10. Results must be weighted by trials, never averaged across unequal batches.

The evaluator uses an allocation-light score path when verbose diagnostics are disabled. Board sampling currently allocates a short list and index array per trial. Measure before replacing this with a more complex implementation.
