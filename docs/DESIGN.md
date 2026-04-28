# OMAgent 设计文档

## 产品概述

基于 Spring AI Alibaba 的 OMS 运维智能助手，提供聊天式交互界面，帮助现场运维人员快速解决配置疑问和日志排查问题。支持文本对话、TXT文件导入、截图分析三种输入方式。

## 核心功能

### 1. 智能对话

- 提供聊天窗口，支持多轮对话和上下文记忆
- 流式输出，实时显示AI回复内容
- 对话历史持久化到MySQL

### 2. 配置问答（RAG增强）

- 基于OMS配置说明书、配置模板构建向量知识库
- 回答配置项含义、合理配置范围、配置间依赖关系
- 引用知识来源，提供可追溯的依据

### 3. 日志排查

- 支持粘贴错误日志文本或导入TXT日志文件
- 支持截图直接发送给多模态模型分析错误信息
- 基于OMS源码知识库判断问题根因（程序Bug/配置错误/环境问题）
- 给出排查建议和修复方案

### 4. 知识库管理

- 支持TXT文件导入，自动分块、向量化存入Redis
- 支持截图/图片上传，多模态模型直接分析
- 知识库按类型分索引：配置知识库、源码/日志知识库

## 技术栈

| 类别 | 技术选型 |
|------|----------|
| 后端框架 | Java 17 + Spring Boot 3.3.6 + Spring AI Alibaba 1.1.2.0 |
| 大模型 | DashScope 通义千问（qwen-plus 对话 + qwen-vl-max 多模态图片分析） |
| 向量存储 | Redis + spring-ai-redis-store + DashScope Embedding（text-embedding-v1） |
| 关系数据库 | MySQL（会话历史、用户管理、文件记录） |
| Agent框架 | Spring AI Alibaba ReactAgent + ToolCallback |
| 前端 | Vue3 + TailwindCSS + Markdown渲染（highlight.js代码高亮） |
| 实时通信 | SSE（Server-Sent Events）流式输出 |

## 实现方案

### 整体架构

前后端分离架构。后端提供 REST API + SSE 流式接口，Vue3 前端独立项目通过 HTTP 调用。后端核心采用 Spring AI Alibaba 的 ReactAgent，挂载两个专业 Tool（ConfigSearchTool 配置检索 + LogAnalysisTool 日志分析），通过 RAG 从 Redis 向量库检索 OMS 项目知识后交给 LLM 生成回答。截图走 DashScope 多模态模型(qwen-vl)直接分析图片中的错误信息。

### 关键设计决策

1. **双知识库索引**：Redis 中创建 `oms-config-index`（配置说明书+配置模板）和 `oms-source-index`（源码+日志FAQ），按场景路由检索，避免配置问题被源码噪声干扰
2. **ReactAgent + Tool模式**：Agent 根据用户问题自动决定调用配置检索工具还是日志分析工具，而非简单 RAG，让 LLM 自主判断是否需要检索、检索哪个库
3. **多模态截图分析**：截图不经过向量化，直接以 base64 传给 DashScope qwen-vl-max 模型提取错误信息，再交给日志分析 Agent 处理
4. **流式输出**：使用 SSE 推送 Agent 中间推理过程和最终回答，提升用户等待体验
5. **ETL管道复用**：参考 AgentDemo 的 TextReader + TokenTextSplitter + RedisVectorWriter 模式，扩展支持外部文件上传导入

### 数据流

```
用户输入(文本/TXT文件/截图)
  → ChatController
  → 判断输入类型
  → 文本: 直接交给ReactAgent
  → TXT文件: ETL管道向量化后检索
  → 截图: qwen-vl多模态分析 → 文本结果交给ReactAgent
  → ReactAgent(配置检索Tool / 日志分析Tool)
  → Redis向量库相似度检索
  → LLM生成回答
  → SSE流式返回前端
```

## 项目目录结构

