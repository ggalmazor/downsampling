package com.ggalmazor.downsampling;

import com.ggalmazor.downsampling.lttb.BucketizationStrategy;
import java.util.List;

/**
 * Entry point for all downsampling algorithms provided by this library.
 *
 * <p>All methods accept a sorted {@code List<T extends Point>} and return a {@code List<T>},
 * preserving the concrete point type throughout. The input list must be sorted by
 * {@link Point#getX()} in monotonically non-decreasing order. No method mutates the input
 * list or its elements.
 *
 * <p>Algorithms:
 * <ul>
 * <li>{@link #lttb} — Largest-Triangle Three-Buckets. Bucket-based; output size is
 *     {@code buckets + 2}. The {@code buckets} parameter controls how many middle points are
 *     selected.</li>
 * <li>{@link #rdp} — Ramer-Douglas-Peucker. Distance-based; output size is data-driven.
 *     The {@code epsilon} parameter is the minimum perpendicular distance for a point to be
 *     retained — larger values produce smaller outputs.</li>
 * <li>{@link #pip} — Perceptually Important Points. Iterative; output size is always exactly
 *     {@code targetSize}. The {@code targetSize} parameter is the desired total number of
 *     output points including first and last.</li>
 * </ul>
 */
public final class Downsampling {

  private Downsampling() {}

  /**
   * Downsamples {@code input} using the Largest-Triangle Three-Buckets algorithm with the
   * {@link BucketizationStrategy#DYNAMIC} strategy.
   *
   * <p>The output contains {@code buckets + 2} points: one selected from each middle bucket,
   * plus the first and last points of the input.
   *
   * @param input   the sorted input list of points
   * @param buckets the number of middle buckets (and therefore middle output points)
   * @param <T>     the type of the {@link Point} elements
   * @return the downsampled list
   */
  public static <T extends Point> List<T> lttb(List<T> input, int buckets) {
    return LargestTriangleThreeBuckets.sorted(input, buckets);
  }

  /**
   * Downsamples {@code input} using the Largest-Triangle Three-Buckets algorithm with the
   * specified {@link BucketizationStrategy}.
   *
   * <p>With {@link BucketizationStrategy#DYNAMIC} the output contains exactly
   * {@code buckets + 2} points. With {@link BucketizationStrategy#FIXED}, empty x-span
   * intervals are skipped so the output may contain fewer than {@code buckets + 2} points.
   *
   * @param input    the sorted input list of points
   * @param buckets  the number of middle buckets
   * @param strategy the bucketization strategy to use
   * @param <T>      the type of the {@link Point} elements
   * @return the downsampled list
   */
  public static <T extends Point> List<T> lttb(
      List<T> input, int buckets, BucketizationStrategy strategy) {
    return LargestTriangleThreeBuckets.sorted(input, buckets, strategy);
  }

  /**
   * Downsamples {@code input} using the Ramer-Douglas-Peucker algorithm.
   *
   * <p>The output size is determined by the data shape and {@code epsilon}: every retained
   * interior point has a perpendicular distance greater than {@code epsilon} from the line
   * connecting its enclosing retained neighbours. A larger {@code epsilon} retains fewer
   * points; {@code epsilon = 0} retains all points.
   *
   * @param input   the sorted input list of points
   * @param epsilon the minimum perpendicular distance for a point to be retained
   * @param <T>     the type of the {@link Point} elements
   * @return the downsampled list; always includes the first and last input points
   */
  public static <T extends Point> List<T> rdp(List<T> input, double epsilon) {
    return RamerDouglasPeucker.simplify(input, epsilon);
  }

  /**
   * Downsamples {@code input} using the Perceptually Important Points algorithm.
   *
   * <p>The output contains exactly {@code targetSize} points (or all input points if
   * {@code targetSize >= input.size()}). Points are selected greedily in order of decreasing
   * perpendicular distance to their currently-selected neighbours.
   *
   * @param input      the sorted input list of points
   * @param targetSize the desired total number of output points, including first and last
   * @param <T>        the type of the {@link Point} elements
   * @return the downsampled list
   */
  public static <T extends Point> List<T> pip(List<T> input, int targetSize) {
    return PerceptuallyImportantPoints.select(input, targetSize);
  }
}
