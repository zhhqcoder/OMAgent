package com.eastcom.omagent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DocumentEtlService {

    private static final Logger log = LoggerFactory.getLogger(DocumentEtlService.class);

    private final VectorStore configVectorStore;
    private final VectorStore sourceVectorStore;
    private final TokenTextSplitter textSplitter;
    private final MarkdownSectionSplitter markdownSplitter;
    private final ChatModel chatModel;

    /** LLM生成中文摘要的system prompt，要求空格分词输出 */
    private static final String SUMMARY_SYSTEM_PROMPT = """
            你是一个技术文档中文摘要生成器。根据给定的技术配置文档片段，输出中文摘要。
            关键要求：
            1. 提取文档中涉及的配置项名称（保留原始英文标识符）
            2. 用中文说明每个配置项的功能和用途
            3. 提及关键取值、默认值或格式要求（如有）
            4. 每个中文词语之间必须用空格分隔（这是最关键的要求）
            5. 不超过150字
            6. 只输出空格分词的摘要内容，不要输出其他解释
            7. 必须包含文档中出现的中文领域术语（如"局数据"、"告警"、"割接"等），这些术语是检索的关键锚点
            8. 如果文档描述的是某个模块的配置，必须包含该模块的中文名称和英文名称
            9. 中文分词粒度：保持有意义的中文词组完整，不要拆成单字。例如"软件包管理"应输出为"软件包 管理"而不是"软 件 包 管 理"，"局数据"应输出为"局数据"而不是"局 数 据"，"告警级别"应输出为"告警级别"而不是"告 警 级 别"
            示例输出：局数据 bureaudata ftp 模式 配置项 ftpMode 地址 账号 密码 端口 路径 日志记录 备份文件 logRecordBackupFilePath
            """;

    /** 支持的源码文件扩展名 */
    private static final Set<String> SOURCE_CODE_EXTENSIONS = Set.of(
            ".java", ".xml", ".yml", ".yaml", ".properties",
            ".sql", ".json", ".html", ".css", ".js", ".ts",
            ".sh", ".bat", ".py", ".go", ".c", ".cpp", ".h",
            ".pom"
    );

    /** 支持的文本文件扩展名 */
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".log", ".csv", ".md"
    );

    /** 支持的压缩包扩展名 */
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
            ".zip"
    );

    /** Embedding API 单次请求最大文本数（DashScope限制25） */
    private static final int EMBEDDING_BATCH_SIZE = 20;

    public DocumentEtlService(
            @Qualifier("configVectorStore") VectorStore configVectorStore,
            @Qualifier("sourceVectorStore") VectorStore sourceVectorStore,
            ChatModel chatModel) {
        this.configVectorStore = configVectorStore;
        this.sourceVectorStore = sourceVectorStore;
        this.chatModel = chatModel;
        this.textSplitter = new TokenTextSplitter(
                800,    // chunkSize
                200,    // overlap
                50,     // minChunkSizeChars
                10000,  // maxNumChunks
                true
        );
        this.markdownSplitter = new MarkdownSectionSplitter(
                3000,   // maxSectionChars - 超长章节最大字符数
                200,    // overlapChars - 二次切分重叠
                true    // skipToc - 跳过目录区域
        );
    }

    /**
     * 导入文件到配置知识库
     */
    public int importToConfigStore(File file, Map<String, Object> metadata) {
        List<Document> documents = loadAndSplit(file, metadata);
        documents = enhanceDocuments(documents);
        documents = generateChineseSummaries(documents);
        addInBatches(configVectorStore, documents);
        return documents.size();
    }

    /**
     * 导入文件到源码/日志知识库
     */
    public int importToSourceStore(File file, Map<String, Object> metadata) {
        List<Document> documents = loadAndSplit(file, metadata);
        documents = enhanceDocuments(documents);
        documents = generateChineseSummaries(documents);
        addInBatches(sourceVectorStore, documents);
        return documents.size();
    }

    /**
     * 导入文档列表到配置知识库
     */
    public int importDocumentsToConfigStore(List<Document> documents) {
        List<Document> splitDocs = textSplitter.apply(documents);
        splitDocs = enhanceDocuments(splitDocs);
        splitDocs = generateChineseSummaries(splitDocs);
        addInBatches(configVectorStore, splitDocs);
        return splitDocs.size();
    }

    /**
     * 导入文档列表到源码/日志知识库
     */
    public int importDocumentsToSourceStore(List<Document> documents) {
        List<Document> splitDocs = textSplitter.apply(documents);
        splitDocs = enhanceDocuments(splitDocs);
        splitDocs = generateChineseSummaries(splitDocs);
        addInBatches(sourceVectorStore, splitDocs);
        return splitDocs.size();
    }

    /**
     * 分批写入向量库，避免超出 Embedding API 单次请求数量限制
     */
    private void addInBatches(VectorStore vectorStore, List<Document> documents) {
        for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(i + EMBEDDING_BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
        }
    }

    /**
     * 对文档进行文本增强：将驼峰命名拆分为独立单词并小写化，追加到原文末尾。
     * 例如：logRecordBackupFilePath → 追加 "log record backup file path logrecordbackupfilepath"
     * 保留原文不修改，额外追加增强词以便向量检索时提高匹配率。
     */
    private List<Document> enhanceDocuments(List<Document> documents) {
        return documents.stream()
                .map(doc -> {
                    String text = doc.getText();
                    String enhanced = enhanceCamelCaseText(text);
                    if (!enhanced.equals(text)) {
                        return new Document(enhanced, doc.getMetadata());
                    }
                    return doc;
                })
                .toList();
    }

    /**
     * 增强文本：提取驼峰标识符，拆分为小写词组追加到末尾
     */
    static String enhanceCamelCaseText(String text) {
        // 匹配驼峰标识符：至少2个大写字母或大写字母开头的连续段（长度>=4，排除短词）
        java.util.Set<String> camelTokens = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[a-z][a-zA-Z0-9]{3,}|[A-Z][a-z]+[a-zA-Z0-9]*")
                .matcher(text);
        while (m.find()) {
            String token = m.group();
            // 只处理包含至少一个大写字母的（驼峰标识符）
            if (token.chars().anyMatch(Character::isUpperCase)) {
                camelTokens.add(token);
            }
        }
        if (camelTokens.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        sb.append("\n\n[关键词索引] ");
        for (String token : camelTokens) {
            // 拆分驼峰: logRecordBackupFilePath → log Record Backup File Path
            String split = token.replaceAll("([a-z])([A-Z])", "$1 $2")
                    .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
            // 小写化: log record backup file path
            String lower = split.toLowerCase();
            // 全小写原词: logrecordbackupfilepath
            String flat = token.toLowerCase();
            sb.append(lower).append(" ").append(flat).append(" ");
        }
        return sb.toString();
    }

    /**
     * 为每个文档chunk生成中文摘要并存入metadata的chinese_summary字段。
     * 摘要用空格分词格式输出，使RedisSearch的TEXT字段能按词匹配中文查询。
     * 例如: chunk包含logRecordBackupFilePath → metadata.chinese_summary = "日志 记录 备份 文件 路径 配置项 logRecordBackupFilePath"
     * 摘要不写入content，避免稀释原文的embedding质量。
     */
    private List<Document> generateChineseSummaries(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        int successCount = 0;
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String text = doc.getText();
            try {
                String summary = callLlmForSummary(text);
                if (summary != null && !summary.isBlank()) {
                    Map<String, Object> newMetadata = new HashMap<>(doc.getMetadata());
                    newMetadata.put("chinese_summary", summary);
                    result.add(new Document(doc.getId(), doc.getText(), newMetadata));
                    successCount++;
                    log.debug("chunk {}/{} 中文摘要: {}", i + 1, documents.size(),
                            summary.length() > 80 ? summary.substring(0, 80) + "..." : summary);
                } else {
                    result.add(doc);
                }
            } catch (Exception e) {
                log.warn("chunk {}/{} 生成中文摘要失败，跳过: {}", i + 1, documents.size(), e.getMessage());
                result.add(doc);
            }
        }
        log.info("中文摘要生成完成: {}/{} 成功", successCount, documents.size());
        return result;
    }

    /**
     * 调用LLM为单个chunk生成空格分词格式的中文摘要
     */
    private String callLlmForSummary(String chunkText) {
        String input = chunkText.length() > 1500 ? chunkText.substring(0, 1500) + "..." : chunkText;
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SUMMARY_SYSTEM_PROMPT),
                new UserMessage(input)
        ));
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();
    }

    /**
     * 根据文件类型自动选择加载方式
     */
    private List<Document> loadAndSplit(File file, Map<String, Object> extraMetadata) {
        String fileName = file.getName().toLowerCase();

        if (isArchive(fileName)) {
            return loadArchiveAndSplit(file, extraMetadata);
        } else if (fileName.endsWith(".md")) {
            // Markdown 文件使用标题语义分块
            return loadMarkdownAndSplit(file, extraMetadata);
        } else {
            return loadSingleFileAndSplit(file, extraMetadata);
        }
    }

    /**
     * 加载 Markdown 文件并按标题语义分块
     * 每个子章节（##/###）作为独立 chunk，保留完整表格和代码块
     */
    private List<Document> loadMarkdownAndSplit(File file, Map<String, Object> extraMetadata) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Map<String, Object> metadata = extraMetadata != null ? new HashMap<>(extraMetadata) : new HashMap<>();
            return markdownSplitter.split(content, metadata);
        } catch (IOException e) {
            throw new RuntimeException("读取Markdown文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载单个文本/源码文件并分块
     */
    private List<Document> loadSingleFileAndSplit(File file, Map<String, Object> extraMetadata) {
        TextReader textReader = new TextReader(new FileSystemResource(file));
        textReader.getCustomMetadata().putAll(extraMetadata != null ? extraMetadata : new HashMap<>());

        // 对POM文件提取依赖元数据
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".pom") || fileName.equals("pom.xml")) {
            extractPomMetadata(file.toPath(), textReader.getCustomMetadata());
        }

        List<Document> documents = textReader.get();
        return textSplitter.apply(documents);
    }

    /**
     * 加载压缩包中的源码文件并分块
     * 遍历zip内所有文件，只处理支持的源码/文本格式
     */
    private List<Document> loadArchiveAndSplit(File zipFile, Map<String, Object> extraMetadata) {
        List<Document> allDocuments = new ArrayList<>();
        Path tempDir;

        try {
            tempDir = Files.createTempDirectory("omagent-unzip-");
        } catch (IOException e) {
            throw new RuntimeException("创建临时目录失败: " + e.getMessage(), e);
        }

        try {
            unzipFile(zipFile, tempDir);

            // 遍历解压后的所有文件
            try (Stream<Path> paths = Files.walk(tempDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return isSourceCode(name) || isTextFile(name);
                        })
                        .forEach(path -> {
                            try {
                                Map<String, Object> fileMetadata = new HashMap<>(extraMetadata != null ? extraMetadata : new HashMap<>());
                                // 记录zip内的相对路径，便于溯源
                                String relativePath = tempDir.relativize(path).toString().replace('\\', '/');
                                fileMetadata.put("source", fileMetadata.getOrDefault("source", "") + "/" + relativePath);

                                // 提取包名和类名（针对Java文件）
                                if (path.toString().endsWith(".java")) {
                                    extractJavaMetadata(path, fileMetadata);
                                }
                                // 提取POM依赖信息
                                if (path.getFileName().toString().toLowerCase().endsWith(".pom")
                                        || path.getFileName().toString().equals("pom.xml")) {
                                    extractPomMetadata(path, fileMetadata);
                                }

                                TextReader textReader = new TextReader(new FileSystemResource(path.toFile()));
                                textReader.getCustomMetadata().putAll(fileMetadata);
                                List<Document> docs = textReader.get();
                                allDocuments.addAll(textSplitter.apply(docs));
                            } catch (Exception e) {
                                // 单文件失败不影响其他文件
                            }
                        });
            }
        } catch (IOException e) {
            throw new RuntimeException("解压文件失败: " + e.getMessage(), e);
        } finally {
            deleteRecursively(tempDir);
        }

        return allDocuments;
    }

    /**
     * 从Java源码文件中提取包名和类名，写入metadata
     */
    private void extractJavaMetadata(Path javaFile, Map<String, Object> metadata) {
        try {
            String content = Files.readString(javaFile, StandardCharsets.UTF_8);
            // 提取包名
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("package ")) {
                    String packageName = trimmed.substring(8).replace(";", "").trim();
                    metadata.put("package_name", packageName);
                    break;
                }
            }
            // 提取类名（从文件名）
            String fileName = javaFile.getFileName().toString();
            metadata.put("class_name", fileName.replace(".java", ""));
        } catch (Exception ignored) {
        }
    }

    /**
     * 从POM文件中提取依赖信息，写入metadata
     * 提取：groupId, artifactId, version（项目自身）和依赖列表
     */
    private void extractPomMetadata(Path pomFile, Map<String, Object> metadata) {
        try {
            String content = Files.readString(pomFile, StandardCharsets.UTF_8);

            // 提取项目自身的 GAV
            String groupId = extractXmlTag(content, "groupId");
            String artifactId = extractXmlTag(content, "artifactId");
            String version = extractXmlTag(content, "version");

            if (groupId != null) metadata.put("pom_groupId", groupId);
            if (artifactId != null) metadata.put("pom_artifactId", artifactId);
            if (version != null) metadata.put("pom_version", version);

            // 提取依赖列表（groupId:artifactId:version 格式，便于检索）
            List<String> dependencies = extractDependencies(content);
            if (!dependencies.isEmpty()) {
                metadata.put("pom_dependencies", String.join(",", dependencies));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 提取XML标签内容（取第一个匹配，跳过注释）
     */
    private String extractXmlTag(String content, String tagName) {
        // 尝试从<project>直接子元素提取（避免匹配到dependencies内的同名标签）
        // 先找 <project> 到第一个 <dependencies> 之间的内容
        String projectSection = content;
        int depsIndex = content.indexOf("<dependencies>");
        if (depsIndex > 0) {
            projectSection = content.substring(0, depsIndex);
        }

        // 按层级优先：直接子级 > 任意位置
        String[] patterns = {
                "<project>\\s*<" + tagName + ">([^<]+)</" + tagName + ">",
                "<" + tagName + ">([^<]+)</" + tagName + ">"
        };

        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(projectSection);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    /**
     * 提取所有依赖，格式为 groupId:artifactId:version
     */
    private List<String> extractDependencies(String content) {
        List<String> deps = new ArrayList<>();
        // 匹配 <dependency> 块
        java.util.regex.Matcher depMatcher = java.util.regex.Pattern.compile(
                "<dependency>(.*?)</dependency>", java.util.regex.Pattern.DOTALL
        ).matcher(content);

        while (depMatcher.find()) {
            String depBlock = depMatcher.group(1);
            String g = extractSimpleTag(depBlock, "groupId");
            String a = extractSimpleTag(depBlock, "artifactId");
            String v = extractSimpleTag(depBlock, "version");
            if (g != null && a != null) {
                deps.add(g + ":" + a + (v != null ? ":" + v : ""));
            }
        }
        return deps;
    }

    /**
     * 从XML片段中提取简单标签内容
     */
    private String extractSimpleTag(String content, String tagName) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<" + tagName + ">([^<]+)</" + tagName + ">"
        ).matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * 解压zip文件到目标目录
     */
    private void unzipFile(File zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile.toPath())), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName());

                // 安全检查：防止zip路径穿越
                if (!entryPath.normalize().startsWith(targetDir.normalize())) {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteRecursively(Path dir) {
        try {
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException ignored) {
        }
    }

    private boolean isArchive(String fileName) {
        return ARCHIVE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private boolean isSourceCode(String fileName) {
        return SOURCE_CODE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private boolean isTextFile(String fileName) {
        return TEXT_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    /**
     * 获取所有支持的文件扩展名（用于前端展示）
     */
    public static String getSupportedExtensions() {
        Set<String> all = new TreeSet<>();
        all.addAll(TEXT_EXTENSIONS);
        all.addAll(SOURCE_CODE_EXTENSIONS);
        all.addAll(ARCHIVE_EXTENSIONS);
        return String.join(", ", all);
    }
}
