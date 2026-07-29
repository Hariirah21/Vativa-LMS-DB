package com.example.lms.service;

import com.example.lms.dto.PackageDto;
import com.example.lms.entity.PackageEntity;
import com.example.lms.repository.PackageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PackageService {

    private final PackageRepository packageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PackageService(PackageRepository packageRepository) {
        this.packageRepository = packageRepository;
    }

    @Transactional
    public PackageDto.Response createPackage(PackageDto.Request request) {
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
        // Business rule pending: check if the package is assigned to an Admin before deleting
        // (SRS Edge Case — "package is in use")
        packageRepository.deleteById(id);
    }

    private void mapRequestToEntity(PackageDto.Request request, PackageEntity entity) {
        entity.setName(request.getName());
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
