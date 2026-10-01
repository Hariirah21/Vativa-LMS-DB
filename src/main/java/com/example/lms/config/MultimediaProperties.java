package com.example.lms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "lms.multimedia")
@Getter
@Setter
public class MultimediaProperties {

    private String storageProvider = "local";
    private String storageLocation = "uploads/multimedia";
    private DataSize maxFileSize = DataSize.ofMegabytes(100);
    private List<String> supportedFileTypes = new ArrayList<>(List.of(
            "mp4", "mp3", "pdf", "docx", "pptx", "xlsx", "jpg", "jpeg", "png", "zip"
    ));
}
