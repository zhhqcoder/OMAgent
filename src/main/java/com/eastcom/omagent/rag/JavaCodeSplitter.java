package com.eastcom.omagent.rag;

import org.springframework.ai.document.Document;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java代码语义分块器
 * <p>
 * 基于大括号匹配+正则解析，在类/方法边界处切分Java代码，
 * 避免在方法中间截断，每个chunk携带作用域上下文。
 * <p>
 * 分块策略：
 * - 一个类 ≤ maxChunkSize → 整个类作为一个chunk
 * - 一个类 > maxChunkSize → 按方法切分，每个方法独立chunk，类头（字段+构造器）单独一个chunk
 * - 一个方法 > maxChunkSize → 在语句边界(;)处二次切分
 * - 每个chunk前注入上下文头（包名、作用域链、方法签名、import）
 */
public class JavaCodeSplitter {

    /** 单个chunk最大字符数（非空白） */
    private final int maxChunkSize;

    /** 二次切分时的重叠行数 */
    private final int overlapLines;

    // ---- 正则模式 ----

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;", Pattern.MULTILINE);

    /** 匹配类/接口/枚举声明行 */
    private static final Pattern CLASS_DECL_PATTERN = Pattern.compile(
            "^\\s*(?:(?:@\\w+(?:\\([^)]*\\))?\\s*)*(?:public|protected|private|abstract|final|static)\\s+)*" +
                    "(class|interface|enum)\\s+(\\w+)",
            Pattern.MULTILINE
    );

    /** 匹配方法声明行（含构造器） */
    private static final Pattern METHOD_DECL_PATTERN = Pattern.compile(
            "^\\s*(?:(?:@\\w+(?:\\([^)]*\\))?\\s*)*(?:public|protected|private|abstract|final|static|synchronized|native|default)\\s+)*" +
                    "(?:<[\\w?,\\s\\[\\]]+>\\s+)?" +
                    "([\\w<>\\[\\],?\\s]+?)\\s+" +
                    "(\\w+)\\s*\\(([^)]*)\\)\\s*" +
                    "(?:throws\\s+[\\w.,\\s]+)?\\s*\\{",
            Pattern.MULTILINE
    );

    public JavaCodeSplitter() {
        this(1500, 5);
    }

    public JavaCodeSplitter(int maxChunkSize, int overlapLines) {
        this.maxChunkSize = maxChunkSize;
        this.overlapLines = overlapLines;
    }

