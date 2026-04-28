package com.eastcom.omagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeImportRequest {

    /** 文件ID */
    private String fileId;

    /** 知识库类型: config / source */
    private String knowledgeType;

    /** 文档类型标签 */
    private String docType;

    /** 模块标签 */
    private String module;
}
