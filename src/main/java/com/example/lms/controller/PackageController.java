package com.example.lms.controller;

import com.example.lms.dto.PackageDto;
import com.example.lms.service.PackageService;   
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/packages")
public class PackageController {

    private final PackageService packageService;

    public PackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    @PostMapping
    public ResponseEntity<PackageDto.Response> create(@Valid @RequestBody PackageDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packageService.createPackage(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PackageDto.Response> update(@PathVariable Long id, @Valid @RequestBody PackageDto.Request request) {
        return ResponseEntity.ok(packageService.updatePackage(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PackageDto.Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(packageService.getPackageById(id));
    }

    @GetMapping
    public ResponseEntity<List<PackageDto.Response>> getAll() {
        return ResponseEntity.ok(packageService.getAllPackages());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        packageService.deletePackage(id);
        return ResponseEntity.noContent().build();
    }
}