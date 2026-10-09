# Compact storage for the existing physical-board solver

The 530-board compatibility milestone is implemented. This is a lossless encoding of the existing game, not a larger board model or a new equilibrium claim. The original payoff hash, public information-set namespace, global observation order and chance summation order remain unchanged. Saved policies remain compatible. The compact artifact has its own storage hash and schema; it explicitly names the original game and exact original payoff hash.

## Representation and trust boundary

`SixMaxHistoryPhysicalCompactStorage` computes each public history's union of observations supported by any private world. Its file stores that history's increasing global indexes and exact counts, first-player wins and ties in local dense vectors. Absent global indexes reconstruct to zero. It retains all twelve joint private worlds and all six private hands, including folded-card blockers; it never multiplies independent seat marginals.

The runtime lookup uses private primitive `int[]` vectors and an `int[]` global-to-local map. Counts are at most 9,880 and win/tie counts are at most `9,880 × 666 = 6,580,080`, so checked 32-bit conversion preserves them exactly. Its read-only counts adapter presents the original global palette to the game in constant lookup time. Payouts use the original arithmetic and active-seat convention. Original global observation keys remain visible to players; local indexes never expose a private world.

Only projection from an opaque, exactly verified original table, or exact replay against that table, can construct the compact `Verified` handle. A parsed record alone cannot create a game or export verified data. Replay checks the complete projected artifact and restoration of every original value. The handle retains gzip-compressed canonical bytes and primitive lookup arrays; requesting `artifact()` returns a newly parsed immutable record rather than retaining boxed numeric vectors. Constructing its game temporarily restores and validates the original table against source and rank parent; that restored object is then disposable.

This first compatibility format intentionally depends on original-table verification. It does not reduce the cost of original physical-board enumeration or bypass old evidence. A future independent broader format needs a new model identity, declared limits and its own exact generation/replay workflow.

## Saved compatibility evidence

The committed inputs remain the staged 3bb/open, 9bb/re-raise source pack, rank parent and 500-iteration joint checkpoint. New files are:

- `docs/data/sixmax-staged-history-physical-compact-payoffs.json.gz`
- `docs/data/sixmax-staged-history-physical-storage-audit.json`

`SixMaxHistoryPhysicalStorageAudit` requires the opaque original-table, validated-study and compact handles. It compares the entire table and both active-player shares, reconstructs all conditional diagnostics and full parent best responses, and requires exact equality with the original study. It then compares full private posteriors, owned maxmin matrix/behavioral evidence and fresh eight-iteration CFR+ policies/traversal counters in up to twenty literal branches whose original root gap exceeds 0.001bb. Those local controls verify compatibility; eight iterations do not certify convergence. An opaque successful result is required to write the audit. Saved audits are exactly recomputed, including their counters and hashes.

For this fixture, the complete audit reproduces:

| Check | Result |
| --- | ---: |
| Counts compared | 123,264 |
| Active-player shares compared bit for bit | 172,592 |
| Complete legal states | 915,457, unchanged |
| Complete policy information sets | 69,987, unchanged |
| Owned maxmin and fresh CFR+ branch controls | 20, identical |
| Original parent NashConv | 0.0013699346262598224bb, unchanged |
| History support widths | 1,182 for five histories; 1,709 for the sixth |
| Original global width | 1,712 |
| Rectangular vector slots | 123,264 |
| History-local vector slots | 91,428 |
| Positive world/observation slots | 86,296 |
| Original three-vector primitive payload equivalent | 2,958,336 bytes |
| Compact three-vector arrays plus lookup maps | 1,138,224 bytes |
| Canonical compact JSON before cache compression | 1,091,015 bytes |
| Retained compressed canonical cache | 217,751 bytes |

Primitive payload counters exclude encoded bytes, object headers, maps, strings, source packs, policies and temporary allocations. They are not whole-heap measurements. Compression can make repeated zeros cheap on disk, so smaller numeric vectors do not imply a smaller gzip file. The separate resource experiment records actual file sizes and advisory-GC heap observations without granting admission.

Compressed-cache length is a runtime observation, exposed by `Verified.retainedEncodedBytes()` and the benchmark. It is deliberately excluded from the deterministic compatibility audit: a JVM compression implementation can change bytes without changing the underlying canonical artifact, payoffs or strategies. Exact replay binds the canonical hash and complete game evidence, while resource observations describe the host where they were measured.

The original game already skipped zero-chance outcomes during legal-state enumeration and traversal. Removing zero storage slots therefore saves no legal states and grants no extra coverage. Three removed rank fallback indexes in the sixth history were fully replaced by literal observations; their absence is not a missing physical board.

