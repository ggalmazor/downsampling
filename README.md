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
| 10k | 100 | 40.2 |
| 10k | 1 000 | 47.8 |
| 10k | 5 000 | 79.8 |
| 100k | 100 | 304 |
| 100k | 1 000 | 7 750 |
| 100k | 5 000 | 8 149 |

The 10k numbers are well-behaved. At 100k the jump from targetSize=100 (304 ms) to
targetSize=1 000 (7.75 s) reveals that the heap accumulates O(n·k) stale entries in total when
segments are large relative to n, negating the theoretical O((n+k) log n) improvement. PIP is
not benchmarked beyond 100k because the combination of large n and large k becomes impractical.

## Algorithm comparison

| | LTTB | RDP | PIP |
|---|:---:|:---:|:---:|
| Output size | exact (`buckets + 2`) | data-driven | exact (`targetSize`) |
| Control parameter | bucket count | epsilon (distance) | target point count |
| Time complexity | O(n) | O(n log n) avg / O(n²) worst | O((n+k) log n) theoretical¹ |
| Stack depth | O(1) | O(1) iterative | O(1) |
| Visual fidelity | high — optimised for charts | geometric — no point with dist > ε discarded | high — most prominent features first |
| Unevenly-spaced data | FIXED strategy | natural | natural |
| Practical limit | 500k+ points, <2 ms | 500k in 120–146 ms | ~50k–100k at low k |

¹ Degrades to O(n·k) in practice when large segments produce many heap re-insertions.

### When to choose each algorithm

**LTTB** is the right default for chart rendering. It is an order of magnitude faster than RDP
and PIP at every data size, scales linearly, and produces output that looks faithful to the
original series. The `FIXED` bucketization strategy handles unevenly-spaced or gappy data.

**RDP** is the right choice when geometric guarantees matter: no discarded point was ever more
than `epsilon` away from the simplified line. The output size is unpredictable, which is
sometimes a drawback. It is ~100× slower than LTTB at 500k points on this hardware.

**PIP** is the right choice when you need a small number of structurally dominant features from
a series — peaks, troughs, abrupt changes — and the input is modest in size (≤50k points with
small targetSize). The greedy selection order is unique: the most important point globally is
always added before any locally-important-but-globally-minor point.

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
