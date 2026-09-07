package com.example.lms.service;

import com.example.lms.entity.CourseCategoryEntity;
import com.example.lms.entity.CourseCategoryType;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseCategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CourseCategoryCatalogService {
    private static final String FIXED_CATEGORY_MESSAGE =
            "Course Category must be one of: Technical, Soft Skills, Compliance, or Leadership.";

    private final CourseCategoryRepository categoryRepository;

    public CourseCategoryCatalogService(CourseCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public List<CourseCategoryEntity> getSelectableCategories() {
        Map<String, CourseCategoryEntity> categoriesByName = new LinkedHashMap<>();
        categoryRepository.findAll().forEach(category -> {
            if (category.getName() != null) {
                String key = normalize(category.getName());
                CourseCategoryEntity current = categoriesByName.get(key);
                if (current == null || isCanonical(category)) {
                    categoriesByName.put(key, category);
                }
            }
        });

        List<CourseCategoryEntity> selectableCategories = new ArrayList<>();
        for (CourseCategoryType type : CourseCategoryType.values()) {
            CourseCategoryEntity category = categoriesByName.get(normalize(type.getDisplayName()));
            if (category == null) {
                category = new CourseCategoryEntity();
                category.setName(type.getDisplayName());
                category.setDescription(type.getDescription());
                category.setActive(true);
                category = categoryRepository.save(category);
            } else {
                boolean changed = false;
                if (!type.getDisplayName().equals(category.getName())) {
                    category.setName(type.getDisplayName());
                    changed = true;
                }
                if (category.getDescription() == null || category.getDescription().isBlank()) {
                    category.setDescription(type.getDescription());
                    changed = true;
                }
                if (!Boolean.TRUE.equals(category.getActive())) {
                    category.setActive(true);
                    changed = true;
                }
                if (changed) {
                    category = categoryRepository.save(category);
                }
            }
            selectableCategories.add(category);
        }
        return List.copyOf(selectableCategories);
    }

    @Transactional(readOnly = true)
    public CourseCategoryEntity getSelectableCategory(Long categoryId) {
        CourseCategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(
                        "Course category not found with id: " + categoryId,
                        HttpStatus.NOT_FOUND));
        if (!CourseCategoryType.supports(category.getName())) {
            throw new ApiException(FIXED_CATEGORY_MESSAGE, HttpStatus.BAD_REQUEST);
        }
        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new ApiException("The selected course category is inactive.", HttpStatus.BAD_REQUEST);
        }
        return category;
    }

    private boolean isCanonical(CourseCategoryEntity category) {
        return CourseCategoryType.displayNames().contains(category.getName());
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