    /**
     * 将Java文件按语义边界分块
     */
    public List<Document> split(File javaFile, Map<String, Object> baseMetadata) {
        try {
            String content = Files.readString(javaFile.toPath(), StandardCharsets.UTF_8);
            return splitContent(content, baseMetadata);
        } catch (IOException e) {
            throw new RuntimeException("读取Java文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将Java代码内容按语义边界分块
     */
    public List<Document> splitContent(String content, Map<String, Object> baseMetadata) {
        // 1. 提取全局信息
        String packageName = extractPackageName(content);
        List<String> imports = extractImports(content);

        // 2. 解析类结构
        List<ClassSpan> classSpans = findClassSpans(content);

        if (classSpans.isEmpty()) {
            // 没有找到类定义，整体作为一个chunk
            Map<String, Object> metadata = createMetadata(baseMetadata, packageName, null, null, imports);
            return List.of(new Document(content.trim(), metadata));
        }

        // 3. 按类分块
        List<Document> documents = new ArrayList<>();
        for (ClassSpan classSpan : classSpans) {
            documents.addAll(chunkClass(content, classSpan, packageName, imports, baseMetadata));
        }

        return documents;
    }

    // ========== 解析方法 ==========

    private String extractPackageName(String content) {
        Matcher m = PACKAGE_PATTERN.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    private List<String> extractImports(String content) {
        List<String> imports = new ArrayList<>();
        Matcher m = IMPORT_PATTERN.matcher(content);
        while (m.find()) {
            imports.add(m.group(1));
        }
        return imports;
    }

    /**
     * 找到所有顶层类声明的位置范围
     */
    private List<ClassSpan> findClassSpans(String content) {
        List<ClassSpan> spans = new ArrayList<>();
        Matcher m = CLASS_DECL_PATTERN.matcher(content);

        while (m.find()) {
            String type = m.group(1); // class / interface / enum
            String name = m.group(2);

            // 找到类声明后第一个 '{'
            int bracePos = content.indexOf('{', m.end() - 1);
            if (bracePos < 0) continue;

            // 找到匹配的 '}'
            int closeBrace = findMatchingBrace(content, bracePos);
            if (closeBrace < 0) closeBrace = content.length() - 1;

            // 只记录顶层类（通过检查 '{' 前的括号深度为0）
            // 简化判断：类声明起始位置之前的 { 和 } 数量差为0
            int depthBefore = countBraceDepthBefore(content, m.start());
            if (depthBefore == 0) {
                spans.add(new ClassSpan(name, type, m.start(), bracePos, closeBrace));
            }
        }
        return spans;
    }

    /**
     * 计算指定位置之前的花括号深度（忽略注释和字符串）
     */
    private int countBraceDepthBefore(String content, int pos) {
        int depth = 0;
        boolean inBlockComment = false;
        boolean inLineComment = false;
        boolean inString = false;
        boolean inChar = false;

        for (int i = 0; i < pos; i++) {
            char c = content.charAt(i);
            char next = (i + 1 < content.length()) ? content.charAt(i + 1) : '\0';

            if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i++; }
                continue;
            }
            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (inChar) {
                if (c == '\\') { i++; continue; }
                if (c == '\'') inChar = false;
                continue;
            }

            if (c == '/' && next == '*') { inBlockComment = true; i++; }
            else if (c == '/' && next == '/') { inLineComment = true; i++; }
            else if (c == '"') { inString = true; }
            else if (c == '\'') { inChar = true; }
            else if (c == '{') { depth++; }
            else if (c == '}') { depth--; }
        }
        return depth;
    }

    /**
     * 找到与 openBracePos 处的 '{' 匹配的 '}' 位置
     */
    private int findMatchingBrace(String content, int openBracePos) {
        int depth = 0;
        boolean inBlockComment = false;
        boolean inLineComment = false;
        boolean inString = false;
        boolean inChar = false;

        for (int i = openBracePos; i < content.length(); i++) {
            char c = content.charAt(i);
            char next = (i + 1 < content.length()) ? content.charAt(i + 1) : '\0';

            if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i++; }
                continue;
            }
            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (inChar) {
                if (c == '\\') { i++; continue; }
                if (c == '\'') inChar = false;
                continue;
            }

            if (c == '/' && next == '*') { inBlockComment = true; i++; }
            else if (c == '/' && next == '/') { inLineComment = true; i++; }
            else if (c == '"') { inString = true; }
            else if (c == '\'') { inChar = true; }
            else if (c == '{') { depth++; }
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1; // 未找到匹配
    }

    // ========== 分块方法 ==========

    /**
     * 将一个类按语义边界分块
     */
    private List<Document> chunkClass(String content, ClassSpan classSpan,
                                      String packageName, List<String> imports,
                                      Map<String, Object> baseMetadata) {
        String classCode = content.substring(classSpan.start, classSpan.end + 1).trim();

        // 如果整个类大小合适，直接作为一个chunk
        if (nonWhitespaceSize(classCode) <= maxChunkSize) {
            String header = buildContextHeader(packageName, classSpan.name, null, imports, null);
            String fullContent = header + classCode;
            Map<String, Object> metadata = createMetadata(baseMetadata, packageName,
                    classSpan.name, null, imports);
            metadata.put("scope_chain", classSpan.name);
            return List.of(new Document(fullContent, metadata));
        }

        // 类太大，按方法切分
        String classBody = content.substring(classSpan.bodyStart + 1, classSpan.bodyEnd).trim();

        // 找到类声明行（到第一个 { 为止，包含字段和内部类声明等）
        String classHeader = content.substring(classSpan.start, classSpan.bodyStart + 1).trim();

        // 解析方法
        List<MethodSpan> methodSpans = findMethodSpans(classBody);

        List<Document> documents = new ArrayList<>();

        // 收集兄弟方法签名（用于上下文）
        List<String> allMethodSigs = methodSpans.stream()
                .map(ms -> ms.signature)
                .toList();

        // 类头chunk（包含类声明、字段、静态块等非方法代码）
        String headerContent = extractNonMethodContent(classBody, methodSpans);
        if (!headerContent.isBlank()) {
            String headerCtx = buildContextHeader(packageName, classSpan.name, null, imports, allMethodSigs);
            String fullHeader = headerCtx + classHeader + "\n" + headerContent + "\n}";
            Map<String, Object> headerMeta = createMetadata(baseMetadata, packageName,
                    classSpan.name, null, imports);
            headerMeta.put("scope_chain", classSpan.name);
            documents.add(new Document(fullHeader, headerMeta));
        }

        // 方法chunks
        for (MethodSpan methodSpan : methodSpans) {
            String methodCode = classBody.substring(methodSpan.start, methodSpan.end + 1).trim();
            List<String> siblingSigs = allMethodSigs.stream()
                    .filter(s -> !s.equals(methodSpan.signature))
                    .toList();

            if (nonWhitespaceSize(methodCode) <= maxChunkSize) {
                // 方法大小合适
                String methodCtx = buildContextHeader(packageName, classSpan.name,
                        methodSpan.signature, imports, siblingSigs);
                String fullMethod = methodCtx + classHeader + "\n  " + methodCode + "\n}";
                Map<String, Object> methodMeta = createMetadata(baseMetadata, packageName,
                        classSpan.name, methodSpan, imports);
                documents.add(new Document(fullMethod, methodMeta));
            } else {
                // 方法过大，二次切分
                List<String> subChunks = splitLongCode(methodCode, maxChunkSize, overlapLines);
                for (int i = 0; i < subChunks.size(); i++) {
                    String subCtx = buildContextHeader(packageName, classSpan.name,
                            methodSpan.signature + " [" + (i + 1) + "/" + subChunks.size() + "]",
                            imports, siblingSigs);
                    String fullSub = subCtx + classHeader + "\n  " + subChunks.get(i) + "\n}";
                    Map<String, Object> subMeta = createMetadata(baseMetadata, packageName,
                            classSpan.name, methodSpan, imports);
                    if (subChunks.size() > 1) {
                        subMeta.put("chunk_index", i);
                        subMeta.put("chunk_total", subChunks.size());
                    }
                    documents.add(new Document(fullSub, subMeta));
                }
            }
        }

        return documents;
    }

    /**
     * 在类体中找到所有方法声明及其范围
     */
    private List<MethodSpan> findMethodSpans(String classBody) {
        List<MethodSpan> spans = new ArrayList<>();
        Matcher m = METHOD_DECL_PATTERN.matcher(classBody);

        while (m.find()) {
            String returnType = m.group(1).trim();
            String methodName = m.group(2);
            String params = m.group(3).trim();

            // 判断是否为方法（排除字段声明等误匹配）
            // 构造器没有返回类型，返回类型与类名相同
            // 字段声明如 "List<String> names = ..." 也会匹配，通过检查是否有 { 来区分
            int bracePos = classBody.indexOf('{', m.end() - 1);
            if (bracePos < 0) continue;

            // 确认 { 紧跟在方法声明之后（中间只有空白或throws）
            String between = classBody.substring(m.end(), bracePos).trim();
            if (!between.isEmpty() && !between.startsWith("throws") && !between.startsWith(",")) {
                continue;
            }

            // 找到方法声明的完整起始行
            int lineStart = classBody.lastIndexOf('\n', m.start()) + 1;

            // 找匹配的 }
            int closeBrace = findMatchingBrace(classBody, bracePos);
            if (closeBrace < 0) closeBrace = classBody.length() - 1;

            // 构建签名
            String signature;
            if (returnType.isEmpty()) {
                signature = methodName + "(" + params + ")";
            } else {
                signature = returnType + " " + methodName + "(" + params + ")";
            }

            spans.add(new MethodSpan(methodName, signature, lineStart, closeBrace));
        }

        // 按起始位置排序，去重（同一个方法可能被匹配两次）
        spans.sort(Comparator.comparingInt(s -> s.start));
        List<MethodSpan> deduped = new ArrayList<>();
        for (MethodSpan span : spans) {
            if (deduped.isEmpty() || span.start > deduped.get(deduped.size() - 1).end) {
                deduped.add(span);
            }
        }

        return deduped;
    }

    /**
     * 提取类体中非方法的代码（字段声明、静态块、内部类声明等）
     */
    private String extractNonMethodContent(String classBody, List<MethodSpan> methodSpans) {
        if (methodSpans.isEmpty()) return classBody;

        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;

        for (MethodSpan span : methodSpans) {
            if (span.start > lastEnd) {
                String between = classBody.substring(lastEnd, span.start).trim();
                if (!between.isEmpty()) {
                    sb.append(between).append("\n");
                }
            }
            lastEnd = span.end + 1;
        }

        // 最后一个方法之后的内容
        if (lastEnd < classBody.length()) {
            String tail = classBody.substring(lastEnd).trim();
            if (!tail.isEmpty()) {
                sb.append(tail).append("\n");
            }
        }

        return sb.toString().trim();
    }

    // ========== 上下文头 ==========

    /**
     * 构建上下文头，参照code-chunk的contextualizedText格式
     * 格式示例：
     * # com.eastcom.omagent.rag.DocumentEtlService
     * # Scope: DocumentEtlService > enhanceCamelCaseText
     * # Defines: enhanceCamelCaseText(String text): String
     * # Imports: org.springframework.ai.document.Document, java.util.List
     * # Siblings: enhanceDocuments, generateChineseSummaries, addInBatches
     */
    private String buildContextHeader(String packageName, String className,
                                      String methodSignature, List<String> imports,
                                      List<String> siblingSignatures) {
        StringBuilder sb = new StringBuilder();

        // 文件/包信息
        if (packageName != null && !packageName.isEmpty()) {
            sb.append("# ").append(packageName);
            if (className != null) sb.append(".").append(className);
            sb.append("\n");
        }

        // 作用域链
        sb.append("# Scope: ");
        if (className != null) sb.append(className);
        if (methodSignature != null) {
            // 从签名中提取方法名
            String methodName = extractMethodNameFromSignature(methodSignature);
            sb.append(" > ").append(methodName);
        }
        sb.append("\n");

        // 定义的方法签名
        if (methodSignature != null) {
            sb.append("# Defines: ").append(methodSignature).append("\n");
        }

        // 关键import（最多8个，避免过长）
        if (imports != null && !imports.isEmpty()) {
            String importStr = imports.stream()
                    .filter(i -> !i.startsWith("java.lang"))
                    .limit(8)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            if (!importStr.isEmpty()) {
                sb.append("# Imports: ").append(importStr).append("\n");
            }
        }

        // 兄弟方法（最多6个）
        if (siblingSignatures != null && !siblingSignatures.isEmpty()) {
            String siblingStr = siblingSignatures.stream()
                    .map(this::extractMethodNameFromSignature)
                    .limit(6)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            if (!siblingStr.isEmpty()) {
                sb.append("# Siblings: ").append(siblingStr).append("\n");
            }
        }

        sb.append("\n");
        return sb.toString();
    }

    private String extractMethodNameFromSignature(String signature) {
        // "void enhanceDocuments(List<Document> documents)" → "enhanceDocuments"
        // "enhanceCamelCaseText(String text)" → "enhanceCamelCaseText"
        int parenIdx = signature.indexOf('(');
        if (parenIdx < 0) return signature;
        String beforeParen = signature.substring(0, parenIdx).trim();
        int spaceIdx = beforeParen.lastIndexOf(' ');
        return spaceIdx >= 0 ? beforeParen.substring(spaceIdx + 1) : beforeParen;
    }

    // ========== 二次切分 ==========

    /**
     * 对超长代码块在语句边界处切分
     */
    private List<String> splitLongCode(String code, int maxSize, int overlapLines) {
        List<String> lines = Arrays.asList(code.split("\n"));
        List<String> chunks = new ArrayList<>();
        List<String> currentChunk = new ArrayList<>();
        int currentSize = 0;

        for (String line : lines) {
            int lineSize = nonWhitespaceSize(line);
            if (lineSize == 0) {
                currentChunk.add(line);
                continue;
            }

            if (currentSize + lineSize > maxSize && !currentChunk.isEmpty()) {
                // 当前chunk已满，在语句边界处断开
                chunks.add(String.join("\n", currentChunk));
                // 保留overlap行
                int overlapStart = Math.max(0, currentChunk.size() - overlapLines);
                List<String> overlap = currentChunk.subList(overlapStart, currentChunk.size());
                currentChunk = new ArrayList<>(overlap);
                currentSize = overlap.stream().mapToInt(this::nonWhitespaceSize).sum();
            }

            currentChunk.add(line);
            currentSize += lineSize;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(String.join("\n", currentChunk));
        }

        return chunks;
    }

    // ========== 辅助方法 ==========

    private int nonWhitespaceSize(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > ' ') count++;
        }
        return count;
    }

    private Map<String, Object> createMetadata(Map<String, Object> baseMetadata,
                                                String packageName, String className,
                                                MethodSpan methodSpan, List<String> imports) {
        Map<String, Object> metadata = new HashMap<>(baseMetadata != null ? baseMetadata : new HashMap<>());
        if (packageName != null) metadata.put("package_name", packageName);
        if (className != null) metadata.put("class_name", className);
        if (methodSpan != null) {
            metadata.put("method_name", methodSpan.name);
            metadata.put("scope_chain", className + ">" + methodSpan.name);
        }
        if (imports != null && !imports.isEmpty()) {
            metadata.put("imports", String.join(",", imports));
        }
        return metadata;
    }

    // ========== 内部数据类 ==========

    static class ClassSpan {
        final String name;
        final String type;  // class / interface / enum
        final int start;    // 类声明起始位置
        final int bodyStart; // 类体 '{' 位置
        final int bodyEnd;  // 类体 '}' 位置
        final int end;      // 等于bodyEnd

        ClassSpan(String name, String type, int start, int bodyStart, int bodyEnd) {
            this.name = name;
            this.type = type;
            this.start = start;
            this.bodyStart = bodyStart;
            this.bodyEnd = bodyEnd;
            this.end = bodyEnd;
        }
    }

    static class MethodSpan {
        final String name;
        final String signature;
        final int start;   // 方法起始位置（相对于类体）
        final int end;     // 方法结束位置

        MethodSpan(String name, String signature, int start, int end) {
            this.name = name;
            this.signature = signature;
            this.start = start;
            this.end = end;
        }
    }
}
