package com.example.lms.service;

import com.example.lms.dto.PackageAssignmentDto;
import com.example.lms.dto.PackageDto;
import com.example.lms.entity.PackageEntity;
import com.example.lms.entity.SystemFeatureEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.exception.RateLimitExceededException;
import com.example.lms.repository.PackageRepository;
import com.example.lms.repository.SystemFeatureRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageService {

    private final PackageRepository packageRepository;
    private final SystemFeatureRepository systemFeatureRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Doc06/Doc07 Edge Case #1: "User clicks Save Package button multiple
     * times -> system should process only the first request and prevent
     * duplicate submissions."
     *
     * The unique constraint on `name` already prevents two *successful*
     * creates with the same name, but that still lets a rapid double-click
     * hit the DB twice and rely on a constraint violation to reject the
     * second one (noisy, and doesn't cover updates). This in-memory guard
     * short-circuits an identical request from the same idempotency key
     * within a short window.
     *
     * NOTE: a ConcurrentHashMap is fine for a single instance; behind a
     * load balancer with multiple app instances, back this with a shared
     * store (Redis SETNX with TTL) instead so the guard works cluster-wide.
     */
    private final ConcurrentHashMap<String, Instant> recentSubmissions = new ConcurrentHashMap<>();
    private static final Duration DUPLICATE_SUBMIT_WINDOW = Duration.ofSeconds(5);

    @Transactional
    public PackageDto.Response createPackage(PackageDto.Request request, String idempotencyKey) {
        guardAgainstDuplicateSubmit(idempotencyKey);

        // SRS Edge Case #6 / Field #11: duplicate Package Name must be blocked
        // with a specific message, not a generic server error.
        if (packageRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ApiException("A package with the same name already exists", HttpStatus.CONFLICT);
        }

        validatePermissionFeaturesExist(request.getPermissions());

        PackageEntity entity = new PackageEntity();
        mapRequestToEntity(request, entity);
        PackageEntity saved = packageRepository.save(entity);
        return mapEntityToResponse(saved);
    }

    @Transactional
    public PackageDto.Response updatePackage(Long id, PackageDto.Request request) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));

        // Doc07 Edge Case #6: "Multiple Super Admins attempt to update
        // permissions for the same package simultaneously -> prevent
        // conflicting updates and display an appropriate message if the
        // package has already been modified."
        // Explicit app-level check first, so the error message matches the
        // SRS wording exactly rather than a generic JPA exception message.
        if (request.getVersion() != null && !request.getVersion().equals(entity.getVersion())) {
            throw new ApiException(
                    "This package has already been modified by another user. Please refresh and try again.",
                    HttpStatus.CONFLICT);
        }

        // SRS Edge Case #6: "prevent duplicate package names when creating OR
        // updating a package" - single indexed existence check, race-safer
        // and cheaper than fetch-then-filter-in-memory.
        if (packageRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new ApiException("A package with the same name already exists", HttpStatus.CONFLICT);
        }

        validatePermissionFeaturesExist(request.getPermissions());

        mapRequestToEntity(request, entity);
        try {
            PackageEntity saved = packageRepository.save(entity);
            return mapEntityToResponse(saved);
        } catch (ObjectOptimisticLockingFailureException ex) {
            // DB-level safety net in case two writes race past the app-level
            // check above between our read and our write.
            throw new ApiException(
                    "This package has already been modified by another user. Please refresh and try again.",
                    HttpStatus.CONFLICT);
        }
    }

    public PackageDto.Response getPackageById(Long id) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));
        return mapEntityToResponse(entity);
    }

    /**
     * Doc06 Field #1 (Search by Package Name) and Field #2 (Filter:
     * All Packages / Active / Inactive), paginated for performance.
     *
     * @param search free-text package-name filter, or null/blank for none
     * @param status "Active", "Inactive", or null/blank for "All Packages"
     */
    public Page<PackageDto.Response> getAllPackages(String search, String status, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        String normalizedStatus = (status == null || status.isBlank() || "All".equalsIgnoreCase(status))
                ? null
                : status.trim();

        return packageRepository.search(normalizedStatus, normalizedSearch, pageable)
                .map(this::mapEntityToResponse);
    }

    /**
     * Doc07 Field #1 / §6 Dependencies: the Admin Permission matrix is built
     * from "predefined system features configured in the platform". This
     * feeds that matrix to the UI grouped by category.
     */
    public List<PackageDto.FeatureCatalogEntry> getFeatureCatalog() {
        List<SystemFeatureEntity> features = systemFeatureRepository.findByActiveTrueOrderByCategoryIdAsc();

        Map<String, List<SystemFeatureEntity>> byCategory = features.stream()
                .collect(Collectors.groupingBy(
                        SystemFeatureEntity::getCategoryId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<PackageDto.FeatureCatalogEntry> catalog = new ArrayList<>();
        for (Map.Entry<String, List<SystemFeatureEntity>> entry : byCategory.entrySet()) {
            List<SystemFeatureEntity> categoryFeatures = entry.getValue();
            String categoryName = categoryFeatures.get(0).getCategoryName();

            List<PackageDto.Feature> featureDtos = new ArrayList<>();
            for (SystemFeatureEntity f : categoryFeatures) {
                PackageDto.Feature dto = new PackageDto.Feature();
                dto.setId(f.getId());
                dto.setName(f.getName());
                dto.setPermissions(new PackageDto.Permission(false, false, false, false));
                featureDtos.add(dto);
            }
            catalog.add(new PackageDto.FeatureCatalogEntry(entry.getKey(), categoryName, featureDtos));
        }
        return catalog;
    }

    @Transactional
    public void deletePackage(Long id) {
        if (!packageRepository.existsById(id)) {
            throw new ApiException("Package not found", HttpStatus.NOT_FOUND);
        }
        // SRS Edge Case #7: "Super Admin attempts to delete a package that is
        // currently assigned to one or more users" -> must block deletion and
        // show a message, not delete silently.
        //
        // STILL NOT WIRED UP: this needs whatever repository/table tracks
        // which package a user is assigned to (e.g. a UserRepository with
        // existsByPackageId(id), or a UserPackageAssignment entity - not
        // present in the files shared so far). Share that repository/entity
        // and this check gets wired in for real; a fabricated method name
        // here would just fail to compile against your actual schema.
        //
        // if (userRepository.existsByPackageId(id)) {
        //     throw new ApiException("This package is currently in use and cannot be deleted", HttpStatus.CONFLICT);
        // }
        packageRepository.deleteById(id);
    }

    /**
     * Doc06 Fields #19-22 - the "Update Package" popup: assign an existing
     * package to a registered user by Email ID. This is distinct from
     * updatePackage() above, which edits a package's own master-data fields.
     *
     * STILL NEEDS YOUR USER ENTITY/REPOSITORY: there's no User module in the
     * files shared so far, so the "does this email belong to a registered
     * user" lookup and the actual persistence of "this user now has this
     * package" can't be wired up without fabricating a schema that might not
     * match your real one. Once you share your User entity/repository, this
     * becomes a two-line lookup + save.
     */
    @Transactional
    public void assignPackageToUser(PackageAssignmentDto request) {
        PackageEntity packageEntity = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));

        // TODO: replace with your real lookup, e.g.:
        // User user = userRepository.findByEmailIgnoreCase(request.getEmailId())
        //         .orElseThrow(() -> new ApiException("Please select an Email ID", HttpStatus.BAD_REQUEST));
        // user.setPackageId(packageEntity.getId());
        // userRepository.save(user);
        throw new UnsupportedOperationException(
                "assignPackageToUser requires the User entity/repository - not yet available. " +
                        "Package " + packageEntity.getId() + " and emailId " + request.getEmailId() +
                        " were validated up to this point.");
    }

    /**
     * Doc07 Field #1 / §6 Dependencies: reject any submitted permission
     * category/feature id that isn't part of the predefined, active feature
     * catalog. Without this, a caller could persist arbitrary category/
     * feature identifiers into permissions_json.
     */
    private void validatePermissionFeaturesExist(List<PackageDto.Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return; // already rejected by @NotEmpty / @AssertTrue on the DTO
        }

        Set<String> knownFeatureIds = systemFeatureRepository.findByActiveTrueOrderByCategoryIdAsc().stream()
                .map(SystemFeatureEntity::getId)
                .collect(Collectors.toSet());

        Set<String> submittedFeatureIds = categories.stream()
                .filter(cat -> cat.getFeatures() != null)
                .flatMap(cat -> cat.getFeatures().stream())
                .map(PackageDto.Feature::getId)
                .collect(Collectors.toSet());

        if (!knownFeatureIds.containsAll(submittedFeatureIds)) {
            throw new ApiException(
                    "One or more selected features are invalid or no longer available",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void guardAgainstDuplicateSubmit(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return; // caller didn't opt in; name-uniqueness constraint is the fallback
        }
        Instant now = Instant.now();
        recentSubmissions.entrySet().removeIf(e -> Duration.between(e.getValue(), now).compareTo(DUPLICATE_SUBMIT_WINDOW) > 0);

        Instant previous = recentSubmissions.putIfAbsent(idempotencyKey, now);
        if (previous != null) {
            throw new RateLimitExceededException(
                    "This request is already being processed. Please wait a moment before retrying.");
        }
    }

    private void mapRequestToEntity(PackageDto.Request request, PackageEntity entity) {
        entity.setAvailablePackage(request.getAvailablePackage());
        entity.setName(request.getName().trim());
        entity.setDescription(request.getDescription());
        entity.setPrice(request.getPrice());
        entity.setBillingCycle(request.getBillingCycle());
        entity.setUserLimit(request.getUserLimit());
        entity.setStorageLimit(request.getStorageLimit());
        try {
            entity.setPermissionsJson(objectMapper.writeValueAsString(request.getPermissions()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize permissions", e);
        }
    }

    private PackageDto.Response mapEntityToResponse(PackageEntity entity) {
        PackageDto.Response response = new PackageDto.Response();
        response.setId(entity.getId());
        response.setAvailablePackage(entity.getAvailablePackage());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setPrice(entity.getPrice());
        response.setBillingCycle(entity.getBillingCycle());
        response.setUserLimit(entity.getUserLimit());
        response.setStorageLimit(entity.getStorageLimit());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setVersion(entity.getVersion());
        try {
            if (entity.getPermissionsJson() != null) {
                List<PackageDto.Category> categories = objectMapper.readValue(
                        entity.getPermissionsJson(), new TypeReference<List<PackageDto.Category>>() {});
                response.setPermissions(categories);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize permissions", e);
        }
        return response;
    }
}