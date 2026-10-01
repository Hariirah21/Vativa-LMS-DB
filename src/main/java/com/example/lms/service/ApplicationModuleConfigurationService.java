package com.example.lms.service;

import com.example.lms.dto.PackageDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class ApplicationModuleConfigurationService {

    private final List<PackageDto.Category> modules;

    public ApplicationModuleConfigurationService(
            @Value("${app.modules.configuration}") Resource configuration) throws IOException {
        try (var input = configuration.getInputStream()) {
            this.modules = List.copyOf(new ObjectMapper().readValue(
                    input,
                    new TypeReference<List<PackageDto.Category>>() {}));
        }
    }

    public List<PackageDto.Category> getModules() {
        return modules;
    }
}
