package com.example.lms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "lms.multimedia")
@Getter
@Setter
public class MultimediaProperties {

    private String storageProvider = "local";
    private String storageLocation = "uploads/multimedia";
    private DataSize maxFileSize = DataSize.ofMegabytes(100);
}
