package com.eastcom.omagent.rag;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;

@Service
public class MultimodalService {

    private final ChatModel multimodalChatModel;

    private static final String SCREENSHOT_ANALYSIS_PROMPT = """
            你是一个专业的OMS运维日志分析专家。请仔细分析这张截图中显示的错误信息，包括：
            1. 识别截图中的错误类型和关键错误信息
            2. 提取错误日志中的异常堆栈、错误码、时间戳等关键信息
            3. 初步判断问题类别：程序Bug / 配置错误 / 环境问题 / 网络问题
            4. 如果有相关的配置项，列出可能需要检查的配置
            
            请以结构化格式输出分析结果。
            """;

    public MultimodalService(@Qualifier("multimodalChatModel") ChatModel multimodalChatModel) {
        this.multimodalChatModel = multimodalChatModel;
    }

    /**
     * 分析截图中的错误信息
     *
     * @param imageBase64 图片的Base64编码
     * @param mimeType    图片MIME类型
     * @return 分析结果文本
     */
    public String analyzeScreenshot(String imageBase64, String mimeType) {
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(mimeType))
                .data(URI.create("data:" + mimeType + ";base64," + imageBase64))
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text(SCREENSHOT_ANALYSIS_PROMPT)
                .media(media)
                .build();

        Prompt prompt = new Prompt(java.util.List.of(userMessage));
        ChatResponse response = multimodalChatModel.call(prompt);

        return response.getResult().getOutput().getText();
    }

    /**
     * 结合上下文分析截图
     *
     * @param imageBase64 图片的Base64编码
     * @param mimeType    图片MIME类型
     * @param context     额外的上下文信息（如用户描述的问题）
     * @return 分析结果文本
     */
    public String analyzeScreenshotWithContext(String imageBase64, String mimeType, String context) {
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(mimeType))
                .data(URI.create("data:" + mimeType + ";base64," + imageBase64))
                .build();

        String combinedPrompt = SCREENSHOT_ANALYSIS_PROMPT + "\n\n用户补充说明：" + context;

        UserMessage userMessage = UserMessage.builder()
                .text(combinedPrompt)
                .media(media)
                .build();

        Prompt prompt = new Prompt(java.util.List.of(userMessage));
        ChatResponse response = multimodalChatModel.call(prompt);

        return response.getResult().getOutput().getText();
    }
}
