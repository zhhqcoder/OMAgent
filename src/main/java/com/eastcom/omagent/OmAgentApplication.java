package com.eastcom.omagent;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class OmAgentApplication {

    @Value("${omagent.upload.dir:./uploads}")
    private String uploadDir;

    @PostConstruct
    public void init() {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("创建上传目录失败: " + uploadDir, e);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(OmAgentApplication.class, args);
    }
}
