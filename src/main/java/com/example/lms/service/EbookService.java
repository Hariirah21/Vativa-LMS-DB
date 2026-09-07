package com.example.lms.service;

import com.example.lms.config.EbookProperties;
import com.example.lms.dto.EbookDto;
import com.example.lms.entity.*;
import com.example.lms.exception.ApiException;
import com.example.lms.exception.FileStorageException;
import com.example.lms.repository.*;
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
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EbookService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "mp4", "webm", "mov");
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of("video/mp4", "video/webm", "video/quicktime");

    private final EbookRepository ebookRepository;
    private final EbookMediaRepository mediaRepository;
    private final CourseRepository courseRepository;
    private final EbookProperties properties;
    private Path storageRoot;

    @PostConstruct
    void initializeStorage() {
        storageRoot = Path.of(properties.getStorageLocation()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new FileStorageException("Could not initialize ebook media storage.");
        }
    }

    @Transactional
    public synchronized EbookDto.Response create(EbookDto.CreateRequest request, String username) {
        if (request.getClientRequestId() != null) {
            Optional<EbookEntity> existing = ebookRepository.findByClientRequestId(request.getClientRequestId());
            if (existing.isPresent()) return toResponse(existing.get());
        }
        CourseEntity course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ApiException("Course not found.", HttpStatus.BAD_REQUEST));
        EbookEntity saved = ebookRepository.saveAndFlush(EbookEntity.builder()
                .title(request.getTitle().trim())
                .description(trimToNull(request.getDescription()))
                .content(request.getContent().trim())
                .status(EbookStatus.DRAFT)
                .course(course)
                .createdBy(username)
                .clientRequestId(request.getClientRequestId())
                .build());
        return toResponse(saved);
    }

    @Transactional
    public EbookDto.Response update(UUID id, EbookDto.UpdateRequest request) {
        EbookEntity ebook = findEbook(id);
        requireDraft(ebook);
        requireCurrentVersion(ebook, request.getVersion());
        ebook.setTitle(request.getTitle().trim());
        ebook.setDescription(trimToNull(request.getDescription()));
        ebook.setContent(request.getContent().trim());
        return toResponse(ebookRepository.saveAndFlush(ebook));
    }

    @Transactional
    public EbookDto.Response publish(UUID id, EbookDto.PublishRequest request) {
        EbookEntity ebook = findEbook(id);
        if (ebook.getStatus() == EbookStatus.PUBLISHED) return toResponse(ebook);
        requireCurrentVersion(ebook, request.getVersion());
        validateMandatoryContent(ebook);
        ebook.setStatus(EbookStatus.PUBLISHED);
        ebook.setPublishedAt(LocalDateTime.now());
        return toResponse(ebookRepository.saveAndFlush(ebook));
    }

    @Transactional(readOnly = true)
    public EbookDto.Response get(UUID id, boolean privileged) {
        EbookEntity ebook = findEbook(id);
        requireReadable(ebook, privileged);
        return toResponse(ebook);
    }

    @Transactional(readOnly = true)
    public List<EbookDto.Response> listByCourse(Long courseId, boolean privileged) {
        if (!courseRepository.existsById(courseId)) throw new ApiException("Course not found.", HttpStatus.NOT_FOUND);
        List<EbookEntity> ebooks = privileged
                ? ebookRepository.findByCourseIdOrderByUpdatedAtDesc(courseId)
                : ebookRepository.findByCourseIdAndStatusOrderByUpdatedAtDesc(courseId, EbookStatus.PUBLISHED);
        return ebooks.stream().map(this::toResponse).toList();
    }

    @Transactional
    public EbookDto.MediaResponse uploadMedia(UUID ebookId, MultipartFile file) {
        EbookEntity ebook = findEbook(ebookId);
        requireDraft(ebook);
        validateMedia(file);
        String originalName = safeOriginalFileName(file);
        String extension = extensionOf(originalName);
        Path ebookDirectory = storageRoot.resolve(ebookId.toString()).normalize();
        Path destination = ebookDirectory.resolve(UUID.randomUUID() + "." + extension).normalize();
        ensureInsideStorage(destination);
        try {
            Files.createDirectories(ebookDirectory);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            EbookMediaEntity saved = mediaRepository.saveAndFlush(EbookMediaEntity.builder()
                    .ebook(ebook)
                    .filePath(destination.toString())
                    .originalFileName(originalName)
                    .contentType(normalizedContentType(file, extension))
                    .fileSize(file.getSize())
                    .build());
            ebook.getMedia().add(saved);
            return toMediaResponse(saved);
        } catch (IOException ex) {
            deleteQuietly(destination);
            throw new FileStorageException("The ebook media could not be saved. Please try again.");
        } catch (RuntimeException ex) {
            deleteQuietly(destination);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public DownloadedMedia loadMedia(UUID mediaId, boolean privileged) {
        EbookMediaEntity media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ApiException("Ebook media not found.", HttpStatus.NOT_FOUND));
        requireReadable(media.getEbook(), privileged);
        Path path = Path.of(media.getFilePath()).toAbsolutePath().normalize();
        ensureInsideStorage(path);
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable())
                throw new FileStorageException("The stored ebook media is unavailable.", HttpStatus.NOT_FOUND);
            return new DownloadedMedia(resource, media.getOriginalFileName(), media.getContentType());
        } catch (IOException ex) {
            throw new FileStorageException("The stored ebook media is unavailable.", HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public void deleteMedia(UUID ebookId, UUID mediaId) {
        EbookEntity ebook = findEbook(ebookId);
        requireDraft(ebook);
        EbookMediaEntity media = mediaRepository.findById(mediaId)
                .filter(item -> item.getEbook().getId().equals(ebookId))
                .orElseThrow(() -> new ApiException("Ebook media not found.", HttpStatus.NOT_FOUND));
        mediaRepository.delete(media);
        mediaRepository.flush();
        deleteStoredFile(media.getFilePath());
    }

    @Transactional
    public void delete(UUID id) {
        EbookEntity ebook = findEbook(id);
        requireDraft(ebook);
        List<String> paths = ebook.getMedia().stream().map(EbookMediaEntity::getFilePath).toList();
        ebookRepository.delete(ebook);
        ebookRepository.flush();
        paths.forEach(this::deleteStoredFile);
    }

    private void validateMandatoryContent(EbookEntity ebook) {
        if (!StringUtils.hasText(ebook.getTitle()) || !StringUtils.hasText(ebook.getContent()))
            throw new ApiException("Please complete all required fields before publishing.", HttpStatus.BAD_REQUEST);
    }

    private void validateMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException("File is required and must not be empty.", HttpStatus.BAD_REQUEST);
        if (file.getSize() > properties.getMaxMediaSize().toBytes())
            throw new ApiException("File size exceeds limit.", HttpStatus.BAD_REQUEST);
        String name = safeOriginalFileName(file);
        String extension = extensionOf(name);
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean allowedType = ALLOWED_IMAGE_TYPES.contains(type) || ALLOWED_VIDEO_TYPES.contains(type);
        if (!ALLOWED_EXTENSIONS.contains(extension) || (!type.isBlank() && !allowedType))
            throw new ApiException("Invalid file format.", HttpStatus.BAD_REQUEST);
    }

    private void requireDraft(EbookEntity ebook) {
        if (ebook.getStatus() != EbookStatus.DRAFT)
            throw new ApiException("Published ebooks are read-only.", HttpStatus.CONFLICT);
    }

    private void requireCurrentVersion(EbookEntity ebook, Long version) {
        if (!Objects.equals(ebook.getVersion(), version))
            throw new ApiException("This ebook was updated by another user. Refresh to load the latest version.", HttpStatus.CONFLICT);
    }

    private void requireReadable(EbookEntity ebook, boolean privileged) {
        if (!privileged && ebook.getStatus() != EbookStatus.PUBLISHED)
            throw new ApiException("This ebook is not published.", HttpStatus.FORBIDDEN);
    }

    private EbookEntity findEbook(UUID id) {
        return ebookRepository.findById(id).orElseThrow(() -> new ApiException("Ebook not found.", HttpStatus.NOT_FOUND));
    }

    private EbookDto.Response toResponse(EbookEntity ebook) {
        return EbookDto.Response.builder()
                .id(ebook.getId()).title(ebook.getTitle()).description(ebook.getDescription()).content(ebook.getContent())
                .status(ebook.getStatus()).courseId(ebook.getCourse().getId()).createdBy(ebook.getCreatedBy())
                .version(ebook.getVersion()).createdAt(ebook.getCreatedAt()).updatedAt(ebook.getUpdatedAt())
                .publishedAt(ebook.getPublishedAt()).media(ebook.getMedia().stream().map(this::toMediaResponse).toList()).build();
    }

    private EbookDto.MediaResponse toMediaResponse(EbookMediaEntity media) {
        return EbookDto.MediaResponse.builder().id(media.getId()).fileName(media.getOriginalFileName())
                .contentType(media.getContentType()).size(media.getFileSize())
                .downloadUrl("/api/ebooks/media/" + media.getId()).build();
    }

    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String safeOriginalFileName(MultipartFile file) {
        String name = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (!StringUtils.hasText(name) || name.contains("..")) throw new ApiException("The uploaded file name is invalid.", HttpStatus.BAD_REQUEST);
        return name;
    }
    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private String normalizedContentType(MultipartFile file, String extension) {
        if (StringUtils.hasText(file.getContentType())) return file.getContentType().toLowerCase(Locale.ROOT);
        return switch (extension) { case "jpg", "jpeg" -> "image/jpeg"; case "png" -> "image/png"; case "webm" -> "video/webm"; case "mov" -> "video/quicktime"; default -> "video/mp4"; };
    }
    private void ensureInsideStorage(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(storageRoot)) throw new ApiException("Invalid file path.", HttpStatus.BAD_REQUEST);
    }
    private void deleteStoredFile(String path) {
        try { Path file = Path.of(path).toAbsolutePath().normalize(); ensureInsideStorage(file); Files.deleteIfExists(file); }
        catch (IOException ex) { throw new FileStorageException("Ebook metadata was changed, but a stored media file could not be removed."); }
    }
    private void deleteQuietly(Path path) { try { Files.deleteIfExists(path); } catch (IOException ignored) {} }

    public record DownloadedMedia(Resource resource, String fileName, String contentType) {}
}
