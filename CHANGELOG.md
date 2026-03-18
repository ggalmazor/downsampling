# Changelog

## `v21`

## Release 21.1.0

- `DoublePoint` is now a `record` — correct `equals`/`hashCode`/`toString` out of the box,
  and the JIT can inline record accessors more aggressively than regular getters
- `Point` interface accessors renamed: `getX()` → `x()`, `getY()` → `y()`, matching the record
  accessor convention — **breaking change** (see migration guide in README)

## Release 21.0.0

- Java 21 LTS branch, backported from `main` (25.0.0)
- Initial release: LTTB, RDP, and PIP downsampling algorithms under a shared `Point` /
  `DoublePoint` data contract
- `Downsampling` static facade as the single entry point
- LTTB with `BucketizationStrategy.DYNAMIC` and `BucketizationStrategy.FIXED` strategies
- RDP: iterative (stack-based), squared-distance comparison, coordinate arrays
- PIP: max-heap + array-based doubly-linked list over selected indices
- JMH benchmarks
- Checkstyle (Google Java Style) enforced on main sources