## Reproduce projection and audit replay

Compile the solver and engine and put their classes/dependencies on the Java classpath. Run the following class, using `project` for two new output paths or `replay` for the committed outputs:

```text
com.pokerlab.solver.SixMaxHistoryPhysicalStorageMain replay
  docs/data/sixmax-staged-three-nine-source-pack.json
  docs/data/sixmax-staged-rank-texture-payoffs.json.gz
  docs/data/sixmax-staged-history-physical-payoffs.json.gz
  docs/data/sixmax-staged-history-physical-policy-500.json.gz
  docs/data/sixmax-staged-history-physical-study-500.json.gz
  docs/data/sixmax-staged-history-physical-compact-payoffs.json.gz
  docs/data/sixmax-staged-history-physical-storage-audit.json
```

The lines above describe one invocation with eight arguments including the mode; join them according to your shell. The CLI rejects normalized path aliases, hard links and existing output paths before loading inputs. It first exactly replays the original payoffs and study. Compact tables keep the existing 8MiB decompressed cap; the small compatibility report has a 128KiB decompressed cap. Projection completes the audit before writing either output.

`SixMaxHistoryPhysicalStorageBenchmarkMain` accepts `baseline`, `original` or `compact` followed by source, rank table, original table, checkpoint, compact table and a new measurement output. Use a fresh `java -Xmx2g` process for each measurement. It exactly replays original payoffs in all modes, then returns from the loading frame, retaining identical source/parent/checkpoint objects. Baseline drops the table and game. Original retains the original verified table and game; compact retains the compact handle and game. Five advisory GC requests precede the retained-heap reading. Game modes then perform two evaluator warmups and five timed evaluations. A reachability fence keeps the selected objects live through measurement. GC cooperation, JIT, CPU load and the host affect observations; no timing or heap threshold is asserted by CI.

The [saved resource measurements](data/sixmax-staged-history-physical-storage-measurements.json) contain all nine final observations: three separate processes per mode on Windows/Java 21.0.3, with the same 2GiB maximum heap and exact input identities. `scripts/summarize-physical-storage-benchmark.py` requires those three groups, matching identities/runtime settings and identical evaluator utility vectors, and summarizes the medians. It independently checks canonical table hashes against the exact compatibility audit. This research summary remains explicitly observational.

| Advisory-GC retained heap, three-process median | Bytes |
| --- | ---: |
| Shared source/rank/checkpoint baseline | 41,281,288 |
| Baseline plus original table and rebuilt game | 59,485,416 |
| Baseline plus compact handle and rebuilt game | 57,300,056 |
| Original minus compact | 2,185,360 (about 2.08MiB) |

The whole retained-heap difference is about 3.67%. Subtracting the shared baseline gives 18,204,128 versus 16,018,768 bytes for table/game increments, about 12.00% lower; this increment is not a measurement of the table in isolation.

The compact pretty-JSON file is 237,145 compressed bytes and 1,497,484 expanded bytes, versus 243,042 and 1,746,732 for the original. Canonical JSON is 1,091,015 versus 1,250,205 bytes. Python gzip level 9 compresses canonical compact JSON to 214,214 bytes versus 213,514 for the original, demonstrating that the smaller format can compress slightly worse under another serialization/compression configuration. The private Java gzip cache uses the default Java compression configuration and occupies 217,751 bytes.

The first prototype retained uncompressed canonical bytes and used 64-bit arrays. Its initial heap observation was slightly larger than the original. The final representation compresses that cache and uses checked exact 32-bit arrays. Whole-heap observations include the shared source, rank parent, saved policy and rebuilt game, so report their measured difference separately from primitive payload counters. These measurements establish a small compatibility resource improvement on this fixture; they do not establish peak-heap savings, a speed improvement or capacity for the proposed 8,192-board model. Timed trials overlapped other local work and must not be used as a performance gate.

## Remaining model work

The compact format leaves the old 600-revelation, 2,000-observation and one-million-state caps in place. They cannot support the unchanged 25% retained heads-up coverage target: even 600 ideal literal revelations cover at most 6.073%. Broader per-history support needs its own model namespace and measured state, policy, payoff, heap and full-replay budgets before fresh training and exhaustive action-EV screening. Storage compatibility alone grants no retained-content credit. The current four sampled action-EV failures, unexamined eligible cases, realistic ranges, rake and genuine multiway equilibrium remain open. AWS deployment stays paused.
