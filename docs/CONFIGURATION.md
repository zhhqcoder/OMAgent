# OMAgent 配置文件说明书

本文档详细说明 OMAgent 项目中所有配置文件的用途、配置项含义及推荐配置。

---

## 一、配置文件清单

| 文件 | 路径 | 用途 |
|------|------|------|
| `pom.xml` | 项目根目录 | Maven 依赖和构建配置 |
| `application.yml` | `src/main/resources/` | 后端主配置文件 |
| `application-dev.yml` | `src/main/resources/` | 开发环境配置（覆盖主配置） |
| `schema.sql` | `src/main/resources/` | MySQL 数据库初始化脚本 |
| `vite.config.js` | `omagent-ui/` | 前端 Vite 构建配置 |
| `tailwind.config.js` | `omagent-ui/` | 前端 TailwindCSS 主题配置 |
| `postcss.config.js` | `omagent-ui/` | 前端 PostCSS 配置 |
| `tsconfig.json` | `omagent-ui/` | 前端 TypeScript 编译配置 |
| `package.json` | `omagent-ui/` | 前端 NPM 依赖配置 |

---

## 二、后端配置详解

### 2.1 pom.xml — Maven 项目配置

#### 基础信息

| 配置项 | 值 | 说明 |
|--------|-----|------|
| groupId | `com.eastcom` | 组织标识 |
| artifactId | `omagent` | 项目标识 |
| version | `1.0.0` | 项目版本 |
| java.version | `17` | JDK 版本要求 |

#### 核心依赖版本

| 依赖 | 版本 | 说明 |
|------|------|------|
| Spring Boot | `3.3.6` | 基础框架 |
| Spring AI | `1.1.2` | AI 抽象层 |
| Spring AI Alibaba | `1.1.2.0` | 阿里云 AI 集成 |
| Spring AI Alibaba Extensions | `1.1.2.1` | Agent 框架扩展 |
| Jedis | `7.3.0` | Redis 客户端（覆盖 spring-ai-redis-store 默认版本） |
| Commons IO | `2.18.0` | 文件操作工具 |

#### 关键依赖排除说明

| 依赖 | 排除项 | 原因 |
|------|--------|------|
| `spring-ai-alibaba-agent-framework` | `mcp-json-jackson2`, `mcp-json` | 当前方案使用 ToolCallback，不需要 MCP 协议 |
| `spring-ai-redis-store` | `jedis`（默认版本） | 需要显式引入 Jedis 7.3.0 以支持向量搜索 |
| `spring-boot-starter-data-redis` | `lettuce-core` | 统一使用 Jedis 作为 Redis 客户端，避免冲突 |

---

### 2.2 application.yml — 主配置文件

#### 服务器配置

```yaml
server:
  port: 8080          # 后端 HTTP 服务端口
```

| 配置项 | 默认值 | 说明 | 推荐值 |
|--------|--------|------|--------|
| `server.port` | `8080` | 后端监听端口 | 生产环境可改为 `8443`（配合 HTTPS） |

---

#### DashScope AI 配置

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:sk-your-api-key-here}
      chat:
        options:
          model: qwen-plus
          temperature: 0.7
          top-p: 0.9
          max-tokens: 4096
      multimodal:
        options:
          model: qwen-vl-max
      embedding:
        options:
          model: text-embedding-v1
    model:
      embedding: dashscope
```

| 配置项 | 默认值 | 说明 | 合理范围 |
|--------|--------|------|----------|
| `api-key` | 环境变量 `DASHSCOPE_API_KEY` | DashScope API 密钥，**必填** | 从 [阿里云控制台](https://dashscope.console.aliyun.com/) 获取 |
| `chat.options.model` | `qwen-plus` | 文本对话模型 | `qwen-plus`（均衡）/ `qwen-max`（更强）/ `qwen-turbo`（更快） |
| `chat.options.temperature` | `0.7` | 回答随机性，越高越多样 | `0.1~0.3`（精确问答）/ `0.7~0.9`（创意生成） |
| `chat.options.top-p` | `0.9` | 核采样概率阈值 | `0.8~0.95`，与 temperature 互斥调优 |
| `chat.options.max-tokens` | `4096` | 单次回复最大 token 数 | `2048~8192`，运维场景 `4096` 足够 |
| `multimodal.options.model` | `qwen-vl-max` | 多模态截图分析模型 | `qwen-vl-max`（精度高）/ `qwen-vl-plus`（速度快） |
| `embedding.options.model` | `text-embedding-v1` | 文本向量化模型 | `text-embedding-v1`（维度 1536） |

> **提示**：运维问答场景建议 `temperature=0.3` 以获得更精确的回答；多模态模型在 `AiConfig.java` 中硬编码了 `temperature=0.3` 和 `maxToken=2000`。

---

#### Redis 配置

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      jedis:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2
          max-wait: -1ms
```

