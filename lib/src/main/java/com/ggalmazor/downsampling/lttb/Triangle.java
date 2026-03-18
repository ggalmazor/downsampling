package com.ggalmazor.downsampling.lttb;

import com.ggalmazor.downsampling.Point;
import java.util.List;

/**
 * Represents the maximum-area triangle defined by three consecutive {@link Bucket} buckets.
 *
 * <p>The maximum-area triangle is defined by:
 * <ul>
 * <li>The result point of the left bucket.</li>
 * <li>The center point of the right bucket.</li>
 * <li>The point of the center bucket that maximises the triangle area. This becomes the
 *     selected result for the center bucket.</li>
 * </ul>
 *
 * @param <T> the type of the {@link Point} points in the buckets of this {@link Triangle}
 */
public class Triangle<T extends Point> {

  private final Bucket<T> left;
  private final Bucket<T> center;
  private final Bucket<T> right;

  private Triangle(Bucket<T> left, Bucket<T> center, Bucket<T> right) {
    this.left = left;
    this.center = center;
    this.right = right;
  }

  /**
   * Factory to build an instance of {@link Triangle} from a list of buckets at a given offset.
   *
   * <p>Uses direct index access to avoid allocating a {@code subList} view per iteration.
   *
   * @param buckets the full list of buckets
   * @param offset  the index of the left bucket in the window
   * @param <U>     the type of {@link Point} in the input buckets
   * @return the {@link Triangle} instance formed by buckets at offset, offset+1, offset+2
   */
  public static <U extends Point> Triangle<U> of(List<Bucket<U>> buckets, int offset) {
    return new Triangle<>(
        buckets.get(offset),
        buckets.get(offset + 1),
        buckets.get(offset + 2)
    );
  }

  /**
   * Returns the point of the middle bucket that produces the triangle with the largest area.
   *
   * @return the point of the middle bucket that produces the largest area
   * @throws IllegalStateException if the center bucket is empty
   */
  public T getResult() {
    Point leftPoint = left.getResult();
    Point rightCenter = right.getCenter();

    T bestPoint = null;
    double bestArea = -1.0;

    for (T candidate : center.points()) {
      double area = Area.ofTriangle(leftPoint, candidate, rightCenter);
      if (area > bestArea) {
        bestArea = area;
        bestPoint = candidate;
      }
    }

    if (bestPoint == null) {
      throw new IllegalStateException("Can't obtain max area triangle");
    }

    return bestPoint;
  }
}
