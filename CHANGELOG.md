# Changelog

## `main`

## Release 25.2.0

- PIP: replaced lazy-deletion `PriorityQueue` with a flat array-backed max segment tree,
  eliminating the O(n·k) stale-entry accumulation that caused 25× slowdown at large k
- PIP now operates at true O(n + k log n): O(n) segment tree build + O(log n) per distance
  update; the heap's lack of `decreaseKey` is no longer a bottleneck
- PIP 100k/k=1000: 7.75 s → 959 ms (8× faster); 100k/k=5000: 8.15 s → 1.01 s (8× faster)
- PIP 500k now completes in 4–11 s (previously hung indefinitely)
- PIP 100k/k=100 regressed from 304 ms to 631 ms — a known trade-off: the segment tree builds
  eagerly in O(n) regardless of k; the old heap built lazily and only ever touched O(k) entries
  when k ≪ n, so it was cheaper in that regime. The crossover is k/n ≈ 0.005–0.01: below that
  the heap was faster; above it the heap's stale-entry accumulation dominates. The segment tree
  wins decisively for k/n ≳ 0.01 (targetSize ≳ n/100)

## Release 25.1.0

- `DoublePoint` is now a `record` — correct `equals`/`hashCode`/`toString` out of the box,
  and the JIT can inline record accessors more aggressively than regular getters
- `Point` interface accessors renamed: `getX()` → `x()`, `getY()` → `y()`, matching the record
  accessor convention — **breaking change** (see migration guide in README)

## Release 25.0.0

- Initial release: LTTB, RDP, and PIP downsampling algorithms under a shared `Point` /
  `DoublePoint` data contract
- `Downsampling` static facade as the single entry point
- LTTB ported from [lttb_downsampling](https://github.com/ggalmazor/lttb_downsampling) with
  `BucketizationStrategy.DYNAMIC` (equal point count) and `BucketizationStrategy.FIXED`
  (equal x-span) strategies; LTTB internals namespaced under `com.ggalmazor.downsampling.lttb`
- RDP: iterative (stack-based) implementation; squared-distance comparison avoids `sqrt` in the
  hot loop; coordinates extracted into `double[]` arrays before recursion
- PIP: max-heap + array-based doubly-linked list over selected indices replacing the original
  `TreeMap` + O(n) scan; `prevSel[]`/`nextSel[]` arrays give O(1) neighbour lookup
- LTTB `DoublePoint` fast path uses bucket start/end indices to index directly into
  pre-extracted coordinate arrays, avoiding `getX()`/`getY()` virtual dispatch in the inner loop
- JMH benchmarks comparing all three algorithms
- Checkstyle (Google Java Style) enforced on main sources
