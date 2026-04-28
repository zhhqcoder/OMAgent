package com.eastcom.omagent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final VectorStore configVectorStore;
    private final VectorStore sourceVectorStore;

    @Value("${omagent.knowledge.similarity-threshold:0.1}")
    private double similarityThreshold;

    public KnowledgeBaseService(
            @Qualifier("configVectorStore") VectorStore configVectorStore,
            @Qualifier("sourceVectorStore") VectorStore sourceVectorStore) {
        this.configVectorStore = configVectorStore;
        this.sourceVectorStore = sourceVectorStore;
    }

    /**
     * 在配置知识库中检索（无过滤）
     */
    public List<Document> searchConfig(String query, int topK) {
        return searchConfig(query, topK, null, null);
    }

    /**
     * 在配置知识库中检索（支持按module和doc_type过滤）
     * 自动对查询中的驼峰标识符进行拆分+小写增强
     */
    public List<Document> searchConfig(String query, int topK, String module, String docType) {
        String enhancedQuery = enhanceQuery(query);
        log.debug("查询增强: '{}' → '{}'", query, enhancedQuery);

        // 用增强查询检索
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(enhancedQuery)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        applyFilters(builder, module, docType);

        List<Document> results = configVectorStore.similaritySearch(builder.build());

        // 如果增强查询无结果，尝试用原始查询
        if (results.isEmpty() && !enhancedQuery.equals(query)) {
            log.debug("增强查询无结果，回退原始查询");
            SearchRequest.Builder fallback = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold);
            applyFilters(fallback, module, docType);
            results = configVectorStore.similaritySearch(fallback.build());
        }

        return results;
    }

    /**
     * 在源码/日志知识库中检索（无过滤）
     */
    public List<Document> searchSource(String query, int topK) {
        return searchSource(query, topK, null, null);
    }

    /**
     * 在源码/日志知识库中检索（支持按module和doc_type过滤）
     * 自动对查询中的驼峰标识符进行拆分+小写增强
     */
    public List<Document> searchSource(String query, int topK, String module, String docType) {
        String enhancedQuery = enhanceQuery(query);
        log.debug("查询增强: '{}' → '{}'", query, enhancedQuery);

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(enhancedQuery)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        applyFilters(builder, module, docType);

        List<Document> results = sourceVectorStore.similaritySearch(builder.build());

        if (results.isEmpty() && !enhancedQuery.equals(query)) {
            log.debug("增强查询无结果，回退原始查询");
            SearchRequest.Builder fallback = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold);
            applyFilters(fallback, module, docType);
            results = sourceVectorStore.similaritySearch(fallback.build());
        }

        return results;
    }

    /**
     * 同时在两个知识库中检索，合并结果
     */
    public Map<String, List<Document>> searchAll(String query, int topK) {
        Map<String, List<Document>> results = new HashMap<>();
        results.put("config", searchConfig(query, topK));
        results.put("source", searchSource(query, topK));
        return results;
    }

    /**
     * 将检索结果格式化为上下文字符串
     */
    public String formatSearchResults(List<Document> docs) {
        return docs.stream()
                .map(doc -> {
                    Map<String, Object> metadata = doc.getMetadata();
                    String source = metadata.getOrDefault("source", "未知来源").toString();
                    String module = metadata.getOrDefault("module", "").toString();
                    String docType = metadata.getOrDefault("doc_type", "").toString();

                    StringBuilder header = new StringBuilder("【来源: ").append(source);
                    if (!module.isEmpty()) header.append(" | 模块: ").append(module);
                    if (!docType.isEmpty()) header.append(" | 类型: ").append(docType);
                    header.append("】");

                    return header + "\n" + doc.getText();
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 构建过滤表达式：module == 'xxx' AND doc_type == 'yyy'
     */
    private void applyFilters(SearchRequest.Builder builder, String module, String docType) {
        if ((module == null || module.isEmpty()) && (docType == null || docType.isEmpty())) {
            return;
        }

        FilterExpressionBuilder feb = new FilterExpressionBuilder();
        if (module != null && !module.isEmpty() && docType != null && !docType.isEmpty()) {
            builder.filterExpression(
                    feb.and(
                            feb.eq("module", module),
                            feb.eq("doc_type", docType)
                    ).build()
            );
        } else if (module != null && !module.isEmpty()) {
            builder.filterExpression(feb.eq("module", module).build());
        } else {
            builder.filterExpression(feb.eq("doc_type", docType).build());
        }
    }

    /**
     * 查询增强：将驼峰标识符拆分为小写词组，提高向量检索匹配率。
     * 例如: "logRecordBackupFilePath配置" → "logRecordBackupFilePath配置 log record backup file path logrecordbackupfilepath"
     */
    private String enhanceQuery(String query) {
        java.util.Set<String> camelTokens = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[a-z][a-zA-Z0-9]{3,}|[A-Z][a-z]+[a-zA-Z0-9]*")
                .matcher(query);
        while (m.find()) {
            String token = m.group();
            if (token.chars().anyMatch(Character::isUpperCase)) {
                camelTokens.add(token);
            }
        }
        if (camelTokens.isEmpty()) {
            return query;
        }
        StringBuilder sb = new StringBuilder(query);
        for (String token : camelTokens) {
            String split = token.replaceAll("([a-z])([A-Z])", "$1 $2")
                    .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
            String lower = split.toLowerCase();
            String flat = token.toLowerCase();
            sb.append(" ").append(lower).append(" ").append(flat);
        }
        return sb.toString();
    }
}
