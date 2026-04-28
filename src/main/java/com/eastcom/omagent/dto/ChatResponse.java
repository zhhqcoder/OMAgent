package com.eastcom.omagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String sessionId;

    private String messageId;

    private String role;

    private String content;

    private List<String> sources;
}
