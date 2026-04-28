package com.eastcom.omagent.controller;

import com.eastcom.omagent.dto.ChatRequest;
import com.eastcom.omagent.dto.ChatResponse;
import com.eastcom.omagent.entity.ChatMessage;
import com.eastcom.omagent.entity.ChatSession;
import com.eastcom.omagent.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public ChatSession createSession(@RequestParam(defaultValue = "default") String userId) {
        return chatService.createSession(userId);
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/sessions")
    public List<ChatSession> getSessions(@RequestParam(defaultValue = "default") String userId) {
        return chatService.getSessions(userId);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(@PathVariable String sessionId,
                              @RequestParam(defaultValue = "default") String userId) {
        chatService.deleteSession(sessionId, userId);
    }

    /**
     * 获取会话消息历史
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessage> getMessages(@PathVariable String sessionId) {
        return chatService.getMessageHistory(sessionId);
    }

    /**
     * 发送消息（同步）
     */
    @PostMapping("/messages")
    public ChatResponse sendMessage(@RequestBody ChatRequest request,
                                    @RequestParam(defaultValue = "default") String userId) {
        return chatService.sendMessage(request, userId);
    }

    /**
     * 发送消息（SSE流式）
     */
    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessageStream(@RequestBody ChatRequest request,
                                          @RequestParam(defaultValue = "default") String userId) {
        return chatService.sendMessageStream(request, userId);
    }
}
