package com.ggalmazor.downsampling;

import java.util.ArrayList;
import java.util.List;

/**
 * Perceptually Important Points (PIP) time-series downsampling algorithm.
 *
 * <p>PIP is an iterative greedy algorithm that builds the output set starting from the first
 * and last points, then repeatedly finds and adds the remaining point that has the largest
 * perpendicular distance from the line connecting its two currently-selected neighbours, until
 * the desired number of output points is reached.
 *
 * <p>Unlike RDP, PIP always produces exactly {@code targetSize} output points (or all input
 * points if the input is smaller).
 *
 * <p>The implementation uses a max segment tree over the squared perpendicular distances array.
 * This gives O(log n) for both "find the unselected point with the maximum distance" and
 * "update the distance of a point after its neighbours change". Total complexity is
 * O(n + k log n): O(n) to build the initial tree, O(k log n) to select k points each
 * requiring O(segment length) distance updates, each O(log n). There are no stale heap
 * entries, no garbage accumulation, and no dependence on Java's {@link java.util.PriorityQueue}
 * lacking a {@code decreaseKey} operation.
 *
 * <p>The input list must be sorted by {@link Point#x()} in monotonically non-decreasing
 * order. This class does not mutate the input list or its elements.
 */
public final class PerceptuallyImportantPoints {

  private PerceptuallyImportantPoints() {}

  /**
   * Returns a downsampled version of the provided sorted {@code input} list with exactly
   * {@code targetSize} points (or all points if {@code targetSize >= input.size()}).
   *
   * @param input      the sorted input list of {@link Point} points to downsample
   * @param targetSize the desired total number of output points including the first and last
   * @param <U>        the type of the {@link Point} elements in the input list
   * @return the downsampled output list
   */
  public static <U extends Point> List<U> select(List<U> input, int targetSize) {
    int size = input.size();
    if (targetSize >= size) {
      return new ArrayList<>(input);
    }
    if (targetSize < 2) {
      throw new IllegalArgumentException("targetSize must be at least 2");
    }

    // Extract coordinates into flat arrays: eliminates repeated List.get() and virtual
    // dispatch through the Point interface in the distance computation hot path.
    double[] xs = new double[size];
    double[] ys = new double[size];
    for (int i = 0; i < size; i++) {
      Point p = input.get(i);
      xs[i] = p.x();
      ys[i] = p.y();
    }

    // Doubly-linked list over *selected* indices only. prevSel[i] / nextSel[i] give the
    // nearest selected neighbour on each side of index i. O(1) lookup, O(segment) update.
    int[] prevSel = new int[size];
    int[] nextSel = new int[size];
    for (int i = 0; i < size; i++) {
      prevSel[i] = 0;
      nextSel[i] = size - 1;
    }

    boolean[] selected = new boolean[size];
    selected[0] = true;
    selected[size - 1] = true;

    // Initial squared distances from the single segment [0, size-1].
    double[] distances = new double[size];
    for (int i = 1; i < size - 1; i++) {
      distances[i] = perpendicularDistanceSq(xs, ys, i, 0, size - 1);
    }
    // Endpoints are already selected; mark them out of range.
    distances[0] = -1.0;
    distances[size - 1] = -1.0;

    // Max segment tree over distances[0..size-1]. Each leaf stores (distance, originalIndex).
    // Internal nodes store the max of their children. Query and update are both O(log n).
    // Selected points are set to -1.0, which is below any valid squared distance (>= 0),
    // so they are naturally excluded from max queries without a separate "active" flag.
    SegmentTree tree = new SegmentTree(distances, size);

    int selectedCount = 2;
    while (selectedCount < targetSize) {
      int idx = tree.argmax();
      if (idx < 0) {
        break; // all remaining points have distance -1 (degenerate)
      }

      selected[idx] = true;
      selectedCount++;

      int lo = prevSel[idx];
      int hi = nextSel[idx];

      // Recompute distances for unselected points in (lo, idx): right neighbour is now idx.
      for (int j = lo + 1; j < idx; j++) {
        if (!selected[j]) {
          nextSel[j] = idx;
          distances[j] = perpendicularDistanceSq(xs, ys, j, prevSel[j], idx);
          tree.update(j, distances[j]);
        }
      }
      // Recompute distances for unselected points in (idx, hi): left neighbour is now idx.
      for (int j = idx + 1; j < hi; j++) {
        if (!selected[j]) {
          prevSel[j] = idx;
          distances[j] = perpendicularDistanceSq(xs, ys, j, idx, nextSel[j]);
          tree.update(j, distances[j]);
        }
      }

      // Remove idx itself from future queries.
      tree.update(idx, -1.0);
    }

    List<U> result = new ArrayList<>(targetSize);
    for (int i = 0; i < size; i++) {
      if (selected[i]) {
        result.add(input.get(i));
      }
    }
    return result;
  }

