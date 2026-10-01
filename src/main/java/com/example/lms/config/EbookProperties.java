package com.example.lms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "lms.ebook")
@Getter @Setter
public class EbookProperties {
    private String storageLocation = "uploads/ebooks";
    private DataSize maxMediaSize = DataSize.ofMegabytes(50);
}
