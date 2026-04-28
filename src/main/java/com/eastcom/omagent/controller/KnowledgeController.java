package com.eastcom.omagent.controller;

import com.eastcom.omagent.dto.KnowledgeImportRequest;
import com.eastcom.omagent.service.FileService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final FileService fileService;

    public KnowledgeController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 将上传的文件导入知识库
     */
    @PostMapping("/import")
    public Map<String, Object> importToKnowledge(@RequestBody KnowledgeImportRequest request) {
        int count = fileService.importToKnowledge(
                request.getFileId(),
                request.getKnowledgeType(),
                request.getDocType(),
                request.getModule()
        );
        return Map.of(
                "success", true,
                "chunkCount", count,
                "message", "成功导入 " + count + " 个文档块"
        );
    }

    /**
     * 获取知识库统计信息
     */
    @GetMapping("/stats")
    public Map<String, Long> getKnowledgeStats() {
        return fileService.getKnowledgeStats();
    }
}
