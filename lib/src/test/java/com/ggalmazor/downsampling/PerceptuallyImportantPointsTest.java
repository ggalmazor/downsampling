package com.ggalmazor.downsampling;

import static com.ggalmazor.downsampling.PointMatcher.pointAt;
import static java.util.Arrays.asList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PerceptuallyImportantPointsTest {

  @Test
  public void returns_all_points_when_nout_equals_input_size() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 0)
    );

    List<DoublePoint> output = PerceptuallyImportantPoints.select(input, 3);

    assertThat(output, equalTo(input));
  }

  @Test
  public void returns_all_points_when_nout_exceeds_input_size() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 0)
    );

    List<DoublePoint> output = PerceptuallyImportantPoints.select(input, 100);

    assertThat(output, equalTo(input));
  }

  @Test
  public void two_points_returns_first_and_last() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 5),
        DoublePoint.of(2, 3),
        DoublePoint.of(3, 8),
        DoublePoint.of(4, 0)
    );

    List<DoublePoint> output = PerceptuallyImportantPoints.select(input, 2);

    assertThat(output, hasSize(2));
    assertThat(output.get(0), pointAt(0, 0));
    assertThat(output.get(output.size() - 1), pointAt(4, 0));
  }

  @Test
  public void throws_when_target_size_less_than_two() {
    List<DoublePoint> input = asList(DoublePoint.of(0, 0), DoublePoint.of(1, 1));
    assertThrows(IllegalArgumentException.class, () ->
        PerceptuallyImportantPoints.select(input, 1)
    );
  }

  @Test
  public void first_and_last_are_always_preserved() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 99),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 2),
        DoublePoint.of(3, 3),
        DoublePoint.of(4, 88)
    );

    List<DoublePoint> output = PerceptuallyImportantPoints.select(input, 3);

    assertThat(output.get(0), pointAt(0, 99));
    assertThat(output.get(output.size() - 1), pointAt(4, 88));
  }

  @Test
  public void output_has_exactly_nout_points() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 2),
        DoublePoint.of(2, 1),
        DoublePoint.of(3, 4),
        DoublePoint.of(4, 0),
        DoublePoint.of(5, 3),
        DoublePoint.of(6, 0)
    );

    List<DoublePoint> output = PerceptuallyImportantPoints.select(input, 4);

    assertThat(output, hasSize(4));
  }

  @Test
  public void prominent_peak_is_selected_first() {
    // The highest peak should be selected before flatter points
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1),
        DoublePoint.of(2, 10), // large spike
        DoublePoint.of(3, 1),
        DoublePoint.of(4, 0)
    );

    List<DoublePoint> output = PerceptuallyImportantPoints.select(input, 3);

    // The spike at x=2 must be in the output
    assertThat(output.stream().anyMatch(p -> p.x() == 2.0 && p.y() == 10.0), equalTo(true));
  }

  @Test
  public void collinear_points_give_lowest_priority() {
    // Only first, last, and the non-collinear peak should survive when nOut=3
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 1), // collinear
        DoublePoint.of(2, 2), // collinear
        DoublePoint.of(3, 5), // off-line spike
        DoublePoint.of(4, 4), // collinear
        DoublePoint.of(5, 5), // collinear
        DoublePoint.of(6, 6)
    );

    List<DoublePoint> output = PerceptuallyImportantPoints.select(input, 3);

    assertThat(output.get(0), pointAt(0, 0));
    assertThat(output.get(output.size() - 1), pointAt(6, 6));
    // The spike (3,5) has the largest perpendicular distance from any connecting line
    assertThat(output.stream().anyMatch(p -> p.x() == 3.0), equalTo(true));
  }

  @Test
  public void output_is_in_sorted_x_order() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 3),
        DoublePoint.of(2, 1),
        DoublePoint.of(3, 4),
        DoublePoint.of(4, 2),
        DoublePoint.of(5, 0)
    );

    List<DoublePoint> output = PerceptuallyImportantPoints.select(input, 4);

    for (int i = 1; i < output.size(); i++) {
      assertThat(output.get(i).x() >= output.get(i - 1).x(), equalTo(true));
    }
  }

  @SuppressWarnings({"DataFlowIssue", "resource"})
  @Test
  public void complex_downsampling_scenario() throws URISyntaxException, IOException {
    URI uri = PerceptuallyImportantPoints.class
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

    List<DateSeriesPoint> output = PerceptuallyImportantPoints.select(series, 12);
    List<LocalDate> selectedDates = output.stream()
        .map(DateSeriesPoint::date)
        .collect(toList());
    assertThat(selectedDates, contains(
        LocalDate.of(1979, 12, 31),
        LocalDate.of(1981, 8, 10),
        LocalDate.of(1981, 10, 9),
        LocalDate.of(1984, 3, 6),
        LocalDate.of(1985, 2, 25),
        LocalDate.of(1986, 3, 4),
        LocalDate.of(1987, 12, 31),
        LocalDate.of(1989, 6, 14),
        LocalDate.of(1991, 2, 11),
        LocalDate.of(1991, 7, 2),
        LocalDate.of(1992, 9, 2),
        LocalDate.of(1998, 12, 31)
    ));
  }

  @Test
  public void facade_pip_delegates_to_select() {
    List<DoublePoint> input = asList(
        DoublePoint.of(0, 0),
        DoublePoint.of(1, 2),
        DoublePoint.of(2, 1),
        DoublePoint.of(3, 3),
        DoublePoint.of(4, 0)
    );

    List<DoublePoint> viaFacade = Downsampling.pip(input, 3);
    List<DoublePoint> direct = PerceptuallyImportantPoints.select(input, 3);

    assertThat(viaFacade, equalTo(direct));
  }
}
