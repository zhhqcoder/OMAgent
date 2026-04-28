package com.eastcom.omagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @Column(length = 36)
    private String id;

    @Column(length = 36, nullable = false)
    private String sessionId;

    @Column(length = 20, nullable = false)
    private String role; // user / assistant / system

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 2000)
    private String sources; // JSON格式的引用来源

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
