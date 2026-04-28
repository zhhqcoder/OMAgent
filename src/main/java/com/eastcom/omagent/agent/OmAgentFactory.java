package com.eastcom.omagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.eastcom.omagent.rag.KnowledgeBaseService;
import com.eastcom.omagent.tool.ConfigSearchTool;
import com.eastcom.omagent.tool.LogAnalysisTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OmAgentFactory {

    private final ChatModel chatModel;
    private final KnowledgeBaseService knowledgeBaseService;
    private final OmSystemPrompt omSystemPrompt;

    public OmAgentFactory(ChatModel chatModel, KnowledgeBaseService knowledgeBaseService, OmSystemPrompt omSystemPrompt) {
        this.chatModel = chatModel;
        this.knowledgeBaseService = knowledgeBaseService;
        this.omSystemPrompt = omSystemPrompt;
    }

    /**
     * 创建配置问答Agent
     */
    public ReactAgent createConfigQaAgent() {
        List<ToolCallback> tools = new ArrayList<>();
        tools.add(ConfigSearchTool.createToolCallback(knowledgeBaseService));
        tools.add(LogAnalysisTool.createToolCallback(knowledgeBaseService));

        return ReactAgent.builder()
                .name("config_qa_agent")
                .model(chatModel)
                .description("OMS配置问答助手，解答配置项含义、取值范围、依赖关系等问题")
                .systemPrompt(omSystemPrompt.getConfigQaPrompt())
                .tools(tools)
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 创建日志分析Agent
     */
    public ReactAgent createLogAnalysisAgent() {
        List<ToolCallback> tools = new ArrayList<>();
        tools.add(LogAnalysisTool.createToolCallback(knowledgeBaseService));

        return ReactAgent.builder()
                .name("log_analysis_agent")
                .model(chatModel)
                .description("OMS日志分析助手，分析错误日志并判断是程序Bug还是配置问题")
                .systemPrompt(omSystemPrompt.getLogAnalysisPrompt())
                .tools(tools)
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 创建通用运维Agent（同时拥有配置检索和日志分析能力）
     */
    public ReactAgent createGeneralAgent() {
        List<ToolCallback> tools = new ArrayList<>();
        tools.add(ConfigSearchTool.createToolCallback(knowledgeBaseService));
        tools.add(LogAnalysisTool.createToolCallback(knowledgeBaseService));

        return ReactAgent.builder()
                .name("om_ops_agent")
                .model(chatModel)
                .description("OMS运维智能助手，同时支持配置问答和日志分析")
                .systemPrompt(omSystemPrompt.getGeneralAgentPrompt())
                .tools(tools)
                .saver(new MemorySaver())
                .build();
    }
}
