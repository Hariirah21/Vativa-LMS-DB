package com.example.lms.service;

import com.example.lms.dto.SectionDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.SectionEntity;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.SectionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SectionService {

    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;

    public SectionService(SectionRepository sectionRepository, CourseRepository courseRepository) {
        this.sectionRepository = sectionRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public SectionDto.SectionResponse createSection(Long courseId, SectionDto.SectionRequest request) {
        CourseEntity course = getCourseOrThrow(courseId);

        String name = validateAndNormalizeName(request.getName());

        if (sectionRepository.existsByCourseIdAndNameIgnoreCase(courseId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A section with the same name already exists in this course.");
        }

        Integer maxOrder = sectionRepository.findMaxOrderByCourseId(courseId);
        int nextOrder = (maxOrder == null ? 0 : maxOrder) + 1;

        SectionEntity section = SectionEntity.builder()
                .course(course)
                .name(name)
                .sectionOrder(nextOrder)
                .build();

        return toResponse(sectionRepository.save(section));
    }

    @Transactional(readOnly = true)
    public List<SectionDto.SectionResponse> getAllSections(Long courseId) {
        getCourseOrThrow(courseId);
        return sectionRepository.findByCourseIdOrderBySectionOrderAsc(courseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SectionDto.SectionResponse getSectionById(Long courseId, Long sectionId) {
        return toResponse(getSectionOrThrow(courseId, sectionId));
    }

    @Transactional
    public SectionDto.SectionResponse updateSection(Long courseId, Long sectionId, SectionDto.SectionRequest request) {
        SectionEntity section = getSectionOrThrow(courseId, sectionId);
        String name = validateAndNormalizeName(request.getName());

        if (sectionRepository.existsByCourseIdAndNameIgnoreCaseAndIdNot(courseId, name, sectionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A section with the same name already exists in this course.");
        }

        section.setName(name);
        return toResponse(sectionRepository.save(section));
    }

    @Transactional
    public void deleteSection(Long courseId, Long sectionId) {
        SectionEntity section = getSectionOrThrow(courseId, sectionId);

        sectionRepository.delete(section);

        List<SectionEntity> remaining = sectionRepository.findByCourseIdOrderBySectionOrderAsc(courseId);
        int order = 1;
        for (SectionEntity s : remaining) {
            if (!s.getSectionOrder().equals(order)) {
                s.setSectionOrder(order);
            }
            order++;
        }
        sectionRepository.saveAll(remaining);
    }

    @Transactional
    public List<SectionDto.SectionResponse> reorderSections(Long courseId, SectionDto.ReorderRequest request) {
        getCourseOrThrow(courseId);
        long total = sectionRepository.countByCourseId(courseId);
        if (total < 2) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Reordering requires at least two sections in the course.");
        }

        List<SectionEntity> allSections = sectionRepository.findByCourseIdOrderBySectionOrderAsc(courseId);
        Map<Long, SectionEntity> byId = allSections.stream()
                .collect(Collectors.toMap(section -> section.getId(), section -> section));

        for (SectionDto.ReorderItem item : request.getItems()) {
            SectionEntity section = byId.get(item.getSectionId());
            if (section == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Section not found with id: " + item.getSectionId() + " in course: " + courseId);
            }
            section.setSectionOrder(item.getNewOrder());
        }

        List<SectionEntity> saved = sectionRepository.saveAll(allSections);
        saved.sort((a, b) -> a.getSectionOrder().compareTo(b.getSectionOrder()));
        return saved.stream().map(this::toResponse).toList();
    }

    // ---------- helpers ----------

    private CourseEntity getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Course not found with id: " + courseId));
    }

    private SectionEntity getSectionOrThrow(Long courseId, Long sectionId) {
        return sectionRepository.findByIdAndCourseId(sectionId, courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Section not found with id: " + sectionId + " in course: " + courseId));
    }

    private String validateAndNormalizeName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Section Name is required.");
        }
        int wordCount = name.split("\\s+").length;
        if (wordCount > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Section Name must not exceed 100 words.");
        }
        return name;
    }

    private SectionDto.SectionResponse toResponse(SectionEntity s) {
        return SectionDto.SectionResponse.builder()
                .id(s.getId())
                .courseId(s.getCourse().getId())
                .name(s.getName())
                .sectionOrder(s.getSectionOrder())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
