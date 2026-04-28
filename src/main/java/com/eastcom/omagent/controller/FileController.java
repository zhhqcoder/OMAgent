package com.eastcom.omagent.controller;

import com.eastcom.omagent.entity.UploadFile;
import com.eastcom.omagent.service.FileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 上传文件（TXT日志或截图）
     */
    @PostMapping("/upload")
    public UploadFile uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "default") String userId,
            @RequestParam(required = false) String knowledgeType) throws IOException {
        return fileService.uploadFile(file, userId, knowledgeType);
    }

    /**
     * 获取用户上传文件列表
     */
    @GetMapping
    public List<UploadFile> getUserFiles(@RequestParam(defaultValue = "default") String userId) {
        return fileService.getUserFiles(userId);
    }

    /**
     * 读取文件内容
     */
    @GetMapping("/{fileId}/content")
    public String readFileContent(@PathVariable String fileId) {
        String content = fileService.readFileContent(fileId);
        if (content == null) {
            return "{\"error\": \"文件不存在或读取失败\"}";
        }
        return content;
    }
}
