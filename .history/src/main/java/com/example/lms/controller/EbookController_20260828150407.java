package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.Ebook;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.EbookRepository;
import com.example.lms.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/courses/{courseId}/ebooks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
public class EbookController {

    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024L * 1024L;
    private static final Set<String> ALLOWED_FILE_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "ppt", "pptx");

    private final EbookRepository ebookRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<Ebook>> create(
            @PathVariable Long courseId,
            @Valid @RequestBody Ebook request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        CourseEntity course = getCourse(courseId);
        validateFileMetadata(request);
        User user = getAuthenticatedUser(principal);
        request.setId(null);
        request.setCourse(course);
        request.setCreatedBy(user);
        request.setUpdatedBy(user);
        Ebook saved = ebookRepository.save(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ebook created successfully.", saved));
    }

    @GetMapping("/{ebookId}")
    public ResponseEntity<ApiResponse<Ebook>> get(
            @PathVariable Long courseId,
            @PathVariable Long ebookId) {
        return ResponseEntity.ok(ApiResponse.success("Ebook fetched successfully.",
                getEbook(courseId, ebookId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Ebook>>> list(@PathVariable Long courseId) {
        getCourse(courseId);
        return ResponseEntity.ok(ApiResponse.success("Ebooks fetched successfully.",
                ebookRepository.findAllByCourseId(courseId)));
    }

    @PutMapping("/{ebookId}")
    public ResponseEntity<ApiResponse<Ebook>> update(
            @PathVariable Long courseId,
            @PathVariable Long ebookId,
            @RequestBody Ebook request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        Ebook existing = getEbook(courseId, ebookId);
        if (request.getSection() != null) {
            validateSection(request.getSection());
            existing.setSection(request.getSection());
        }
        if (request.getContentTitle() != null) {
            if (request.getContentTitle().length() > 100) {
                throw new ApiException("Content Title must not exceed 100 characters.", HttpStatus.BAD_REQUEST);
            }
            existing.setContentTitle(request.getContentTitle());
        }
        copyContent(existing, request);
        validateFileMetadata(existing);
        existing.setUpdatedBy(getAuthenticatedUser(principal));
        if (request.getVersion() != null && !request.getVersion().equals(existing.getVersion())) {
            throw new ApiException("This Ebook has already been modified by another user.",
                    HttpStatus.CONFLICT);
        }
        return ResponseEntity.ok(ApiResponse.success("Ebook updated successfully.",
                ebookRepository.save(existing)));
    }

    @DeleteMapping("/{ebookId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long courseId,
            @PathVariable Long ebookId) {
        Ebook ebook = getEbook(courseId, ebookId);
        ebookRepository.delete(ebook);
        return ResponseEntity.ok(ApiResponse.success("Ebook deleted successfully."));
    }

    private CourseEntity getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException("Course not found with id: " + courseId,
                        HttpStatus.NOT_FOUND));
    }

    private Ebook getEbook(Long courseId, Long ebookId) {
        return ebookRepository.findByIdAndCourseId(ebookId, courseId)
                .orElseThrow(() -> new ApiException("Ebook not found with id: " + ebookId,
                        HttpStatus.NOT_FOUND));
    }

    private User getAuthenticatedUser(AuthPrincipal principal) {
        if (principal == null) {
            throw new ApiException("Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException("Authenticated user no longer exists.",
                        HttpStatus.UNAUTHORIZED));
    }

    private void validateFileMetadata(Ebook ebook) {
        if (ebook.getUploadedFileSize() != null && ebook.getUploadedFileSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException("File size exceeds 100 MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String filename = ebook.getUploadedFileName();
        if (filename == null || filename.isBlank()) {
            return;
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_FILE_EXTENSIONS.contains(extension)) {
            throw new ApiException("Invalid uploaded file type. Supported formats are PDF, DOC, DOCX, PPT, and PPTX.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateSection(String section) {
        if (section.isBlank()) {
            throw new ApiException("Section is required.", HttpStatus.BAD_REQUEST);
        }
        if (section.length() > 100) {
            throw new ApiException("Section must not exceed 100 characters.", HttpStatus.BAD_REQUEST);
        }
    }

    private void copyContent(Ebook target, Ebook source) {
        if (source.getUploadedFileName() != null) target.setUploadedFileName(source.getUploadedFileName());
        if (source.getUploadedFileUrl() != null) target.setUploadedFileUrl(source.getUploadedFileUrl());
        if (source.getUploadedFileSize() != null) target.setUploadedFileSize(source.getUploadedFileSize());
        if (source.getImportedContent() != null) target.setImportedContent(source.getImportedContent());
        if (source.getOwnLayout() != null) target.setOwnLayout(source.getOwnLayout());
        if (source.getBlankEbook() != null) target.setBlankEbook(source.getBlankEbook());
        if (source.getCourseWelcome() != null) target.setCourseWelcome(source.getCourseWelcome());
        if (source.getCourseOverview() != null) target.setCourseOverview(source.getCourseOverview());
        if (source.getSummary() != null) target.setSummary(source.getSummary());
        if (source.getEbookCover() != null) target.setEbookCover(source.getEbookCover());
        if (source.getMediaAndText() != null) target.setMediaAndText(source.getMediaAndText());
        if (source.getCounters() != null) target.setCounters(source.getCounters());
        if (source.getDownloadFile() != null) target.setDownloadFile(source.getDownloadFile());
        if (source.getExternalContent() != null) target.setExternalContent(source.getExternalContent());
        if (source.getTeamAndAuthors() != null) target.setTeamAndAuthors(source.getTeamAndAuthors());
        if (source.getLayout() != null) target.setLayout(source.getLayout());
        if (source.getEbookText() != null) target.setEbookText(source.getEbookText());
        if (source.getSectionDesign() != null) target.setSectionDesign(source.getSectionDesign());
    }
}