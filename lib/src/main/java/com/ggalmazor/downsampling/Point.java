package com.ggalmazor.downsampling;

/**
 * Defines the properties of two-dimensional points that can be processed by downsampling
 * algorithms in this library.
 *
 * <p>Users of this library must use {@link DoublePoint} or implement their own subtypes of
 * this interface.
 *
 * <p><strong>Contract:</strong> {@code getX()} must be monotonically non-decreasing across a
 * sorted input list — i.e. for any two consecutive points {@code a} and {@code b} in the list
 * passed to a downsampler, {@code a.getX() <= b.getX()} must hold. Algorithms do not verify
 * this; violating it produces undefined output.
 */
public interface Point {

  /**
   * Computes the geometric center point of the segment linking the provided {@code a} and
   * {@code b} points.
   *
   * @param a the first point of the segment
   * @param b the second point of the segment
   * @return a {@link DoublePoint} in the geometric center between the two provided points
   */
  static DoublePoint centerBetween(Point a, Point b) {
    return new DoublePoint((a.getX() + b.getX()) / 2.0, (a.getY() + b.getY()) / 2.0);
  }

  /**
   * Returns the x (horizontal / time) value of this point.
   *
   * @return the x (horizontal / time) value of this point
   */
  double getX();

  /**
   * Returns the y (vertical / value) value of this point.
   *
   * @return the y (vertical / value) value of this point
   */
  double getY();
}
