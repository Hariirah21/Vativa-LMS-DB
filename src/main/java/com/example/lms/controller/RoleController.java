package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.RoleDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.UserRepository;
import com.example.lms.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class RoleController {

    private final RoleService roleService;
    private final UserRepository userRepository;

    public RoleController(RoleService roleService, UserRepository userRepository) {
        this.roleService = roleService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleDto.Response>> create(
            Authentication authentication,
            @Valid @RequestBody RoleDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Role created successfully.",
                        roleService.createRole(adminId(authentication), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleDto.Response>> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody RoleDto.Request request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Role updated successfully.",
                roleService.updateRole(adminId(authentication), id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleDto.Response>> getById(
            Authentication authentication,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Role fetched successfully.",
                roleService.getRoleById(adminId(authentication), id)));
    }

    // Full list — Role Management / Admin list screen (all statuses)
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleDto.Response>>> getAll(
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Roles fetched successfully.",
                roleService.getAllRoles(adminId(authentication))));
    }

    // SRS: "Available Roles dropdown displays all active roles"
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<RoleDto.Response>>> getActive(
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Active roles fetched successfully.",
                roleService.getActiveRoles(adminId(authentication))));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<RoleDto.Response>> updateStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody RoleDto.StatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Role status updated successfully.",
                roleService.updateStatus(
                        adminId(authentication), id, request.getStatus())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication authentication,
            @PathVariable Long id) {
        roleService.deleteRole(adminId(authentication), id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted successfully."));
    }

    private Long adminId(Authentication authentication) {
        User admin = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(
                        "Authenticated administrator was not found.",
                        HttpStatus.UNAUTHORIZED));
        if (!Boolean.TRUE.equals(admin.getActive())
                || admin.getRole() == null
                || !isAdministratorRole(admin.getRole())) {
            throw new ApiException(
                    "Only administrators can manage roles.",
                    HttpStatus.FORBIDDEN);
        }
        return admin.getId();
    }

    private boolean isAdministratorRole(String role) {
        if (role == null) {
            return false;
        }
        String normalized = role.trim().toUpperCase();
        return "ADMIN".equals(normalized)
                || "ROLE_ADMIN".equals(normalized)
                || "SUPER_ADMIN".equals(normalized)
                || "ROLE_SUPER_ADMIN".equals(normalized);
    }
}
