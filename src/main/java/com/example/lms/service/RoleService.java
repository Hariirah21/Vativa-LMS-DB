package com.example.lms.service;

import com.example.lms.dto.RoleDto;
import com.example.lms.entity.RoleEntity;
import com.example.lms.repository.RoleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (roleRepository.existsByNameIgnoreCaseAndCreatedByAdminId(request.getName(), adminId)) {
            throw new IllegalArgumentException("Role already exists.");
        }
        RoleEntity entity = new RoleEntity();
        entity.setCreatedByAdminId(adminId);
        entity.setStatus("Active"); // SRS: "Role Status defaults to Active when creating a new role"
        mapRequestToEntity(request, entity);
        return mapEntityToResponse(roleRepository.save(entity));
    }

    @Transactional
    public RoleDto.Response updateRole(Long adminId, Long id, RoleDto.Request request) {
        RoleEntity entity = roleRepository.findByIdAndCreatedByAdminId(id, adminId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        // If the name changed, re-check uniqueness (excluding this role itself)
        if (!entity.getName().equalsIgnoreCase(request.getName())
                && roleRepository.existsByNameIgnoreCaseAndCreatedByAdminId(request.getName(), adminId)) {
            throw new IllegalArgumentException("Role already exists.");
        }

        mapRequestToEntity(request, entity);
        return mapEntityToResponse(roleRepository.save(entity));
    }

    public RoleDto.Response getRoleById(Long adminId, Long id) {
        RoleEntity entity = roleRepository.findByIdAndCreatedByAdminId(id, adminId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
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
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        entity.setStatus(status);
        return mapEntityToResponse(roleRepository.save(entity));
    }

    @Transactional
    public void deleteRole(Long adminId, Long id) {
        if (!roleRepository.existsByIdAndCreatedByAdminId(id, adminId)) {
            throw new IllegalArgumentException("Role not found");
        }
        roleRepository.deleteById(id);
    }

    private void mapRequestToEntity(RoleDto.Request request, RoleEntity entity) {
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        try {
            entity.setPermissionsJson(objectMapper.writeValueAsString(request.getPermissions()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize permissions", e);
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
            throw new RuntimeException("Failed to deserialize permissions", e);
        }
        return response;
    }
}
