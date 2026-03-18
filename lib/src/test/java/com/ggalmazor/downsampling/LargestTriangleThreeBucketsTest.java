package com.ggalmazor.downsampling;

import static com.ggalmazor.downsampling.PointMatcher.pointAt;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ggalmazor.downsampling.lttb.BucketizationStrategy;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class LargestTriangleThreeBucketsTest {

  @Test
  public void one_bucket_with_one_point_produces_that_point() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 0)
    );

    List<DoublePoint> output = LargestTriangleThreeBuckets.sorted(input, input.size(), 1);

    assertThat(output, contains(
        pointAt(0, 0),
        pointAt(1, 1),
        pointAt(2, 0)
    ));
  }

  @Test
  public void one_bucket_with_two_points_with_same_area_produces_the_first_point() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, -1),
        DoublePoint.of(3, 0)
    );

    List<DoublePoint> output = LargestTriangleThreeBuckets.sorted(input, input.size(), 1);

    assertThat(output, contains(
        pointAt(0, 0),
        pointAt(1, 1),
        pointAt(3, 0)
    ));
  }

  @Test
  public void one_bucket_with_two_points_with_different_area_produces_the_point_that_generates_max_area() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 2),
        DoublePoint.of(3, 0)
    );

    List<DoublePoint> output = LargestTriangleThreeBuckets.sorted(input, input.size(), 1);

    assertThat(output, contains(
        pointAt(0, 0),
        pointAt(2, 2),
        pointAt(3, 0)
    ));
  }

  @Test
  public void two_buckets_with_one_point_each() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 2),
        DoublePoint.of(3, 0)
    );

    List<DoublePoint> output = LargestTriangleThreeBuckets.sorted(input, input.size(), 2);

    assertThat(output, equalTo(input));
  }

  @Test
  public void two_buckets_non_full_middle_buckets() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 2),
        DoublePoint.of(3, 1),
        DoublePoint.of(4, 5)
    );

    List<DoublePoint> output = LargestTriangleThreeBuckets.sorted(input, input.size(), 2);

    assertThat(output, contains(
        pointAt(0, 0),
        pointAt(2, 2),
        pointAt(3, 1),
        pointAt(4, 5)
    ));
  }

  @Test
  public void two_buckets_full_middle_buckets() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 3),
        DoublePoint.of(3, 1),
        DoublePoint.of(4, 3),
        DoublePoint.of(5, 2),
        DoublePoint.of(6, 0)
    );

    List<DoublePoint> output = LargestTriangleThreeBuckets.sorted(input, input.size(), 2);

    assertThat(output, contains(
        pointAt(0, 0),
        pointAt(2, 3),
        pointAt(4, 3),
        pointAt(6, 0)
    ));
  }

  @Test
  public void throws_when_more_buckets_than_possible() {
    assertThrows(IllegalArgumentException.class, () ->
        LargestTriangleThreeBuckets.sorted(emptyList(), 3, 2)
    );
  }

  // ---- via Downsampling facade ----

  @Test
  public void facade_lttb_produces_expected_output() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 3),
        DoublePoint.of(3, 1),
        DoublePoint.of(4, 3),
        DoublePoint.of(5, 2),
        DoublePoint.of(6, 0)
    );

    List<DoublePoint> output = Downsampling.lttb(input, 2);

    assertThat(output, contains(
        pointAt(0, 0),
        pointAt(2, 3),
        pointAt(4, 3),
        pointAt(6, 0)
    ));
  }

  @Test
  public void facade_lttb_fixed_evenly_spaced_matches_dynamic() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 3),
        DoublePoint.of(3, 1),
        DoublePoint.of(4, 3),
        DoublePoint.of(5, 2),
        DoublePoint.of(6, 0)
    );

    List<DoublePoint> dynamic = Downsampling.lttb(input, 2);
    List<DoublePoint> fixed = Downsampling.lttb(input, 2, BucketizationStrategy.FIXED);

    assertThat(fixed, equalTo(dynamic));
  }

  @Test
  public void facade_lttb_fixed_skips_empty_buckets_in_gappy_series() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 5),
        DoublePoint.of(2, 3),
        DoublePoint.of(3, 4),
        DoublePoint.of(9, 1),
        DoublePoint.of(10, 0)
    );

    List<DoublePoint> output = Downsampling.lttb(input, 4, BucketizationStrategy.FIXED);

    assertThat(output.size(), equalTo(5));
    assertThat(output.get(0), pointAt(0, 0));
    assertThat(output.get(output.size() - 1), pointAt(10, 0));
  }

  @Test
  public void facade_lttb_fixed_throws_when_all_x_values_are_equal() {
    List<DoublePoint> input = asList(
        DoublePoint.of(1, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(1, 2)
    );

    assertThrows(IllegalArgumentException.class, () ->
        Downsampling.lttb(input, 2, BucketizationStrategy.FIXED)
    );
  }

  @SuppressWarnings({"DataFlowIssue", "resource"})
  @Test
  public void complex_downsampling_scenario() throws URISyntaxException, IOException {
    URI uri = Downsampling.class
        .getResource("/daily-foreign-exchange-rates-31-.csv").toURI();
    List<DateSeriesPoint> series = Files.lines(Paths.get(uri))
        .map(line -> line.split(";"))
        .map(cols -> {
          LocalDate date = LocalDate.parse(cols[0]);
          double value = Double.parseDouble(cols[1]);
          return new DateSeriesPoint(date, value);
        })
        .sorted(comparing(Point::x))
        .collect(toList());

    List<DateSeriesPoint> output = Downsampling.lttb(series, 10);
    List<LocalDate> selectedDates = output.stream()
        .map(DateSeriesPoint::date)
        .collect(toList());
    assertThat(selectedDates, contains(
        LocalDate.of(1979, 12, 31),
        LocalDate.of(1981, 8, 10),
        LocalDate.of(1982, 11, 8),
        LocalDate.of(1985, 2, 25),
        LocalDate.of(1985, 9, 18),
        LocalDate.of(1987, 12, 31),
        LocalDate.of(1991, 2, 11),
        LocalDate.of(1992, 9, 2),
        LocalDate.of(1995, 3, 7),
        LocalDate.of(1995, 4, 19),
        LocalDate.of(1997, 8, 5),
        LocalDate.of(1998, 12, 31)
    ));
  }
}
