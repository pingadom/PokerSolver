"""Optional NumPy/SciPy full-flow oracle; no application or CI dependency.

Reconstruct the literal actual-card game, add nonnegative floor slacks, and solve both
original full-flow programs with HiGHS. No owned affine matrices are read or invoked.
"""
import gzip
import json
from pathlib import Path

import numpy as np
import scipy
from scipy.optimize import linprog

FIXTURE = Path(__file__).resolve().parents[1] / "solver/src/test/resources/behavior-floor-literal-control.json.gz"


def problem(root):
    actors = (2, 5)
    infos, leaves = {a: {} for a in actors}, []

    def walk(node, own, chance, depth):
        actor = node["actor"]
        if actor == -2:
            leaves.append((chance, [own[a][-1] if own[a] else None for a in actors], node["utilities"]))
            return
        if actor == -1:
            assert abs(sum(node["probabilities"]) - 1) < 1e-12
            assert len(node["probabilities"]) == len(node["children"])
            for child, p in zip(node["children"], node["probabilities"]):
                assert p > 0
                walk(child, own, chance * p, depth + 1)
            return
        assert actor in actors
        key, actions = node["key"], tuple(node["actions"])
        signature = (actions, tuple(own[actor]), depth)
        assert key not in infos[actor] or infos[actor][key] == signature
        infos[actor][key] = signature
        assert len(actions) == len(set(actions)) == len(node["children"])
        for action, child in zip(actions, node["children"]):
            copied = {a: list(own[a]) for a in actors}
            copied[actor].append((key, action))
            walk(child, copied, chance, depth + 1)

    walk(root, {a: [] for a in actors}, 1, 0)
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
            flow[i, ids[actor][history[-1] if history else None]] = -1
            for action in actions:
                flow[i, ids[actor][(key, action)]] = 1
        flows[actor] = flow
        rhs[actor] = np.r_[1., np.zeros(len(infos[actor]))]
    payoff = np.zeros((len(ids[2]), len(ids[5])))
    for probability, sequences, utilities in leaves:
        assert abs(utilities[2] + utilities[5] - .5) < 1e-12
        payoff[ids[2][sequences[0]], ids[5][sequences[1]]] += probability * utilities[2]
    return flows[2], rhs[2], flows[5], rhs[5], payoff


def augmented(flow, rhs, floor):
    extra = []
    for row in flow[1:]:
        parent = np.flatnonzero(row == -1)
        assert len(parent) == 1
        for child in np.flatnonzero(row == 1):
            constraint = np.zeros(flow.shape[1])
            constraint[child], constraint[parent[0]] = 1, -floor
            extra.append(constraint)
    constraints = np.asarray(extra)
    return (np.block([[flow, np.zeros((len(rhs), len(extra)))],
                      [constraints, -np.eye(len(extra))]]), np.r_[rhs, np.zeros(len(extra))])


def verify(case):
    E, e, F, f, A = problem(case["root"])
    for expected in case["ownedControls"] + case["slackControls"]:
        floor = expected["minimumActionProbability"]
        EF, ee = augmented(E, e, floor)
        FF, ff = augmented(F, f, floor)
        payoff = np.zeros((EF.shape[1], FF.shape[1]))
        payoff[:E.shape[1], :F.shape[1]] = A
        options = {"primal_feasibility_tolerance": 1e-10, "dual_feasibility_tolerance": 1e-10}
        x = linprog(np.r_[np.zeros(EF.shape[1]), -ff],
                    A_ub=np.c_[-payoff.T, FF.T], b_ub=np.zeros(FF.shape[1]),
                    A_eq=np.c_[EF, np.zeros((len(ee), len(ff)))], b_eq=ee,
                    bounds=[(0, None)] * EF.shape[1] + [(None, None)] * len(ff), method="highs", options=options)
        y = linprog(np.r_[np.zeros(FF.shape[1]), ee],
                    A_ub=np.c_[payoff, -EF.T], b_ub=np.zeros(EF.shape[1]),
                    A_eq=np.c_[FF, np.zeros((len(ff), len(ee)))], b_eq=ff,
                    bounds=[(0, None)] * FF.shape[1] + [(None, None)] * len(ee), method="highs", options=options)
        assert x.success and y.success, (x.message, y.message)
        assert abs(-x.fun - y.fun) < 1e-8
        for flow, result in ((E, x), (F, y)):
            masses = result.x[:flow.shape[1]]
            for row in flow[1:]:
                parent = masses[np.flatnonzero(row == -1)[0]]
                assert parent > 0
                probabilities = masses[np.flatnonzero(row == 1)] / parent
                assert min(probabilities) >= floor * (1 - 1e-6)
                assert abs(sum(probabilities) - 1) < 1e-8
        assert abs(-x.fun - expected["constrainedLowerValue"]) < 1e-8
        assert abs(y.fun - expected["constrainedUpperValue"]) < 1e-8
        print(f"PASS floor={floor:g} value={-x.fun:.15g} gap={abs(x.fun+y.fun):.3g}")


def main():
    with gzip.open(FIXTURE, "rb") as stream:
        data = stream.read(8 * 1024 * 1024 + 1)
    if len(data) > 8 * 1024 * 1024:
        raise ValueError("Expanded oracle fixture exceeds byte bound")
    case = json.loads(data)
    assert case["status"] == "LITERAL_CONTROL_ONLY_NO_TRAINER_ADMISSION"
    verify(case)
    print(f"Verified independent original-flow controls with NumPy {np.__version__}, SciPy {scipy.__version__}")


if __name__ == "__main__":
    main()
