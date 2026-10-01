package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.SectionDto;
import com.example.lms.service.SectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses/{courseId}/sections")
public class SectionController {

    private final SectionService sectionService;

    public SectionController(SectionService sectionService) {
        this.sectionService = sectionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SectionDto.SectionResponse>> createSection(
            @PathVariable Long courseId,
            @Valid @RequestBody SectionDto.SectionRequest request) {
        SectionDto.SectionResponse created = sectionService.createSection(courseId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Section created successfully.", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SectionDto.SectionResponse>>> getAllSections(
            @PathVariable Long courseId) {
        List<SectionDto.SectionResponse> sections = sectionService.getAllSections(courseId);
        return ResponseEntity.ok(ApiResponse.success("Sections fetched successfully.", sections));
    }

    @GetMapping("/{sectionId}")
    public ResponseEntity<ApiResponse<SectionDto.SectionResponse>> getSectionById(
            @PathVariable Long courseId,
            @PathVariable Long sectionId) {
        SectionDto.SectionResponse section = sectionService.getSectionById(courseId, sectionId);
        return ResponseEntity.ok(ApiResponse.success("Section fetched successfully.", section));
    }

    @PutMapping("/{sectionId}")
    public ResponseEntity<ApiResponse<SectionDto.SectionResponse>> updateSection(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @Valid @RequestBody SectionDto.SectionRequest request) {
        SectionDto.SectionResponse updated = sectionService.updateSection(courseId, sectionId, request);
        return ResponseEntity.ok(ApiResponse.success("Section updated successfully.", updated));
    }

    @DeleteMapping("/{sectionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSection(
            @PathVariable Long courseId,
            @PathVariable Long sectionId) {
        sectionService.deleteSection(courseId, sectionId);
        return ResponseEntity.ok(ApiResponse.success("Section deleted successfully."));
    }

    @PatchMapping("/reorder")
    public ResponseEntity<ApiResponse<List<SectionDto.SectionResponse>>> reorderSections(
            @PathVariable Long courseId,
            @Valid @RequestBody SectionDto.ReorderRequest request) {
        List<SectionDto.SectionResponse> reordered = sectionService.reorderSections(courseId, request);
        return ResponseEntity.ok(ApiResponse.success("Sections reordered successfully.", reordered));
    }
}
