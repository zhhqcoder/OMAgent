package com.eastcom.omagent.tool;

import com.eastcom.omagent.rag.KnowledgeBaseService;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LogAnalysisTool {

    private final KnowledgeBaseService knowledgeBaseService;

    public LogAnalysisTool(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public record Request(
            String query,
            String module,
            String docType
    ) {
        public Request {
            // 允许null
        }

        /** 兼容只传query的调用 */
        public Request(String query) {
            this(query, null, null);
        }
    }

    public record Response(String sourceContent, String configContent, List<String> sources) {}

    public Response search(Request request) {
        // 同时搜索源码/日志知识库和配置知识库
        List<Document> sourceResults = knowledgeBaseService.searchSource(
                request.query(), 5, request.module(), request.docType()
        );
        List<Document> configResults = knowledgeBaseService.searchConfig(
                request.query(), 3, request.module(), request.docType()
        );

        String sourceContent = knowledgeBaseService.formatSearchResults(sourceResults);
        String configContent = knowledgeBaseService.formatSearchResults(configResults);

        List<String> sources = new ArrayList<>(sourceResults.stream()
                .map(doc -> doc.getMetadata().getOrDefault("source", "未知").toString())
                .collect(Collectors.toList()));
        sources.addAll(configResults.stream()
                .map(doc -> doc.getMetadata().getOrDefault("source", "未知").toString())
                .collect(Collectors.toList()));

        return new Response(sourceContent, configContent, sources.stream().distinct().collect(Collectors.toList()));
    }

    /**
     * 创建Spring AI ToolCallback，供Agent调用
     */
    public static FunctionToolCallback<Request, Response> createToolCallback(KnowledgeBaseService knowledgeBaseService) {
        LogAnalysisTool tool = new LogAnalysisTool(knowledgeBaseService);
        return FunctionToolCallback.<Request, Response>builder(
                        "analyze_log",
                        (Function<Request, Response>) tool::search
                )
                .description("分析OMS程序错误日志，从源码知识库和配置知识库中检索相关信息，" +
                        "帮助判断是程序Bug还是配置问题。当用户粘贴错误日志或上传日志文件时应使用此工具。" +
                        "可选参数：module（模块名，如alarm/bureau/configuration等）用于限定搜索范围；" +
                        "docType（文档类型，如source-code/faq等）用于限定文档类别。" +
                        "如果日志中明确涉及某个模块，应填写module参数以获得更精准的结果。")
                .inputType(Request.class)
                .build();
    }
}