| 配置项 | 默认值 | 说明 | 推荐值 |
|--------|--------|------|--------|
| `host` | `127.0.0.1` | Redis 服务地址 | 生产环境改为实际 IP |
| `port` | `6379` | Redis 端口 | 默认即可 |
| `password` | 空 | Redis 密码 | 生产环境**必须设置** |
| `pool.max-active` | `16` | 连接池最大连接数 | 并发高时调至 `32` |
| `pool.max-idle` | `8` | 连接池最大空闲连接 | 通常为 `max-active` 的一半 |
| `pool.min-idle` | `2` | 连接池最小空闲连接 | 保持 `2~4` |
| `pool.max-wait` | `-1ms` | 获取连接最大等待时间（-1 无限等待） | 生产环境建议 `3000ms` |

> **重要**：Redis 需安装 **RediSearch** 模块（Redis Stack 自带），向量搜索依赖 `FT.CREATE`、`FT.SEARCH` 等命令。

---

#### MySQL 配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/omagent?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
```

| 配置项 | 默认值 | 说明 | 推荐值 |
|--------|--------|------|--------|
| `url` | `localhost:3306/omagent` | 数据库连接 URL | 生产环境改为实际地址，开启 `useSSL=true` |
| `username` | `root` | 数据库用户名 | 生产环境创建专用账号 |
| `password` | `root` | 数据库密码 | 生产环境**必须修改** |
| `jpa.hibernate.ddl-auto` | `update` | 表结构自动更新策略 | `update`（开发）/ `validate`（生产）/ `none`（生产更安全） |
| `jpa.show-sql` | `false` | 是否打印 SQL | 开发时可设为 `true` |
| `jpa.properties.hibernate.format_sql` | `true` | 格式化 SQL 日志 | 配合 `show-sql` 使用 |

> **注意**：`ddl-auto: update` 会自动创建/更新表结构，但不会删除列。生产环境建议改为 `validate` 或 `none`，通过 `schema.sql` 手动管理。

---

#### 文件上传配置

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 20MB
```

| 配置项 | 默认值 | 说明 | 推荐值 |
|--------|--------|------|--------|
| `max-file-size` | `10MB` | 单个文件最大大小 | 日志文件可能较大，可调至 `50MB` |
| `max-request-size` | `20MB` | 单次请求最大大小 | 应 ≥ `max-file-size` × 同时上传数 |

---

#### OMAgent 自定义配置

```yaml
omagent:
  upload:
    dir: ${UPLOAD_DIR:./uploads}
  knowledge:
    config-index:
      name: oms-config-index
      prefix: oms-config
    source-index:
      name: oms-source-index
      prefix: oms-source
    preset-dir: classpath:knowledge/
  screenshot:
    max-size: 5MB
    allowed-types: image/jpeg,image/png,image/webp
```

| 配置项 | 默认值 | 说明 | 推荐值 |
|--------|--------|------|--------|
| `upload.dir` | `./uploads` | 上传文件存储目录 | 生产环境改为绝对路径如 `/data/omagent/uploads` |
| `knowledge.config-index.name` | `oms-config-index` | 配置知识库 Redis 索引名 | 保持默认，不同环境可用不同前缀区分 |
| `knowledge.config-index.prefix` | `oms-config` | 配置知识库 Redis Key 前缀 | 保持默认 |
| `knowledge.source-index.name` | `oms-source-index` | 源码日志库 Redis 索引名 | 保持默认 |
| `knowledge.source-index.prefix` | `oms-source` | 源码日志库 Redis Key 前缀 | 保持默认 |
| `knowledge.preset-dir` | `classpath:knowledge/` | 预置知识文件目录 | 可改为 `file:/data/omagent/knowledge/` 读取外部目录 |
| `screenshot.max-size` | `5MB` | 截图最大大小 | 多模态模型限制，不建议超过 `10MB` |
| `screenshot.allowed-types` | `image/jpeg,image/png,image/webp` | 允许的图片格式 | 按需增减，注意 qwen-vl 不支持 GIF |

