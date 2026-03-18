package com.ggalmazor.downsampling;

import static com.ggalmazor.downsampling.PointMatcher.pointAt;
import static java.util.Arrays.asList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RamerDouglasPeuckerTest {

  @Test
  public void empty_input_returns_empty() {
    List<DoublePoint> output = RamerDouglasPeucker.simplify(List.of(), 1.0);
    assertThat(output, hasSize(0));
  }

  @Test
  public void single_point_returns_that_point() {
    List<DoublePoint> input = List.of(DoublePoint.of(0, 0));
    List<DoublePoint> output = RamerDouglasPeucker.simplify(input, 1.0);
    assertThat(output, contains(pointAt(0, 0)));
  }

  @Test
  public void two_points_are_always_preserved() {
    List<DoublePoint> input = asList(DoublePoint.of(0, 0), DoublePoint.of(10, 5));
    List<DoublePoint> output = RamerDouglasPeucker.simplify(input, 1.0);
    assertThat(output, contains(pointAt(0, 0), pointAt(10, 5)));
  }

  @Test
  public void collinear_points_are_discarded() {
    // Points perfectly on a line — all interior points should be removed
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 2),
        DoublePoint.of(3, 3),
        DoublePoint.of(4, 4)
    );

    List<DoublePoint> output = RamerDouglasPeucker.simplify(input, 0.0001);

    assertThat(output, contains(pointAt(0, 0), pointAt(4, 4)));
  }

  @Test
  public void high_epsilon_reduces_to_two_points() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 0),
        DoublePoint.of(3, 1),
        DoublePoint.of(4, 0)
    );

    // Large epsilon — all interior distances are small relative to threshold
    List<DoublePoint> output = RamerDouglasPeucker.simplify(input, 100.0);

    assertThat(output, contains(pointAt(0, 0), pointAt(4, 0)));
  }

  @Test
  public void prominent_peak_is_retained() {
    // Sharp peak at (5, 10) should be retained with moderate epsilon
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(2, 1),
        DoublePoint.of(5, 10),
        DoublePoint.of(8, 1),
        DoublePoint.of(10, 0)
    );

    List<DoublePoint> output = RamerDouglasPeucker.simplify(input, 1.0);

    assertThat(output.stream().anyMatch(p -> p.x() == 5.0 && p.y() == 10.0), equalTo(true));
  }

  @Test
  public void first_and_last_points_are_always_preserved() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 7),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 2),
        DoublePoint.of(10, 3)
    );

    List<DoublePoint> output = RamerDouglasPeucker.simplify(input, 100.0);

    assertThat(output.get(0), pointAt(0, 7));
    assertThat(output.get(output.size() - 1), pointAt(10, 3));
  }

  @Test
  public void larger_epsilon_produces_fewer_or_equal_points() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 2),
        DoublePoint.of(2, 1),
        DoublePoint.of(3, 3),
        DoublePoint.of(4, 0),
        DoublePoint.of(5, 2),
        DoublePoint.of(6, 0)
    );

    List<DoublePoint> fineOutput = RamerDouglasPeucker.simplify(input, 0.5);
    List<DoublePoint> coarseOutput = RamerDouglasPeucker.simplify(input, 2.0);

    assertThat(coarseOutput.size(), lessThanOrEqualTo(fineOutput.size()));
  }

  @Test
  public void facade_rdp_delegates_to_simplify() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 2),
        DoublePoint.of(2, 1),
        DoublePoint.of(3, 3),
        DoublePoint.of(4, 0)
    );

    List<DoublePoint> viaFacade = Downsampling.rdp(input, 1.0);
    List<DoublePoint> direct = RamerDouglasPeucker.simplify(input, 1.0);

    assertThat(viaFacade, equalTo(direct));
  }

  @SuppressWarnings({"DataFlowIssue", "resource"})
  @Test
  public void complex_downsampling_scenario() throws URISyntaxException, IOException {
    URI uri = RamerDouglasPeucker.class
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

    // epsilon=0.5 retains the structurally most important turning points in the FX series
    List<DateSeriesPoint> output = RamerDouglasPeucker.simplify(series, 0.5);
    List<LocalDate> selectedDates = output.stream()
        .map(DateSeriesPoint::date)
        .collect(toList());
    assertThat(selectedDates, contains(
        LocalDate.of(1979, 12, 31),
        LocalDate.of(1981, 8, 10),
        LocalDate.of(1984, 3, 6),
        LocalDate.of(1985, 2, 25),
        LocalDate.of(1986, 3, 4),
        LocalDate.of(1987, 12, 31),
        LocalDate.of(1998, 12, 31)
    ));
  }

  @Test
  public void perpendicular_distance_from_point_on_line_is_zero() {
    Point lineStart = DoublePoint.of(0, 0);
    Point lineEnd = DoublePoint.of(4, 4);
    Point onLine = DoublePoint.of(2, 2);

    double dist = RamerDouglasPeucker.perpendicularDistance(onLine, lineStart, lineEnd);

    assertThat(dist < 1e-10, equalTo(true));
  }

  @Test
  public void perpendicular_distance_degenerate_segment_returns_euclidean_distance() {
    Point lineStart = DoublePoint.of(1, 1);
    Point lineEnd = DoublePoint.of(1, 1); // same point
    Point query = DoublePoint.of(4, 5);

    double dist = RamerDouglasPeucker.perpendicularDistance(query, lineStart, lineEnd);

    // Euclidean distance from (1,1) to (4,5) = sqrt(9+16) = 5
    assertThat(Math.abs(dist - 5.0) < 1e-10, equalTo(true));
  }
}
