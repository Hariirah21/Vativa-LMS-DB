package com.example.lms.entity;

import com.example.lms.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public enum DashboardTimePeriod {
    TODAY(Grouping.HOUR),
    YESTERDAY(Grouping.HOUR),
    WEEK(Grouping.DAY),
    MONTH(Grouping.DAY),
    LAST_WEEK(Grouping.DAY),
    LAST_30_DAYS(Grouping.DAY),
    LAST_60_DAYS(Grouping.MONTH),
    LAST_90_DAYS(Grouping.MONTH),
    LAST_180_DAYS(Grouping.MONTH),
    LAST_YEAR(Grouping.MONTH);

    private final Grouping grouping;

    DashboardTimePeriod(Grouping grouping) {
        this.grouping = grouping;
    }

    public Grouping getGrouping() {
        return grouping;
    }

    public DateRange resolve(LocalDateTime now) {
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        LocalDateTime currentWeek = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();

        return switch (this) {
            case TODAY -> new DateRange(today, inclusiveCurrentTime(now));
            case YESTERDAY -> new DateRange(today.minusDays(1), today);
            case WEEK -> new DateRange(currentWeek, inclusiveCurrentTime(now));
            case MONTH -> new DateRange(now.toLocalDate().withDayOfMonth(1).atStartOfDay(),
                    inclusiveCurrentTime(now));
            case LAST_WEEK -> new DateRange(currentWeek.minusWeeks(1), currentWeek);
            case LAST_30_DAYS -> new DateRange(now.minusDays(30), inclusiveCurrentTime(now));
            case LAST_60_DAYS -> new DateRange(now.minusDays(60), inclusiveCurrentTime(now));
            case LAST_90_DAYS -> new DateRange(now.minusDays(90), inclusiveCurrentTime(now));
            case LAST_180_DAYS -> new DateRange(now.minusDays(180), inclusiveCurrentTime(now));
            case LAST_YEAR -> new DateRange(now.minusYears(1), inclusiveCurrentTime(now));
        };
    }

    public static DashboardTimePeriod from(String value) {
        if (value == null || value.isBlank()) {
            return LAST_WEEK;
        }
        try {
            return valueOf(value.trim()
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_'));
        } catch (IllegalArgumentException exception) {
            throw new ApiException("Invalid dashboard time period: " + value + ".", HttpStatus.BAD_REQUEST);
        }
    }

    private static LocalDateTime inclusiveCurrentTime(LocalDateTime now) {
        return now.equals(LocalDateTime.MAX) ? now : now.plusNanos(1);
    }

    public enum Grouping {
        HOUR,
        DAY,
        MONTH
    }

    public record DateRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {
    }
}
