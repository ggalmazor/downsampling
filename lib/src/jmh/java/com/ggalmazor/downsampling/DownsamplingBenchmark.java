package com.ggalmazor.downsampling;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for LTTB, RDP, and PIP downsampling algorithms.
 *
 * <p>Each algorithm is parameterised on axes that are meaningful for it:
 * <ul>
 * <li>LTTB: {@code dataSize} × {@code targetPoints} — O(n) in data size.</li>
 * <li>RDP: {@code dataSize} × {@code epsilon} — O(n log n) average, O(n²) worst case;
 *     output size is data-driven.</li>
 * <li>PIP: {@code dataSize} × {@code targetPoints} — O((n + k) log n).</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class DownsamplingBenchmark {

  private static final long SEED = 0xDEADBEEFL;

  // ---- LTTB state -------------------------------------------------------

  /**
   * LTTB is O(n) in data size — test all three data sizes and target-point counts.
   */
  @State(Scope.Benchmark)
  public static class LttbState {

    @Param({"10000", "100000", "500000"})
    public int dataSize;

    @Param({"100", "1000", "5000"})
    public int targetPoints;

    public List<DoublePoint> data;

    @Setup(Level.Trial)
    public void setup() {
      data = generateData(dataSize);
    }
  }

  // ---- RDP state --------------------------------------------------------

  /**
   * RDP is O(n log n) average — test all data sizes; epsilon varies independently.
   */
  @State(Scope.Benchmark)
  public static class RdpState {

    @Param({"10000", "100000", "500000"})
    public int dataSize;

    // 0.5 → aggressive, 2.0 → moderate, 5.0 → light simplification
    @Param({"0.5", "2.0", "5.0"})
    public double epsilon;

    public List<DoublePoint> data;

    @Setup(Level.Trial)
    public void setup() {
      data = generateData(dataSize);
    }
  }

  // ---- PIP state --------------------------------------------------------

  /**
   * PIP is O((n + k) log n) — capped at 100k due to heap pressure at large k.
   */
  @State(Scope.Benchmark)
  public static class PipState {

    @Param({"10000", "100000"})
    public int dataSize;

    @Param({"100", "1000", "5000"})
    public int targetPoints;

    public List<DoublePoint> data;

    @Setup(Level.Trial)
    public void setup() {
      data = generateData(dataSize);
    }
  }

  // ---- Benchmarks -------------------------------------------------------

  @Benchmark
  public void lttb(LttbState state, Blackhole bh) {
    bh.consume(LargestTriangleThreeBuckets.sorted(state.data, state.targetPoints));
  }

  @Benchmark
  public void rdp(RdpState state, Blackhole bh) {
    bh.consume(RamerDouglasPeucker.simplify(state.data, state.epsilon));
  }

  @Benchmark
  public void pip(PipState state, Blackhole bh) {
    bh.consume(PerceptuallyImportantPoints.select(state.data, state.targetPoints));
  }

  // ---- Shared data generation -------------------------------------------

  private static List<DoublePoint> generateData(int size) {
    Random random = new Random(SEED);
    List<DoublePoint> points = new ArrayList<>(size);
    double baseValue = 100.0;
    double trend = 0.001;
    for (int i = 0; i < size; i++) {
      double x = i;
      double y = baseValue + (trend * i) + (Math.sin(i * 0.01) * 10) + (random.nextDouble() * 5 - 2.5);
      points.add(new DoublePoint(x, y));
    }
    return points;
  }
}