---

#### 日志级别配置

```yaml
logging:
  level:
    com.eastcom.omagent: DEBUG
    org.springframework.ai: DEBUG
```

| 配置项 | 默认值 | 说明 | 推荐值 |
|--------|--------|------|--------|
| `com.eastcom.omagent` | `DEBUG` | 项目业务日志 | 开发 `DEBUG` / 生产 `INFO` |
| `org.springframework.ai` | `DEBUG` | Spring AI 框架日志 | 开发 `DEBUG` / 生产 `WARN` |

---

### 2.3 application-dev.yml — 开发环境配置

此文件在 `spring.profiles.active=dev` 时加载，覆盖主配置中的敏感信息为环境变量引用：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}    # 必须通过环境变量设置，无默认值
  data:
    redis:
      host: ${REDIS_HOST}               # 必须通过环境变量设置
      password: ${REDIS_PASSWORD}       # 必须通过环境变量设置
  datasource:
    url: jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT:3306}/omagent?...
    username: ${MYSQL_USER}             # 必须通过环境变量设置
    password: ${MYSQL_PASSWORD}         # 必须通过环境变量设置

logging:
  level:
    com.eastcom.omagent: INFO           # 开发环境降低业务日志级别
    org.springframework.ai: WARN        # 开发环境降低AI框架日志级别
```

> **使用方式**：启动时加 `--spring.profiles.active=dev`，所有密码类配置通过环境变量注入，不在代码中存储明文。

---

### 2.4 schema.sql — 数据库初始化脚本

自动创建 `omagent` 数据库及三张表：

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `chat_session` | 聊天会话 | `id`(UUID), `title`, `user_id`, `created_at`, `updated_at` |
| `chat_message` | 聊天消息 | `id`(UUID), `session_id`(FK), `role`(user/assistant/system), `content`, `image_url`, `sources`(JSON) |
| `upload_file` | 上传文件记录 | `id`(UUID), `original_name`, `file_type`(txt/image), `knowledge_type`(config/source), `vectorized`(0/1) |

> **注意**：由于 `jpa.ddl-auto=update`，应用启动时会自动建表，此脚本作为初始化参考。生产环境建议设 `ddl-auto=none` 并手动执行此脚本。

---

## 三、前端配置详解

### 3.1 vite.config.js — Vite 构建配置

```javascript
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',       // 监听所有网络接口
    port: 5173,             // 前端开发服务器端口
    allowedHosts: true,     // 允许所有主机访问
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // 后端地址
        changeOrigin: true,
      }
    }
  }
})
```

| 配置项 | 默认值 | 说明 | 推荐值 |
|--------|--------|------|--------|
| `server.host` | `0.0.0.0` | 开发服务器监听地址 | 默认即可，允许局域网访问 |
| `server.port` | `5173` | 前端端口 | 不与后端冲突即可 |
| `proxy./api.target` | `http://localhost:8080` | 后端 API 代理地址 | 与后端 `server.port` 一致 |

> **生产部署**：生产环境不需要 Vite 代理，前端构建为静态文件由 Nginx 托管，或后端 `spring-boot-starter-static` 直接服务。

---

### 3.2 tailwind.config.js — 主题配置

项目采用深色主题，定义了以下自定义颜色体系：

| 颜色名 | 色值 | 用途 |
|--------|------|------|
| `primary` | `#1890FF` | 主色调（按钮、链接、高亮） |
| `primary-dark` | `#096DD9` | 主色调深色态（hover） |
| `primary-deeper` | `#0050B3` | 主色调更深态（active） |
| `surface` | `#1F1F1F` | 主背景色 |
| `surface-light` | `#2A2A2A` | 面板背景色 |
| `surface-lighter` | `#333333` | 输入框/卡片背景色 |
| `success` | `#52C41A` | 成功状态 |
| `warning` | `#FAAD14` | 警告状态 |
| `danger` | `#FA541C` | 错误/危险状态 |

字体：`PingFang-SC`（macOS）/ `Microsoft YaHei`（Windows）。

---

