-- OMAgent 数据库初始化脚本

CREATE DATABASE IF NOT EXISTS omagent
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE omagent;

-- 聊天会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200) NOT NULL DEFAULT '新对话',
    user_id VARCHAR(50) NOT NULL DEFAULT 'default',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 聊天消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant/system',
    content TEXT NOT NULL,
    image_url VARCHAR(500) COMMENT '截图URL',
    sources VARCHAR(2000) COMMENT '引用来源JSON',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 上传文件记录表
CREATE TABLE IF NOT EXISTS upload_file (
    id VARCHAR(36) PRIMARY KEY,
    original_name VARCHAR(500) NOT NULL,
    stored_name VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_type VARCHAR(50) NOT NULL COMMENT 'txt/image',
    file_size BIGINT NOT NULL DEFAULT 0,
    knowledge_type VARCHAR(50) COMMENT 'config/source',
    vectorized TINYINT NOT NULL DEFAULT 0 COMMENT '是否已向量化',
    user_id VARCHAR(50) NOT NULL DEFAULT 'default',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_knowledge_type (knowledge_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
