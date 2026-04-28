package com.eastcom.omagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DocumentEtlService {

    private final VectorStore configVectorStore;
    private final VectorStore sourceVectorStore;
    private final TokenTextSplitter textSplitter;

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

    /** 支持的文档文件扩展名（需要专用解析器） */
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            ".doc", ".docx"
    );

    /** 支持的压缩包扩展名 */
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
            ".zip"
    );

    public DocumentEtlService(
            @Qualifier("configVectorStore") VectorStore configVectorStore,
            @Qualifier("sourceVectorStore") VectorStore sourceVectorStore) {
        this.configVectorStore = configVectorStore;
        this.sourceVectorStore = sourceVectorStore;
        this.textSplitter = new TokenTextSplitter(
                800,    // chunkSize
                200,    // overlap
                50,     // minChunkSizeChars
                10000,  // maxNumChunks
                true
        );
    }

    /**
     * 导入文件到配置知识库
     */
    public int importToConfigStore(File file, Map<String, Object> metadata) {
        List<Document> documents = loadAndSplit(file, metadata);
        documents = enhanceDocuments(documents);
        configVectorStore.add(documents);
        return documents.size();
    }

    /**
     * 导入文件到源码/日志知识库
     */
    public int importToSourceStore(File file, Map<String, Object> metadata) {
        List<Document> documents = loadAndSplit(file, metadata);
        documents = enhanceDocuments(documents);
        sourceVectorStore.add(documents);
        return documents.size();
    }

    /**
     * 导入文档列表到配置知识库
     */
    public int importDocumentsToConfigStore(List<Document> documents) {
        List<Document> splitDocs = textSplitter.apply(documents);
        splitDocs = enhanceDocuments(splitDocs);
        configVectorStore.add(splitDocs);
        return splitDocs.size();
    }

    /**
     * 导入文档列表到源码/日志知识库
     */
    public int importDocumentsToSourceStore(List<Document> documents) {
        List<Document> splitDocs = textSplitter.apply(documents);
        splitDocs = enhanceDocuments(splitDocs);
        sourceVectorStore.add(splitDocs);
        return splitDocs.size();
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
     * 根据文件类型自动选择加载方式
     */
    private List<Document> loadAndSplit(File file, Map<String, Object> extraMetadata) {
        String fileName = file.getName().toLowerCase();

        if (isArchive(fileName)) {
            return loadArchiveAndSplit(file, extraMetadata);
        } else if (isDocument(fileName)) {
            return loadDocxAndSplit(file, extraMetadata);
        } else if (isSourceCode(fileName) || isTextFile(fileName)) {
            return loadSingleFileAndSplit(file, extraMetadata);
        } else {
            // 兜底：尝试作为文本文件读取
            return loadSingleFileAndSplit(file, extraMetadata);
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
                            return isSourceCode(name) || isTextFile(name) || isDocument(name);
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

                                List<Document> docs;
                                if (isDocument(path.getFileName().toString().toLowerCase())) {
                                    // DOCX文件需要专用解析
                                    docs = loadDocxFromPath(path, fileMetadata);
                                } else {
                                    TextReader textReader = new TextReader(new FileSystemResource(path.toFile()));
                                    textReader.getCustomMetadata().putAll(fileMetadata);
                                    docs = textReader.get();
                                }
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

    private boolean isDocument(String fileName) {
        return DOCUMENT_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    /**
     * 加载DOCX文件并分块
     * 使用Apache POI解析Word文档，提取段落文本
     */
    /**
     * 加载Word文档并分块（支持.doc和.docx）
     */
    private List<Document> loadDocxAndSplit(File file, Map<String, Object> extraMetadata) {
        String content = extractWordText(file);
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> metadata = new HashMap<>(extraMetadata != null ? extraMetadata : new HashMap<>());
        Document document = new Document(content, metadata);
        return textSplitter.apply(List.of(document));
    }

    /**
     * 从Path加载Word文档（用于zip内的doc/docx文件）
     */
    private List<Document> loadDocxFromPath(Path path, Map<String, Object> extraMetadata) {
        String content = extractWordText(path.toFile());
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> metadata = new HashMap<>(extraMetadata != null ? extraMetadata : new HashMap<>());
        Document document = new Document(content, metadata);
        return List.of(document);
    }

    /**
     * 提取Word文档文本，自动识别.doc(HWPF)和.docx(XWPF)格式
     * 同时提取段落文本和表格内容，确保表格中的数据不会丢失
     */
    private String extractWordText(File file) {
        String fileName = file.getName().toLowerCase();
        try {
            if (fileName.endsWith(".docx")) {
                // .docx格式 - XWPFDocument，提取段落+表格
                try (FileInputStream fis = new FileInputStream(file);
                     XWPFDocument doc = new XWPFDocument(fis)) {
                    StringBuilder sb = new StringBuilder();
                    // 提取段落
                    for (XWPFParagraph paragraph : doc.getParagraphs()) {
                        String text = paragraph.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            sb.append(text).append("\n");
                        }
                    }
                    // 提取表格
                    for (XWPFTable table : doc.getTables()) {
                        for (XWPFTableRow row : table.getRows()) {
                            List<String> cellTexts = new ArrayList<>();
                            for (XWPFTableCell cell : row.getTableCells()) {
                                String cellText = cell.getText();
                                if (cellText != null && !cellText.trim().isEmpty()) {
                                    cellTexts.add(cellText.trim());
                                }
                            }
                            if (!cellTexts.isEmpty()) {
                                sb.append(String.join("\t", cellTexts)).append("\n");
                            }
                        }
                    }
                    return sb.toString();
                }
            } else if (fileName.endsWith(".doc")) {
                // .doc格式 - HWPFDocument
                // 使用Range遍历所有段落（包括表格内的段落）
                // 不依赖isInTable()判断，因为某些表格段落该方法返回false
                // 通过\u0007字符识别表格单元格边界
                try (FileInputStream fis = new FileInputStream(file);
                     HWPFDocument doc = new HWPFDocument(fis)) {
                    StringBuilder sb = new StringBuilder();
                    org.apache.poi.hwpf.usermodel.Range range = doc.getRange();

                    for (int i = 0; i < range.numParagraphs(); i++) {
                        org.apache.poi.hwpf.usermodel.Paragraph p = range.getParagraph(i);
                        String text = p.text();
                        if (text == null || text.trim().isEmpty()) {
                            continue;
                        }
                        // \u0007是Word表格单元格结束符
                        // 将\u0007替换为制表符，保持表格结构可读
                        text = text.replace("\u0007", "\t");
                        // 清理回车符
                        text = text.replace("\r", "");
                        // 合并连续制表符为单个
                        text = text.replaceAll("\t{2,}", "\t");
                        text = text.trim();
                        if (!text.isEmpty()) {
                            sb.append(text).append("\n");
                        }
                    }

                    return sb.toString();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("解析Word文档失败: " + file.getName() + " - " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 获取所有支持的文件扩展名（用于前端展示）
     */
    public static String getSupportedExtensions() {
        Set<String> all = new TreeSet<>();
        all.addAll(TEXT_EXTENSIONS);
        all.addAll(SOURCE_CODE_EXTENSIONS);
        all.addAll(DOCUMENT_EXTENSIONS);
        all.addAll(ARCHIVE_EXTENSIONS);
        return String.join(", ", all);
    }
}
