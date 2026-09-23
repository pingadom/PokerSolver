"""Plot measured benchmark summaries. Requires matplotlib; no synthetic data."""
import csv
from pathlib import Path
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

root = Path(__file__).resolve().parents[1]
with (root / "docs/data/benchmark-summary.csv").open() as source:
    rows = list(csv.DictReader(source))
workers = [int(row["workers"]) for row in rows]
seconds = [float(row["median_seconds"]) for row in rows]
speedup = [float(row["speedup"]) for row in rows]
fig, axes = plt.subplots(1, 2, figsize=(9, 3.8), layout="constrained")
axes[0].bar([str(w) for w in workers], seconds, color="#285648", width=0.55)
axes[0].set(xlabel="Engine worker threads", ylabel="Median wall time (seconds)", title="10 million trials · three repeats")
for index, value in enumerate(seconds):
    axes[0].text(index, value + 0.2, f"{value:.2f}s", ha="center", fontsize=9)
axes[0].set_ylim(0, max(seconds) * 1.15)
axes[1].plot(workers, workers, "--", color="#9b9e98", label="Ideal linear scaling")
axes[1].plot(workers, speedup, "o-", color="#285648", linewidth=2, label="Measured speedup")
axes[1].set(xlabel="Engine worker threads", ylabel="Speedup versus one thread", xticks=workers, title="Same seeded workload at every concurrency")
axes[1].legend(frameon=False, fontsize=8)
for axis in axes:
    axis.spines[["top", "right"]].set_visible(False)
    axis.grid(axis="y", alpha=0.16)
    axis.set_axisbelow(True)
output = root / "docs/images"
output.mkdir(exist_ok=True)
fig.savefig(output / "scaling.svg")
fig.savefig(output / "scaling.png", dpi=180)
