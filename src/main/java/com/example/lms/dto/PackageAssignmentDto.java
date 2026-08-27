package com.example.lms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Doc06 "Update Package Fields" (S#19-22) - this is a DISTINCT screen from
 * editing a package's own details. It lets the Super Admin re-assign an
 * existing package to a specific already-registered user, identified by
 * Email ID:
 *
 *   S#19 Email ID  - dropdown of registered user emails (mandatory)
 *   S#20 Package   - dropdown of Basic/Standard/Premium (mandatory)
 *   S#21 Save Update - persists the assignment
 *   S#22 Cancel      - discards
 *
 * The previous implementation only had PUT /api/packages/{id}, which edits
 * the package's own master-data fields (name/price/etc.) - it never
 * implemented this user-to-package assignment flow at all.
 */
@Data
@NoArgsConstructor
public class PackageAssignmentDto {

    @NotBlank(message = "Please select an Email ID")
    @Email(message = "Please select an Email ID")
    private String emailId;

    @NotNull(message = "Please select a package")
    private Long packageId;
}