package com.eastcom.omagent.rag;

import org.springframework.ai.document.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 标题语义分块器
 * <p>
 * 按 ## / ### 标题将 Markdown 文档切分为独立的章节块，
 * 每个 chunk 保留完整的章节内容（表格、代码块等不会被截断），
 * 并在 metadata 中记录章节路径和层级信息。
 * <p>
 * 对超长章节（token 数超过阈值），会使用 TokenTextSplitter 二次切分，
 * 但确保每个子 chunk 仍携带章节上下文头。
 */
public class MarkdownSectionSplitter {

    /** 匹配 ## 和 ### 标题行 */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{2,3})\\s+(.+)$", Pattern.MULTILINE);

    /** 超长章节的最大字符数（超过则二次切分） */
    private final int maxSectionChars;

    /** 二次切分时的重叠字符数 */
    private final int overlapChars;

    /** 是否跳过目录区域 */
    private final boolean skipToc;

    public MarkdownSectionSplitter() {
        this(3000, 200, true);
    }

    public MarkdownSectionSplitter(int maxSectionChars, int overlapChars, boolean skipToc) {
        this.maxSectionChars = maxSectionChars;
        this.overlapChars = overlapChars;
        this.skipToc = skipToc;
    }

    /**
     * 将 Markdown 内容按标题语义分块
     *
     * @param markdownContent Markdown 原始内容
     * @param baseMetadata    基础 metadata（source, file_id 等）
     * @return 分块后的 Document 列表
     */
    public List<Document> split(String markdownContent, Map<String, Object> baseMetadata) {
        List<Section> sections = parseSections(markdownContent);
        List<Document> documents = new ArrayList<>();

        for (Section section : sections) {
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put("section_path", section.path);
            metadata.put("section_level", section.level);
            metadata.put("section_title", section.title);

            // 从标题中提取模块名（如 "2.6 kafka配置" → "kafka"）
            String module = extractModuleFromTitle(section.title);
            if (module != null && !module.isEmpty() && !metadata.containsKey("module")) {
                metadata.put("module", module);
            }

            String content = section.content.trim();
            if (content.isEmpty()) {
                continue;
            }

            // 注入章节上下文头
            String contextPrefix = "[" + section.path + "]\n\n";
            String fullContent = contextPrefix + content;

            // 超长章节二次切分
            if (fullContent.length() > maxSectionChars) {
                List<String> subChunks = splitLongSection(fullContent, maxSectionChars, overlapChars);
                for (int i = 0; i < subChunks.size(); i++) {
                    Map<String, Object> subMeta = new HashMap<>(metadata);
                    if (subChunks.size() > 1) {
                        subMeta.put("chunk_index", i);
                        subMeta.put("chunk_total", subChunks.size());
                    }
                    documents.add(new Document(subChunks.get(i), subMeta));
                }
            } else {
                documents.add(new Document(fullContent, metadata));
            }
        }

        return documents;
    }

    /**
     * 解析 Markdown 内容，按标题切分为 Section 列表
     */
    List<Section> parseSections(String content) {
        List<Section> sections = new ArrayList<>();

        // 收集所有标题位置
        List<HeadingInfo> headings = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(content);
        while (matcher.find()) {
            int level = matcher.group(1).length(); // 2=##, 3=###
            String title = matcher.group(2).trim();
            headings.add(new HeadingInfo(matcher.start(), matcher.end(), level, title));
        }

        if (headings.isEmpty()) {
            // 没有标题，整体作为一个块
            String trimmed = content.trim();
            if (!trimmed.isEmpty()) {
                sections.add(new Section("文档", "文档", trimmed, 1));
            }
            return sections;
        }

        // 处理标题前的封面/前言内容
        String preamble = content.substring(0, headings.get(0).start).trim();
        if (!preamble.isEmpty() && !skipToc) {
            sections.add(new Section("封面", "封面", preamble, 1));
        }

        // 构建每个章节
        String currentParentTitle = null;
        for (int i = 0; i < headings.size(); i++) {
            HeadingInfo heading = headings.get(i);
            int contentStart = heading.end;
            int contentEnd = (i + 1 < headings.size()) ? headings.get(i + 1).start : content.length();

            String sectionContent = content.substring(contentStart, contentEnd).trim();

            // 跳过目录区域（从"## 目录"到下一个## 之间的内容）
            if (skipToc && "目录".equals(heading.title) && heading.level == 2) {
                currentParentTitle = null;
                continue;
            }

            // 构建章节路径
            String path;
            if (heading.level == 2) {
                currentParentTitle = heading.title;
                path = heading.title;
            } else if (heading.level == 3 && currentParentTitle != null) {
                path = currentParentTitle + " / " + heading.title;
            } else {
                path = heading.title;
                if (heading.level == 3) {
                    currentParentTitle = null;
                }
            }

            // 过滤掉封面、版本历史等非配置内容
            if (shouldSkip(heading.title, heading.level)) {
                currentParentTitle = (heading.level == 2) ? null : currentParentTitle;
                continue;
            }

            if (!sectionContent.isEmpty()) {
                sections.add(new Section(path, heading.title, sectionContent, heading.level));
            }
        }

        return sections;
    }

    /**
     * 判断是否应跳过该章节（封面、版本历史、目录等非配置内容）
     */
    private boolean shouldSkip(String title, int level) {
        if (level == 2) {
            // 跳过 "版本历史及修订"、"目录"
            return title.contains("版本历史") || title.equals("目录");
        }
        return false;
    }

    /**
     * 从章节标题中提取模块名
     * 例如: "2.6 kafka：kafka配置" → "kafka"
     *       "2.1 Server：oms服务端配置" → "server"
     */
    String extractModuleFromTitle(String title) {
        if (title == null) return null;
        // 匹配 "数字.数字 英文名" 格式
        Matcher m = Pattern.compile("^\\d+(\\.\\d+)?\\s+([a-zA-Z][a-zA-Z0-9_-]*)").matcher(title);
        if (m.find()) {
            return m.group(2).toLowerCase();
        }
        // 匹配 "数字.数字 中文名：" 格式，取冒号前的部分
        m = Pattern.compile("^\\d+(\\.\\d+)?\\s+(.+?)[：:]").matcher(title);
        if (m.find()) {
            String name = m.group(2).trim();
            // 如果是纯中文，返回拼音首字母不方便，直接返回原文
            return name.toLowerCase();
        }
        // 匹配纯英文标题
        m = Pattern.compile("^\\d+(\\.\\d+)?\\s+([a-zA-Z][a-zA-Z0-9_]+)").matcher(title);
        if (m.find()) {
            return m.group(2).toLowerCase();
        }
        return null;
    }

    /**
     * 对超长章节进行二次切分，尽量在空行处断开
     */
    private List<String> splitLongSection(String content, int maxChars, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < content.length()) {
            int end = Math.min(start + maxChars, content.length());

            if (end < content.length()) {
                // 尝试在空行处断开
                int breakPoint = findBreakPoint(content, start, end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }

            chunks.add(content.substring(start, end).trim());

            if (end >= content.length()) break;

            // 下一块的起始位置（带重叠）
            start = Math.max(end - overlap, end);
            // 跳过空白行
            while (start < content.length() && content.charAt(start) == '\n') {
                start++;
            }
        }

        return chunks;
    }

    /**
     * 在指定范围内找一个合适的断点（优先空行，其次换行符）
     */
    private int findBreakPoint(String content, int start, int preferredEnd) {
        // 从 preferredEnd 往前找空行
        for (int i = preferredEnd - 1; i > start + maxSectionChars / 2; i--) {
            if (i > 0 && content.charAt(i) == '\n' && content.charAt(i - 1) == '\n') {
                return i + 1; // 空行后
            }
        }
        // 找不到空行，找换行符
        for (int i = preferredEnd - 1; i > start + maxSectionChars / 2; i--) {
            if (content.charAt(i) == '\n') {
                return i + 1;
            }
        }
        return preferredEnd;
    }

    // ---- 内部数据类 ----

    static class Section {
        final String path;      // 章节路径，如 "2 application-dev配置文件 / 2.6 kafka：kafka配置"
        final String title;     // 章节标题
        final String content;   // 章节内容（不含标题行本身）
        final int level;        // 标题层级 (2 or 3)

        Section(String path, String title, String content, int level) {
            this.path = path;
            this.title = title;
            this.content = content;
            this.level = level;
        }
    }

    static class HeadingInfo {
        final int start;
        final int end;
        final int level;
        final String title;

        HeadingInfo(int start, int end, int level, String title) {
            this.start = start;
            this.end = end;
            this.level = level;
            this.title = title;
        }
    }
}
