package com.eastcom.omagent.service;

import com.eastcom.omagent.entity.UploadFile;
import com.eastcom.omagent.rag.DocumentEtlService;
import com.eastcom.omagent.repository.UploadFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileService {

    private final UploadFileRepository uploadFileRepository;
    private final DocumentEtlService documentEtlService;

    @Value("${omagent.upload.dir:./uploads}")
    private String uploadDir;

    public FileService(UploadFileRepository uploadFileRepository,
                       DocumentEtlService documentEtlService) {
        this.uploadFileRepository = uploadFileRepository;
        this.documentEtlService = documentEtlService;
    }

    /**
     * 上传文件
     */
    @Transactional
    public UploadFile uploadFile(MultipartFile file, String userId, String knowledgeType) throws IOException {
        // 确保上传目录存在（转为绝对路径，避免Tomcat将相对路径解析到临时目录）
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成存储文件名
        String originalName = file.getOriginalFilename();
        String storedName = UUID.randomUUID().toString() + "_" + originalName;
        Path filePath = uploadPath.resolve(storedName);

        // 保存文件
        file.transferTo(filePath.toFile());

        // 判断文件类型
        String fileType = determineFileType(originalName);

        // 保存记录
        UploadFile uploadFile = UploadFile.builder()
                .id(UUID.randomUUID().toString())
                .originalName(originalName)
                .storedName(storedName)
                .filePath(filePath.toString())
                .fileType(fileType)
                .fileSize(file.getSize())
                .knowledgeType(knowledgeType)
                .vectorized(false)
                .userId(userId)
                .build();

        return uploadFileRepository.save(uploadFile);
    }

    /**
     * 将上传的文件导入知识库（向量化）
     */
    @Transactional
    public int importToKnowledge(String fileId, String knowledgeType, String docType, String module) {
        UploadFile uploadFile = uploadFileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在: " + fileId));

        File file = new File(uploadFile.getFilePath());
        if (!file.exists()) {
            throw new RuntimeException("文件已丢失: " + uploadFile.getFilePath());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", uploadFile.getOriginalName());
        if (docType != null) {
            metadata.put("doc_type", docType);
        }
        if (module != null) {
            metadata.put("module", module);
        }

        int count;
        String targetKnowledgeType = knowledgeType != null ? knowledgeType : uploadFile.getKnowledgeType();

        if ("source".equals(targetKnowledgeType)) {
            count = documentEtlService.importToSourceStore(file, metadata);
        } else {
            count = documentEtlService.importToConfigStore(file, metadata);
        }

        // 更新文件记录
        uploadFile.setVectorized(true);
        uploadFile.setKnowledgeType(targetKnowledgeType);
        uploadFileRepository.save(uploadFile);

        return count;
    }

    /**
     * 读取文件内容（用于Agent分析）
     */
    public String readFileContent(String fileId) {
        UploadFile uploadFile = uploadFileRepository.findById(fileId)
                .orElse(null);
        if (uploadFile == null) {
            return null;
        }

        try {
            Path path = Paths.get(uploadFile.getFilePath());
            if (Files.exists(path)) {
                return Files.readString(path);
            }
        } catch (IOException e) {
            return "读取文件失败: " + e.getMessage();
        }
        return null;
    }

    /**
     * 获取用户上传文件列表
     */
    public List<UploadFile> getUserFiles(String userId) {
        return uploadFileRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取知识库统计
     */
    public Map<String, Long> getKnowledgeStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("configTotal", uploadFileRepository.countByKnowledgeType("config"));
        stats.put("sourceTotal", uploadFileRepository.countByKnowledgeType("source"));
        return stats;
    }

    private String determineFileType(String filename) {
        if (filename == null) return "unknown";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".csv") || lower.endsWith(".md")) {
            return "txt";
        } else if (lower.endsWith(".java") || lower.endsWith(".xml") || lower.endsWith(".yml") || lower.endsWith(".yaml")
                || lower.endsWith(".properties") || lower.endsWith(".sql") || lower.endsWith(".json")
                || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".html") || lower.endsWith(".css")
                || lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".py")
                || lower.endsWith(".go") || lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".h")
                || lower.endsWith(".pom")) {
            return "source";
        } else if (lower.endsWith(".zip")) {
            return "zip";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
            return "image";
        } else if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "doc";
        } else if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        return "other";
    }
}
