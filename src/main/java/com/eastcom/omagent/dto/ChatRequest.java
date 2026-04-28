package com.eastcom.omagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private String sessionId;

    private String message;

    /** 截图Base64编码 */
    private String imageBase64;

    /** 图片MIME类型 */
    private String imageMimeType;

    /** 上传文件ID（已通过FileController上传的文件） */
    private String fileId;
}
