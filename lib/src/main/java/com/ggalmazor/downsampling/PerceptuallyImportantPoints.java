package com.ggalmazor.downsampling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

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
 * <p>The input list must be sorted by {@link Point#getX()} in monotonically non-decreasing
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

    // Doubly-linked list over *selected* indices only. prevSelected[i] / nextSelected[i]
    // give the nearest selected neighbour on each side of index i. Initially only 0 and
    // size-1 are selected, so every middle point has prevSelected=0 and nextSelected=size-1.
    // When a new point k is selected, all points in (prevSelected[k], k) and (k, nextSelected[k])
    // have their nextSelected or prevSelected updated to k respectively. This gives O(1) lookup
    // and O(segment length) update per accepted point — total O(n) updates across the whole run.
    int[] prevSel = new int[size];
    int[] nextSel = new int[size];
    for (int i = 0; i < size; i++) {
      prevSel[i] = 0;
      nextSel[i] = size - 1;
    }
    // Sentinel: selected endpoints point to themselves.
    prevSel[0] = 0;
    nextSel[size - 1] = size - 1;

    boolean[] selected = new boolean[size];
    selected[0] = true;
    selected[size - 1] = true;

    // distances[i] is the current squared perpendicular distance for unselected point i.
    // This is the single source of truth for staleness detection in the heap.
    double[] distances = new double[size];
    for (int i = 1; i < size - 1; i++) {
      distances[i] = perpendicularDistanceSq(xs, ys, i, 0, size - 1);
    }

    // Max-heap of {squaredDistanceBits, index}. Entries become stale when a neighbour is
    // added (distances[idx] changes). Staleness is detected on pop by comparing the stored
    // distance bits against distances[idx]; stale entries are simply discarded.
    PriorityQueue<long[]> heap = new PriorityQueue<>(
        size - 2,
        Comparator.comparingDouble((long[] e) -> Double.longBitsToDouble(e[0])).reversed()
    );
    for (int i = 1; i < size - 1; i++) {
      heap.offer(new long[]{Double.doubleToRawLongBits(distances[i]), i});
    }

    int selectedCount = 2;
    while (selectedCount < targetSize && !heap.isEmpty()) {
      long[] top = heap.poll();
      int idx = (int) top[1];
      double storedDist = Double.longBitsToDouble(top[0]);

      if (selected[idx] || distances[idx] != storedDist) {
        continue;
      }

      // Accept this point.
      selected[idx] = true;
      selectedCount++;

      int lo = prevSel[idx]; // nearest selected to the left
      int hi = nextSel[idx]; // nearest selected to the right

      // Update prevSel/nextSel for all points in (lo, idx): their right selected neighbour
      // was hi; it is now idx.
      for (int j = lo + 1; j < idx; j++) {
        nextSel[j] = idx;
        if (!selected[j]) {
          // Their left neighbour (lo) hasn't changed, but right has: recompute.
          distances[j] = perpendicularDistanceSq(xs, ys, j, prevSel[j], idx);
          heap.offer(new long[]{Double.doubleToRawLongBits(distances[j]), j});
        }
      }
      // Update prevSel/nextSel for all points in (idx, hi): their left selected neighbour
      // was lo; it is now idx.
      for (int j = idx + 1; j < hi; j++) {
        prevSel[j] = idx;
        if (!selected[j]) {
          // Their right neighbour (hi) hasn't changed, but left has: recompute.
          distances[j] = perpendicularDistanceSq(xs, ys, j, idx, nextSel[j]);
          heap.offer(new long[]{Double.doubleToRawLongBits(distances[j]), j});
        }
      }
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
}
