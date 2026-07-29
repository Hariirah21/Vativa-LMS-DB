package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.MultimediaDto;
import com.example.lms.service.MultimediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/multimedia")
@RequiredArgsConstructor
public class MultimediaController {

    private final MultimediaService multimediaService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<MultimediaDto.ResourceResponse>> upload(
            @Valid @ModelAttribute MultimediaDto.UploadRequest request,
            Authentication authentication
    ) {
        MultimediaDto.ResourceResponse uploaded =
                multimediaService.upload(request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Resource uploaded successfully.", uploaded));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<List<MultimediaDto.ResourceResponse>>> listByCourse(
            @PathVariable Long courseId,
            Authentication authentication
    ) {
        List<MultimediaDto.ResourceResponse> resources =
                multimediaService.listByCourse(courseId, isAdminOrInstructor(authentication));
        return ResponseEntity.ok(ApiResponse.success("Resources fetched successfully.", resources));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        multimediaService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Resource deleted successfully."));
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<Resource> download(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        MultimediaService.DownloadedFile file =
                multimediaService.loadForDownload(id, isAdminOrInstructor(authentication));

        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (file.contentType() != null) {
            try {
                contentType = MediaType.parseMediaType(file.contentType());
            } catch (IllegalArgumentException ignored) {
                // Unknown browser-provided MIME types download as binary.
            }
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(file.resource());
    }

    private boolean isAdminOrInstructor(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(role -> role.equals("ROLE_ADMIN")
                        || role.equals("ROLE_SUPER_ADMIN")
                        || role.equals("ROLE_INSTRUCTOR"));
    }
}
