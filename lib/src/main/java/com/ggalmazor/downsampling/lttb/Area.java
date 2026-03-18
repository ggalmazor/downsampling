package com.ggalmazor.downsampling.lttb;

import static java.lang.Math.abs;

import com.ggalmazor.downsampling.Point;

/**
 * Utility class for triangle area computation used by the LTTB algorithm.
 *
 * <p>Exposes only a scalar computation method to avoid per-candidate object allocation
 * in the hot inner loop of {@link Triangle#getResult()}.
 */
class Area {

  private Area() {}

  /**
   * Computes the area of the triangle defined by three {@link Point} points using the
   * shoelace formula.
   *
   * @param a first point of the triangle
   * @param b second (middle) point of the triangle
   * @param c third point of the triangle
   * @return the area of the triangle as a scalar double value
   */
  static double ofTriangle(Point a, Point b, Point c) {
    // area of a triangle = |[Ax(By - Cy) + Bx(Cy - Ay) + Cx(Ay - By)] / 2|
    double sum = a.getX() * (b.getY() - c.getY())
        + b.getX() * (c.getY() - a.getY())
        + c.getX() * (a.getY() - b.getY());
    return abs(sum / 2);
  }
}
