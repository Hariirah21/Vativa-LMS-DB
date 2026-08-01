package com.example.lms.controller;

import com.example.lms.dto.StaffDto;
import com.example.lms.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public ResponseEntity<List<StaffDto.Response>> list(Authentication authentication) {
        return ResponseEntity.ok(staffService.list(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<StaffDto.Response> create(
            Authentication authentication,
            @Valid @RequestBody StaffDto.Request request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffService.create(authentication.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StaffDto.Response> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody StaffDto.Request request
    ) {
        return ResponseEntity.ok(staffService.update(authentication.getName(), id, request));
    }

    @PostMapping("/invitations")
    public ResponseEntity<StaffDto.Response> invite(
            Authentication authentication,
            @Valid @RequestBody StaffDto.InvitationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffService.invite(authentication.getName(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long id
    ) {
        staffService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