### 3.3 package.json — 前端依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `vue` | `^3.5.13` | 前端框架 |
| `vue-router` | `^4.5.0` | 路由管理 |
| `markdown-it` | `^14.1.0` | Markdown 渲染 |
| `highlight.js` | `^11.11.1` | 代码块语法高亮 |
| `tailwindcss` | `^3.4.17` | CSS 工具类框架 |
| `tailwindcss-animate` | `^1.0.7` | Tailwind 动画插件 |
| `@vitejs/plugin-vue` | `^5.2.3` | Vite Vue 插件 |
| `vite` | `^6.3.2` | 构建工具 |

---

## 四、Java 配置类说明

### 4.1 AiConfig.java — AI 模型配置

注册三个核心 Bean：

| Bean | 类型 | 用途 | 关键参数 |
|------|------|------|----------|
| `dashScopeApi` | `DashScopeApi` | DashScope API 客户端 | `api-key` |
| `chatModel` | `ChatModel` | 文本对话模型 | `model=qwen-plus`, `temperature`, `topP`, `maxTokens` |
| `embeddingModel` | `EmbeddingModel` | 文本向量化模型 | `model=text-embedding-v1`, `textType=DOCUMENT` |
| `multimodalChatModel` | `ChatModel` | 多模态截图分析模型 | `model=qwen-vl-max`, `temperature=0.3`, `maxToken=2000` |

> 多模态模型的 `temperature=0.3` 和 `maxToken=2000` 在代码中硬编码，不在 yml 中配置。

---

### 4.2 RedisVectorStoreConfig.java — 向量存储配置

注册三个 Bean：

| Bean | 类型 | 索引名 | Key 前缀 | 元数据字段 | 用途 |
|------|------|--------|----------|------------|------|
| `jedisPooled` | `JedisPooled` | - | - | - | Redis 连接池 |
| `configVectorStore` | `RedisVectorStore` | `oms-config-index` | `oms-config` | `source`, `doc_type`, `module` | 配置知识库 |
| `sourceVectorStore` | `RedisVectorStore` | `oms-source-index` | `oms-source` | `source`, `doc_type`, `class_name`, `package_name` | 源码日志库 |

> `initializeSchema=true` 表示应用启动时自动创建 Redis 索引（如不存在）。索引使用 DashScope `text-embedding-v1` 模型，向量维度 1536。

---

### 4.3 WebConfig.java — Web 配置

| 功能 | 配置 |
|------|------|
| CORS 跨域 | 允许所有来源访问 `/api/**`，支持 GET/POST/PUT/DELETE/OPTIONS |
| 静态资源映射 | `/uploads/**` → `file:${omagent.upload.dir}/`（上传文件访问路径） |

---

## 五、环境变量汇总

| 环境变量 | 必填 | 默认值 | 说明 |
|----------|------|--------|------|
| `DASHSCOPE_API_KEY` | **是** | 无（dev profile） | DashScope API 密钥 |
| `REDIS_HOST` | 否 | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | 否 | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 否 | 空 | Redis 密码 |
| `MYSQL_HOST` | 否 | `127.0.0.1` | MySQL 地址 |
| `MYSQL_PORT` | 否 | `3306` | MySQL 端口 |
| `MYSQL_USER` | 否 | `root` | MySQL 用户名 |
| `MYSQL_PASSWORD` | 否 | `root` | MySQL 密码 |
| `UPLOAD_DIR` | 否 | `./uploads` | 上传文件存储目录 |

> **最小启动配置**：只需设置 `DASHSCOPE_API_KEY`，其余使用默认值即可本地运行（需本地安装 Redis 和 MySQL）。

---

## 六、典型部署配置示例

### 开发环境

```bash
# 最简启动
export DASHSCOPE_API_KEY=sk-xxx
mvn spring-boot:run

# 前端
cd omagent-ui && npm run dev
```

### 生产环境

```bash
# 后端
export DASHSCOPE_API_KEY=sk-xxx
export REDIS_HOST=10.0.1.100
export REDIS_PASSWORD=your-redis-pwd
export MYSQL_HOST=10.0.1.200
export MYSQL_PASSWORD=your-mysql-pwd
export UPLOAD_DIR=/data/omagent/uploads

java -jar omagent.jar \
  --spring.profiles.active=dev \
  --server.port=8080 \
  --spring.jpa.hibernate.ddl-auto=none \
  --logging.level.com.eastcom.omagent=INFO

# 前端构建
cd omagent-ui && npm run build
# 将 dist/ 目录部署到 Nginx
```
