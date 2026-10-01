package com.example.lms.controller;

import com.example.lms.dto.*;
import com.example.lms.service.EbookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/ebooks")
@RequiredArgsConstructor
public class EbookController {
    private final EbookService ebookService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<EbookDto.Response>> create(@Valid @RequestBody EbookDto.CreateRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Ebook saved as draft successfully.", ebookService.create(request, auth.getName())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<EbookDto.Response>> update(@PathVariable UUID id, @Valid @RequestBody EbookDto.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ebook draft updated successfully.", ebookService.update(id, request)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<EbookDto.Response>> publish(@PathVariable UUID id, @Valid @RequestBody EbookDto.PublishRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ebook published successfully.", ebookService.publish(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EbookDto.Response>> get(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Ebook fetched successfully.", ebookService.get(id, isPrivileged(auth))));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<ApiResponse<List<EbookDto.Response>>> list(@PathVariable Long courseId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Ebooks fetched successfully.", ebookService.listByCourse(courseId, isPrivileged(auth))));
    }

    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<EbookDto.MediaResponse>> uploadMedia(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Ebook media uploaded successfully.", ebookService.uploadMedia(id, file)));
    }

    @GetMapping("/media/{mediaId}")
    public ResponseEntity<Resource> media(@PathVariable UUID mediaId, Authentication auth) {
        EbookService.DownloadedMedia media = ebookService.loadMedia(mediaId, isPrivileged(auth));
        MediaType type;
        try { type = MediaType.parseMediaType(media.contentType()); }
        catch (IllegalArgumentException ex) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(media.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(media.resource());
    }

    @DeleteMapping("/{ebookId}/media/{mediaId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(@PathVariable UUID ebookId, @PathVariable UUID mediaId) {
        ebookService.deleteMedia(ebookId, mediaId);
        return ResponseEntity.ok(ApiResponse.success("Ebook media deleted successfully."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        ebookService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Ebook draft deleted successfully."));
    }

    private boolean isPrivileged(Authentication auth) {
        return auth.getAuthorities().stream().map(a -> a.getAuthority()).anyMatch(role ->
                role.equals("ROLE_ADMIN") || role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_INSTRUCTOR"));
    }
}
