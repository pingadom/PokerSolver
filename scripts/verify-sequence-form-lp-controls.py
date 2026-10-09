"""Recompute the saved independent LP oracle; optional research NumPy/SciPy, never runtime deps.

Run from any directory: python scripts/verify-sequence-form-lp-controls.py [fixture.json.gz]
The fixture holds literal original inequalities, so this check does not use PokerLab's LP or
sequence-form reduction. It creates no output files and does not modify the saved controls.
"""

import argparse
import gzip
import json
from pathlib import Path

import numpy as np
import scipy
from scipy.optimize import linprog


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture", nargs="?", type=Path, default=(
        Path(__file__).resolve().parents[1]
        / "solver/src/test/resources/bounded-lp-oracle-controls.json.gz"))
    args = parser.parse_args()
    with gzip.open(args.fixture, "rb") as stream:
        expanded = stream.read(8 * 1024 * 1024 + 1)
    if len(expanded) > 8 * 1024 * 1024:
        raise ValueError("Oracle fixture exceeds expanded byte bound")
    fixture = json.loads(expanded)
    if fixture["schemaVersion"] != "independent-bounded-lp-controls/v1":
        raise ValueError("Unknown controls schema")
    problems = fixture["problems"]
    if len(problems) != 292 or sum(p["group"] == "sequence" for p in problems) != 52:
        raise ValueError("Expected 52 sequence-form and 240 generic original LPs")
    worst_value_error = 0.0
    for problem in problems:
        a = np.asarray(problem["matrix"], dtype=float)
        b = np.asarray(problem["rhs"], dtype=float)
        c = np.asarray(problem["cost"], dtype=float)
        if (a.shape != (len(b), len(c)) or not np.isfinite(a).all()
                or not np.isfinite(b).all() or not np.isfinite(c).all()):
            raise ValueError("Malformed literal LP")
        result = linprog(-c, A_ub=a, b_ub=b, bounds=(0, None), method="highs", options={
            "primal_feasibility_tolerance": 1e-9,
            "dual_feasibility_tolerance": 1e-9,
        })
        if not result.success:
            raise AssertionError((problem["group"], problem["id"], result.message))
        x, y = result.x, -result.ineqlin.marginals
        error = abs(float(c @ x) - problem["expectedMaximum"])
        if (error > 1e-8 or np.min(x) < -1e-8 or np.min(y) < -1e-8
                or np.max(a @ x - b) > 1e-8 or np.max(c - a.T @ y) > 1e-8
                or abs(float(c @ x - b @ y)) > 1e-8):
            raise AssertionError((problem["group"], problem["id"], "Original certificate failed"))
        worst_value_error = max(worst_value_error, error)
    print(json.dumps({"status": "PASS_INDEPENDENT_MATHEMATICAL_CONTROLS_NOT_ADMISSION",
                      "problems": len(problems), "scipyVersion": scipy.__version__,
                      "numpyVersion": np.__version__, "maximumValueError": worst_value_error}))


if __name__ == "__main__":
    main()
