package com.example.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CourseCategoryDto {
    private Long id;
    private String name;
    private String description;
    private boolean active;
}
