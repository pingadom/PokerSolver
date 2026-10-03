"""Exercise a loaded full-round research pack through a running API (standard library only)."""

import json
import math
import sys
import urllib.error
import urllib.request


def request(base, path="", body=None):
    payload = None if body is None else json.dumps(body).encode("utf-8")
    query = urllib.request.Request(
        base + path,
        data=payload,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(query, timeout=30) as response:
        assert response.headers.get("Cache-Control") == "no-store"
        return json.load(response)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:18080"
    base = root.rstrip("/") + "/api/v1/trainer/research/sixmax-preflop"
    metadata = request(base)
    assert metadata["packSchema"] == "six-max-preflop-checkdown-pack/v1"
    assert metadata["publicationStatus"] == "VALIDATION_ONLY"
    assert metadata["payoffMethod"] == "EXACT_ENUMERATION"
    assert metadata["continuationModel"] == "MANDATORY_CHECKDOWN"
    assert metadata["chanceModel"] == "EXACT_RANGE_PRODUCT"
    assert metadata["seats"] == ["UTG", "HJ", "CO", "BTN", "SB", "BB"]
    assert metadata["rangeComboCounts"] == [1, 1, 1, 2, 1, 1]
    assert metadata["raiseToBb"] == [3.0, 100.0]
    assert metadata["sessionLength"] == 10
    assert 0 <= metadata["nashConvBb"] <= 0.05
    assert metadata["maximumPayoffStandardErrorBb"] == 0
    pack_hash = metadata["packHash"]
    assert len(pack_hash) == 64

    seed = "9223372036854775807"
    actions = []
    losses = []
    for index in range(metadata["sessionLength"]):
        path = f"/sessions/{seed}/questions/{index}"
        question = request(base, path)
        assert question == request(base, path)
        assert question["sessionSeed"] == seed
        assert question["index"] == index
        assert question["packHash"] == pack_hash
        assert question["actingSeat"] in metadata["seats"]
        assert [player["seat"] for player in question["players"]] == metadata["seats"]
        acting = [player for player in question["players"] if player["status"] == "ACTING"]
        assert len(acting) == 1 and acting[0]["seat"] == question["actingSeat"]
        assert math.isclose(sum(player["committedBb"] for player in question["players"]), question["potBb"], abs_tol=1e-9)
        for player in question["players"]:
            assert math.isclose(player["committedBb"] + player["remainingStackBb"], metadata["stackBb"], abs_tol=1e-9)
            assert not {"heroCombo", "holeCards", "combo"} & player.keys()
        assert not {"seed", "opponentCards", "actionEvBb", "feedback"} & question.keys()
        action = question["legalActions"][0]
        actions.append(action)
        graded = request(base, "/grade", {
            "sessionSeed": seed, "index": index, "packHash": pack_hash, "action": action,
        })
        assert graded["question"] == question
        feedback = graded["feedback"]
        assert feedback["selectedAction"] == action
        assert set(feedback["actionEvBb"]) == set(question["legalActions"])
        assert set(feedback["actionFrequency"]) == set(question["legalActions"])
        assert set(feedback["actionPayoffStandardErrorBb"]) == set(question["legalActions"])
        assert all(value == 0 for value in feedback["actionPayoffStandardErrorBb"].values())
        assert math.isfinite(feedback["evLossBb"]) and feedback["evLossBb"] >= 0
        losses.append(feedback["evLossBb"])

    review = request(base, "/review", {
        "sessionSeed": seed, "packHash": pack_hash, "actions": actions,
    })
    assert review["packHash"] == pack_hash
    assert len(review["attempts"]) == 10
    for index, attempt in enumerate(review["attempts"]):
        assert attempt["question"]["index"] == index
        assert attempt["feedback"]["selectedAction"] == actions[index]
        assert attempt["feedback"]["evLossBb"] == losses[index]
    assert math.isclose(review["totalEvLossBb"], sum(losses), abs_tol=1e-9)
    assert math.isclose(review["averageEvLossBb"], sum(losses) / 10, abs_tol=1e-9)

    stale_hash = ("0" if pack_hash[0] != "0" else "1") + pack_hash[1:]
    try:
        request(base, "/grade", {
            "sessionSeed": seed, "index": 0, "packHash": stale_hash, "action": actions[0],
        })
    except urllib.error.HTTPError as error:
        assert error.code == 400
    else:
        raise AssertionError("A stale artifact hash must be rejected")
    print("Full-round research trainer smoke passed: exact pack, ten grades, replayed review")


if __name__ == "__main__":
    main()