  /**
   * Computes the squared perpendicular distance from {@code (xs[i], ys[i])} to the line
   * connecting {@code (xs[a], ys[a])} and {@code (xs[b], ys[b])}.
   *
   * <p>Squared distances preserve the same ordering as true distances (both non-negative)
   * and avoid {@link Math#sqrt} in the hot path.
   */
  private static double perpendicularDistanceSq(
      double[] xs, double[] ys, int i, int a, int b) {
    double x0 = xs[i];
    double y0 = ys[i];
    double x1 = xs[a];
    double y1 = ys[a];
    double dx = xs[b] - x1;
    double dy = ys[b] - y1;
    double lenSq = dx * dx + dy * dy;
    if (lenSq == 0.0) {
      double ex = x0 - x1;
      double ey = y0 - y1;
      return ex * ex + ey * ey;
    }
    double num = dy * x0 - dx * y0 + (x1 + dx) * y1 - (y1 + dy) * x1;
    return (num * num) / lenSq;
  }

  /**
   * A flat, array-backed max segment tree that stores {@code (maxDistance, argmax)} pairs.
   *
   * <p>The tree is laid out in a 1-indexed array of size {@code 4 * n}. Node {@code 1} is the
   * root; children of node {@code v} are {@code 2v} (left) and {@code 2v+1} (right). Each node
   * stores the maximum distance and the original array index that produced it.
   *
   * <p>Both {@link #update} and {@link #argmax} run in O(log n) time.
   *
   * <p>Selected points are represented as distance {@code -1.0}, which is below any valid
   * squared perpendicular distance (≥ 0), so they are automatically excluded from max queries.
   */
  private static final class SegmentTree {

    private final double[] maxDist;
    private final int[] maxIdx;
    private final int n;

    SegmentTree(double[] distances, int n) {
      this.n = n;
      this.maxDist = new double[4 * n];
      this.maxIdx = new int[4 * n];
      build(distances, 1, 0, n - 1);
    }

    private void build(double[] distances, int node, int lo, int hi) {
      if (lo == hi) {
        maxDist[node] = distances[lo];
        maxIdx[node] = lo;
        return;
      }
      int mid = (lo + hi) >>> 1;
      build(distances, 2 * node, lo, mid);
      build(distances, 2 * node + 1, mid + 1, hi);
      pushUp(node);
    }

    private void pushUp(int node) {
      int left = 2 * node;
      int right = 2 * node + 1;
      if (maxDist[left] >= maxDist[right]) {
        maxDist[node] = maxDist[left];
        maxIdx[node] = maxIdx[left];
      } else {
        maxDist[node] = maxDist[right];
        maxIdx[node] = maxIdx[right];
      }
    }

    /**
     * Updates the distance at position {@code pos} to {@code value} in O(log n).
     *
     * @param pos   the position to update (original array index)
     * @param value the new distance value
     */
    void update(int pos, double value) {
      update(1, 0, n - 1, pos, value);
    }

    private void update(int node, int lo, int hi, int pos, double value) {
      if (lo == hi) {
        maxDist[node] = value;
        maxIdx[node] = lo;
        return;
      }
      int mid = (lo + hi) >>> 1;
      if (pos <= mid) {
        update(2 * node, lo, mid, pos, value);
      } else {
        update(2 * node + 1, mid + 1, hi, pos, value);
      }
      pushUp(node);
    }

    /**
     * Returns the index of the element with the maximum distance in O(log n).
     *
     * @return the original array index of the maximum-distance element,
     *         or {@code -1} if all distances are negative (all points selected)
     */
    int argmax() {
      return maxDist[1] < 0.0 ? -1 : maxIdx[1];
    }
  }
}
