package com.ggalmazor.downsampling;

import com.ggalmazor.downsampling.lttb.Bucket;
import com.ggalmazor.downsampling.lttb.BucketizationStrategy;
import com.ggalmazor.downsampling.lttb.OnePassBucketizer;
import com.ggalmazor.downsampling.lttb.Triangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Largest-Triangle Three-Buckets (LTTB) time-series downsampling algorithm.
 *
 * <p>LTTB preserves the visual shape of a time series by selecting, from each bucket of the
 * input, the point that forms the largest triangle area with the previously selected point and
 * the centroid of the next bucket.
 *
 * <p>The input list must be sorted by {@link Point#getX()} in monotonically non-decreasing
 * order. None of the methods in this class mutate the input list or its elements.
 *
 * <p>When the input contains {@link DoublePoint} instances, a struct-of-arrays fast path is
 * used: coordinates are extracted into contiguous {@code double[]} arrays, eliminating
 * per-point pointer chasing in the hot selection loop.
 *
 * <p>When the bucket count meets or exceeds {@value #PARALLEL_THRESHOLD}, the triangle
 * selection loop runs in parallel via the common {@link java.util.concurrent.ForkJoinPool}.
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName") // LT is a domain abbreviation
public final class LargestTriangleThreeBuckets {

  /**
   * Minimum number of buckets to engage the parallel triangle-selection path.
   *
   * <p>Below this threshold, thread-management overhead exceeds the work saved.
   */
  static final int PARALLEL_THRESHOLD = 512;

  private LargestTriangleThreeBuckets() {}

  /**
   * Returns a downsampled version of the provided {@code input} list using the DYNAMIC strategy.
   *
   * @param input          the input list of {@link Point} points to downsample
   * @param desiredBuckets the desired number of middle buckets
   * @param <U>            the type of the {@link Point} elements in the input list
   * @return the downsampled output list
   */
  public static <U extends Point> List<U> sorted(List<U> input, int desiredBuckets) {
    return sorted(input, input.size(), desiredBuckets, BucketizationStrategy.DYNAMIC);
  }

  /**
   * Returns a downsampled version of the provided {@code input} list using the specified
   * {@link BucketizationStrategy}.
   *
   * @param input          the input list of {@link Point} points to downsample
   * @param desiredBuckets the desired number of middle buckets
   * @param strategy       the bucketization strategy to use
   * @param <U>            the type of the {@link Point} elements in the input list
   * @return the downsampled output list
   */
  public static <U extends Point> List<U> sorted(
      List<U> input, int desiredBuckets, BucketizationStrategy strategy) {
    return sorted(input, input.size(), desiredBuckets, strategy);
  }

  /**
   * Returns a downsampled version of the provided {@code input} list using the DYNAMIC strategy.
   *
   * @param input          the input list of {@link Point} points to downsample
   * @param inputSize      the size of the input list
   * @param desiredBuckets the desired number of middle buckets
   * @param <U>            the type of the {@link Point} elements in the input list
   * @return the downsampled output list
   */
  public static <U extends Point> List<U> sorted(List<U> input, int inputSize, int desiredBuckets) {
    return sorted(input, inputSize, desiredBuckets, BucketizationStrategy.DYNAMIC);
  }

  /**
   * Returns a downsampled version of the provided {@code input} list using the specified
   * {@link BucketizationStrategy}.
   *
   * @param input          the input list of {@link Point} points to downsample
   * @param inputSize      the size of the input list
   * @param desiredBuckets the desired number of middle buckets
   * @param strategy       the bucketization strategy to use
   * @param <U>            the type of the {@link Point} elements in the input list
   * @return the downsampled output list
   */
  public static <U extends Point> List<U> sorted(
      List<U> input, int inputSize, int desiredBuckets, BucketizationStrategy strategy) {
    List<Bucket<U>> buckets = OnePassBucketizer.bucketize(input, inputSize, desiredBuckets, strategy);
    int actualBuckets = buckets.size() - 2;

    if (!input.isEmpty() && input.get(0) instanceof DoublePoint) {
      @SuppressWarnings("unchecked")
      List<DoublePoint> dpInput = (List<DoublePoint>) input;
      @SuppressWarnings("unchecked")
      List<Bucket<DoublePoint>> dpBuckets = (List<Bucket<DoublePoint>>) (List<?>) buckets;
      @SuppressWarnings("unchecked")
      List<U> result = (List<U>) sortedDoublePoint(dpInput, dpBuckets, actualBuckets);
      return result;
    }

    return sortedGeneric(buckets, actualBuckets);
  }

  /**
   * Struct-of-arrays fast path for {@link DoublePoint} inputs.
   *
   * <p>Extracts all coordinates into contiguous {@code double[]} arrays once; the inner
   * selection loop then operates on primitives with no pointer chasing.
   */
  private static List<DoublePoint> sortedDoublePoint(
      List<DoublePoint> input,
      List<Bucket<DoublePoint>> buckets,
      int desiredBuckets) {
    int size = input.size();
    double[] xs = new double[size];
    double[] ys = new double[size];
    for (int i = 0; i < size; i++) {
      DoublePoint p = input.get(i);
      xs[i] = p.x();
      ys[i] = p.y();
    }

    @SuppressWarnings("unchecked")
    DoublePoint[] middleResults = new DoublePoint[desiredBuckets];

    IntStream stream = IntStream.range(0, desiredBuckets);
    if (desiredBuckets >= PARALLEL_THRESHOLD) {
      stream = stream.parallel();
    }

    stream.forEach(i -> middleResults[i] = selectBestDoublePoint(buckets, xs, ys, i));

    List<DoublePoint> results = new ArrayList<>(desiredBuckets + 2);
    results.add(buckets.get(0).getFirst());
    results.addAll(Arrays.asList(middleResults));
    results.add(buckets.get(buckets.size() - 1).getLast());
    return results;
  }

  /**
   * Selects the {@link DoublePoint} from the center bucket at window {@code offset} forming
   * the largest-area triangle, operating entirely on primitive {@code double[]} arrays.
   *
   * <p>Uses the bucket's {@link Bucket#getStartIndex()}/{@link Bucket#getEndIndex()} to index
   * directly into the pre-extracted {@code xs[]} and {@code ys[]} arrays, avoiding two virtual
   * method calls ({@link DoublePoint#x()}/{@link DoublePoint#y()}) per candidate.
   */
  private static DoublePoint selectBestDoublePoint(
      List<Bucket<DoublePoint>> buckets,
      double[] xs,
      double[] ys,
      int offset) {
    Bucket<DoublePoint> left = buckets.get(offset);
    Bucket<DoublePoint> center = buckets.get(offset + 1);
    Bucket<DoublePoint> right = buckets.get(offset + 2);

    // Left anchor: the result of the previous bucket (already a selected point).
    int leftResultIdx = left.getStartIndex();
    double lx = xs[leftResultIdx];
    double ly = ys[leftResultIdx];

    // Right anchor: the centroid of the next bucket's x/y span, computed from array values.
    int rightStart = right.getStartIndex();
    int rightEnd = right.getEndIndex();
    double rx = (xs[rightStart] + xs[rightEnd - 1]) / 2.0;
    double ry = (ys[rightStart] + ys[rightEnd - 1]) / 2.0;

    // Iterate over center bucket candidates using raw array indices — no object reads.
    int centerStart = center.getStartIndex();
    int centerEnd = center.getEndIndex();
    int bestIdx = centerStart;
    double bestArea = -1.0;

    for (int i = centerStart; i < centerEnd; i++) {
      double cx = xs[i];
      double cy = ys[i];
      double area = Math.abs(lx * (cy - ry) + cx * (ry - ly) + rx * (ly - cy)) / 2.0;
      if (area > bestArea) {
        bestArea = area;
        bestIdx = i;
      }
    }

    return buckets.get(offset + 1).points().get(bestIdx - centerStart);
  }

  /**
   * Generic path for arbitrary {@link Point} implementations.
   *
   * <p>Uses index-based {@link Triangle#of(List, int)} to avoid allocating a {@code subList}
   * view per iteration, and runs in parallel above {@value #PARALLEL_THRESHOLD} buckets.
   */
  @SuppressWarnings("unchecked")
  private static <U extends Point> List<U> sortedGeneric(
      List<Bucket<U>> buckets,
      int desiredBuckets) {
    U[] middleResults = (U[]) new Point[desiredBuckets];

    IntStream stream = IntStream.range(0, desiredBuckets);
    if (desiredBuckets >= PARALLEL_THRESHOLD) {
      stream = stream.parallel();
    }

    stream.forEach(i -> middleResults[i] = Triangle.of(buckets, i).getResult());

    List<U> results = new ArrayList<>(desiredBuckets + 2);
    results.add(buckets.get(0).getFirst());
    results.addAll(Arrays.asList(middleResults));
    results.add(buckets.get(buckets.size() - 1).getLast());
    return results;
  }
}
