package com.example.lms.service;

import com.example.lms.dto.RoleDto;
import com.example.lms.entity.RoleEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.RoleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final Set<String> VALID_STATUSES = Set.of("Active", "Inactive");

    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public RoleDto.Response createRole(Long adminId, RoleDto.Request request) {
        // SRS: "Role names must be unique in the system" — checked globally,
        // not per-admin, since roles (Admin/Instructor/Learner/etc.) are
        // shared system-wide, not private to the admin who created them.
        if (roleRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ApiException("Role already exists.", HttpStatus.CONFLICT);
        }

        RoleEntity entity = new RoleEntity();
        entity.setCreatedByAdminId(adminId);
        entity.setStatus("Active"); // SRS: "Role Status defaults to Active when creating a new role"
        mapRequestToEntity(request, entity);

        try {
            return mapEntityToResponse(roleRepository.save(entity));
        } catch (DataIntegrityViolationException e) {
            // Belt-and-braces against the race where two admins submit the
            // same new Role Name at the same instant (SRS edge case: both
            // requests pass the existsByNameIgnoreCase() check above before
            // either commits). The DB's unique constraint on `name` is what
            // actually stops the second insert.
            throw new ApiException("Role already exists.", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public RoleDto.Response updateRole(Long id, RoleDto.Request request) {
        RoleEntity entity = roleRepository.findById(id)
                .orElseThrow(() -> new ApiException("Role not found.", HttpStatus.NOT_FOUND));

        if (!entity.getName().equalsIgnoreCase(request.getName())
                && roleRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new ApiException("Role already exists.", HttpStatus.CONFLICT);
        }

        mapRequestToEntity(request, entity);

        try {
            return mapEntityToResponse(roleRepository.save(entity));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException("Role already exists.", HttpStatus.CONFLICT);
        }
    }

    public RoleDto.Response getRoleById(Long id) {
        RoleEntity entity = roleRepository.findById(id)
                .orElseThrow(() -> new ApiException("Role not found.", HttpStatus.NOT_FOUND));
        return mapEntityToResponse(entity);
    }

    // Full list — used in the Admin's Role Management / list screen (all statuses)
    public List<RoleDto.Response> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    // SRS: "Available Roles dropdown displays all active roles" — used for the dropdown specifically
    public List<RoleDto.Response> getActiveRoles() {
        return roleRepository.findAllByStatus("Active").stream()
                .map(this::mapEntityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoleDto.Response updateStatus(Long id, String status) {
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new ApiException("Status must be either 'Active' or 'Inactive'.", HttpStatus.BAD_REQUEST);
        }
        RoleEntity entity = roleRepository.findById(id)
                .orElseThrow(() -> new ApiException("Role not found.", HttpStatus.NOT_FOUND));
        entity.setStatus(status);
        return mapEntityToResponse(roleRepository.save(entity));
    }

    @Transactional
    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new ApiException("Role not found.", HttpStatus.NOT_FOUND);
        }
        roleRepository.deleteById(id);
    }

    private void mapRequestToEntity(RoleDto.Request request, RoleEntity entity) {
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        try {
            entity.setPermissionsJson(objectMapper.writeValueAsString(request.getPermissions()));
        } catch (Exception e) {
            throw new ApiException("Failed to save role permissions.", HttpStatus.INTERNAL_SERVER_ERROR);
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
        } catch (Exception e) {
            throw new ApiException("Failed to load role permissions.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }
}