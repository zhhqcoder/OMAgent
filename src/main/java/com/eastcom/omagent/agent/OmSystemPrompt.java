package com.eastcom.omagent.agent;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 提示词管理器 - 从外部配置文件加载提示词
 * 
 * 加载优先级：
 * 1. 外部文件 config/prompts/{name}.txt（优先，方便运行时修改）
 * 2. classpath: prompts/{name}.txt（内置默认值）
 */
@Component
public class OmSystemPrompt {

    private String configQaPrompt;
    private String logAnalysisPrompt;
    private String generalGuidePrompt;

    @PostConstruct
    public void init() {
        this.configQaPrompt = loadPrompt("config-qa");
        this.logAnalysisPrompt = loadPrompt("log-analysis");
        this.generalGuidePrompt = loadPrompt("general-guide");
    }

    public String getConfigQaPrompt() {
        return configQaPrompt;
    }

    public String getLogAnalysisPrompt() {
        return logAnalysisPrompt;
    }

    public String getGeneralGuidePrompt() {
        return generalGuidePrompt;
    }

    /**
     * 获取通用Agent的完整提示词 = 配置问答 + 日志分析 + 综合指引
     */
    public String getGeneralAgentPrompt() {
        return configQaPrompt + "\n\n---\n\n" + logAnalysisPrompt + "\n\n---\n\n" + generalGuidePrompt;
    }

    /**
     * 加载提示词文件，优先从外部config目录读取，fallback到classpath
     */
    private String loadPrompt(String name) {
        // 优先尝试外部文件
        Path externalPath = Paths.get("config", "prompts", name + ".txt");
        if (Files.exists(externalPath)) {
            try {
                String content = Files.readString(externalPath, StandardCharsets.UTF_8);
                return content.trim();
            } catch (IOException e) {
                // 读取失败，fallback到classpath
            }
        }

        // 从classpath读取
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + name + ".txt");
            return resource.getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new RuntimeException("无法加载提示词文件: prompts/" + name + ".txt", e);
        }
    }
}
