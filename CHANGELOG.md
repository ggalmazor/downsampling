# Changelog

## `v17`

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
