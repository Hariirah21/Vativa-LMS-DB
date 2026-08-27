package com.example.lms.controller;

import com.example.lms.dto.PackageAssignmentDto;
import com.example.lms.dto.PackageDto;
import com.example.lms.service.PackageService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PackageDto.Response> create(
            @Valid @RequestBody PackageDto.Request request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packageService.createPackage(request, idempotencyKey));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PackageDto.Response> update(@PathVariable Long id, @Valid @RequestBody PackageDto.Request request) {
        return ResponseEntity.ok(packageService.updatePackage(id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<PackageDto.Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(packageService.getPackageById(id));
    }

    /**
     * Doc06 Field #1 (Search by Package Name) and Field #2 (Filter:
     * All Packages / Active / Inactive). `status` omitted or "All" means no
     * status filter. Paginated - see PackageRepository#search.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Page<PackageDto.Response>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(packageService.getAllPackages(search, status, pageable));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        packageService.deletePackage(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Doc07 Field #1 / §6 Dependencies: the predefined feature catalog the
     * Admin Permission matrix is rendered from.
     */
    @GetMapping("/features")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<PackageDto.FeatureCatalogEntry>> getFeatureCatalog() {
        return ResponseEntity.ok(packageService.getFeatureCatalog());
    }

    /**
     * Doc06 Fields #19-22 - the "Update Package" popup: assign an existing
     * package to a registered user by Email ID. Distinct from PUT /{id},
     * which edits a package's own master-data fields.
     */
    @PostMapping("/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> assign(@Valid @RequestBody PackageAssignmentDto request) {
        packageService.assignPackageToUser(request);
        return ResponseEntity.noContent().build();
    }
}