package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.RoleDto;
import com.example.lms.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    public ResponseEntity<RoleDto.Response> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody RoleDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody RoleDto.Request request) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDto.Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    // Full list — Role Management / Admin list screen (all statuses, all roles system-wide)
    @GetMapping
    public ResponseEntity<List<RoleDto.Response>> getAll() {
        return ResponseEntity.ok(roleService.getAllRoles());
    }

    // SRS: "Available Roles dropdown displays all active roles"
    @GetMapping("/active")
    public ResponseEntity<List<RoleDto.Response>> getActive() {
        return ResponseEntity.ok(roleService.getActiveRoles());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RoleDto.Response> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody RoleDto.StatusUpdateRequest request) {
        return ResponseEntity.ok(roleService.updateStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}