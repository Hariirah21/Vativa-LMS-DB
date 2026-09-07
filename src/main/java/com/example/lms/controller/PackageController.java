package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.PackageDto;
import com.example.lms.service.PackageService;   
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/packages")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class PackageController {

    private final PackageService packageService;

    public PackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PackageDto.Response>> create(
            @Valid @RequestBody PackageDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Package created successfully.", packageService.createPackage(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageDto.Response>> update(
            @PathVariable Long id, @Valid @RequestBody PackageDto.Request request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Package updated successfully.", packageService.updatePackage(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageDto.Response>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Package fetched successfully.", packageService.getPackageById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PackageDto.Response>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(
                "Packages fetched successfully.", packageService.getAllPackages()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        packageService.deletePackage(id);
        return ResponseEntity.ok(ApiResponse.success("Package deleted successfully."));
    }
}