```
d:/IdeaProjects/OMAgent/
├── pom.xml                              # Maven项目配置
├── docs/
│   └── DESIGN.md                        # 本设计文档
├── src/main/java/com/eastcom/omagent/
│   ├── OmAgentApplication.java          # Spring Boot启动类
│   ├── config/
│   │   ├── AiConfig.java                # AI相关Bean配置(ChatModel, EmbeddingModel)
│   │   ├── RedisVectorStoreConfig.java  # 双索引Redis向量存储配置
│   │   └── WebConfig.java               # CORS跨域配置、文件上传限制
│   ├── controller/
│   │   ├── ChatController.java          # 聊天接口(文本对话、SSE流式输出)
│   │   ├── FileController.java          # 文件上传接口(TXT导入、截图上传)
│   │   └── KnowledgeController.java     # 知识库管理接口(导入状态、重建索引)
│   ├── agent/
│   │   ├── OmAgentFactory.java          # OMS运维Agent工厂，创建配置问答/日志分析Agent
│   │   ├── OmSystemPrompt.java          # 系统提示词模板，定义Agent角色和OMS领域知识
│   │   └── OmAgentRunner.java           # Agent执行器，封装invoke/stream调用
│   ├── tool/
│   │   ├── ConfigSearchTool.java        # 配置检索工具，检索oms-config-index向量库
│   │   └── LogAnalysisTool.java         # 日志分析工具，检索oms-source-index向量库
│   ├── rag/
│   │   ├── DocumentEtlService.java      # 文档ETL管道(读取→分块→向量化→写入)
│   │   ├── KnowledgeBaseService.java    # 知识库管理(初始化、检索、状态查询)
│   │   └── MultimodalService.java       # 多模态服务，截图转base64调用qwen-vl分析
│   ├── entity/
│   │   ├── ChatSession.java             # 聊天会话实体
│   │   ├── ChatMessage.java             # 聊天消息实体
│   │   └── UploadFile.java              # 上传文件记录实体
│   ├── repository/
│   │   ├── ChatSessionRepository.java   # 会话JPA Repository
│   │   ├── ChatMessageRepository.java   # 消息JPA Repository
│   │   └── UploadFileRepository.java    # 文件记录JPA Repository
│   ├── service/
│   │   ├── ChatService.java             # 聊天业务逻辑(会话管理、消息持久化)
│   │   └── FileService.java             # 文件处理业务逻辑(保存、解析、向量化)
│   └── dto/
│       ├── ChatRequest.java             # 聊天请求DTO(含文本/图片base64/文件ID)
│       ├── ChatResponse.java            # 聊天响应DTO
│       └── KnowledgeImportRequest.java  # 知识导入请求DTO
├── src/main/resources/
│   ├── application.yml                  # 主配置(DashScope/Redis/MySQL/文件路径)
│   ├── application-dev.yml              # 开发环境配置
│   ├── knowledge/                       # 预置知识文件目录
│   └── schema.sql                       # MySQL建表脚本
└── omagent-ui/                          # Vue3前端独立项目
    ├── package.json
    ├── vite.config.js
    ├── tailwind.config.js
    ├── postcss.config.js
    ├── tsconfig.json
    ├── index.html
    └── src/
        ├── App.vue                      # 根组件(含路由布局)
        ├── main.js                      # 入口文件
        ├── index.css                    # 全局样式(深色主题CSS变量)
        ├── api/
        │   └── chat.js                  # 后端API调用封装(含SSE)
        ├── components/
        │   ├── ChatWindow.vue           # 主聊天窗口组件
        │   ├── MessageItem.vue          # 消息气泡组件(支持Markdown渲染)
        │   ├── InputBar.vue             # 输入栏(文本+附件+截图上传)
        │   └── SessionList.vue          # 左侧会话列表
        ├── views/
        │   ├── ChatView.vue             # 聊天主页面
        │   ├── KnowledgeView.vue        # 知识库管理页面
        │   └── SettingsView.vue         # 设置页面
        └── utils/
            ├── sse.js                   # SSE流式接收工具
            └── markdown.js              # Markdown渲染配置(markdown-it + highlight.js)
```

## 系统提示词设计

### 配置问答提示词

```
你是OMS（Operations Management System）运维智能助手，专门负责解答现场运维人员关于OMS系统配置的疑问。

职责：
1. 解答配置项的含义和作用
2. 说明配置项的合理取值范围和建议值
3. 解释配置项之间的依赖关系和影响
4. 提供配置修改的建议和注意事项

回答规范：
- 回答必须基于检索到的知识库内容，不要编造配置信息
- 引用来源时标注文档出处
- 给出明确建议值和范围时，说明依据
- 如果知识库中没有相关信息，如实告知并建议查阅官方文档
- 使用中文回答，专业术语保留英文原文
```

### 日志分析提示词

```
你是OMS运维日志分析专家，专门负责帮助现场运维人员排查程序错误日志。

职责：
1. 分析错误日志，识别异常类型和根因
2. 判断问题是程序Bug、配置错误、环境问题还是网络问题
3. 提供排查步骤和修复建议
4. 关联相关源码和配置知识给出精准定位

问题分类标准：
- 程序Bug: 异常堆栈指向代码逻辑错误（NullPointerException、ClassCastException等）
- 配置错误: 异常信息包含连接失败、认证失败、找不到Bean等配置相关错误
- 环境问题: 涉及磁盘空间、内存不足、端口占用、文件权限等
- 网络问题: 连接超时、拒绝连接、SSL握手失败等
```

