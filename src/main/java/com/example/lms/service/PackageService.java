package com.example.lms.service;

import com.example.lms.dto.PackageAssignmentDto;
import com.example.lms.dto.PackageDto;
import com.example.lms.entity.PackageEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.exception.RateLimitExceededException;
import com.example.lms.repository.PackageRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PackageService {

    private final PackageRepository packageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, Instant> recentSubmissions = new ConcurrentHashMap<>();
    private static final Duration DUPLICATE_SUBMIT_WINDOW = Duration.ofSeconds(5);

    public PackageService(PackageRepository packageRepository, UserRepository userRepository) {
        this.packageRepository = packageRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PackageDto.Response createPackage(PackageDto.Request request) {
        return createPackage(request, null);
    }

    @Transactional
    public PackageDto.Response createPackage(PackageDto.Request request, String idempotencyKey) {
        guardAgainstDuplicateSubmit(idempotencyKey);
        if (packageRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("A package with the same name already exists");
        }
        PackageEntity entity = new PackageEntity();
        mapRequestToEntity(request, entity);
        PackageEntity saved = packageRepository.save(entity);
        return mapEntityToResponse(saved);
    }

    @Transactional
    public PackageDto.Response updatePackage(Long id, PackageDto.Request request) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Package not found"));
        mapRequestToEntity(request, entity);
        PackageEntity saved = packageRepository.save(entity);
        return mapEntityToResponse(saved);
    }

    public PackageDto.Response getPackageById(Long id) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Package not found"));
        return mapEntityToResponse(entity);
    }

    public List<PackageDto.Response> getAllPackages() {
        return packageRepository.findAll().stream()
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deletePackage(Long id) {
        if (!packageRepository.existsById(id)) {
            throw new IllegalArgumentException("Package not found");
        }
        if (userRepository.existsByPackageId(id)) {
            throw new ApiException(
                    "This package is currently in use and cannot be deleted",
                    HttpStatus.CONFLICT);
        }
        packageRepository.deleteById(id);
    }

    @Transactional
    public void assignPackageToUser(PackageAssignmentDto request) {
        PackageEntity packageEntity = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));
        User user = userRepository.findByEmailIgnoreCase(request.getEmailId())
                .orElseThrow(() -> new ApiException(
                        "Please select a registered Email ID", HttpStatus.BAD_REQUEST));
        user.setPackageId(packageEntity.getId());
        user.setPackageAssignedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }

    private void guardAgainstDuplicateSubmit(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        recentSubmissions.entrySet().removeIf(entry ->
                Duration.between(entry.getValue(), now).compareTo(DUPLICATE_SUBMIT_WINDOW) > 0);
        if (recentSubmissions.putIfAbsent(idempotencyKey.trim(), now) != null) {
            throw new RateLimitExceededException(
                    "This request is already being processed. Please wait a moment before retrying.");
        }
    }

    private void mapRequestToEntity(PackageDto.Request request, PackageEntity entity) {
        entity.setName(request.getName());
        entity.setAvailablePackage(request.getAvailablePackage());
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
        response.setName(entity.getName());
        response.setAvailablePackage(entity.getAvailablePackage());
        response.setDescription(entity.getDescription());
        response.setPrice(entity.getPrice());
        response.setBillingCycle(entity.getBillingCycle());
        response.setUserLimit(entity.getUserLimit());
        response.setStorageLimit(entity.getStorageLimit());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
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
