package com.eastcom.omagent.tool;

import com.eastcom.omagent.rag.KnowledgeBaseService;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConfigSearchTool {

    private final KnowledgeBaseService knowledgeBaseService;

    public ConfigSearchTool(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public record Request(
            String query,
            String module,
            String docType
    ) {
        public Request {
            // 允许null，提供无参构造兼容
        }

        /** 兼容只传query的调用 */
        public Request(String query) {
            this(query, null, null);
        }
    }

    public record Response(String content, List<String> sources) {}

    public Response search(Request request) {
        List<Document> results = knowledgeBaseService.searchConfig(
                request.query(), 5, request.module(), request.docType()
        );
        String content = knowledgeBaseService.formatSearchResults(results);
        List<String> sources = results.stream()
                .map(doc -> doc.getMetadata().getOrDefault("source", "未知").toString())
                .distinct()
                .collect(Collectors.toList());
        return new Response(content, sources);
    }

    /**
     * 创建Spring AI ToolCallback，供Agent调用
     */
    public static FunctionToolCallback<Request, Response> createToolCallback(KnowledgeBaseService knowledgeBaseService) {
        ConfigSearchTool tool = new ConfigSearchTool(knowledgeBaseService);
        return FunctionToolCallback.<Request, Response>builder(
                        "search_config",
                        (Function<Request, Response>) tool::search
                )
                .description("搜索OMS配置知识库，查找配置项的含义、取值范围、依赖关系等信息。" +
                        "当用户询问配置相关问题时应使用此工具。" +
                        "可选参数：module（模块名，如alarm/bureau/configuration等）用于限定搜索范围；" +
                        "docType（文档类型，如config-guide/faq等）用于限定文档类别。" +
                        "如果用户明确提到了某个模块，应填写module参数以获得更精准的结果。")
                .inputType(Request.class)
                .build();
    }
}
