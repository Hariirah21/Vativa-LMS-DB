package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.DashboardDto;
import com.example.lms.entity.DashboardTimePeriod;
import com.example.lms.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardDto.DashboardResponse>> getDashboard(
            @RequestParam(required = false) String timePeriod) {
        DashboardTimePeriod selectedPeriod = DashboardTimePeriod.from(timePeriod);
        return ResponseEntity.ok(ApiResponse.success(
                "Dashboard loaded successfully.",
                dashboardService.getDashboard(selectedPeriod)));
    }
}
