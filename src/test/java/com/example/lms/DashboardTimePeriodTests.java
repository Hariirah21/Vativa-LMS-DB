package com.example.lms;

import com.example.lms.entity.DashboardTimePeriod;
import com.example.lms.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardTimePeriodTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 15, 30);

    @ParameterizedTest
    @EnumSource(DashboardTimePeriod.class)
    void everyPeriodResolvesToAValidRange(DashboardTimePeriod period) {
        DashboardTimePeriod.DateRange range = period.resolve(NOW);

        assertThat(range.startInclusive()).isBefore(range.endExclusive());
    }

    @Test
    void fixedCalendarPeriodsUseApplicationLocalCalendarBoundaries() {
        assertThat(DashboardTimePeriod.TODAY.resolve(NOW).startInclusive())
                .isEqualTo(LocalDateTime.of(2026, 9, 7, 0, 0));
        assertThat(DashboardTimePeriod.YESTERDAY.resolve(NOW))
                .isEqualTo(new DashboardTimePeriod.DateRange(
                        LocalDateTime.of(2026, 9, 6, 0, 0),
                        LocalDateTime.of(2026, 9, 7, 0, 0)));
        assertThat(DashboardTimePeriod.WEEK.resolve(NOW).startInclusive())
                .isEqualTo(LocalDateTime.of(2026, 9, 7, 0, 0));
        assertThat(DashboardTimePeriod.MONTH.resolve(NOW).startInclusive())
                .isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(DashboardTimePeriod.LAST_WEEK.resolve(NOW))
                .isEqualTo(new DashboardTimePeriod.DateRange(
                        LocalDateTime.of(2026, 8, 31, 0, 0),
                        LocalDateTime.of(2026, 9, 7, 0, 0)));
    }

    @Test
    void rollingPeriodsUseTheRequestedLookback() {
        assertThat(DashboardTimePeriod.LAST_30_DAYS.resolve(NOW).startInclusive())
                .isEqualTo(NOW.minusDays(30));
        assertThat(DashboardTimePeriod.LAST_60_DAYS.resolve(NOW).startInclusive())
                .isEqualTo(NOW.minusDays(60));
        assertThat(DashboardTimePeriod.LAST_90_DAYS.resolve(NOW).startInclusive())
                .isEqualTo(NOW.minusDays(90));
        assertThat(DashboardTimePeriod.LAST_180_DAYS.resolve(NOW).startInclusive())
                .isEqualTo(NOW.minusDays(180));
        assertThat(DashboardTimePeriod.LAST_YEAR.resolve(NOW).startInclusive())
                .isEqualTo(NOW.minusYears(1));
    }

    @Test
    void parsingDefaultsAndValidatesValues() {
        assertThat(DashboardTimePeriod.from(null)).isEqualTo(DashboardTimePeriod.LAST_WEEK);
        assertThat(DashboardTimePeriod.from(" ")).isEqualTo(DashboardTimePeriod.LAST_WEEK);
        assertThat(DashboardTimePeriod.from("last 30 days"))
                .isEqualTo(DashboardTimePeriod.LAST_30_DAYS);
        assertThatThrownBy(() -> DashboardTimePeriod.from("quarter"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid dashboard time period");
    }
}
