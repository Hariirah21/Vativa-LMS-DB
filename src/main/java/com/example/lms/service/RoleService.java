package com.example.lms.service;

import com.example.lms.dto.RoleDto;
import com.example.lms.entity.RoleEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.RoleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional
    public RoleDto.Response createRole(Long adminId, RoleDto.Request request) {
        String name = request.getName().trim();
        validatePermissions(request);
        if (roleRepository.existsByNameIgnoreCaseAndCreatedByAdminId(name, adminId)) {
            throw new ApiException("Role already exists.", HttpStatus.CONFLICT);
        }
        RoleEntity entity = new RoleEntity();
        entity.setCreatedByAdminId(adminId);
        entity.setStatus("Active");
        mapRequestToEntity(request, entity);
        return mapEntityToResponse(roleRepository.save(entity));
    }

    @Transactional
    public RoleDto.Response updateRole(Long adminId, Long id, RoleDto.Request request) {
        RoleEntity entity = roleRepository.findByIdAndCreatedByAdminId(id, adminId)
                .orElseThrow(() -> new ApiException("Role not found.", HttpStatus.NOT_FOUND));
        validatePermissions(request);

        if (!entity.getName().equalsIgnoreCase(request.getName())
                && roleRepository.existsByNameIgnoreCaseAndCreatedByAdminId(request.getName(), adminId)) {
            throw new ApiException("Role already exists.", HttpStatus.CONFLICT);
        }

        mapRequestToEntity(request, entity);
        return mapEntityToResponse(roleRepository.save(entity));
    }

    public RoleDto.Response getRoleById(Long adminId, Long id) {
        RoleEntity entity = roleRepository.findByIdAndCreatedByAdminId(id, adminId)
                .orElseThrow(() -> new ApiException("Role not found.", HttpStatus.NOT_FOUND));
        return mapEntityToResponse(entity);
    }

    // Full list — used in the Admin's Role Management / list screen (all statuses)
    public List<RoleDto.Response> getAllRoles(Long adminId) {
        return roleRepository.findAllByCreatedByAdminId(adminId).stream()
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    // SRS: "Available Roles dropdown displays all active roles" — used for the dropdown specifically
    public List<RoleDto.Response> getActiveRoles(Long adminId) {
        return roleRepository.findAllByCreatedByAdminIdAndStatus(adminId, "Active").stream()
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoleDto.Response updateStatus(Long adminId, Long id, String status) {
        RoleEntity entity = roleRepository.findByIdAndCreatedByAdminId(id, adminId)
                .orElseThrow(() -> new ApiException("Role not found.", HttpStatus.NOT_FOUND));
        String normalizedStatus = status.trim();
        if (!"Active".equalsIgnoreCase(normalizedStatus)
                && !"Inactive".equalsIgnoreCase(normalizedStatus)) {
            throw new ApiException(
                    "Status must be Active or Inactive.",
                    HttpStatus.BAD_REQUEST);
        }
        entity.setStatus("Active".equalsIgnoreCase(normalizedStatus) ? "Active" : "Inactive");
        return mapEntityToResponse(roleRepository.save(entity));
    }

    @Transactional
    public void deleteRole(Long adminId, Long id) {
        if (!roleRepository.existsByIdAndCreatedByAdminId(id, adminId)) {
            throw new ApiException("Role not found.", HttpStatus.NOT_FOUND);
        }
        roleRepository.deleteById(id);
    }

    private void mapRequestToEntity(RoleDto.Request request, RoleEntity entity) {
        entity.setName(request.getName().trim());
        entity.setDescription(request.getDescription());
        try {
            entity.setPermissionsJson(objectMapper.writeValueAsString(request.getPermissions()));
        } catch (JsonProcessingException e) {
            throw new ApiException(
                    "Permissions could not be saved.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private RoleDto.Response mapEntityToResponse(RoleEntity entity) {
        RoleDto.Response response = new RoleDto.Response();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        try {
            if (entity.getPermissionsJson() != null) {
                response.setPermissions(objectMapper.readValue(
                        entity.getPermissionsJson(),
                        new TypeReference<List<com.example.lms.dto.PackageDto.Category>>() {}));
            }
        } catch (JsonProcessingException e) {
            throw new ApiException(
                    "Stored permissions are invalid.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    private void validatePermissions(RoleDto.Request request) {
        boolean selected = request.getPermissions() != null
                && request.getPermissions().stream()
                .filter(category -> category.getFeatures() != null)
                .flatMap(category -> category.getFeatures().stream())
                .map(com.example.lms.dto.PackageDto.Feature::getPermissions)
                .filter(permission -> permission != null)
                .anyMatch(permission -> permission.isCreate()
                        || permission.isRead()
                        || permission.isUpdate()
                        || permission.isDelete());
        if (!selected) {
            throw new ApiException(
                    "Select at least one permission before saving the role.",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
