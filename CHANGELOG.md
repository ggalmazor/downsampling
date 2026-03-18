# Changelog

## `main`

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
