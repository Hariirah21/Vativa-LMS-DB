package com.example.lms.service;

import com.example.lms.dto.PackageDto;
import com.example.lms.dto.StaffDto;
import com.example.lms.entity.RoleEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.RoleRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class StaffService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StaffService(
            UserRepository userRepository,
            RoleRepository roleRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * The Role Management list is a view of registered users. It deliberately
     * does not maintain a second staff_members copy of the same people.
     */
    @Transactional(readOnly = true)
    public List<StaffDto.Response> list(String adminEmail) {
        Long adminId = requireAdmin(adminEmail).getId();
        return userRepository.findAllByActiveTrueOrderByFirstNameAscLastNameAsc()
                .stream()
                .map(user -> toResponse(user, adminId))
                .toList();
    }

    /**
     * Creates the role definition (or refreshes its permissions) and assigns
     * it to an existing registered user selected by email.
     */
    @Transactional
    public StaffDto.Response create(String adminEmail, StaffDto.Request request) {
        User admin = requireAdmin(adminEmail);
        User user = requireRegisteredUser(request.getEmail());
        assignRole(admin.getId(), user, request);
        return toResponse(userRepository.save(user), admin.getId());
    }

    @Transactional
    public StaffDto.Response update(
            String adminEmail,
            Long userId,
            StaffDto.Request request
    ) {
        User admin = requireAdmin(adminEmail);
        User user = requireUser(userId);
        String requestedEmail = normalizeEmail(request.getEmail());
        if (!user.getEmail().equalsIgnoreCase(requestedEmail)) {
            throw new ApiException(
                    "The registered email address cannot be changed from Role Management.",
                    HttpStatus.BAD_REQUEST);
        }
        assignRole(admin.getId(), user, request);
        return toResponse(userRepository.save(user), admin.getId());
    }

    /**
     * Invitations are not persisted as staff rows. A registered user is
     * returned immediately; an unknown email must complete normal sign-up.
     */
    @Transactional(readOnly = true)
    public StaffDto.Response invite(
            String adminEmail,
            StaffDto.InvitationRequest request
    ) {
        Long adminId = requireAdmin(adminEmail).getId();
        User user = requireRegisteredUser(request.getEmail());
        return toResponse(user, adminId);
    }

    /**
     * Deactivation keeps audit/history data intact while removing the user
     * from active user and instructor dropdowns.
     */
    @Transactional
    public void delete(String adminEmail, Long userId) {
        User admin = requireAdmin(adminEmail);
        if (admin.getId().equals(userId)) {
            throw new ApiException(
                    "You cannot remove your own administrator account.",
                    HttpStatus.BAD_REQUEST);
        }
        User user = requireUser(userId);
        user.setActive(false);
        userRepository.save(user);
    }

    private void assignRole(Long adminId, User user, StaffDto.Request request) {
        String roleName = request.getRole().trim();
        validatePermissions(request.getPermissions());
        RoleEntity role = roleRepository
                .findByNameIgnoreCaseAndCreatedByAdminId(roleName, adminId)
                .orElseGet(() -> {
                    RoleEntity created = new RoleEntity();
                    created.setCreatedByAdminId(adminId);
                    created.setName(roleName);
                    created.setDescription("Role configured in Role Management.");
                    created.setStatus("Active");
                    return created;
                });
        role.setPermissionsJson(writePermissions(request.getPermissions()));
        role.setStatus("Active");
        roleRepository.save(role);
        user.setRole(roleName);
    }

    private User requireAdmin(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(
                        "Authenticated user was not found.",
                        HttpStatus.UNAUTHORIZED));
        if (!Boolean.TRUE.equals(user.getActive())
                || user.getRole() == null
                || !"ADMIN".equalsIgnoreCase(user.getRole().trim())) {
            throw new ApiException(
                    "Only administrators can manage users and roles.",
                    HttpStatus.FORBIDDEN);
        }
        return user;
    }

    private User requireRegisteredUser(String rawEmail) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(rawEmail))
                .orElseThrow(() -> new ApiException(
                        "No registered user was found for this email. Ask the user to sign up first.",
                        HttpStatus.NOT_FOUND));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        "Registered user not found.",
                        HttpStatus.NOT_FOUND));
    }

    private StaffDto.Response toResponse(User user, Long adminId) {
        StaffDto.Response response = new StaffDto.Response();
        response.setId(user.getId());
        response.setUsername(displayName(user));
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setStatus(Boolean.TRUE.equals(user.getActive()) ? "Active" : "Inactive");
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setPermissions(roleRepository
                .findByNameIgnoreCaseAndCreatedByAdminId(user.getRole(), adminId)
                .map(role -> readPermissions(role.getPermissionsJson()))
                .orElseGet(List::of));
        return response;
    }

    private String writePermissions(List<PackageDto.Category> permissions) {
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    "Permissions could not be saved.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validatePermissions(List<PackageDto.Category> categories) {
        boolean selected = categories != null
                && categories.stream()
                .filter(category -> category.getFeatures() != null)
                .flatMap(category -> category.getFeatures().stream())
                .map(PackageDto.Feature::getPermissions)
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

    private List<PackageDto.Category> readPermissions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<PackageDto.Category>>() {});
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    "Stored permissions are invalid.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String displayName(User user) {
        String name = (user.getFirstName() + " " + user.getLastName()).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