## API 设计

### 聊天接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/sessions` | 创建新会话 |
| GET | `/api/chat/sessions` | 获取会话列表 |
| DELETE | `/api/chat/sessions/{id}` | 删除会话 |
| GET | `/api/chat/sessions/{id}/messages` | 获取会话消息历史 |
| POST | `/api/chat/sessions/{id}/send` | 发送消息（返回SSE流） |

### 文件接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/files/upload` | 上传文件（TXT/截图） |
| GET | `/api/files/{id}` | 获取文件信息 |

### 知识库接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/import` | 导入知识文件到向量库 |
| GET | `/api/knowledge/stats` | 获取知识库统计信息 |

## 数据库设计

### chat_session 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 会话ID |
| title | VARCHAR(200) | 会话标题 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### chat_message 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 消息ID |
| session_id | BIGINT FK | 关联会话 |
| role | VARCHAR(20) | 角色(user/assistant/system) |
| content | TEXT | 消息内容 |
| image_url | VARCHAR(500) | 截图URL(可选) |
| sources | TEXT | 引用来源(JSON) |
| created_at | DATETIME | 创建时间 |

### upload_file 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 文件ID |
| original_name | VARCHAR(255) | 原始文件名 |
| file_path | VARCHAR(500) | 存储路径 |
| file_type | VARCHAR(50) | 文件类型(txt/image) |
| file_size | BIGINT | 文件大小(bytes) |
| knowledge_type | VARCHAR(50) | 知识库类型(config/source) |
| imported | BOOLEAN | 是否已导入向量库 |
| created_at | DATETIME | 创建时间 |

## 向量存储设计

Redis 中维护两个向量索引，使用 DashScope text-embedding-v1 模型（维度1536）：

| 索引名 | 前缀 | 用途 | 内容来源 |
|--------|------|------|----------|
| oms-config-index | oms-config | 配置知识库 | OMS配置说明书、配置YML模板 |
| oms-source-index | oms-source | 源码日志库 | OMS源码片段、FAQ、接口说明 |

## 页面规划（3个核心页面）

### 1. 聊天主页面

- **顶部导航栏**: 应用Logo"OMS运维助手" + 模型状态指示灯 + 设置入口，深色磨砂背景
- **左侧会话面板(260px)**: 会话列表，支持新建/删除/重命名会话，当前会话高亮，底部显示知识库状态
- **中间聊天区域**: 消息流式展示，用户消息右对齐蓝色气泡，AI回复左对齐深灰气泡+Markdown渲染+代码高亮，AI回复底部显示引用来源标签
- **底部输入栏**: 多行文本输入框 + 附件按钮(TXT上传) + 截图按钮 + 发送按钮，支持Ctrl+Enter发送

### 2. 知识库管理页面

- **顶部**: 标题"知识库管理" + 导入按钮
- **知识库列表**: 两个卡片(配置知识库/源码日志库)，各显示文档数量、最后更新时间、索引状态
- **导入面板**: 拖拽上传区域 + 文件列表 + 导入进度条

### 3. 设置页面

- **模型配置**: 当前模型显示 + 参数调节(temperature/topP/maxTokens)
- **连接状态**: DashScope/Redis/MySQL连接状态指示

## 设计风格

采用科技感深色主题设计，契合运维工具的专业定位。整体风格偏 Dark Mode + Glassmorphism，深色背景配合半透明玻璃质感面板，营造现代智能助手氛围。

### 交互细节

- 消息流式输出时显示打字机动画效果
- 文件上传显示进度条和解析状态
- 截图上传后显示缩略图预览
- AI回复中代码块一键复制
- 会话切换时自动加载历史消息

## 启动方式

### 环境依赖

- JDK 17+
- Redis 6.0+（需启用 Redis Stack 或 RediSearch 模块以支持向量搜索）
- MySQL 8.0+
- DashScope API Key

### 后端启动

```bash
# 设置环境变量（必填）
export DASHSCOPE_API_KEY=sk-your-api-key
# 以下按需修改，默认连接本地 Redis/MySQL
export MYSQL_PASSWORD=your-password

# 创建数据库并导入表结构
mysql -u root -p < src/main/resources/schema.sql

# 方式一：使用默认配置（application.yml，敏感配置有默认兜底值）
mvn spring-boot:run

# 方式二：使用开发配置（application-dev.yml，敏感配置无默认值，必须设环境变量，否则启动报错）
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 前端启动

```bash
cd d:/IdeaProjects/OMAgent/omagent-ui
npm install
npm run dev
# 访问 http://localhost:5173
```
