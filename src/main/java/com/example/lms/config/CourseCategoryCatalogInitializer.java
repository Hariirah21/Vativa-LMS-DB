    package com.example.lms.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.lms.service.CourseCategoryCatalogService;

@Component
@Order(1)
public class CourseCategoryCatalogInitializer implements ApplicationRunner {
    private final CourseCategoryCatalogService categoryCatalogService;

    public CourseCategoryCatalogInitializer(CourseCategoryCatalogService categoryCatalogService) {
        this.categoryCatalogService = categoryCatalogService;
    }

    @Override
    public void run(ApplicationArguments args) {
        categoryCatalogService.getSelectableCategories();
    }
}
