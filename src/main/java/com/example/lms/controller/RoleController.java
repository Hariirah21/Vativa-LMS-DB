package com.example.lms.controller;

import com.example.lms.dto.RoleDto;
import com.example.lms.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    public ResponseEntity<RoleDto.Response> create(
            @RequestHeader("X-Admin-Id") Long adminId,
            @Valid @RequestBody RoleDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.createRole(adminId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDto.Response> update(
            @RequestHeader("X-Admin-Id") Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody RoleDto.Request request) {
        return ResponseEntity.ok(roleService.updateRole(adminId, id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDto.Response> getById(
            @RequestHeader("X-Admin-Id") Long adminId,
            @PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(adminId, id));
    }

    // Full list — Role Management / Admin list screen (all statuses)
    @GetMapping
    public ResponseEntity<List<RoleDto.Response>> getAll(
            @RequestHeader("X-Admin-Id") Long adminId) {
        return ResponseEntity.ok(roleService.getAllRoles(adminId));
    }

    // SRS: "Available Roles dropdown displays all active roles"
    @GetMapping("/active")
    public ResponseEntity<List<RoleDto.Response>> getActive(
            @RequestHeader("X-Admin-Id") Long adminId) {
        return ResponseEntity.ok(roleService.getActiveRoles(adminId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RoleDto.Response> updateStatus(
            @RequestHeader("X-Admin-Id") Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody RoleDto.StatusUpdateRequest request) {
        return ResponseEntity.ok(roleService.updateStatus(adminId, id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Admin-Id") Long adminId,
            @PathVariable Long id) {
        roleService.deleteRole(adminId, id);
        return ResponseEntity.noContent().build();
    }
}