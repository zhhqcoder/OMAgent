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
        try {
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
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "chunkCount", 0,
                    "message", "导入失败: " + e.getMessage()
            );
        }
    }

    /**
     * 获取知识库统计信息
     */
    @GetMapping("/stats")
    public Map<String, Long> getKnowledgeStats() {
        return fileService.getKnowledgeStats();
    }

    /**
     * 清空指定知识库
     * @param type 知识库类型: "config" 或 "source"
     */
    @DeleteMapping("/clear/{type}")
    public Map<String, Object> clearKnowledge(@PathVariable String type) {
        if (!"config".equals(type) && !"source".equals(type)) {
            return Map.of("success", false, "message", "无效的知识库类型，仅支持 config 或 source");
        }
        int deletedCount = fileService.clearKnowledge(type);
        return Map.of(
                "success", true,
                "deletedCount", deletedCount,
                "message", "已清空" + ("config".equals(type) ? "配置" : "源码/日志") + "知识库，删除 " + deletedCount + " 条记录"
        );
    }

    /**
     * 重新向量化指定文件
     * @param fileId 文件ID
     */
    @PostMapping("/reimport/{fileId}")
    public Map<String, Object> reimportFile(@PathVariable String fileId,
                                            @RequestParam(required = false) String knowledgeType,
                                            @RequestParam(required = false) String docType,
                                            @RequestParam(required = false) String module) {
        int count = fileService.reimportToKnowledge(fileId, knowledgeType, docType, module);
        return Map.of(
                "success", true,
                "chunkCount", count,
                "message", "重新向量化完成，导入 " + count + " 个文档块"
        );
    }

    /**
     * 删除指定文件及其向量数据
     * @param fileId 文件ID
     */
    @DeleteMapping("/file/{fileId}")
    public Map<String, Object> deleteFile(@PathVariable String fileId) {
        int deletedCount = fileService.deleteFile(fileId);
        return Map.of(
                "success", true,
                "deletedCount", deletedCount,
                "message", "已删除文件及 " + deletedCount + " 条向量记录"
        );
    }
}
