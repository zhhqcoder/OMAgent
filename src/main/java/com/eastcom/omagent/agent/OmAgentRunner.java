package com.eastcom.omagent.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Optional;

@Component
public class OmAgentRunner {

    private final OmAgentFactory omAgentFactory;

    // 缓存Agent实例，按会话隔离
    private final java.util.concurrent.ConcurrentHashMap<String, ReactAgent> agentCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    public OmAgentRunner(OmAgentFactory omAgentFactory) {
        this.omAgentFactory = omAgentFactory;
    }

    /**
     * 同步调用Agent
     */
    public String invoke(String sessionId, String userInput) {
        try {
            ReactAgent agent = getOrCreateAgent(sessionId);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            Optional<?> result = agent.invoke(userInput, config);
            return extractTextFromOutput(result.orElse(null));
        } catch (Exception e) {
            return "调用Agent失败: " + e.getMessage();
        }
    }

    /**
     * 流式调用Agent
     */
    public Flux<String> stream(String sessionId, String userInput) {
        try {
            ReactAgent agent = getOrCreateAgent(sessionId);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            return agent.stream(userInput, config)
                    .mapNotNull(this::extractChunkFromStreamOutput)
                    .filter(s -> !s.isEmpty());
        } catch (Exception e) {
            return Flux.just("调用Agent失败: " + e.getMessage());
        }
    }

    /**
     * 处理截图分析结果 - 将多模态分析结果交给日志分析Agent处理
     */
    public String invokeWithScreenshotAnalysis(String sessionId, String screenshotAnalysisResult) {
        try {
            ReactAgent agent = getOrCreateLogAgent(sessionId);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            String prompt = "以下是一张错误截图的分析结果，请结合知识库进一步分析：\n\n" + screenshotAnalysisResult;
            Optional<?> result = agent.invoke(prompt, config);
            return extractTextFromOutput(result.orElse(null));
        } catch (Exception e) {
            return "调用Agent失败: " + e.getMessage();
        }
    }

    /**
     * 清除会话的Agent缓存
     */
    public void clearAgent(String sessionId) {
        agentCache.remove(sessionId);
    }

    private ReactAgent getOrCreateAgent(String sessionId) {
        return agentCache.computeIfAbsent(sessionId,
                id -> omAgentFactory.createGeneralAgent());
    }

    private ReactAgent getOrCreateLogAgent(String sessionId) {
        return agentCache.computeIfAbsent("log_" + sessionId,
                id -> omAgentFactory.createLogAnalysisAgent());
    }

    /**
     * 从流式输出中提取纯文本chunk
     * 只提取 AGENT_MODEL_STREAMING 类型的增量片段，过滤掉：
     * - NodeOutput (START/END标记)
     * - AGENT_MODEL_FINISHED (完整消息，会导致重复)
     */
    private String extractChunkFromStreamOutput(Object output) {
        if (output instanceof StreamingOutput<?> streaming) {
            // 只输出模型流式片段，过滤掉FINISHED（完整消息会导致内容重复）
            if (streaming.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
                String chunk = streaming.chunk();
                return chunk != null ? chunk : "";
            }
        }
        return null;
    }

    /**
     * 从同步调用结果中提取最终文本
     */
    private String extractTextFromOutput(Object output) {
        if (output == null) {
            return "无法生成回复";
        }
        if (output instanceof NodeOutput nodeOutput) {
            // 从state中提取最后一条assistant消息
            var state = nodeOutput.state();
            if (state != null && state.data() != null) {
                var messages = (java.util.List<?>) state.data().get("messages");
                if (messages != null && !messages.isEmpty()) {
                    // 从后往前找最后一条ASSISTANT消息
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        Object msg = messages.get(i);
                        if (msg instanceof AssistantMessage assistantMessage) {
                            String text = assistantMessage.getText();
                            if (text != null && !text.isEmpty()) {
                                return text;
                            }
                        }
                    }
                }
            }
            // fallback: 返回toString但去掉NodeOutput前缀
            String str = output.toString();
            // 如果toString还是原始格式，尝试提取
            return str.isEmpty() ? "无法生成回复" : str;
        }
        return output.toString();
    }
}
