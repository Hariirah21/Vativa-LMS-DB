package com.example.lms.service;

import com.example.lms.config.MultimediaProperties;
import com.example.lms.dto.MultimediaDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.MultimediaEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.exception.FileStorageException;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.MultimediaRepository;
import com.example.lms.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class MultimediaService {

    private final MultimediaRepository multimediaRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final MultimediaProperties properties;
    private final PlatformTransactionManager transactionManager;

    private Path storageRoot;
    private Set<String> supportedFileTypes;
    private final ConcurrentMap<UUID, CompletableFuture<MultimediaDto.ResourceResponse>>
            inFlightUploads = new ConcurrentHashMap<>();

    @PostConstruct
    void initializeStorage() {
        if (!"local".equalsIgnoreCase(properties.getStorageProvider())) {
            throw new IllegalStateException(
                    "Unsupported multimedia storage provider '" + properties.getStorageProvider()
                            + "'. Configure 'local' or provide a cloud storage implementation."
            );
        }

        if (properties.getMaxFileSize() == null || properties.getMaxFileSize().toBytes() <= 0) {
            throw new IllegalStateException("Multimedia maximum file size must be greater than zero.");
        }
        supportedFileTypes = normalizeSupportedFileTypes(properties.getSupportedFileTypes());
        if (supportedFileTypes.isEmpty()) {
            throw new IllegalStateException("At least one multimedia file type must be configured.");
        }

        storageRoot = Path.of(properties.getStorageLocation()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new FileStorageException("Could not initialize multimedia file storage.");
        }
    }

    public MultimediaDto.ResourceResponse upload(
            MultimediaDto.UploadRequest request,
            String uploadedBy
    ) {
        UUID submissionId = request.getClientRequestId();
        if (submissionId == null) {
            return executeUploadTransaction(request, uploadedBy);
        }

        CompletableFuture<MultimediaDto.ResourceResponse> current = new CompletableFuture<>();
        CompletableFuture<MultimediaDto.ResourceResponse> first =
                inFlightUploads.putIfAbsent(submissionId, current);
        if (first != null) {
            return awaitFirstSubmission(first);
        }

        try {
            MultimediaDto.ResourceResponse response = executeUploadTransaction(request, uploadedBy);
            current.complete(response);
            return response;
        } catch (RuntimeException exception) {
            current.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightUploads.remove(submissionId, current);
        }
    }

    private MultimediaDto.ResourceResponse executeUploadTransaction(
            MultimediaDto.UploadRequest request,
            String uploadedBy
    ) {
        MultimediaDto.ResourceResponse response;
        try {
            response = new TransactionTemplate(transactionManager)
                    .execute(status -> uploadOnce(request, uploadedBy));
        } catch (DataIntegrityViolationException exception) {
            response = findExistingSubmission(request.getClientRequestId());
            if (response == null) {
                throw exception;
            }
        }
        if (response == null) {
            throw new FileStorageException("The upload transaction did not return a resource.");
        }
        return response;
    }

    private MultimediaDto.ResourceResponse findExistingSubmission(UUID submissionId) {
        if (submissionId == null) {
            return null;
        }
        return new TransactionTemplate(transactionManager).execute(status ->
                multimediaRepository.findByClientRequestId(submissionId)
                        .map(this::toResponse)
                        .orElse(null));
    }

    private MultimediaDto.ResourceResponse awaitFirstSubmission(
            CompletableFuture<MultimediaDto.ResourceResponse> first
    ) {
        try {
            return first.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private MultimediaDto.ResourceResponse uploadOnce(
            MultimediaDto.UploadRequest request,
            String uploadedBy
    ) {
        if (request.getClientRequestId() != null) {
            var existing = multimediaRepository.findByClientRequestId(request.getClientRequestId());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        MultipartFile file = request.getFile();
        validateFile(file);

        CourseEntity course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ApiException("Course not found.", HttpStatus.BAD_REQUEST));

        String originalFileName = safeOriginalFileName(file);
        String extension = extensionOf(originalFileName);
        String storedFileName = UUID.randomUUID() + "." + extension;
        Path destination = storageRoot.resolve(storedFileName).normalize();
        Path temporary = storageRoot.resolve(storedFileName + ".part").normalize();

        ensureInsideStorage(destination);
        ensureInsideStorage(temporary);

        try {
            Files.copy(file.getInputStream(), temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveNotSupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            MultimediaEntity saved = multimediaRepository.saveAndFlush(MultimediaEntity.builder()
                    .resourceName(request.getResourceName().trim())
                    .resourceDescription(trimToNull(request.getResourceDescription()))
                    .resourceType(request.getResourceType())
                    .filePath(destination.toString())
                    .originalFileName(originalFileName)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .course(course)
                    .uploadedBy(uploadedBy)
                    .published(request.isPublished())
                    .clientRequestId(request.getClientRequestId())
                    .build());

            return toResponse(saved);
        } catch (IOException ex) {
            deleteQuietly(temporary);
            deleteQuietly(destination);
            throw new FileStorageException("The file could not be saved. Please try again.");
        } catch (RuntimeException ex) {
            deleteQuietly(temporary);
            deleteQuietly(destination);
            throw ex;
        }
    }

    public MultimediaDto.PreviewInfo preview(MultipartFile file) {
        validateFile(file);
        return MultimediaDto.PreviewInfo.builder()
                .fileName(safeOriginalFileName(file))
                .contentType(file.getContentType())
                .size(file.getSize())
                .inlinePreviewSupported(supportsInlinePreview(file.getContentType()))
                .build();
    }

    public MultimediaDto.UploadConfiguration uploadConfiguration() {
        return MultimediaDto.UploadConfiguration.builder()
                .supportedFileTypes(supportedFileTypes.stream()
                        .map(extension -> extension.toUpperCase(Locale.ROOT))
                        .toList())
                .maximumUploadSizeBytes(properties.getMaxFileSize().toBytes())
                .maximumUploadSizeMegabytes(properties.getMaxFileSize().toMegabytes())
                .build();
    }

    @Transactional(readOnly = true)
    public List<MultimediaDto.ResourceResponse> listByCourse(
            Long courseId,
            boolean privilegedUser,
            String username
    ) {
        if (!courseRepository.existsById(courseId)) {
            throw new ApiException("Course not found.", HttpStatus.NOT_FOUND);
        }

        boolean assignedLearner = !privilegedUser && hasActiveCourseAssignment(courseId, username);
        List<MultimediaEntity> resources = privilegedUser || assignedLearner
                ? multimediaRepository.findByCourseIdOrderByCreatedAtDesc(courseId)
                : multimediaRepository.findByCourseIdAndPublishedTrueOrderByCreatedAtDesc(courseId);

        return resources.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        MultimediaEntity resource = findResource(id);
        multimediaRepository.delete(resource);
        multimediaRepository.flush();

        try {
            Files.deleteIfExists(Path.of(resource.getFilePath()));
        } catch (IOException ex) {
            throw new FileStorageException("Metadata was deleted, but the stored file could not be removed.");
        }
    }

    @Transactional(readOnly = true)
    public DownloadedFile loadForDownload(UUID id, boolean privilegedUser, String username) {
        MultimediaEntity multimedia = findResource(id);
        boolean allowedByAssignment = !privilegedUser
                && hasActiveCourseAssignment(multimedia.getCourse().getId(), username);
        if (!privilegedUser && !multimedia.isPublished() && !allowedByAssignment) {
            throw new ApiException(
                    "This resource is not published or assigned to this learner.",
                    HttpStatus.FORBIDDEN
            );
        }

        try {
            Path filePath = Path.of(multimedia.getFilePath()).toAbsolutePath().normalize();
            ensureInsideStorage(filePath);
            Resource file = new UrlResource(filePath.toUri());
            if (!file.exists() || !file.isReadable()) {
                throw new FileStorageException("The stored file is unavailable.", HttpStatus.NOT_FOUND);
            }
            return new DownloadedFile(file, multimedia.getOriginalFileName(), multimedia.getContentType());
        } catch (IOException ex) {
            throw new FileStorageException("The stored file is unavailable.", HttpStatus.NOT_FOUND);
        }
    }

    private MultimediaEntity findResource(UUID id) {
        return multimediaRepository.findById(id)
                .orElseThrow(() -> new ApiException("Multimedia resource not found.", HttpStatus.NOT_FOUND));
    }

    private boolean hasActiveCourseAssignment(Long courseId, String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return userRepository.findByEmailIgnoreCase(username)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(this::isLearner)
                .map(User::getId)
                .map(userId -> enrollmentRepository.existsActiveAssignment(
                        courseId, userId, LocalDateTime.now()))
                .orElse(false);
    }

    private boolean isLearner(User user) {
        return user.getRole() != null
                && "LEARNER".equalsIgnoreCase(user.getRole().replace("ROLE_", "").trim());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("File is required and must not be empty.", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new ApiException(
                    "File size must not exceed " + properties.getMaxFileSize().toMegabytes() + " MB.",
                    HttpStatus.BAD_REQUEST
            );
        }

        String extension = extensionOf(safeOriginalFileName(file));
        if (!supportedFileTypes.contains(extension)) {
            throw new ApiException(
                    "Unsupported file format. Allowed formats: "
                            + supportedFileTypes.stream()
                                    .map(value -> value.toUpperCase(Locale.ROOT))
                                    .collect(java.util.stream.Collectors.joining(", "))
                            + ".",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private Set<String> normalizeSupportedFileTypes(List<String> configuredTypes) {
        if (configuredTypes == null) {
            return Set.of();
        }
        return configuredTypes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(value -> value.startsWith(".") ? value.substring(1) : value)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private MultimediaDto.ResourceResponse toResponse(MultimediaEntity resource) {
        return MultimediaDto.ResourceResponse.builder()
                .id(resource.getId())
                .resourceName(resource.getResourceName())
                .resourceDescription(resource.getResourceDescription())
                .resourceType(resource.getResourceType())
                .courseId(resource.getCourse().getId())
                .createdAt(resource.getCreatedAt())
                .uploadedBy(resource.getUploadedBy())
                .published(resource.isPublished())
                .preview(MultimediaDto.PreviewInfo.builder()
                        .fileName(resource.getOriginalFileName())
                        .contentType(resource.getContentType())
                        .size(resource.getFileSize())
                        .inlinePreviewSupported(supportsInlinePreview(resource.getContentType()))
                        .downloadUrl("/api/multimedia/files/" + resource.getId())
                        .build())
                .build();
    }

    private String safeOriginalFileName(MultipartFile file) {
        String name = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename()
        );
        if (!StringUtils.hasText(name) || name.contains("..")) {
            throw new ApiException("The uploaded file name is invalid.", HttpStatus.BAD_REQUEST);
        }
        return name;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void ensureInsideStorage(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(storageRoot)) {
            throw new ApiException("Invalid file path.", HttpStatus.BAD_REQUEST);
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean supportsInlinePreview(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("image/")
                || normalized.startsWith("video/")
                || normalized.startsWith("audio/")
                || normalized.equals("application/pdf");
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The original exception remains the useful error to return.
        }
    }

    public record DownloadedFile(Resource resource, String fileName, String contentType) {
    }
}
