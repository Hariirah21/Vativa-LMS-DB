package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.PackageDto;
import com.example.lms.service.ApplicationModuleConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/application-modules")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class ApplicationModuleConfigurationController {

    private final ApplicationModuleConfigurationService moduleConfiguration;

    public ApplicationModuleConfigurationController(
            ApplicationModuleConfigurationService moduleConfiguration) {
        this.moduleConfiguration = moduleConfiguration;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PackageDto.Category>>> getModules() {
        return ResponseEntity.ok(ApiResponse.success(
                "Application modules fetched successfully.",
                moduleConfiguration.getModules()));
    }
}
