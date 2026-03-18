# Changelog

## `v17`

## Release 17.1.0

- PIP: replaced lazy-deletion `PriorityQueue` with a flat array-backed max segment tree,
  eliminating the O(n·k) stale-entry accumulation that caused 25× slowdown at large k
- PIP now operates at true O(n + k log n): O(n) segment tree build + O(log n) per distance
  update; the heap's lack of `decreaseKey` is no longer a bottleneck
- PIP 100k/k=1000: 7.75 s → 959 ms (8× faster); 100k/k=5000: 8.15 s → 1.01 s (8× faster)
- PIP 500k now completes in 4–11 s (previously hung indefinitely)
- PIP 100k/k=100 regressed slightly (304 ms → 631 ms): O(n) tree build dominates at small k;
  the segment tree wins decisively for targetSize ≳ 500

## Release 17.0.0

- Java 17 LTS branch, backported from `main` (25.0.0)
- Initial release: LTTB, RDP, and PIP downsampling algorithms under a shared `Point` /
  `DoublePoint` data contract
- `Downsampling` static facade as the single entry point
- LTTB with `BucketizationStrategy.DYNAMIC` and `BucketizationStrategy.FIXED` strategies
- RDP: iterative (stack-based), squared-distance comparison, coordinate arrays
- PIP: max-heap + array-based doubly-linked list over selected indices
- JMH benchmarks
- Checkstyle (Google Java Style) enforced on main sources
