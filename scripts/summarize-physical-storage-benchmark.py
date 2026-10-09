"""Summarize isolated Java measurements; this output is observational, never admission evidence."""

import argparse
import gzip
import hashlib
import json
from pathlib import Path
from statistics import median


def read(path):
    raw = Path(path).read_bytes()
    return json.loads(gzip.decompress(raw) if str(path).endswith(".gz") else raw)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--original-table", required=True)
    parser.add_argument("--compact-table", required=True)
    parser.add_argument("--audit", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("measurements", nargs="+")
    args = parser.parse_args()
    audit = read(args.audit)
    records = [read(path) for path in args.measurements]
    groups = {mode: [r for r in records if r["mode"] == mode]
              for mode in ("baseline", "original", "compact")}
    if len(records) != 9 or any(len(rows) != 3 for rows in groups.values()):
        raise ValueError("Require three fresh-process measurements per mode")
    for row in records:
        if (row["status"] != "OBSERVATIONAL_RESOURCE_MEASUREMENT_NOT_ADMISSION"
                or row["payoffTableHash"] != audit["binding"]["payoffTableHash"]
                or row["completeStates"] != audit["binding"]["completeTreeStates"]
                or row["solutionHash"] != records[0]["solutionHash"]
                or row["javaRuntime"] != records[0]["javaRuntime"]
                or row["os"] != records[0]["os"]
                or row["maximumHeapBytes"] != 2 * 1024**3):
            raise ValueError("Mixed input identities or runtime settings")
        times = row["twoWarmupsFiveEvaluatorNanos"]
        if len(times) != (0 if row["mode"] == "baseline" else 5):
            raise ValueError("Incorrect evaluator measurement count")
    played = groups["original"] + groups["compact"]
    if any(r["utilitiesBb"] != played[0]["utilitiesBb"] for r in played):
        raise ValueError("Original/compact evaluator utilities differ")
    heap = {mode: median(r["advisoryGcRetainedHeapBytes"] for r in rows)
            for mode, rows in groups.items()}
    file_sizes = {}
    for mode, path in (("original", args.original_table), ("compact", args.compact_table)):
        value = read(path)
        data = canonical(value)
        expected = audit["binding"]["payoffTableHash"] if mode == "original" else audit["compactHash"]
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError("Canonical table digest differs from exact audit")
        raw = Path(path).read_bytes()
        file_sizes[mode] = {"actualFileBytes": len(raw),
                            "actualExpandedBytes": len(gzip.decompress(raw)),
                            "canonicalJsonBytes": len(data),
                            "canonicalPythonGzipLevel9Bytes": len(gzip.compress(data, mtime=0))}
    output = {
        "status": "OBSERVATIONAL_RESOURCE_MEASUREMENT_NOT_ADMISSION",
        "trainerAdmission": False,
        "binding": audit["binding"], "compactHash": audit["compactHash"],
        "layout": audit["layout"], "files": file_sizes,
        "medianRetainedHeapBytes": heap,
        "medianAboveBaselineBytes": {mode: heap[mode] - heap["baseline"]
                                      for mode in ("original", "compact")},
        "originalMinusCompactMedianHeapBytes": heap["original"] - heap["compact"],
        "medianEvaluatorNanos": {mode: median(n for r in groups[mode]
                                             for n in r["twoWarmupsFiveEvaluatorNanos"])
                                 for mode in ("original", "compact")},
        "interpretation": "Advisory-GC retained objects on one host; not peak heap, allocation rate, a CI performance gate or broader capacity. Timing is affected by concurrent work; no speedup claim.",
        "measurements": records,
    }
    # Exclusive creation also protects outputs that alias any input.
    with Path(args.output).open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(output, stream, indent=2, sort_keys=True)
        stream.write("\n")
    print(json.dumps({k: output[k] for k in ("medianRetainedHeapBytes",
                     "originalMinusCompactMedianHeapBytes", "medianEvaluatorNanos")}, indent=2))


if __name__ == "__main__":
    main()
