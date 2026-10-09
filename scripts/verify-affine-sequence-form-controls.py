"""Optional independent NumPy/SciPy controls; never an application or CI dependency.

Verify the literal reduced LPs, then reconstruct original full-flow programs from synthetic
raise trees. Neither check invokes PokerLab's LP or affine compiler. No files are modified.
"""

import gzip
import json
from pathlib import Path

import numpy as np
import scipy
from scipy.optimize import linprog

ROOT = Path(__file__).resolve().parents[1] / "solver/src/test/resources"
OPTIONS = {"primal_feasibility_tolerance": 1e-9, "dual_feasibility_tolerance": 1e-9}


def read(name):
    with gzip.open(ROOT / name, "rb") as stream:
        data = stream.read(8 * 1024 * 1024 + 1)
    if len(data) > 8 * 1024 * 1024:
        raise ValueError("Oracle fixture exceeds expanded byte bound")
    return json.loads(data)


def original_full_flow(case):
    actors = [2, 5]
    infos = {actor: {} for actor in actors}
    leaves = []

    def walk(node, own, chance, depth):
        actor = node["actor"]
        if actor == -2:
            leaves.append((chance, [own[a][-1] if own[a] else None for a in actors], node["utilities"]))
            return
        if actor == -1:
            weights = node["probabilities"]
            assert len(weights) == len(node["children"]) and abs(sum(weights) - 1) < 1e-12
            assert all(p > 0 for p in weights)
            for child, p in zip(node["children"], weights):
                walk(child, own, chance * p, depth + 1)
            return
        assert actor in actors
        key = node["key"]
        signature = (tuple(node["actions"]), tuple(own[actor]), depth)
        assert key not in (row[0] for row in own[actor])
        assert key not in infos[actor] or infos[actor][key] == signature
        infos[actor][key] = signature
        assert len(node["actions"]) == len(node["children"])
        for action, child in zip(node["actions"], node["children"]):
            copied = {a: list(own[a]) for a in actors}
            copied[actor].append((key, action))
            walk(child, copied, chance, depth + 1)

    walk(case["root"], {a: [] for a in actors}, 1, 0)
    ids, flows, rhs = {}, {}, {}
    for actor in actors:
        ids[actor] = {None: 0}
        for key in sorted(infos[actor]):
            for action in infos[actor][key][0]:
                ids[actor][(key, action)] = len(ids[actor])
        flow = np.zeros((1 + len(infos[actor]), len(ids[actor])))
        flow[0, 0] = 1
        for i, key in enumerate(sorted(infos[actor]), 1):
            actions, history, _ = infos[actor][key]
            parent = history[-1] if history else None
            flow[i, ids[actor][parent]] = -1
            for action in actions:
                flow[i, ids[actor][(key, action)]] = 1
        flows[actor] = flow
        rhs[actor] = np.zeros(flow.shape[0])
        rhs[actor][0] = 1
    a, b = actors
    payoff = np.zeros((len(ids[a]), len(ids[b])))
    for probability, sequences, utilities in leaves:
        assert abs(utilities[a] + utilities[b] - .5) < 1e-12
        payoff[ids[a][sequences[0]], ids[b][sequences[1]]] += probability * utilities[a]
    # Original full realization variables and unrestricted flow duals, without affine elimination.
    row = linprog(np.r_[np.zeros(len(ids[a])), -rhs[b]],
                  A_ub=np.c_[-payoff.T, flows[b].T], b_ub=np.zeros(len(ids[b])),
                  A_eq=np.c_[flows[a], np.zeros((len(rhs[a]), len(rhs[b])))], b_eq=rhs[a],
                  bounds=[(0, None)] * len(ids[a]) + [(None, None)] * len(rhs[b]),
                  method="highs", options=OPTIONS)
    col = linprog(np.r_[np.zeros(len(ids[b])), rhs[a]],
                  A_ub=np.c_[payoff, -flows[a].T], b_ub=np.zeros(len(ids[a])),
                  A_eq=np.c_[flows[b], np.zeros((len(rhs[b]), len(rhs[a])))], b_eq=rhs[b],
                  bounds=[(0, None)] * len(ids[b]) + [(None, None)] * len(rhs[a]),
                  method="highs", options=OPTIONS)
    assert row.success and col.success, case["id"]
    x, y = row.x[:len(ids[a])], col.x[:len(ids[b])]
    assert np.max(np.abs(flows[a] @ x - rhs[a])) < 1e-8
    assert np.max(np.abs(flows[b] @ y - rhs[b])) < 1e-8
    assert min(np.min(x), np.min(y)) > -1e-8
    assert abs(-row.fun - col.fun) < 1e-8
    assert abs(float(x @ payoff @ y) + row.fun) < 1e-8
    return -row.fun, col.fun


def main():
    fixture = read("affine-sequence-form-lp-controls.json.gz")
    assert fixture["schemaVersion"] == "independent-affine-sequence-form-lp-controls/v1"
    assert len(fixture["problems"]) == 160
    worst = 0.0
    for p in fixture["problems"]:
        a, b, c = np.asarray(p["matrix"]), np.asarray(p["rhs"]), np.asarray(p["cost"])
        out = linprog(-c, A_ub=a, b_ub=b, bounds=(0, None), method="highs", options=OPTIONS)
        assert out.success, (p["group"], p["id"], out.message)
        x, y = out.x, -out.ineqlin.marginals
        error = abs(float(c @ x) - p["expectedMaximum"])
        assert error < 1e-8
        assert min(np.min(x), np.min(y)) > -1e-8
        assert np.max(a @ x - b) < 1e-8 and np.max(c - a.T @ y) < 1e-8
        assert abs(float(c @ x - b @ y)) < 1e-8
        worst = max(worst, error)
    trees = read("affine-multiraise-full-trees.json.gz")
    assert trees["schemaVersion"] == "independent-synthetic-multiraise-trees/v1"
    values = json.loads((ROOT / "affine-multiraise-values.json").read_text())
    expected = {v["id"]: v for v in values}
    assert len(expected) == len(values) == len(trees["cases"]) == 15
    assert {c["id"] for c in trees["cases"]} == set(expected)
    for case in trees["cases"]:
        lower, upper = original_full_flow(case)
        error = max(abs(lower - expected[case["id"]]["lower"]), abs(upper - expected[case["id"]]["upper"]))
        assert error < 1e-8, case["id"]
        worst = max(worst, error)
    print(json.dumps({"status": "PASS_INDEPENDENT_MATHEMATICAL_CONTROLS_NOT_ADMISSION",
                      "literalReducedPrograms": 160, "originalFullFlowRaiseTrees": 15,
                      "scipyVersion": scipy.__version__, "numpyVersion": np.__version__,
                      "maximumValueError": worst}))


if __name__ == "__main__":
    main()
