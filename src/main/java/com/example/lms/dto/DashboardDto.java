package com.example.lms.dto;

import com.example.lms.entity.DashboardTimePeriod;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDto {
    private DashboardDto() {
    }

    @Getter
    @Builder
    public static class CountChartDataResponse {
        private String label;
        private long value;
    }

    @Getter
    @Builder
    public static class RevenueChartDataResponse {
        private String label;
        private BigDecimal value;
    }

    @Getter
    @Builder
    public static class UserDistributionResponse {
        private long admins;
        private long instructors;
        private long learners;
    }

    @Getter
    @Builder
    public static class DashboardResponse {
        private DashboardTimePeriod timePeriod;
        private List<CountChartDataResponse> logins;
        private List<CountChartDataResponse> newSignups;
        private List<RevenueChartDataResponse> revenue;
        private BigDecimal totalRevenue;
        private long activeLearners;
        private BigDecimal averageLearningTime;
        private BigDecimal courseCompletionRate;
        private UserDistributionResponse userDistribution;
        private long onlineUsers;
    }
}
