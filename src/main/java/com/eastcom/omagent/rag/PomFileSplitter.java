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
 * POM文件结构化分块器
 * <p>
 * 按XML结构边界切分POM文件，确保每个&lt;dependency&gt;、&lt;plugin&gt;等结构完整不被截断。
 * <p>
 * 分块策略：
 * - 项目GAV + parent → 项目信息chunk
 * - &lt;properties&gt; → 属性定义chunk
 * - 每个 &lt;dependency&gt; → 独立chunk
 * - 每个 &lt;plugin&gt; → 独立chunk
 * - 其他部分（repositories/profiles等）→ 合并chunk
 * - 每个chunk注入项目GAV上下文头
 */
public class PomFileSplitter {

    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "<dependency>\\s*(.*?)\\s*</dependency>", Pattern.DOTALL
    );

    private static final Pattern PLUGIN_PATTERN = Pattern.compile(
            "<plugin>\\s*(.*?)\\s*</plugin>", Pattern.DOTALL
    );

    private static final Pattern PROPERTIES_PATTERN = Pattern.compile(
            "<properties>\\s*(.*?)\\s*</properties>", Pattern.DOTALL
    );

    private static final Pattern DEPENDENCIES_BLOCK_PATTERN = Pattern.compile(
            "<dependencies>\\s*(.*?)\\s*</dependencies>", Pattern.DOTALL
    );

    private static final Pattern PLUGINS_BLOCK_PATTERN = Pattern.compile(
            "<plugins>\\s*(.*?)\\s*</plugins>", Pattern.DOTALL
    );

    private static final Pattern BUILD_BLOCK_PATTERN = Pattern.compile(
            "<build>\\s*(.*?)\\s*</build>", Pattern.DOTALL
    );

    private static final Pattern REPOSITORIES_PATTERN = Pattern.compile(
            "<repositories>\\s*(.*?)\\s*</repositories>", Pattern.DOTALL
    );

    private static final Pattern PROFILES_PATTERN = Pattern.compile(
            "<profiles>\\s*(.*?)\\s*</profiles>", Pattern.DOTALL
    );

    private static final Pattern PARENT_PATTERN = Pattern.compile(
            "<parent>\\s*(.*?)\\s*</parent>", Pattern.DOTALL
    );

    /** 单个chunk最大字符数 */
    private final int maxChunkSize;

    public PomFileSplitter() {
        this(1500);
    }

    public PomFileSplitter(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    /**
     * 将POM文件按结构化边界分块
     */
    public List<Document> split(File pomFile, Map<String, Object> baseMetadata) {
        try {
            String content = Files.readString(pomFile.toPath(), StandardCharsets.UTF_8);
            return splitContent(content, baseMetadata);
        } catch (IOException e) {
            throw new RuntimeException("读取POM文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将POM内容按结构化边界分块
     */
    public List<Document> splitContent(String content, Map<String, Object> baseMetadata) {
        // 1. 提取项目GAV
        String groupId = extractProjectTag(content, "groupId");
        String artifactId = extractProjectTag(content, "artifactId");
        String version = extractProjectTag(content, "version");
        String packaging = extractProjectTag(content, "packaging");

        String projectGav = buildGav(groupId, artifactId, version);

        // 2. 提取parent信息
        String parentGav = null;
        Matcher parentMatcher = PARENT_PATTERN.matcher(content);
        if (parentMatcher.find()) {
            String parentBlock = parentMatcher.group(1);
            String pg = extractSimpleTag(parentBlock, "groupId");
            String pa = extractSimpleTag(parentBlock, "artifactId");
            String pv = extractSimpleTag(parentBlock, "version");
            parentGav = buildGav(pg, pa, pv);
        }

        // 3. 构建metadata
        Map<String, Object> metadata = new HashMap<>(baseMetadata != null ? baseMetadata : new HashMap<>());
        if (groupId != null) metadata.put("pom_groupId", groupId);
        if (artifactId != null) metadata.put("pom_artifactId", artifactId);
        if (version != null) metadata.put("pom_version", version);

        // 收集所有依赖的GAV（用于metadata和上下文）
        List<String> allDepGavs = extractDependencyGavs(content);
        if (!allDepGavs.isEmpty()) {
            metadata.put("pom_dependencies", String.join(",", allDepGavs));
        }

        // 4. 按结构分块
        List<Document> documents = new ArrayList<>();

        // 4.1 项目信息chunk（parent + 项目GAV + packaging）
        String projectInfoChunk = buildProjectInfoChunk(content, projectGav, parentGav, packaging);
        if (projectInfoChunk != null) {
            Map<String, Object> projectMeta = new HashMap<>(metadata);
            projectMeta.put("scope_chain", artifactId != null ? artifactId : "project");
            projectMeta.put("pom_section", "project-info");
            String header = buildContextHeader(projectGav, "Project Info", null);
            documents.add(new Document(header + projectInfoChunk, projectMeta));
        }

        // 4.2 properties chunk
        Matcher propsMatcher = PROPERTIES_PATTERN.matcher(content);
        if (propsMatcher.find()) {
            String propsContent = propsMatcher.group(0);
            Map<String, Object> propsMeta = new HashMap<>(metadata);
            propsMeta.put("pom_section", "properties");
            String header = buildContextHeader(projectGav, "Properties", null);
            documents.add(new Document(header + propsContent, propsMeta));
        }

        // 4.3 每个 dependency 独立chunk
        Matcher depsMatcher = DEPENDENCY_PATTERN.matcher(content);
        int depIndex = 0;
        while (depsMatcher.find()) {
            depIndex++;
            String depBlock = depsMatcher.group(0);
            String depContent = depsMatcher.group(1);
            String depGav = buildDependencyGav(depContent);

            Map<String, Object> depMeta = new HashMap<>(metadata);
            depMeta.put("pom_section", "dependency");
            depMeta.put("scope_chain", (artifactId != null ? artifactId : "project") + ">dep:" + depGav);
            if (depGav != null) {
                depMeta.put("pom_dep_gav", depGav);
            }

            // 提取scope和optional
            String scope = extractSimpleTag(depContent, "scope");
            String optional = extractSimpleTag(depContent, "optional");
            if (scope != null) depMeta.put("pom_dep_scope", scope);
            if (optional != null) depMeta.put("pom_dep_optional", optional);

            String header = buildContextHeader(projectGav, "Dependency", depGav);
            documents.add(new Document(header + depBlock, depMeta));
        }

        // 4.4 每个 plugin 独立chunk
        Matcher pluginsMatcher = PLUGIN_PATTERN.matcher(content);
        while (pluginsMatcher.find()) {
            String pluginBlock = pluginsMatcher.group(0);
            String pluginContent = pluginsMatcher.group(1);
            String pluginGav = buildDependencyGav(pluginContent);

            Map<String, Object> pluginMeta = new HashMap<>(metadata);
            pluginMeta.put("pom_section", "plugin");
            pluginMeta.put("scope_chain", (artifactId != null ? artifactId : "project") + ">plugin:" + pluginGav);
            if (pluginGav != null) {
                pluginMeta.put("pom_dep_gav", pluginGav);
            }

            String header = buildContextHeader(projectGav, "Plugin", pluginGav);
            documents.add(new Document(header + pluginBlock, pluginMeta));
        }

        // 4.5 其他部分（repositories, profiles等）
        String othersChunk = buildOthersChunk(content);
        if (othersChunk != null) {
            Map<String, Object> othersMeta = new HashMap<>(metadata);
            othersMeta.put("pom_section", "other");
            String header = buildContextHeader(projectGav, "Other", null);
            documents.add(new Document(header + othersChunk, othersMeta));
        }

        // 如果POM内容很少，没有产生任何chunk，则整体作为一个chunk
        if (documents.isEmpty()) {
            Map<String, Object> fullMeta = new HashMap<>(metadata);
            fullMeta.put("scope_chain", artifactId != null ? artifactId : "project");
            documents.add(new Document(content.trim(), fullMeta));
        }

        return documents;
    }

    // ========== 构建chunk内容 ==========

    private String buildProjectInfoChunk(String content, String projectGav, String parentGav, String packaging) {
        StringBuilder sb = new StringBuilder();

        // parent
        Matcher parentMatcher = PARENT_PATTERN.matcher(content);
        if (parentMatcher.find()) {
            sb.append(parentMatcher.group(0)).append("\n\n");
        }

        // 项目GAV
        sb.append("<project>\n");
        if (projectGav != null) {
            String[] parts = projectGav.split(":");
            if (parts.length >= 1) sb.append("  <groupId>").append(parts[0]).append("</groupId>\n");
            if (parts.length >= 2) sb.append("  <artifactId>").append(parts[1]).append("</artifactId>\n");
            if (parts.length >= 3) sb.append("  <version>").append(parts[2]).append("</version>\n");
        }
        if (packaging != null) {
            sb.append("  <packaging>").append(packaging).append("</packaging>\n");
        }
        sb.append("  ...\n</project>");

        return sb.toString();
    }

    private String buildOthersChunk(String content) {
        StringBuilder sb = new StringBuilder();

        Matcher reposMatcher = REPOSITORIES_PATTERN.matcher(content);
        if (reposMatcher.find()) {
            sb.append(reposMatcher.group(0)).append("\n\n");
        }

        Matcher profilesMatcher = PROFILES_PATTERN.matcher(content);
        if (profilesMatcher.find()) {
            sb.append(profilesMatcher.group(0)).append("\n\n");
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    // ========== 上下文头 ==========

    /**
     * 构建POM chunk上下文头
     * 格式示例：
     * # com.eastcom:omagent:1.0.0
     * # Scope: Dependency
     * # Artifact: org.springframework.boot:spring-boot-starter-web:3.2.0
     */
    private String buildContextHeader(String projectGav, String scope, String artifact) {
        StringBuilder sb = new StringBuilder();
        if (projectGav != null) {
            sb.append("# ").append(projectGav).append("\n");
        }
        sb.append("# Scope: ").append(scope);
        if (artifact != null) {
            sb.append(" > ").append(artifact);
        }
        sb.append("\n\n");
        return sb.toString();
    }

    // ========== XML提取辅助方法 ==========

    /**
     * 从POM项目级别提取标签（优先匹配<project>直接子元素，避免匹配dependencies内部）
     */
    private String extractProjectTag(String content, String tagName) {
        // 截取 <dependencies> 之前的部分
        String projectSection = content;
        int depsIndex = content.indexOf("<dependencies>");
        if (depsIndex > 0) {
            projectSection = content.substring(0, depsIndex);
        }

        // 按层级优先
        String[] patterns = {
                "<project>\\s*<" + tagName + ">([^<]+)</" + tagName + ">",
                "<" + tagName + ">([^<]+)</" + tagName + ">"
        };

        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(projectSection);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private String extractSimpleTag(String content, String tagName) {
        Matcher m = Pattern.compile("<" + tagName + ">([^<]+)</" + tagName + ">").matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }

    private List<String> extractDependencyGavs(String content) {
        List<String> gavs = new ArrayList<>();
        Matcher m = DEPENDENCY_PATTERN.matcher(content);
        while (m.find()) {
            String depContent = m.group(1);
            String gav = buildDependencyGav(depContent);
            if (gav != null) {
                gavs.add(gav);
            }
        }
        return gavs;
    }

    private String buildDependencyGav(String depContent) {
        String g = extractSimpleTag(depContent, "groupId");
        String a = extractSimpleTag(depContent, "artifactId");
        String v = extractSimpleTag(depContent, "version");
        return buildGav(g, a, v);
    }

    private String buildGav(String groupId, String artifactId, String version) {
        if (groupId == null && artifactId == null) return null;
        StringBuilder sb = new StringBuilder();
        if (groupId != null) sb.append(groupId);
        if (artifactId != null) sb.append(sb.length() > 0 ? ":" : "").append(artifactId);
        if (version != null) sb.append(sb.length() > 0 ? ":" : "").append(version);
        return sb.toString();
    }

    /**
     * 判断文件是否为POM文件
     */
    public static boolean isPomFile(File file) {
        String name = file.getName().toLowerCase();
        return name.equals("pom.xml") || name.endsWith(".pom");
    }

    /**
     * 判断文件名是否为POM文件
     */
    public static boolean isPomFileName(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.equals("pom.xml") || lower.endsWith(".pom");
    }
}
