package com.example.lms.service;

import com.example.lms.dto.PackageDto;
import com.example.lms.entity.PackageEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.PackageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PackageService {

    private final PackageRepository packageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PackageDto.Response createPackage(PackageDto.Request request) {
        // SRS Edge Case #6 / Field #11: duplicate Package Name must be blocked
        // with a specific message, not a generic server error.
        if (packageRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ApiException("A package with the same name already exists", HttpStatus.CONFLICT);
        }
        PackageEntity entity = new PackageEntity();
        mapRequestToEntity(request, entity);
        PackageEntity saved = packageRepository.save(entity);
        return mapEntityToResponse(saved);
    }

    @Transactional
    public PackageDto.Response updatePackage(Long id, PackageDto.Request request) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));

        // SRS Edge Case #6: "prevent duplicate package names when creating OR
        // updating a package" - this check was missing on update, so renaming
        // a package to an already-used name previously slipped through.
        packageRepository.findByNameIgnoreCase(request.getName())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ApiException("A package with the same name already exists", HttpStatus.CONFLICT);
                });

        mapRequestToEntity(request, entity);
        PackageEntity saved = packageRepository.save(entity);
        return mapEntityToResponse(saved);
    }

    public PackageDto.Response getPackageById(Long id) {
        PackageEntity entity = packageRepository.findById(id)
                .orElseThrow(() -> new ApiException("Package not found", HttpStatus.NOT_FOUND));
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
            throw new ApiException("Package not found", HttpStatus.NOT_FOUND);
        }
        // SRS Edge Case #7: "Super Admin attempts to delete a package that is
        // currently assigned to one or more users" -> must block deletion and
        // show a message, not delete silently.
        //
        // NOT WIRED UP YET: this needs whatever repository/table tracks which
        // package a user/admin is assigned to (e.g. a UserRepository with
        // existsByPackageId(id), or an AdminRepository/UserPackageAssignment
        // entity - not present in the files shared so far). Share that
        // repository/entity and I'll wire the real check in; a fake method
        // name here would just fail to compile against your actual schema.
        //
        // if (userRepository.existsByPackageId(id)) {
        //     throw new ApiException("This package is currently in use and cannot be deleted", HttpStatus.CONFLICT);
        // }
        packageRepository.deleteById(id);
    }

    private void mapRequestToEntity(PackageDto.Request request, PackageEntity entity) {
        entity.setAvailablePackage(request.getAvailablePackage());
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
        response.setAvailablePackage(entity.getAvailablePackage());
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