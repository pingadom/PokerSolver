"""Independent test oracle only; SciPy is never a production solver dependency.

Run from the repository root with NumPy and SciPy installed:
    python scripts/generate-maxmin-controls.py <new-output.json.gz>
The saved literal matrices remain authoritative across library RNG/version changes.
"""
import gzip
import json
import sys
from pathlib import Path

import numpy as np
import scipy
from scipy.optimize import linprog


def generate():
    rng = np.random.default_rng(261)
    controls = []
    for i in range(120):
        m, n = int(rng.integers(1, 25)), int(rng.integers(1, 25))
        if i % 5 == 0:
            matrix = rng.integers(-20, 20, (m, 1)) + rng.integers(-20, 20, (1, n))
        elif i % 5 == 1:
            matrix = np.zeros((m, n)) + rng.uniform(-100, 100)
        elif i % 5 == 2:
            matrix = rng.integers(-100, 101, (m, n)).astype(float)
            if m > 1:
                matrix[-1] = matrix[0]
            if n > 1:
                matrix[:, -1] = matrix[:, 0]
        else:
            matrix = rng.uniform(-100, 100, (m, n))
        # Independently formulate both original-payoff LPs, without the owned solver's shift.
        column = linprog(
            np.r_[np.zeros(n), 1], A_ub=np.c_[matrix, -np.ones(m)],
            b_ub=np.zeros(m), A_eq=[np.r_[np.ones(n), 0]], b_eq=[1],
            bounds=[(0, None)] * n + [(None, None)], method="highs",
        )
        row = linprog(
            np.r_[np.zeros(m), -1], A_ub=np.c_[-matrix.T, np.ones(n)],
            b_ub=np.zeros(n), A_eq=[np.r_[np.ones(m), 0]], b_eq=[1],
            bounds=[(0, None)] * m + [(None, None)], method="highs",
        )
        if not column.success or not row.success:
            raise RuntimeError("Independent oracle failed")
        p, q = row.x[:m], column.x[:n]
        lower, upper = float(np.min(p @ matrix)), float(np.max(matrix @ q))
        if (min(p) < -1e-12 or min(q) < -1e-12
                or abs(sum(p)-1) > 1e-12 or abs(sum(q)-1) > 1e-12
                or upper-lower > 1e-8):
            raise RuntimeError("Independent original-matrix certificate failed")
        controls.append(dict(name=f"control-{i}", values=matrix.tolist(),
                             rowMixture=p.tolist(), columnMixture=q.tolist(),
                             lowerValue=lower, upperValue=upper))
    return dict(schemaVersion="finite-maxmin-independent-controls/v1",
                purpose="TEST_ORACLE_ONLY_NOT_POKER_STRATEGY_DATA", seed=261,
                reference=f"SciPy {scipy.__version__} linprog HiGHS, two original-payoff LPs",
                numpyVersion=np.__version__, controls=controls)


if __name__ == "__main__":
    output = Path(sys.argv[1])
    if output.exists():
        raise ValueError("Output must be a new path")
    data = json.dumps(generate(), allow_nan=False, separators=(",", ":")).encode()
    with output.open("xb") as stream:
        stream.write(gzip.compress(data, mtime=0))
    print(f"Saved 120 independent controls; {len(data)} decompressed bytes")
