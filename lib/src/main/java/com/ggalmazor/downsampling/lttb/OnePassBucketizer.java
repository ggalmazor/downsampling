package com.ggalmazor.downsampling.lttb;

import com.ggalmazor.downsampling.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class that divides the input list of {@link Point} points into {@link Bucket} buckets
 * as required by the LTTB algorithm.
 *
 * <p>The first and last buckets always contain a single point (the first and last of the input).
 * Two strategies are supported; see {@link BucketizationStrategy}.
 */
public class OnePassBucketizer {

  private OnePassBucketizer() {}

  /**
   * Returns buckets from {@code input} using the count-based (DYNAMIC) strategy.
   *
   * @param input          the input list of points
   * @param inputSize      the size of the input list
   * @param desiredBuckets the desired middle bucket count
   * @param <T>            the type of the {@link Point} points
   * @return the list of buckets
   */
  public static <T extends Point> List<Bucket<T>> bucketize(
      List<T> input, int inputSize, int desiredBuckets) {
    return bucketize(input, inputSize, desiredBuckets, BucketizationStrategy.DYNAMIC);
  }

  /**
   * Returns buckets from {@code input} using the specified {@link BucketizationStrategy}.
   *
   * @param input          the input list of points
   * @param inputSize      the size of the input list
   * @param desiredBuckets the desired middle bucket count
   * @param strategy       the bucketization strategy to use
   * @param <T>            the type of the {@link Point} points
   * @return the list of buckets
   */
  public static <T extends Point> List<Bucket<T>> bucketize(
      List<T> input, int inputSize, int desiredBuckets, BucketizationStrategy strategy) {
    return switch (strategy) {
      case DYNAMIC -> bucketizeByCount(input, inputSize, desiredBuckets);
      case FIXED -> bucketizeByFixedSpan(input, desiredBuckets);
    };
  }

  /**
   * Divides the input into buckets of equal point count.
   *
   * <p>Bucket size is {@code floor(middleSize / desiredBuckets)}, with remainder distributed
   * one extra point across the first buckets. Uses {@code subList} views — no element copying.
   */
  private static <T extends Point> List<Bucket<T>> bucketizeByCount(
      List<T> input, int inputSize, int desiredBuckets) {
    int middleSize = inputSize - 2;
    int bucketSize = middleSize / desiredBuckets;
    int remainingElements = middleSize % desiredBuckets;

    if (bucketSize == 0) {
      throw new IllegalArgumentException(
          "Can't produce " + desiredBuckets + " buckets from an input series of "
              + (middleSize + 2) + " elements");
    }

    List<Bucket<T>> buckets = new ArrayList<>(desiredBuckets + 2);
    buckets.add(Bucket.of(input.get(0), 0));

    int currentIndex = 1;
    for (int bucketIndex = 0; bucketIndex < desiredBuckets; bucketIndex++) {
      int currentBucketSize = bucketIndex < remainingElements ? bucketSize + 1 : bucketSize;
      int end = currentIndex + currentBucketSize;
      buckets.add(Bucket.of(input.subList(currentIndex, end), currentIndex, end));
      currentIndex = end;
    }

    buckets.add(Bucket.of(input.get(input.size() - 1), input.size() - 1));
    return buckets;
  }

  /**
   * Divides the input into buckets of equal x-span.
   *
   * <p>The total x range is split into {@code desiredBuckets} equal-width intervals. Each
   * middle point is assigned to the interval containing its x value. Empty intervals are
   * skipped, so the returned list may have fewer than {@code desiredBuckets + 2} buckets.
   */
  private static <T extends Point> List<Bucket<T>> bucketizeByFixedSpan(
      List<T> input, int desiredBuckets) {
    if (input.size() < 2) {
      throw new IllegalArgumentException(
          "Fixed-span bucketization requires at least 2 points");
    }

    double x0 = input.get(0).getX();
    double x1 = input.get(input.size() - 1).getX();
    double bucketWidth = (x1 - x0) / desiredBuckets;

    if (bucketWidth == 0) {
      throw new IllegalArgumentException(
          "Fixed-span bucketization requires points with distinct x() values");
    }

    // Each window accumulates (point, original-index) pairs so we can record start/end.
    @SuppressWarnings("unchecked")
    List<int[]>[] windowIndices = new List[desiredBuckets];
    for (int i = 0; i < desiredBuckets; i++) {
      windowIndices[i] = new ArrayList<>();
    }

    int lastBucketIndex = desiredBuckets - 1;
    for (int i = 1; i < input.size() - 1; i++) {
      int bucketIndex = (int) ((input.get(i).getX() - x0) / bucketWidth);
      windowIndices[Math.min(bucketIndex, lastBucketIndex)].add(new int[]{i});
    }

    List<Bucket<T>> buckets = new ArrayList<>(desiredBuckets + 2);
    buckets.add(Bucket.of(input.get(0), 0));

    for (List<int[]> indices : windowIndices) {
      if (!indices.isEmpty()) {
        int start = indices.get(0)[0];
        int end = indices.get(indices.size() - 1)[0] + 1;
        buckets.add(Bucket.of(input.subList(start, end), start, end));
      }
    }

    buckets.add(Bucket.of(input.get(input.size() - 1), input.size() - 1));
    return buckets;
  }
}
