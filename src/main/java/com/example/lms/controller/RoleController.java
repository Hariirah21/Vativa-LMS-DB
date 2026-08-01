package com.example.lms.controller;

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
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {

    private final RoleService roleService;
    private final UserRepository userRepository;

    public RoleController(RoleService roleService, UserRepository userRepository) {
        this.roleService = roleService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<RoleDto.Response> create(
            Authentication authentication,
            @Valid @RequestBody RoleDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleService.createRole(adminId(authentication), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDto.Response> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody RoleDto.Request request) {
        return ResponseEntity.ok(roleService.updateRole(adminId(authentication), id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDto.Response> getById(
            Authentication authentication,
            @PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(adminId(authentication), id));
    }

    // Full list — Role Management / Admin list screen (all statuses)
    @GetMapping
    public ResponseEntity<List<RoleDto.Response>> getAll(
            Authentication authentication) {
        return ResponseEntity.ok(roleService.getAllRoles(adminId(authentication)));
    }

    // SRS: "Available Roles dropdown displays all active roles"
    @GetMapping("/active")
    public ResponseEntity<List<RoleDto.Response>> getActive(
            Authentication authentication) {
        return ResponseEntity.ok(roleService.getActiveRoles(adminId(authentication)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RoleDto.Response> updateStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody RoleDto.StatusUpdateRequest request) {
        return ResponseEntity.ok(roleService.updateStatus(
                adminId(authentication), id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long id) {
        roleService.deleteRole(adminId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Long adminId(Authentication authentication) {
        User admin = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(
                        "Authenticated administrator was not found.",
                        HttpStatus.UNAUTHORIZED));
        if (!Boolean.TRUE.equals(admin.getActive())
                || admin.getRole() == null
                || !"ADMIN".equalsIgnoreCase(admin.getRole().trim())) {
            throw new ApiException(
                    "Only administrators can manage roles.",
                    HttpStatus.FORBIDDEN);
        }
        return admin.getId();
    }
}
