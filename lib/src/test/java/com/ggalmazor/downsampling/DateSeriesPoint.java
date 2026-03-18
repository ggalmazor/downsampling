package com.ggalmazor.downsampling;

import java.time.LocalDate;
import java.time.ZoneOffset;

public record DateSeriesPoint(LocalDate date, double value) implements Point {

  @Override
  public double x() {
    return (double) date.atStartOfDay().atOffset(ZoneOffset.UTC).toEpochSecond();
  }

  @Override
  public double y() {
    return value;
  }
}
