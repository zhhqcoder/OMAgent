package com.eastcom.omagent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final VectorStore configVectorStore;
    private final VectorStore sourceVectorStore;
    private final ChatModel chatModel;

    @Value("${omagent.knowledge.similarity-threshold:0.1}")
    private double similarityThreshold;

    public KnowledgeBaseService(
            @Qualifier("configVectorStore") VectorStore configVectorStore,
            @Qualifier("sourceVectorStore") VectorStore sourceVectorStore,
            ChatModel chatModel) {
        this.configVectorStore = configVectorStore;
        this.sourceVectorStore = sourceVectorStore;
        this.chatModel = chatModel;
    }

    /**
     * 中英文翻译的系统提示词
     */
    private static final String TRANSLATE_SYSTEM_PROMPT = """
            你是OMS（运维管理系统/网元管理系统）领域的中文→英文术语翻译专家。
            用户会给出一段中文查询，你需要识别其中的技术/运维/通信领域相关术语，并翻译为OMS配置中常见的英文关键词。
            
            OMS领域常见术语对照（供参考，不限于这些）：
            - 局数据 → officeData, switchData, bureauData, officeConfig, switchConfig
            - 网元 → networkElement, ne, element
            - 告警 → alarm, alert, warning
            - 性能 → performance, pm, perf
            - 配置 → configuration, config, cfg
            - 日志 → log, logging, logRecord
            - 备份 → backup, bak
            - 同步 → sync, synchronize, synchronization
            - 版本 → version, ver, release
            - 升级 → upgrade, update
            - 告警级别 → alarmLevel, alarmSeverity, severity
            - 割接 → cutover, switchover
            - 倒换 → switchover, switchOver, failover
            - 主备 → activeStandby, masterSlave, primaryBackup
            - 信令 → signaling, signal, sig
            - 中继 → trunk, trunkGroup
            - 路由 → route, routing
            - 端口 → port
            - 单板 → board, card, module
            - 槽位 → slot
            
            规则：
            1. 对中文查询中的技术术语、运维术语、通信领域术语，都必须翻译
            2. 即使术语看起来像普通中文（如"局数据"），在OMS领域也是专业术语，必须翻译
            3. 每个术语提供多个常见的英文等价词（驼峰命名、空格分隔、缩写、同义词等）
            4. 严格按照JSON格式输出，不要添加任何其他文字
            5. 输出格式: {"translations": {"中文术语1": ["english1","english2","english3"], "中文术语2": ["en1","en2"]}}
            6. 如果查询中没有可翻译的技术术语，返回: {"translations": {}}
            """;

    /**
     * 在配置知识库中检索（无过滤）
     */
    public List<Document> searchConfig(String query, int topK) {
        return searchConfig(query, topK, null, null);
    }

    /**
     * 在配置知识库中检索（支持按module和doc_type过滤）
     * 自动对查询中的驼峰标识符进行拆分+小写增强
     * 向量搜索无结果时自动回退到Redis全文检索
     */
    public List<Document> searchConfig(String query, int topK, String module, String docType) {
        String enhancedQuery = enhanceQuery(query);
        log.debug("查询增强: '{}' → '{}'", query, enhancedQuery);

        List<Document> results = doVectorSearch(configVectorStore, enhancedQuery, query, topK, module, docType);

        // 向量搜索仍无结果，回退到Redis全文检索
        if (results.isEmpty()) {
            log.debug("向量搜索无结果，回退Redis全文检索");
            results = fullTextSearch(configVectorStore, "config", query, topK, module, docType);
        }

        return results;
    }

    /**
     * 执行向量搜索（带异常回退）。
     * 先用增强查询搜索，失败或无结果时回退到原始查询。
     * Spring AI的RedisVectorStore在KNN查询中可能因过滤器语法问题抛异常，
     * 此方法捕获异常后返回空列表，由调用方回退到全文检索。
     */
    private List<Document> doVectorSearch(VectorStore vectorStore, String enhancedQuery, String originalQuery,
                                           int topK, String module, String docType) {
        // 1. 用增强查询检索
        List<Document> results = safeVectorSearch(vectorStore, enhancedQuery, topK, module, docType);

        // 2. 增强查询无结果，回退原始查询
        if (results.isEmpty() && !enhancedQuery.equals(originalQuery)) {
            log.debug("增强查询无结果，回退原始查询");
            results = safeVectorSearch(vectorStore, originalQuery, topK, module, docType);
        }

        return results;
    }

    /**
     * 安全的向量搜索，不传过滤器到Spring AI（避免RedisFilterExpressionConverter对TAG字段
     * 生成TEXT语法@field:(value)导致KNN查询语法错误），改为搜索后手动过滤结果。
     * 适当增大topK以提高过滤后仍有足够结果。
     */
    private List<Document> safeVectorSearch(VectorStore vectorStore, String query, int topK,
                                             String module, String docType) {
        try {
            // 不传过滤器给Spring AI，避免TAG/TEXT语法冲突导致KNN查询报错
            // 增大搜索数量以补偿过滤后的损失
            int searchTopK = topK;
            if ((module != null && !module.isEmpty()) || (docType != null && !docType.isEmpty())) {
                searchTopK = Math.max(topK * 3, 15);
            }

            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(searchTopK)
                    .similarityThreshold(similarityThreshold);
            List<Document> results = vectorStore.similaritySearch(builder.build());

            // 手动过滤：按module和doc_type筛选
            // 注意：当metadata中没有对应字段时，视为"可能匹配"予以保留（而非排除），
            // 因为数据可能在上传时未填写该字段，强制排除会导致召回率为0
            if ((module != null && !module.isEmpty()) || (docType != null && !docType.isEmpty())) {
                results = results.stream()
                        .filter(doc -> {
                            Map<String, Object> meta = doc.getMetadata();
                            boolean match = true;
                            if (module != null && !module.isEmpty()) {
                                Object m = meta.get("module");
                                // 字段不存在时视为匹配（保留），存在时必须精确匹配
                                if (m != null) {
                                    match = module.equals(m.toString());
                                }
                            }
                            if (match && docType != null && !docType.isEmpty()) {
                                Object dt = meta.get("doc_type");
                                // 字段不存在时视为匹配（保留），存在时必须精确匹配
                                if (dt != null) {
                                    match = docType.equals(dt.toString());
                                }
                            }
                            return match;
                        })
                        .limit(topK)
                        .collect(Collectors.toList());
                log.debug("向量搜索手动过滤: {} → {} 条结果（module={}, docType={})",
                        searchTopK, results.size(), module, docType);
            }

            return results;
        } catch (Exception e) {
            log.warn("向量搜索失败（query='{}', module={}, docType={}），将回退到全文检索: {}",
                    query, module, docType, e.getMessage());
            return Collections.emptyList();
        }
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
     * 向量搜索无结果时自动回退到Redis全文检索
     */
    public List<Document> searchSource(String query, int topK, String module, String docType) {
        String enhancedQuery = enhanceQuery(query);
        log.debug("查询增强: '{}' → '{}'", query, enhancedQuery);

        List<Document> results = doVectorSearch(sourceVectorStore, enhancedQuery, query, topK, module, docType);

        // 向量搜索仍无结果，回退到Redis全文检索
        if (results.isEmpty()) {
            log.debug("向量搜索无结果，回退Redis全文检索");
            results = fullTextSearch(sourceVectorStore, "source", query, topK, module, docType);
        }

        return results;
    }

    /**
     * 清空配置知识库
     * @return 删除的文档数量
     */
    public int clearConfigStore() {
        return clearVectorStore(configVectorStore, "config");
    }

    /**
     * 清空源码/日志知识库
     * @return 删除的文档数量
     */
    public int clearSourceStore() {
        return clearVectorStore(sourceVectorStore, "source");
    }

    /**
     * 删除指定文件的所有向量数据
     * @param fileId 文件ID（对应metadata中的file_id字段）
     * @param knowledgeType "config" 或 "source"
     * @return 删除的向量文档数量
     */
    public int deleteDocumentVectors(String fileId, String knowledgeType) {
        VectorStore vectorStore = "source".equals(knowledgeType) ? sourceVectorStore : configVectorStore;
        String storeName = "source".equals(knowledgeType) ? "source" : "config";

        if (!(vectorStore instanceof RedisVectorStore redisStore)) {
            log.warn("知识库 {} 不是RedisVectorStore，无法按文件删除", storeName);
            return 0;
        }

        try {
            var jedis = redisStore.getJedis();
            String indexName = storeName.equals("config") ? "oms-config-index" : "oms-source-index";

            // 使用FT.SEARCH按file_id查找文档
            var searchResult = jedis.ftSearch(indexName, "@file_id:{" + fileId + "}");
            int deletedCount = 0;

            if (searchResult.getTotalResults() > 0) {
                for (var entry : searchResult.getDocuments()) {
                    jedis.del(entry.getId());
                    deletedCount++;
                }
            }

            log.info("从知识库 {} 中删除文件 {} 的 {} 条向量记录", storeName, fileId, deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("从知识库 {} 中删除文件 {} 的向量数据失败", storeName, fileId, e);
            throw new RuntimeException("删除文件向量数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清空指定向量库：删除所有key并重建索引
     */
    private int clearVectorStore(VectorStore vectorStore, String storeName) {
        if (!(vectorStore instanceof RedisVectorStore redisStore)) {
            log.warn("知识库 {} 不是RedisVectorStore，无法清空", storeName);
            return 0;
        }
        try {
            String prefix = storeName.equals("config") ? "oms-config" : "oms-source";
            var jedis = redisStore.getJedis();
            // 扫描并删除所有匹配前缀的key
            var cursor = "0";
            int deletedCount = 0;
            do {
                var result = jedis.scan(cursor,
                    new redis.clients.jedis.params.ScanParams().match(prefix + "*").count(500));
                cursor = result.getCursor();
                var keys = result.getResult();
                if (!keys.isEmpty()) {
                    jedis.del(keys.toArray(new String[0]));
                    deletedCount += keys.size();
                }
            } while (!cursor.equals("0"));

            // 重建索引
            String indexName = storeName.equals("config") ? "oms-config-index" : "oms-source-index";
            try {
                jedis.ftDropIndex(indexName);
                log.info("已删除索引: {}", indexName);
            } catch (Exception e) {
                log.warn("删除索引 {} 失败（可能不存在）: {}", indexName, e.getMessage());
            }
            // 重建索引（Spring AI会在下次写入时自动创建，这里手动触发）
            redisStore.afterPropertiesSet();
            log.info("知识库 {} 已清空，删除 {} 条记录", storeName, deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("清空知识库 {} 失败", storeName, e);
            throw new RuntimeException("清空知识库失败: " + e.getMessage(), e);
        }
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
     * Redis全文检索回退：当向量搜索无结果时，使用FT.SEARCH进行关键词匹配。
     * 对驼峰标识符进行拆分+小写+原词组合搜索，提高技术配置项的命中率。
     * module/docType过滤由doFtSearch内部搜索后手动过滤（数据中可能没有该字段），
     * 过滤后无结果时自动去掉过滤器重试（Agent可能猜错module名）。
     */
    private List<Document> fullTextSearch(VectorStore vectorStore, String storeName,
                                          String query, int topK, String module, String docType) {
        if (!(vectorStore instanceof RedisVectorStore redisStore)) {
            log.warn("知识库 {} 不是RedisVectorStore，无法全文检索", storeName);
            return Collections.emptyList();
        }

        try {
            String indexName = storeName.equals("config") ? "oms-config-index" : "oms-source-index";
            var jedis = redisStore.getJedis();

            // 构建全文检索查询：同时搜索原词和拆分后的小写词
            String ftQuery = buildFullTextQuery(query);

            // 搜索（内部已做手动过滤，数据中无对应字段时视为匹配）
            List<Document> results = doFtSearch(jedis, indexName, ftQuery, topK, module, docType);

            // 带过滤器搜不到结果时，去掉过滤器重试（Agent可能猜错module名）
            if (results.isEmpty() && (module != null && !module.isEmpty() || docType != null && !docType.isEmpty())) {
                log.debug("带过滤器的全文检索无结果，去掉过滤器重试");
                results = doFtSearch(jedis, indexName, ftQuery, topK, null, null);
            }

            // content全文检索仍无结果，尝试通过chinese_summary metadata字段搜索中文摘要
            if (results.isEmpty()) {
                log.debug("content全文检索无结果，尝试chinese_summary中文摘要检索");
                results = searchByChineseSummary(vectorStore, storeName, query, topK);
            }

            return results;
        } catch (Exception e) {
            log.warn("Redis全文检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 执行Redis FT.SEARCH并转换为Document列表。
     * module/docType过滤器不放入FT.SEARCH查询（避免TAG字段连字符语法错误和数据中无该字段导致0结果），
     * 改为搜索后手动过滤，与向量搜索的过滤逻辑保持一致。
     */
    private List<Document> doFtSearch(redis.clients.jedis.JedisPooled jedis, String indexName,
                                       String ftQuery, int topK, String module, String docType) {
        boolean hasFilter = (module != null && !module.isEmpty()) || (docType != null && !docType.isEmpty());
        // 有过滤器时增大搜索数量，补偿手动过滤后的损失
        int searchLimit = hasFilter ? Math.max(topK * 3, 15) : topK;

        log.debug("Redis全文检索: index={}, query={}", indexName, ftQuery);
        var searchResult = jedis.ftSearch(indexName, ftQuery,
                new redis.clients.jedis.search.FTSearchParams().limit(0, searchLimit));

        List<Document> results = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        for (var doc : searchResult.getDocuments()) {
            Map<String, Object> metadata = new HashMap<>();
            String content = "";

            // Jedis对JSON索引的FT.SEARCH返回单个"$"字段，值是整个JSON字符串
            // 需要解析该JSON来提取content、source、module等字段
            for (var entry : doc.getProperties()) {
                String key = entry.getKey();
                if ("$".equals(key) && entry.getValue() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> jsonMap = objectMapper.readValue(
                                entry.getValue().toString(), Map.class);
                        for (var jsonEntry : jsonMap.entrySet()) {
                            String jsonKey = jsonEntry.getKey();
                            if ("content".equals(jsonKey)) {
                                content = jsonEntry.getValue() != null ? jsonEntry.getValue().toString() : "";
                            } else if (!"embedding".equals(jsonKey)) {
                                metadata.put(jsonKey, jsonEntry.getValue());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析FT.SEARCH返回的JSON失败: {}", e.getMessage());
                    }
                }
            }

            log.debug("FT.SEARCH解析结果: id={}, content长度={}, metadata keys={}",
                    doc.getId(), content.length(), metadata.keySet());

            String id = doc.getId();
            results.add(new Document(id, content, metadata));
        }

        // 搜索后手动过滤module和doc_type（与向量搜索保持一致）
        // 当metadata中没有对应字段时，视为"可能匹配"予以保留
        if (hasFilter) {
            results = results.stream()
                    .filter(doc -> {
                        Map<String, Object> meta = doc.getMetadata();
                        boolean match = true;
                        if (module != null && !module.isEmpty()) {
                            Object m = meta.get("module");
                            if (m != null) {
                                match = module.equals(m.toString());
                            }
                        }
                        if (match && docType != null && !docType.isEmpty()) {
                            Object dt = meta.get("doc_type");
                            if (dt != null) {
                                match = docType.equals(dt.toString());
                            }
                        }
                        return match;
                    })
                    .limit(topK)
                    .collect(Collectors.toList());
            log.debug("FT.SEARCH手动过滤: {} → {} 条结果（module={}, docType={})",
                    searchLimit, results.size(), module, docType);
        }

        log.info("Redis全文检索返回 {} 条结果（查询: {}）", results.size(), ftQuery);
        return results;
    }

    /**
     * 构建Redis全文检索查询字符串（仅content字段搜索）。
     * 包括：驼峰拆分 + 英文前缀匹配 + AI中文术语→英文翻译。
     * chinese_summary字段的搜索由 searchByChineseSummary() 单独处理，
     * 避免混在同一个查询中导致RedisSearch @field:中文 语法兼容问题。
     */
    private String buildFullTextQuery(String query) {
        Set<String> contentTerms = new LinkedHashSet<>();
        contentTerms.add(query);

        // 1. 提取驼峰标识符并拆分+前缀匹配
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[a-z][a-zA-Z0-9]{3,}|[A-Z][a-z]+[a-zA-Z0-9]*")
                .matcher(query);
        while (m.find()) {
            String token = m.group();
            if (token.chars().anyMatch(Character::isUpperCase)) {
                String split = token.replaceAll("([a-z])([A-Z])", "$1 $2")
                        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
                contentTerms.add(split.toLowerCase());
                contentTerms.add(token.toLowerCase());

                for (String word : split.toLowerCase().split("\\s+")) {
                    if (word.length() >= 3) {
                        contentTerms.add(word + "*");
                    }
                }
            }
        }

        // 2. 独立英文词前缀匹配
        for (String word : query.split("[\\s，。、；：！？]+")) {
            if (word.matches("[a-zA-Z]{3,}")) {
                contentTerms.add(word.toLowerCase() + "*");
            }
        }

        // 3. AI中文术语→英文关键词翻译
        List<String> englishTerms = translateChineseTerms(query);
        for (String enTerm : englishTerms) {
            contentTerms.add(enTerm);
            for (String w : enTerm.split("[\\s\\-]+")) {
                if (w.length() >= 3) {
                    contentTerms.add(w + "*");
                }
            }
        }

        return contentTerms.stream()
                .map(term -> "(" + term + ")")
                .collect(Collectors.joining("|"));
    }

    /**
     * 通过chinese_summary metadata字段搜索中文摘要。
     * chinese_summary存储空格分词的中文摘要，每个中文词是独立token。
     * 使用RedisSearch的@field:term语法做精确匹配。
     * 同时对中文词做2-gram拆分搜索，提高召回率（防止"软件包"被拆成"软 件 包"导致匹配不到）。
     * 单独调用，避免与content搜索混在同一个查询中导致语法错误。
     */
    private List<Document> searchByChineseSummary(VectorStore vectorStore, String storeName,
                                                   String query, int topK) {
        if (!(vectorStore instanceof RedisVectorStore redisStore)) {
            return Collections.emptyList();
        }

        Set<String> cnTerms = new LinkedHashSet<>();
        java.util.regex.Matcher cnMatcher = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{2,}")
                .matcher(query);
        while (cnMatcher.find()) {
            String cnWord = cnMatcher.group();
            cnTerms.add(cnWord);
            // 对长中文词做2-gram拆分，提高召回率
            // 例如"软件包管理" → "软件","件包","包管","管理"
            if (cnWord.length() >= 3) {
                for (int i = 0; i <= cnWord.length() - 2; i++) {
                    cnTerms.add(cnWord.substring(i, i + 2));
                }
            }
        }
        if (cnTerms.isEmpty()) {
            return Collections.emptyList();
        }

        String indexName = storeName.equals("config") ? "oms-config-index" : "oms-source-index";
        try {
            var jedis = redisStore.getJedis();
            // 对每个中文词/子词在chinese_summary字段中搜索，用OR连接
            String ftQuery = cnTerms.stream()
                    .map(cnWord -> "@chinese_summary:" + cnWord)
                    .collect(Collectors.joining("|"));
            log.debug("中文摘要检索: index={}, query={}", indexName, ftQuery);

            var searchResult = jedis.ftSearch(indexName, ftQuery,
                    new redis.clients.jedis.search.FTSearchParams().limit(0, topK));

            List<Document> results = new ArrayList<>();
            ObjectMapper objectMapper = new ObjectMapper();
            for (var doc : searchResult.getDocuments()) {
                Map<String, Object> metadata = new HashMap<>();
                String content = "";
                for (var entry : doc.getProperties()) {
                    String key = entry.getKey();
                    if ("$".equals(key) && entry.getValue() != null) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> jsonMap = objectMapper.readValue(
                                    entry.getValue().toString(), Map.class);
                            for (var jsonEntry : jsonMap.entrySet()) {
                                String jsonKey = jsonEntry.getKey();
                                if ("content".equals(jsonKey)) {
                                    content = jsonEntry.getValue() != null ? jsonEntry.getValue().toString() : "";
                                } else if (!"embedding".equals(jsonKey)) {
                                    metadata.put(jsonKey, jsonEntry.getValue());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("解析中文摘要检索结果失败: {}", e.getMessage());
                        }
                    }
                }
                results.add(new Document(doc.getId(), content, metadata));
            }
            log.info("中文摘要检索返回 {} 条结果", results.size());
            return results;
        } catch (Exception e) {
            log.warn("中文摘要检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 转义 RediSearch tag 查询值中的特殊字符。
     * 连字符、逗号等在 tag 值中需要用反斜杠转义。
     */
    private String escapeTagValue(String value) {
        return value.replace("-", "\\-")
                    .replace(",", "\\,")
                    .replace(":", "\\:");
    }

    /**
     * 查询增强：将驼峰标识符拆分为小写词组，并通过AI将中文术语翻译为英文关键词。
     * 例如: "日志备份的配置" → "日志备份的配置 log backup logRecordBackup config ..."
     */
    private String enhanceQuery(String query) {
        StringBuilder sb = new StringBuilder(query);

        // 1. 驼峰标识符拆分
        java.util.Set<String> camelTokens = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[a-z][a-zA-Z0-9]{3,}|[A-Z][a-z]+[a-zA-Z0-9]*")
                .matcher(query);
        while (m.find()) {
            String token = m.group();
            if (token.chars().anyMatch(Character::isUpperCase)) {
                camelTokens.add(token);
            }
        }
        for (String token : camelTokens) {
            String split = token.replaceAll("([a-z])([A-Z])", "$1 $2")
                    .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
            sb.append(" ").append(split.toLowerCase()).append(" ").append(token.toLowerCase());
        }

        // 2. AI中文术语→英文翻译
        List<String> englishTerms = translateChineseTerms(query);
        for (String enTerm : englishTerms) {
            sb.append(" ").append(enTerm);
        }

        return sb.toString();
    }

    /**
     * 使用AI将中文查询中的技术术语翻译为英文关键词。
     * 返回去重后的英文术语列表。
     */
    private List<String> translateChineseTerms(String query) {
        // 如果查询中没有中文字符，跳过翻译
        if (!query.matches(".*[\\u4e00-\\u9fa5].*")) {
            return Collections.emptyList();
        }

        try {
            String userMsg = "请翻译以下中文查询中的技术术语：\n" + query;
            var response = chatModel.call(new Prompt(
                    List.of(
                            new org.springframework.ai.chat.messages.SystemMessage(TRANSLATE_SYSTEM_PROMPT),
                            new org.springframework.ai.chat.messages.UserMessage(userMsg)
                    )
            ));
            String content = response.getResult().getOutput().getText().trim();
            log.debug("AI翻译原始返回: {}", content);

            // 解析JSON响应
            return parseTranslationResponse(content);
        } catch (Exception e) {
            log.warn("AI中文术语翻译失败，跳过翻译: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析AI翻译的JSON响应，提取所有英文术语（去重）。
     */
    private List<String> parseTranslationResponse(String jsonContent) {
        Set<String> terms = new LinkedHashSet<>();
        try {
            // 提取JSON部分（可能被markdown代码块包裹）
            String json = jsonContent;
            if (json.contains("```")) {
                int start = json.indexOf("{");
                int end = json.lastIndexOf("}") + 1;
                if (start >= 0 && end > start) {
                    json = json.substring(start, end);
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = mapper.readValue(json, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, List<String>> translations = (Map<String, List<String>>) root.get("translations");
            if (translations != null) {
                for (List<String> enTerms : translations.values()) {
                    terms.addAll(enTerms);
                    // 对含空格/连字符的英文词，拆分后也加入
                    for (String enTerm : enTerms) {
                        for (String w : enTerm.split("[\\s\\-]+")) {
                            if (w.length() >= 2) {
                                terms.add(w);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析AI翻译JSON失败: {}", e.getMessage());
        }
        return new ArrayList<>(terms);
    }
}
