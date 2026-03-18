# downsampling

[![CI](https://github.com/ggalmazor/downsampling/actions/workflows/ci.yml/badge.svg)](https://github.com/ggalmazor/downsampling/actions/workflows/ci.yml)

A Java library providing three time-series downsampling algorithms under a shared data contract:
**LTTB**, **RDP**, and **PIP**.

These algorithms are designed to reduce the number of points in a series without losing its
important visual features. See how they compare at
[ggalmazor.com/blog/evaluating_downsampling_algorithms](https://ggalmazor.com/blog/evaluating_downsampling_algorithms).

All three algorithms accept a `List<T extends Point>` and return a `List<T>`, preserving the
caller's concrete point type throughout. The consistent API is in the data types, not a shared
behavioural interface — each algorithm exposes different parameters that reflect what it actually
controls.

Javadoc at [ggalmazor.com/downsampling](https://ggalmazor.com/downsampling)

## Java version support

| Version | Java baseline | Branch | Status |
|---------|---------------|--------|--------|
| 17.x.x | Java 17 | `v17` | Active (bug fixes only) |
| 21.x.x | Java 21 | `v21` | Active |
| 25.x.x | Java 25 | `main` | Active (cutting edge) |

The library version number reflects the minimum Java version required to use it. The JDK version
is managed by [mise](https://mise.jdx.dev/). Run `mise install` to get the correct JDK for the
branch you are on.

## Download

Latest versions: 25.0.0 / 21.0.0 / 17.0.0

### Gradle

```kotlin
implementation("com.ggalmazor:downsampling:25.0.0")
```

### Maven

```xml
<dependency>
  <groupId>com.ggalmazor</groupId>
  <artifactId>downsampling</artifactId>
  <version>25.0.0</version>
</dependency>
```

## Algorithms

### LTTB — Largest-Triangle Three-Buckets

Based on the paper *"Downsampling Time Series for Visual Representation"* by Sveinn Steinarsson,
University of Iceland (2013).
([PDF](http://skemman.is/stream/get/1946/15343/37285/3/SS_MSthesis.pdf))

Produces exactly `buckets + 2` output points. The input is divided into `buckets` equal-sized
windows; from each window the point that forms the largest triangle with the previously selected
point and the centroid of the next window is retained. Always preserves the first and last input
points.

**When to use:** when you need a fixed, predictable output size and want the result to look
visually faithful to the original series on a chart.

```java
// Downsample to 100 middle points (102 total including first and last)
List<MyPoint> result = Downsampling.lttb(series, 100);

// Unevenly-spaced data: use fixed x-span buckets instead of equal point-count buckets
List<MyPoint> result = Downsampling.lttb(series, 100, BucketizationStrategy.FIXED);
```

`BucketizationStrategy.DYNAMIC` (default) divides the input into buckets of equal point count.
`BucketizationStrategy.FIXED` divides the x-axis range into equal-width intervals; empty
intervals are silently skipped, so the output may contain fewer than `buckets + 2` points.

### RDP — Ramer-Douglas-Peucker

Output size is determined by the data shape and `epsilon`: every retained interior point has a
perpendicular distance greater than `epsilon` from the line connecting its two nearest retained
neighbours. A larger `epsilon` retains fewer points; `epsilon = 0` retains all points.

The implementation uses an explicit `Deque` stack rather than JVM call-stack recursion. The
naive recursive formulation has O(log n) average / O(n) worst-case stack depth and causes
`StackOverflowError` on adversarial or pathological inputs at large sizes. The iterative
implementation eliminates this risk entirely — stack depth is O(1) regardless of input shape
or size.

**When to use:** when you care about geometric fidelity (no point that would deviate more than
`epsilon` from the simplified line is ever discarded) and are willing to accept a variable output
size.

```java
// Simplify, discarding points within epsilon=0.5 of the connecting line
List<MyPoint> result = Downsampling.rdp(series, 0.5);
```

### PIP — Perceptually Important Points

Produces exactly `targetSize` output points (or all points if `targetSize >= input.size()`).
Starting from the first and last points, the algorithm greedily adds the remaining point with
the greatest perpendicular distance to the line connecting its two currently-selected neighbours,
until the target count is reached.

**When to use:** when you need a precise output size and want the most globally prominent
structural features preserved first — at the cost of significantly higher runtime than LTTB or
RDP at large input sizes.

```java
// Select the 20 most perceptually important points
List<MyPoint> result = Downsampling.pip(series, 20);
```

## Usage

### Custom point types

Implement `Point` to use your own data type:

```java
public class Reading implements Point {
    private final Instant timestamp;
    private final double value;

    public Reading(Instant timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    @Override public double getX() { return timestamp.toEpochMilli(); }
    @Override public double getY() { return value; }

    public Instant getTimestamp() { return timestamp; }
}

// The output list is List<Reading> — the concrete type is preserved
List<Reading> downsampled = Downsampling.lttb(readings, 200);
```

`getX()` must be monotonically non-decreasing across the input list. Algorithms do not verify
this; violating it produces undefined output.

### Built-in point type

For simple cases, use `DoublePoint`:

```java
List<DoublePoint> points = List.of(
    DoublePoint.of(0, 1.2),
    DoublePoint.of(1, 3.4),
    DoublePoint.of(2, 2.1)
);
List<DoublePoint> result = Downsampling.rdp(points, 0.1);
```

## Building

```shell
mise install                        # installs the correct JDK for this branch via mise
./gradlew build                     # compile, test, checkstyle
./gradlew test                      # run tests only
./gradlew checkstyleMain            # run Checkstyle on main sources only
./gradlew jmh                       # run JMH benchmarks (takes several minutes)
./gradlew javadoc                   # generate Javadoc
```

## Benchmarks

Measured with [JMH](https://github.com/openjdk/jmh) on OpenJDK 25.0.2, Apple M-series,
2 JVM forks × 5 measurement iterations × 1 s each, average time mode. Synthetic input: linear
trend + sine wave + random noise, all `DoublePoint`. RDP output size is data-driven and varies
with epsilon.

Run benchmarks locally with:

```bash
mise exec -- ./gradlew jmh
```

### LTTB

| dataSize | targetPoints | ms/op |
|:---:|:---:|---:|
| 10k | 100 | 0.023 |
| 10k | 1 000 | 0.060 |
| 10k | 5 000 | 0.136 |
| 100k | 100 | 0.226 |
| 100k | 1 000 | 0.249 |
| 100k | 5 000 | 0.321 |
| 500k | 100 | 1.574 |
| 500k | 1 000 | 1.359 |
| 500k | 5 000 | 1.549 |

Linear scaling confirmed: 10× data → ~10× time. `targetPoints` has a modest secondary effect
within a data-size tier (more buckets = more array extraction overhead) but it disappears at
500k, where the O(n) coordinate-extraction pass dominates. **500k points in ~1.5 ms.**

### RDP

| dataSize | epsilon | ms/op |
|:---:|:---:|---:|
| 10k | 0.5 | 0.734 |
| 10k | 2.0 | 0.573 |
| 10k | 5.0 | 0.210 |
| 100k | 0.5 | 16.778 |
| 100k | 2.0 | 15.162 |
| 100k | 5.0 | 11.561 |
| 500k | 0.5 | 145.516 |
| 500k | 2.0 | 133.401 |
| 500k | 5.0 | 118.610 |

10k → 100k: ~22× slower. 100k → 500k: ~9× slower — consistent with O(n log n). The epsilon
effect is visible: smaller epsilon → more retained points → deeper recursion trees → more work.
At 500k the spread is 145 ms (epsilon=0.5) vs 119 ms (epsilon=5.0), a 22% difference from tree
shape alone. **500k points in 120–146 ms.**

### PIP

| dataSize | targetSize | ms/op |
|:---:|:---:|---:|
| 10k | 100 | 13.3 |
| 10k | 1 000 | 19.0 |
| 10k | 5 000 | 21.7 |
| 100k | 100 | 631 |
| 100k | 1 000 | 959 |
| 100k | 5 000 | 1 010 |
| 500k | 100 | 4 098 |
| 500k | 1 000 | 10 876 |
| 500k | 5 000 | 11 005 |

The previous heap-based implementation had a 25× blowup when going from targetSize=100 to
targetSize=1 000 at 100k points (304 ms → 7.75 s), caused by O(n·k) stale entry accumulation
in the lazy-deletion priority queue. The segment tree implementation resolves this: the same
step is now 631 ms → 959 ms, a 1.5× increase for a 10× increase in k.

At 100k, targetSize=100 regressed from 304 ms to 631 ms. This is a deliberate trade-off: the
segment tree costs O(n) to build regardless of k, which dominates at very small k where the old
heap's smaller initial size was faster. At k=1 000 and above the segment tree wins decisively.

At 500k, all combinations now complete in finite time. The k=100 → k=1 000 jump is 4.1 s →
10.9 s — a 2.7× increase for a 10× increase in k, confirming the improved complexity.
**500k points in 4–11 s** depending on targetSize.

## Algorithm comparison

| | LTTB | RDP | PIP |
|---|:---:|:---:|:---:|
| Output size | exact (`buckets + 2`) | data-driven | exact (`targetSize`) |
| Control parameter | bucket count | epsilon (distance) | target point count |
| Time complexity | O(n) | O(n log n) avg / O(n²) worst | O(n + k log n)¹ |
| Stack depth | O(1) | O(1) — iterative, no `StackOverflowError` risk | O(1) |
| Visual fidelity | high — optimised for charts | geometric — no point with dist > ε discarded | high — most prominent features first |
| Unevenly-spaced data | FIXED strategy | natural | natural |
| Practical limit | 500k+ points, <2 ms | 500k in 120–146 ms | 500k in 4–11 s |

¹ O(n) to build the segment tree + O(k log n) to select k points, each requiring O(segment)
distance updates each costing O(log n). Small targetSize at large n carries O(n) segment tree
build overhead; the segment tree wins decisively over the heap for targetSize ≳ 500.

### When to choose each algorithm

**LTTB** is the right default for chart rendering. It is an order of magnitude faster than RDP
and PIP at every data size, scales linearly, and produces output that looks faithful to the
original series. The `FIXED` bucketization strategy handles unevenly-spaced or gappy data.

**RDP** is the right choice when geometric guarantees matter: no discarded point was ever more
than `epsilon` away from the simplified line. The output size is unpredictable, which is
sometimes a drawback. It is ~100× slower than LTTB at 500k points on this hardware. Unlike
most RDP implementations, this one uses an explicit stack rather than recursion, so it is safe
on any input size — there is no `StackOverflowError` risk.

**PIP** is the right choice when you need a precise number of structurally dominant features
from a series — peaks, troughs, abrupt changes — and the selection order matters: the most
globally important point is always added before any locally-important-but-globally-minor point.
The segment tree implementation scales to 500k points, though it is still orders of magnitude
slower than LTTB. Prefer it when the global ranking of importance is the priority; prefer LTTB
when throughput is.

## Contributing

This project enforces [Google Java Style](https://google.github.io/styleguide/javaguide.html)
via Checkstyle. The configuration lives in `config/checkstyle/`. Run it with:

```bash
./gradlew checkstyleMain
```

Checkstyle runs automatically as part of `./gradlew build`. The `test` and `jmh` source sets are
excluded from Checkstyle. The only active suppression is an inline `@SuppressWarnings` on
`LargestTriangleThreeBuckets` to allow the `LT` domain abbreviation.

The JDK version is managed by [mise](https://mise.jdx.dev/). Run `mise install` to get the
correct JDK for this branch.
