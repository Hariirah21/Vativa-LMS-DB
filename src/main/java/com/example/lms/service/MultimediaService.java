package com.example.lms.service;

import com.example.lms.config.MultimediaProperties;
import com.example.lms.dto.MultimediaDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.MultimediaResource;
import com.example.lms.exception.ApiException;
import com.example.lms.exception.FileStorageException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.MultimediaResourceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MultimediaService {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("mp4", "mp3", "pdf", "docx", "pptx", "xlsx", "jpg", "jpeg", "png", "zip");

    private final MultimediaResourceRepository multimediaRepository;
    private final CourseRepository courseRepository;
    private final MultimediaProperties properties;

    private Path storageRoot;

    @PostConstruct
    void initializeStorage() {
        if (!"local".equalsIgnoreCase(properties.getStorageProvider())) {
            throw new IllegalStateException(
                    "Unsupported multimedia storage provider '" + properties.getStorageProvider()
                            + "'. Configure 'local' or provide a cloud storage implementation."
            );
        }

        storageRoot = Path.of(properties.getStorageLocation()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new FileStorageException("Could not initialize multimedia file storage.");
        }
    }

    @Transactional
    public synchronized MultimediaDto.ResourceResponse upload(
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

            MultimediaResource saved = multimediaRepository.save(MultimediaResource.builder()
                    .resourceName(request.getResourceName().trim())
                    .resourceDescription(trimToNull(request.getResourceDescription()))
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

    @Transactional(readOnly = true)
    public List<MultimediaDto.ResourceResponse> listByCourse(Long courseId, boolean privilegedUser) {
        if (!courseRepository.existsById(courseId)) {
            throw new ApiException("Course not found.", HttpStatus.NOT_FOUND);
        }

        List<MultimediaResource> resources = privilegedUser
                ? multimediaRepository.findByCourseIdOrderByCreatedAtDesc(courseId)
                : multimediaRepository.findByCourseIdAndPublishedTrueOrderByCreatedAtDesc(courseId);

        return resources.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(UUID id) {
        MultimediaResource resource = findResource(id);
        multimediaRepository.delete(resource);
        multimediaRepository.flush();

        try {
            Files.deleteIfExists(Path.of(resource.getFilePath()));
        } catch (IOException ex) {
            throw new FileStorageException("Metadata was deleted, but the stored file could not be removed.");
        }
    }

    @Transactional(readOnly = true)
    public DownloadedFile loadForDownload(UUID id, boolean privilegedUser) {
        MultimediaResource multimedia = findResource(id);
        if (!privilegedUser && !multimedia.isPublished()) {
            throw new ApiException("This resource is not published.", HttpStatus.FORBIDDEN);
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

    private MultimediaResource findResource(UUID id) {
        return multimediaRepository.findById(id)
                .orElseThrow(() -> new ApiException("Multimedia resource not found.", HttpStatus.NOT_FOUND));
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
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(
                    "Unsupported file format. Allowed formats: MP4, MP3, PDF, DOCX, PPTX, XLSX, JPG, JPEG, PNG, ZIP.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private MultimediaDto.ResourceResponse toResponse(MultimediaResource resource) {
        return MultimediaDto.ResourceResponse.builder()
                .id(resource.getId())
                .resourceName(resource.getResourceName())
                .resourceDescription(resource.getResourceDescription())
                .courseId(resource.getCourse().getId())
                .createdAt(resource.getCreatedAt())
                .uploadedBy(resource.getUploadedBy())
                .published(resource.isPublished())
                .preview(MultimediaDto.PreviewInfo.builder()
                        .fileName(resource.getOriginalFileName())
                        .contentType(resource.getContentType())
                        .size(resource.getFileSize())
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
