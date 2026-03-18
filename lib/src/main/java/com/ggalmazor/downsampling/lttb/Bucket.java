package com.ggalmazor.downsampling.lttb;

import static com.ggalmazor.downsampling.Point.centerBetween;

import com.ggalmazor.downsampling.DoublePoint;
import com.ggalmazor.downsampling.Point;
import java.util.Collections;
import java.util.List;

/**
 * Represents a bucket of {@link Point} points being downsampled to a single point by LTTB.
 *
 * <p>{@link #startIndex} and {@link #endIndex} (exclusive) are the positions of this bucket's
 * points in the original input list. They are used by the {@link DoublePoint} fast path in
 * {@code LargestTriangleThreeBuckets} to index into pre-extracted coordinate arrays directly,
 * avoiding per-point virtual dispatch through {@link Point#getX()} and {@link Point#getY()}.
 *
 * @param <T> the type of the {@link Point} points in this bucket
 */
public class Bucket<T extends Point> {

  private final List<T> data;
  private final T first;
  private final T last;
  private final Point center;
  private final T result;
  private final int startIndex;
  private final int endIndex;

  private Bucket(List<T> data, T first, T last, Point center, T result, int startIndex, int endIndex) {
    this.data = data;
    this.first = first;
    this.last = last;
    this.center = center;
    this.result = result;
    this.startIndex = startIndex;
    this.endIndex = endIndex;
  }

  /**
   * Utility factory that takes a sublist view and its absolute indices in the original input.
   *
   * @param points     the input list of points in the bucket being built
   * @param startIndex the start index (inclusive) in the original input list
   * @param endIndex   the end index (exclusive) in the original input list
   * @param <U>        the type of the {@link Point} points in the bucket being built
   * @return the bucket
   */
  public static <U extends Point> Bucket<U> of(List<U> points, int startIndex, int endIndex) {
    U first = points.get(0);
    U last = points.get(points.size() - 1);
    DoublePoint center = centerBetween(first, last);
    return new Bucket<>(points, first, last, center, first, startIndex, endIndex);
  }

  /**
   * Utility factory that returns a {@link Bucket} with a single {@link Point} point.
   *
   * @param point      the input point in the bucket being built
   * @param pointIndex the index of this point in the original input list
   * @param <U>        the type of the {@link Point} point in the bucket being built
   * @return the bucket
   */
  public static <U extends Point> Bucket<U> of(U point, int pointIndex) {
    return new Bucket<>(
        Collections.singletonList(point), point, point, point, point, pointIndex, pointIndex + 1);
  }

  /**
   * Returns the result point selected for this bucket.
   *
   * @return the result point
   */
  public T getResult() {
    return result;
  }

  /**
   * Returns the first point in this bucket.
   *
   * @return the first point
   */
  public T getFirst() {
    return first;
  }

  /**
   * Returns the last point in this bucket.
   *
   * @return the last point
   */
  public T getLast() {
    return last;
  }

  /**
   * Returns the center point of this bucket.
   *
   * @return the center point
   */
  public Point getCenter() {
    return center;
  }

  /**
   * Returns the start index (inclusive) of this bucket's points in the original input list.
   *
   * @return the start index
   */
  public int getStartIndex() {
    return startIndex;
  }

  /**
   * Returns the end index (exclusive) of this bucket's points in the original input list.
   *
   * @return the end index
   */
  public int getEndIndex() {
    return endIndex;
  }

  /**
   * Returns a read-only view of the points in this bucket.
   *
   * <p>Used in the hot inner loop of {@link Triangle#getResult()} to iterate candidates
   * without allocating an intermediate mapped collection.
   *
   * @return an unmodifiable view of the points in this bucket
   */
  public List<T> points() {
    return Collections.unmodifiableList(data);
  }
}
