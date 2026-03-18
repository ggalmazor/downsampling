# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Java library providing three time-series downsampling algorithms: LTTB, RDP, and PIP. Published
to Maven Central as `com.ggalmazor:downsampling`.

The library version number reflects the minimum Java version required: `17.x.x` requires Java
17, `21.x.x` requires Java 21, `25.x.x` requires Java 25.

JDK version is managed by [mise](https://mise.jdx.dev/). Run `mise exec -- ./gradlew` to ensure
the correct JDK is used. Or fix `JAVA_HOME` in your shell profile with
`export JAVA_HOME=$(mise where java)`.

## Build Commands

```bash
mise exec -- ./gradlew build              # Build + test + checkstyle
mise exec -- ./gradlew test               # Run tests only
mise exec -- ./gradlew checkstyleMain     # Run Checkstyle on main sources only
mise exec -- ./gradlew jmh                # Run JMH benchmarks (takes several minutes)
mise exec -- ./gradlew javadoc            # Generate Javadoc
mise exec -- ./gradlew publishToMavenLocal  # Publish to local Maven repo (dry-run)
```

The project uses Gradle with a single `lib` subproject. All source code lives under `lib/src/`.

Run a single test class:
```bash
mise exec -- ./gradlew test --tests "com.ggalmazor.downsampling.LargestTriangleThreeBucketsTest"
```

## Code Style

This project enforces [Google Java Style](https://google.github.io/styleguide/javaguide.html)
via Checkstyle 10.x. Configuration lives in `config/checkstyle/`:

- `checkstyle.xml` — Google checks with `LineLength` raised to 120
- `checkstyle-suppressions.xml` — currently empty; prefer inline
  `@SuppressWarnings("checkstyle:RuleName")` for targeted suppressions
- Checkstyle runs on `main` sources only; `test` and `jmh` source sets are excluded
- The only active suppression is `@SuppressWarnings("checkstyle:AbbreviationAsWordInName")` on
  `LargestTriangleThreeBuckets` (LT is a domain abbreviation)
- Static imports must come before regular imports (Google style)

## Publishing

Publishing to Maven Central is triggered by pushing a `v*.*.*` tag. The workflow uses the
`vanniktech/gradle-maven-publish-plugin` with in-memory GPG signing via GitHub secrets:

- `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` — Sonatype portal token
- `GPG_SIGNING_KEY` — armored private key (`gpg --armor --export-secret-keys <KEY_ID>`)
- `GPG_SIGNING_PASSWORD` — key passphrase (empty string if none)

## Architecture

All three algorithms share the same data contract: `List<T extends Point>` in, `List<T>` out.

- **`Point`** — interface with `getX()`/`getY()`. Users implement this for their domain types.
- **`DoublePoint`** — built-in `Point` implementation backed by `double` values.
- **`Downsampling`** — public static facade. Start here.
- **`LargestTriangleThreeBuckets`** — LTTB. Flow: `sorted()` → `OnePassBucketizer.bucketize()`
  → `Triangle.of()` per sliding window of 3 buckets → select point with largest triangle area
  from each middle bucket. `DoublePoint` inputs use a struct-of-arrays fast path.
- **`RamerDouglasPeucker`** — RDP. Iterative (explicit stack); squared distances throughout to
  avoid `sqrt`; coordinates extracted into `double[]` arrays before the main loop.
- **`PerceptuallyImportantPoints`** — PIP. Max-heap + array-based doubly-linked list over
  selected indices; `prevSel[]`/`nextSel[]` give O(1) neighbour lookup.
- **`lttb/`** — LTTB internals: `Bucket`, `Triangle`, `Area`, `OnePassBucketizer`,
  `BucketizationStrategy`. Namespaced here because they are not reused by other algorithms.

## Testing

Tests use JUnit 5 + Hamcrest matchers. The test directory includes `DateSeriesPoint` (a custom
`Point` implementation for testing with real-world date series) and `PointMatcher` (custom
Hamcrest matcher).

Each algorithm has a `complex_downsampling_scenario` test that runs it against a real daily
foreign exchange rate series (`daily-foreign-exchange-rates-31-.csv`) and pins the exact output,
serving as a regression test for algorithmic correctness.

CI on `main` tests against Java 17, 21, and 25. Branch CIs (`v17`, `v21`) test against their
own Java version only.
