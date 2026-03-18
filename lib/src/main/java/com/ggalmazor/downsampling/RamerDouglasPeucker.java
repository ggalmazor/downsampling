package com.ggalmazor.downsampling;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Ramer-Douglas-Peucker (RDP) time-series downsampling algorithm.
 *
 * <p>RDP is a recursive line-simplification algorithm. It retains the first and last points,
 * then recursively finds the point with the maximum perpendicular distance from the line
 * connecting the current segment endpoints. If that distance exceeds {@code epsilon}, the
 * point is kept and the segment is split; otherwise all intermediate points are discarded.
 *
 * <p>Unlike bucket-based algorithms, the number of output points is determined by the data
 * shape and the chosen {@code epsilon} — not specified directly. A larger {@code epsilon}
 * produces fewer output points; a smaller one retains more detail.
 *
 * <p>The input list must be sorted by {@link Point#getX()} in monotonically non-decreasing
 * order. This class does not mutate the input list or its elements.
 */
public final class RamerDouglasPeucker {

  private RamerDouglasPeucker() {}

  /**
   * Returns a simplified version of the provided sorted {@code input} list.
   *
   * @param input   the sorted input list of {@link Point} points to simplify
   * @param epsilon the minimum perpendicular distance for a point to be retained
   * @param <U>     the type of the {@link Point} elements in the input list
   * @return the simplified output list
   */
  public static <U extends Point> List<U> simplify(List<U> input, double epsilon) {
    int size = input.size();
    if (size < 3) {
      return new ArrayList<>(input);
    }

    // Extract coordinates into flat primitive arrays once, eliminating List.get() virtual
    // dispatch and object field reads on every candidate in the recursive hot loop.
    double[] xs = new double[size];
    double[] ys = new double[size];
    for (int i = 0; i < size; i++) {
      Point p = input.get(i);
      xs[i] = p.x();
      ys[i] = p.y();
    }

    // Square epsilon once; the inner loop compares squared distances to avoid sqrt entirely.
    double epsilonSq = epsilon * epsilon;

    boolean[] keep = new boolean[size];
    keep[0] = true;
    keep[size - 1] = true;
    rdp(xs, ys, 0, size - 1, epsilonSq, keep);

    List<U> result = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      if (keep[i]) {
        result.add(input.get(i));
      }
    }
    return result;
  }

  /**
   * Iterative RDP: marks points in {@code keep[]} that exceed the squared-distance threshold.
   *
   * <p>Uses an explicit {@link Deque} stack instead of JVM call-stack recursion, eliminating
   * the O(n) stack-depth risk on adversarial or large inputs (which could otherwise cause
   * {@link StackOverflowError} when every split is maximally unbalanced).
   *
   * <p>Distances are compared as squared values to avoid {@link Math#sqrt} in the hot loop.
   */
  private static void rdp(
      double[] xs, double[] ys, int start, int end, double epsilonSq, boolean[] keep) {
    Deque<int[]> stack = new ArrayDeque<>();
    stack.push(new int[]{start, end});

    while (!stack.isEmpty()) {
      int[] segment = stack.pop();
      int lo = segment[0];
      int hi = segment[1];

      if (hi - lo < 2) {
        continue;
      }

      double x1 = xs[lo];
      double y1 = ys[lo];
      double dx = xs[hi] - x1;
      double dy = ys[hi] - y1;
      double lenSq = dx * dx + dy * dy;

      double maxDistSq = 0.0;
      int maxIndex = lo;

      for (int i = lo + 1; i < hi; i++) {
        double distSq = perpendicularDistanceSq(xs[i], ys[i], x1, y1, dx, dy, lenSq);
        if (distSq > maxDistSq) {
          maxDistSq = distSq;
          maxIndex = i;
        }
      }

      if (maxDistSq > epsilonSq) {
        keep[maxIndex] = true;
        stack.push(new int[]{lo, maxIndex});
        stack.push(new int[]{maxIndex, hi});
      }
    }
  }

  /**
   * Computes the <em>squared</em> perpendicular distance from point {@code (x0, y0)} to the
   * line segment defined by start {@code (x1, y1)}, direction {@code (dx, dy)}, and squared
   * length {@code lenSq}.
   *
   * <p>Pre-computing {@code dx}, {@code dy}, and {@code lenSq} outside the loop avoids
   * redundant arithmetic on the fixed segment endpoints for every candidate point.
   *
   * <p>When {@code lenSq == 0} (degenerate segment), falls back to squared Euclidean distance
   * to the start point.
   */
  private static double perpendicularDistanceSq(
      double x0, double y0, double x1, double y1, double dx, double dy, double lenSq) {
    if (lenSq == 0.0) {
      double ex = x0 - x1;
      double ey = y0 - y1;
      return ex * ex + ey * ey;
    }
    double num = dy * x0 - dx * y0 + (x1 + dx) * y1 - (y1 + dy) * x1;
    return (num * num) / lenSq;
  }

  /**
   * Computes the perpendicular distance from {@code point} to the line defined by
   * {@code lineStart} and {@code lineEnd}.
   *
   * <p>Used by {@link PerceptuallyImportantPoints} and exposed for testing.
   */
  static double perpendicularDistance(Point point, Point lineStart, Point lineEnd) {
    double x0 = point.x();
    double y0 = point.y();
    double x1 = lineStart.x();
    double y1 = lineStart.y();
    double x2 = lineEnd.x();
    double y2 = lineEnd.y();

    double dx = x2 - x1;
    double dy = y2 - y1;
    double lenSq = dx * dx + dy * dy;

    if (lenSq == 0.0) {
      double ex = x0 - x1;
      double ey = y0 - y1;
      return Math.sqrt(ex * ex + ey * ey);
    }

    double num = dy * x0 - dx * y0 + x2 * y1 - y2 * x1;
    return Math.abs(num) / Math.sqrt(lenSq);
  }
}
