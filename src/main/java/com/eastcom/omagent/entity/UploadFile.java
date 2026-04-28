package com.eastcom.omagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "upload_file")
public class UploadFile {

    @Id
    @Column(length = 36)
    private String id;

    @Column(length = 500, nullable = false)
    private String originalName;

    @Column(length = 500, nullable = false)
    private String storedName;

    @Column(length = 1000, nullable = false)
    private String filePath;

    @Column(length = 50, nullable = false)
    private String fileType; // txt / image

    @Builder.Default
    @Column(nullable = false)
    private Long fileSize = 0L;

    @Column(length = 50)
    private String knowledgeType; // config / source

    @Builder.Default
    @Column(nullable = false)
    private Boolean vectorized = false;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String userId = "default";

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
