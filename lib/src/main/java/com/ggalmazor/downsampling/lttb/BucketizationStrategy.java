package com.ggalmazor.downsampling.lttb;

import com.ggalmazor.downsampling.Point;

/**
 * Selects the bucket-size strategy used to divide the input series before the LTTB
 * triangle-selection step.
 *
 * <p>These strategies correspond directly to the dynamic and fixed bucket sizes described in
 * the original LTTB paper: <em>Downsampling Time Series for Visual Representation</em>
 * by Sveinn Steinarsson (2013).
 *
 * <ul>
 * <li>Use {@link #DYNAMIC} (the default) when samples are evenly distributed across the
 *     x-axis. Each bucket contains the same number of points.</li>
 * <li>Use {@link #FIXED} when samples are unevenly distributed or when there are gaps.
 *     Each bucket covers the same x-span. Empty buckets are silently skipped; the output
 *     may therefore have fewer than {@code buckets + 2} points.</li>
 * </ul>
 */
public enum BucketizationStrategy {

  /**
   * Dynamic bucket size: each bucket contains an equal number of points.
   *
   * <p>This is the default strategy. Throws {@link IllegalArgumentException} if the input
   * does not contain enough points for the requested bucket count.
   */
  DYNAMIC,

  /**
   * Fixed bucket size: each bucket covers an equal x-axis span.
   *
   * <p>The total x range {@code [x_first, x_last]} is divided into equal-width intervals.
   * Empty intervals are silently skipped. {@link Point#x()} must be monotonically
   * non-decreasing across the input list.
   */
  FIXED
}
