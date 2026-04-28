package com.eastcom.omagent.service;

import com.eastcom.omagent.agent.OmAgentRunner;
import com.eastcom.omagent.dto.ChatRequest;
import com.eastcom.omagent.dto.ChatResponse;
import com.eastcom.omagent.entity.ChatMessage;
import com.eastcom.omagent.entity.ChatSession;
import com.eastcom.omagent.rag.MultimodalService;
import com.eastcom.omagent.repository.ChatMessageRepository;
import com.eastcom.omagent.repository.ChatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.*;

@Service
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final OmAgentRunner agentRunner;
    private final MultimodalService multimodalService;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public ChatService(ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       OmAgentRunner agentRunner,
                       MultimodalService multimodalService,
                       FileService fileService,
                       ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.agentRunner = agentRunner;
        this.multimodalService = multimodalService;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建新会话
     */
    @Transactional
    public ChatSession createSession(String userId) {
        ChatSession session = ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title("新对话")
                .build();
        return sessionRepository.save(session);
    }

    /**
     * 获取用户会话列表
     */
    public List<ChatSession> getSessions(String userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteSession(String sessionId, String userId) {
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteByIdAndUserId(sessionId, userId);
        agentRunner.clearAgent(sessionId);
    }

    /**
     * 获取会话消息历史
     */
    public List<ChatMessage> getMessageHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 发送消息并同步获取回复
     */
    @Transactional
    public ChatResponse sendMessage(ChatRequest request, String userId) {
        // 确保会话存在
        String sessionId = ensureSession(request, userId);
        String userMessage = buildUserMessage(request);

        // 保存用户消息
        ChatMessage userMsg = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .role("user")
                .content(userMessage)
                .imageUrl(request.getImageBase64() != null ? "screenshot" : null)
                .build();
        messageRepository.save(userMsg);

        // 处理输入并获取Agent回复
        String agentResponse;
        if (request.getImageBase64() != null) {
            // 截图分析流程
            String screenshotAnalysis = multimodalService.analyzeScreenshot(
                    request.getImageBase64(),
                    request.getImageMimeType() != null ? request.getImageMimeType() : "image/png"
            );
            agentResponse = agentRunner.invokeWithScreenshotAnalysis(sessionId, screenshotAnalysis);
        } else {
            agentResponse = agentRunner.invoke(sessionId, userMessage);
        }

        // 保存助手回复
        ChatMessage assistantMsg = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .role("assistant")
                .content(agentResponse)
                .build();
        messageRepository.save(assistantMsg);

        // 更新会话标题（首次对话时）
        updateSessionTitle(sessionId, userMessage);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .messageId(assistantMsg.getId())
                .role("assistant")
                .content(agentResponse)
                .build();
    }

    /**
     * 发送消息并流式获取回复
     */
    @Transactional
    public Flux<String> sendMessageStream(ChatRequest request, String userId) {
        String sessionId = ensureSession(request, userId);
        String userMessage = buildUserMessage(request);

        // 保存用户消息
        ChatMessage userMsg = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .role("user")
                .content(userMessage)
                .imageUrl(request.getImageBase64() != null ? "screenshot" : null)
                .build();
        messageRepository.save(userMsg);

        // 更新会话标题
        updateSessionTitle(sessionId, userMessage);

        // 获取流式输出
        Flux<String> stream;
        if (request.getImageBase64() != null) {
            String screenshotAnalysis = multimodalService.analyzeScreenshot(
                    request.getImageBase64(),
                    request.getImageMimeType() != null ? request.getImageMimeType() : "image/png"
            );
            stream = agentRunner.stream(sessionId,
                    "以下是一张错误截图的分析结果，请结合知识库进一步分析：\n\n" + screenshotAnalysis);
        } else {
            stream = agentRunner.stream(sessionId, userMessage);
        }

        // 收集完整回复并在流结束后保存assistant消息
        StringBuilder fullResponse = new StringBuilder();
        return stream
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    if (fullResponse.length() > 0) {
                        ChatMessage assistantMsg = ChatMessage.builder()
                                .id(UUID.randomUUID().toString())
                                .sessionId(sessionId)
                                .role("assistant")
                                .content(fullResponse.toString())
                                .build();
                        messageRepository.save(assistantMsg);
                    }
                });
    }

    private String ensureSession(ChatRequest request, String userId) {
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            return request.getSessionId();
        }
        ChatSession session = createSession(userId);
        return session.getId();
    }

    private String buildUserMessage(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getMessage() != null) {
            sb.append(request.getMessage());
        }
        if (request.getFileId() != null) {
            String fileContent = fileService.readFileContent(request.getFileId());
            if (fileContent != null) {
                sb.append("\n\n[导入的文件内容]:\n").append(fileContent);
            }
        }
        return sb.toString();
    }

    private void updateSessionTitle(String sessionId, String userMessage) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            if ("新对话".equals(session.getTitle()) && userMessage.length() > 0) {
                String title = userMessage.length() > 30
                        ? userMessage.substring(0, 30) + "..."
                        : userMessage;
                session.setTitle(title);
                sessionRepository.save(session);
            }
        });
    }
}
