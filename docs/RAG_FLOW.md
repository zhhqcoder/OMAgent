# OMAgent RAG 检索流程文档

> 更新时间：2026-04-30

## 一、入库流程（DocumentEtlService）

```
用户上传文件
    │
    ▼
1. loadAndSplit() — 按文件类型选择分块策略
    ├── .zip/.7z 压缩包 → loadArchiveAndSplit()
    │     ├── extractArchive() 解压（.zip用Java内置，.7z调用系统7z命令）
    │     ├── 遍历支持的源码/文本文件（.java/.xml/.yml/.md/...）
    │     ├── .java 文件 → JavaCodeSplitter（按类/方法边界语义分块）
    │     │     ├── 提取 package_name / class_name 到 metadata
    │     │     ├── 提取 method_name / scope_chain / imports 到 metadata
    │     │     ├── 每个 chunk 注入上下文头（包名、作用域链、方法签名、import）
    │     │     └── 分块策略：类≤1500字符整体chunk，大类按方法切分，大方法语句边界二次切分
    │     ├── .pom/pom.xml → PomFileSplitter（按dependency/plugin边界结构化分块）  ← 新增
    │     │     ├── 提取 pom_groupId / pom_artifactId / pom_version / pom_dependencies 到 metadata
    │     │     ├── 每个 <dependency> 独立chunk，注入项目GAV上下文头
    │     │     ├── 每个 <plugin> 独立chunk
    │     │     ├── <properties> 独立chunk
    │     │     └── 项目信息（parent+GAV）独立chunk
    │     └── 其他文件 → TextReader → TokenTextSplitter(800 token, 200 overlap)
    │
    ├── .md 文件 → loadMarkdownAndSplit()
    │     └── MarkdownSectionSplitter(按 ##/### 标题语义分块)
    │           ├── 按 ## 和 ### 标题切分为独立章节
    │           ├── 每个chunk注入章节上下文头 [章节路径]
    │           ├── metadata 写入 section_path / section_level / section_title
    │           ├── 从标题提取 module 名（如 "2.6 kafka：kafka配置" → "kafka"）
    │           ├── 跳过目录和版本历史
    │           └── 超长章节（>3000字符）二次切分（带200字符overlap）
    │
    ├── .java 文件 → loadJavaAndSplit()
    │     └── JavaCodeSplitter(按类/方法边界语义分块)
    │           ├── 提取 package / import 列表
    │           ├── 识别类/接口/枚举声明 → 一级分块边界
    │           ├── 识别方法声明 → 二级分块边界
    │           ├── 按大括号匹配确定每个结构的完整范围
    │           ├── 每个 chunk 注入上下文头：
    │           │     # com.eastcom.omagent.rag.DocumentEtlService
    │           │     # Scope: DocumentEtlService > enhanceCamelCaseText
    │           │     # Defines: enhanceCamelCaseText(String text): String
    │           │     # Imports: org.springframework.ai.document.Document, ...
    │           │     # Siblings: enhanceDocuments, generateChineseSummaries
    │           ├── metadata 新增：method_name / scope_chain / imports
    │           └── 分块参数：maxChunkSize=1500, overlapLines=5
    │
    ├── .pom/pom.xml → loadPomAndSplit()  ← 新增
    │     └── PomFileSplitter(按dependency/plugin边界结构化分块)
    │           ├── 提取项目GAV（groupId:artifactId:version）
    │           ├── 每个 <dependency> 独立chunk + 上下文头
    │           ├── 每个 <plugin> 独立chunk + 上下文头
    │           ├── <properties> 独立chunk
    │           ├── 项目信息（parent + GAV）独立chunk
    │           ├── 其他部分（repositories/profiles等）合并chunk
    │           ├── 每个 chunk 注入上下文头：
    │           │     # com.eastcom:omagent:1.0.0
    │           │     # Scope: Dependency > org.springframework.boot:spring-boot-starter-web:3.2.0
    │           ├── metadata 新增：pom_section / pom_dep_gav / pom_dep_scope / pom_dep_optional
    │           └── 分块参数：maxChunkSize=1500
    │
    └── 其他文本 → loadSingleFileAndSplit()
          └── TextReader → TokenTextSplitter(800, 200)
    │
    ▼
2. enhanceDocuments(isSourceCode) — 区分增强策略
    ├── isSourceCode=true（Java源码/POM chunk）→ 跳过驼峰增强
    │     ├── Java: 上下文头已含语义信息，额外追加驼峰词会稀释embedding
    │     └── POM: 标识符用连字符（spring-boot-starter），驼峰增强无价值反而增加噪音
    ├── isSourceCode=false（配置文档/其他文本文件）→ 驼峰标识符增强
    │     ├── 正则匹配驼峰标识符（如 logRecordBackupFilePath）
    │     ├── 拆分为小写词组：log record backup file path
    │     ├── 追加到原文末尾：[关键词索引] log record backup file path logrecordbackupfilepath
    │     └── 保留原文不变，仅追加增强词
    └── importToSourceStore 中根据文件类型自动判断 isSourceCode
          ├── .java 文件 / .pom/pom.xml / 含Java或POM文件的压缩包 → true
          └── 其他文件 → false
    │
    ▼
3. generateChineseSummaries(systemPrompt) — 区分摘要prompt
    ├── Java源码chunk → CODE_SUMMARY_SYSTEM_PROMPT
    │     ├── 用一句话说明方法/类的核心功能
    │     ├── 列出关键参数名和返回值（保留英文标识符）
    │     ├── 提及调用的关键外部服务或依赖
    │     ├── 每个中文词语之间用空格分隔
    │     ├── 不超过100字
    │     └── 必须包含中文领域术语（如"日志分析"、"向量入库"等）
    │
    ├── POM依赖chunk → POM_SUMMARY_SYSTEM_PROMPT  ← 新增
    │     ├── 列出依赖的groupId:artifactId（保留英文标识符）
    │     ├── 用一句话说明该依赖的核心功能
    │     ├── 提及依赖范围（scope）和可选性（optional）如有
    │     ├── 每个中文词语之间用空格分隔
    │     ├── 不超过80字
    │     └── 必须包含中文领域术语（如"Web框架"、"数据库驱动"、"构建插件"等）
    │
    ├── 配置文档/其他文件 → SUMMARY_SYSTEM_PROMPT
    │     ├── 提取配置项名称（保留英文标识符）
    │     ├── 中文说明功能和用途
    │     ├── 每个中文词语之间用空格分隔
    │     ├── 不超过150字
    │     ├── 包含中文领域术语（局数据、告警、割接等）
    │     └── 保持中文词组完整（"软件包 管理"而非"软 件 包 管 理"）
    │
    ├── 摘要存入 metadata.chinese_summary（不写入content，避免稀释embedding）
    └── 截断超长chunk到1500字符后调用LLM
    │
    ▼
4. addInBatches() — 分批写入向量库
    ├── 每20个文档一批（DashScope Embedding API限制）
    └── 调用 VectorStore.add() → 自动生成 embedding → 写入 Redis
```

### JavaCodeSplitter 分块逻辑（新增）

- 基于**大括号匹配+正则解析**，在类/方法边界切分Java代码
- 不引入tree-sitter等外部依赖，纯Java实现
- 识别 `class`/`interface`/`enum` 声明作为一级分块边界
- 识别方法声明（含构造器）作为二级分块边界
- 大括号匹配时忽略注释（`//`/`/* */`）和字符串（`""`/`''`）中的括号

分块规则：
- 一个类 ≤ maxChunkSize(1500字符) → 整个类作为一个chunk
- 一个类 > maxChunkSize → 按方法切分，类头（字段+构造器）单独一个chunk
- 一个方法 > maxChunkSize → 在语句边界(;)处二次切分，带5行overlap

上下文头格式（参照code-chunk的contextualizedText）：
```
# com.eastcom.omagent.rag.DocumentEtlService
# Scope: DocumentEtlService > enhanceCamelCaseText
# Defines: enhanceCamelCaseText(String text): String
# Imports: org.springframework.ai.document.Document, java.util.List
# Siblings: enhanceDocuments, generateChineseSummaries, addInBatches
```

### MarkdownSectionSplitter 分块逻辑

- 匹配 `##` 和 `###` 标题行
- 每个子章节作为独立 chunk，保留完整表格和代码块
- 注入章节上下文头：`[章节路径]\n\n`
- 从标题自动提取 module 名：
  - `"2.6 kafka：kafka配置"` → `"kafka"`
  - `"2.1 Server：oms服务端配置"` → `"server"`
- 跳过目录（"## 目录"）和版本历史章节
- 超长章节（>3000字符）二次切分，优先在空行处断开

### PomFileSplitter 分块逻辑（新增）

- 基于**正则匹配XML结构边界**，在`<dependency>`/`<plugin>`边界切分POM文件
- 确保每个`<dependency>`块完整不被截断
- 分块规则：
  - 项目信息（parent + GAV + packaging）→ 独立chunk
  - `<properties>` → 独立chunk
  - 每个 `<dependency>` → 独立chunk
  - 每个 `<plugin>` → 独立chunk
  - 其他部分（repositories/profiles等）→ 合并chunk
- 上下文头格式：
```
# com.eastcom:omagent:1.0.0
# Scope: Dependency > org.springframework.boot:spring-boot-starter-web:3.2.0
```
- metadata 新增字段：
  - `pom_section` — 分块类型（project-info / properties / dependency / plugin / other）
  - `pom_dep_gav` — 依赖GAV（如 `org.springframework.boot:spring-boot-starter-web:3.2.0`）
  - `pom_dep_scope` — 依赖范围（如 compile / test / provided）
  - `pom_dep_optional` — 是否可选（true/false）
- 分块参数：maxChunkSize=1500

### 驼峰标识符增强（仅配置文档）

- 正则：`[a-z][a-zA-Z0-9]{3,}|[A-Z][a-z]+[a-zA-Z0-9]*`
- 仅处理包含大写字母的 token
- 拆分规则：`logRecordBackupFilePath` → `log Record Backup File Path`
- 追加格式：`[关键词索引] log record backup file path logrecordbackupfilepath`
- **Java源码chunk跳过此步骤**，上下文头已自然包含语义信息
- **POM chunk跳过此步骤**，POM标识符用连字符（如`spring-boot-starter`），驼峰增强无价值

### 中文摘要生成

SUMMARY_SYSTEM_PROMPT（配置文档）关键规则：
1. 提取配置项名称（保留英文标识符）
2. 中文说明每个配置项的功能和用途
3. 每个中文词语之间必须用空格分隔
4. 不超过150字
5. 只输出空格分词的摘要内容
6. 必须包含中文领域术语（局数据、告警、割接等）
7. 必须包含模块的中文名称和英文名称
8. **中文分词粒度：保持有意义的中文词组完整，不要拆成单字**
   - "软件包管理" → "软件包 管理"（不是 "软 件 包 管 理"）
   - "局数据" → "局数据"（不是 "局 数 据"）

CODE_SUMMARY_SYSTEM_PROMPT（Java源码）关键规则：
1. 用一句话说明该方法/类的核心功能
2. 列出关键参数名和返回值（保留英文标识符）
3. 提及调用的关键外部服务或依赖
4. 每个中文词语之间必须用空格分隔
5. 不超过100字
6. 只输出空格分词的摘要内容
7. 必须包含中文领域术语作为检索锚点
8. 中文分词粒度保持词组完整，不拆成单字

示例输出（配置文档）：`局数据 bureaudata ftp 模式 配置项 ftpMode 地址 账号 密码 端口 路径 日志记录 备份文件 logRecordBackupFilePath`
示例输出（Java源码）：`文档 增强 驼峰 标识符 拆分 enhanceCamelCaseText 方法 拆分 驼峰命名 小写化 追加 关键词 索引`
示例输出（POM依赖）：`Spring Boot Web starter web框架 提供 REST 开发 spring-boot-starter-web 嵌入式 Tomcat`

### 压缩包支持

- 支持格式：`.zip` + `.7z`
- `.zip`：使用 Java 内置 `ZipInputStream` 解压
- `.7z`：调用系统 `7z.exe` 命令解压（需安装7-Zip，搜索常见安装路径）
- 解压后按文件类型路由：
  - `.java` → JavaCodeSplitter 语义分块
  - `.pom`/`pom.xml` → PomFileSplitter 结构化分块
  - 其他 → TextReader + TokenTextSplitter

---

## 二、Redis 索引 Schema

**两个知识库，索引结构不同：**

### configVectorStore（oms-config-index）

| 字段 | 类型 | 用途 |
|------|------|------|
| content | TEXT | 文档内容（全文检索主字段） |
| embedding | VECTOR | 向量嵌入 |
| source | TEXT | 来源文件名 |
| doc_type | TAG | 文档类型 |
| module | TAG | 模块名（如 slf, dmgw） |
| file_id | TAG | 文件ID（按文件删除用） |
| section_path | TEXT | 章节路径 |
| section_title | TEXT | 章节标题 |
| section_level | NUMERIC | 标题层级 |
| chinese_summary | TEXT | 空格分词的中文摘要 |

### sourceVectorStore（oms-source-index）

| 字段 | 类型 | 用途 |
|------|------|------|
| content | TEXT | 文档内容 |
| embedding | VECTOR | 向量嵌入 |
| source | TEXT | 来源文件名 |
| doc_type | TAG | 文档类型 |
| file_id | TAG | 文件ID |
| class_name | TEXT | Java类名 |
| package_name | TEXT | Java包名 |
| method_name | TEXT | 方法名（JavaCodeSplitter提取） ← 新增 |
| scope_chain | TEXT | 作用域链（如 ClassName>methodName） ← 新增 |
| imports | TEXT | 关键import列表（逗号分隔） ← 新增 |
| pom_groupId | TEXT | POM groupId |
| pom_artifactId | TEXT | POM artifactId |
| pom_version | TEXT | POM版本 |
| pom_dependencies | TEXT | POM依赖列表 |
| chinese_summary | TEXT | 空格分词的中文摘要 |

---

## 三、检索流程（KnowledgeBaseService）— 四级回退

```
用户查询 query（如 "软件包管理配置"）
    │
    ▼
┌─────────────────────────────────────────┐
│  第0步：查询增强 enhanceQuery()          │
│                                         │
│  1. 驼峰标识符拆分（仅英文）              │
│     正则: [a-z][a-zA-Z0-9]{3,}          │
│     例: logBackup → "log backup logbackup"│
│     纯中文查询不触发                      │
│                                         │
│  2. AI中英文术语翻译（ChatModel）          │
│     条件: 查询含中文字符时触发             │
│     System Prompt: OMS领域术语对照表      │
│     输出: JSON {"translations":{"软件包": │
│       ["software","package",...]}}       │
│     解析: 提取所有英文词 + 子词拆分        │
│     降级: 翻译失败→空列表，不影响主流程     │
│                                         │
│  结果: "软件包管理配置 software package   │
│         management config ..."           │
└─────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────┐
│  第1级：向量相似度搜索（增强查询）          │
│                                         │
│  safeVectorSearch(vectorStore,           │
│    enhancedQuery, topK, module, docType) │
│                                         │
│  · SearchRequest.builder()               │
│    .query(enhancedQuery)                 │
│    .topK(有过滤器时 max(topK*3, 15))      │
│    .similarityThreshold(0.1)             │
│                                         │
│  · 不传过滤器给Spring AI                  │
│    （避免TAG/TEXT语法冲突导致KNN报错）      │
│    改为搜索后手动过滤 module + doc_type    │
│                                         │
│  · 异常捕获→返回空列表，由调用方回退        │
└─────────────────────────────────────────┘
    │ 有结果 → 返回
    │ 无结果 ↓
┌─────────────────────────────────────────┐
│  第1.5级：向量搜索（原始查询）             │
│                                         │
│  safeVectorSearch(vectorStore,           │
│    originalQuery, topK, module, docType) │
│                                         │
│  回退原因: AI翻译可能引入噪声，             │
│  原始中文查询的embedding可能更准确          │
└─────────────────────────────────────────┘
    │ 有结果 → 返回
    │ 无结果 ↓
┌─────────────────────────────────────────┐
│  第2级：Redis FT.SEARCH 全文检索          │
│  (fullTextSearch)                        │
│                                         │
│  buildFullTextQuery() 构建查询:           │
│  ┌───────────────────────────────────┐  │
│  │ 1. 原始查询词                      │  │
│  │ 2. 驼峰拆分 + 前缀匹配 (word*)     │  │
│  │ 3. 独立英文词前缀匹配 (≥3字母)     │  │
│  │ 4. AI翻译英文术语 + 前缀匹配       │  │
│  │ 所有term用 | (OR) 连接             │  │
│  └───────────────────────────────────┘  │
│                                         │
│  2a. 带过滤器搜索 (module + doc_type)     │
│      "((term1)|(term2)|...) @module:{x}" │
│                                         │
│  2b. 带过滤器无结果 → 去掉过滤器重试       │
│      （Agent可能猜错module名）             │
│                                         │
│  FT.SEARCH返回"$"字段(JSON) → 解析       │
│  提取content + metadata                  │
└─────────────────────────────────────────┘
    │ 有结果 → 返回
    │ 无结果 ↓
┌─────────────────────────────────────────┐
│  第3级：chinese_summary 中文摘要检索       │
│  (searchByChineseSummary)                │
│                                         │
│  1. 从query提取中文词(≥2字)               │
│     正则: [\u4e00-\u9fa5]{2,}            │
│     例: "软件包管理配置" →                 │
│       ["软件包管理配置"]                   │
│                                         │
│  2. 对≥3字的中文词做2-gram拆分            │
│     "软件包管理配置" →                     │
│       "软件","件包","包管",               │
│       "管理","理配","配置"                 │
│                                         │
│  3. 所有中文词/子词用 OR 连接搜索          │
│     "@chinese_summary:软件包管理配置 |     │
│      @chinese_summary:软件 |             │
│      @chinese_summary:件包 | ..."         │
│                                         │
│  4. 单独调用（不与content查询混用）         │
│     避免RedisSearch @field:中文 语法问题   │
│                                         │
│  FT.SEARCH → 解析JSON → 返回Document     │
└─────────────────────────────────────────┘
    │ 有结果 → 返回
    │ 无结果 → 返回空列表
```

---

## 四、Tool 层调用

### ConfigSearchTool（search_config）

- 用途：搜索OMS配置知识库
- 调用：`knowledgeBaseService.searchConfig(query, 5, module, docType)`
- 只搜索 configVectorStore
- 无结果时返回："未在配置知识库中找到与「xxx」相关的信息"
- 可选参数：module（模块名）、docType（文档类型）

### LogAnalysisTool（analyze_log）

- 用途：分析错误日志，从源码和配置知识库同时检索
- 调用：
  - `knowledgeBaseService.searchSource(query, 5, module, docType)` → topK=5
  - `knowledgeBaseService.searchConfig(query, 3, module, docType)` → topK=3
- 两个知识库都搜，合并结果
- 无结果时返回："未在知识库中找到与「xxx」相关的源码或配置信息"

---

## 五、查询增强详细逻辑

### enhanceQuery() — 查询增强

```
输入: "日志备份的配置"
    │
    ├── 1. 驼峰拆分（无驼峰标识符，跳过）
    │
    ├── 2. AI翻译
    │     System Prompt: OMS领域中文→英文术语翻译专家
    │     输出: {"translations":{"日志":["log","logging","logRecord"],"备份":["backup","bak"],"配置":["config","cfg","configuration"]}}
    │     解析: 提取所有英文词 + 子词拆分
    │
    └── 结果: "日志备份的配置 log logging logRecord backup bak config cfg configuration"
```

### translateChineseTerms() — AI翻译

- 系统提示词 `TRANSLATE_SYSTEM_PROMPT` 指导AI只翻译技术术语
- 输出格式：`{"translations": {"中文术语1": ["en1","en2","en3"]}}`
- 解析JSON提取英文词，含空格/连字符的词还会拆分为子词（≥2字符）
- 纯英文查询自动跳过翻译
- 翻译失败降级为空列表，不影响主流程

### buildFullTextQuery() — 全文检索查询构建

构建 RedisSearch 查询字符串，所有 term 用 `|`（OR）连接：

1. **原始查询词**：直接加入
2. **驼峰拆分后的词** + 前缀匹配（`word*`）
3. **独立英文词**前缀匹配（≥3字母）
4. **AI翻译的英文术语** + 前缀匹配

示例：查询 "logBackup 配置"
→ `((logBackup 配置)) | ((log backup)*) | (logbackup) | (log*) | (backup*) | (config*) | (cfg*) | (configuration*)`

### searchByChineseSummary() — 中文摘要检索

1. 从 query 提取中文词（≥2字）：`[\u4e00-\u9fa5]{2,}`
2. 对≥3字的中文词做 **2-gram 拆分**提高召回率：
   - "软件包管理" → "软件", "件包", "包管", "管理"
3. 所有中文词/子词用 `|`（OR）连接搜索
4. 单独调用，不与 content 查询混用

2-gram 的作用：兼容旧数据中 chinese_summary 被拆成单字的情况。即使 "软件包" 被存为 "软 件 包"，2-gram 中的 "软件" 仍能匹配到相邻双字。

---

## 六、结果格式化

```java
formatSearchResults(List<Document>)
    → 每个Document格式化为：
      【来源: xxx.md | 模块: kafka | 类型: config-guide】
      文档内容...
    → 多个结果用 "---" 分隔
```

---

## 七、提示词约束

3个提示词文件（`config-qa.txt` / `log-analysis.txt` / `general-guide.txt`）的输出结构：

```
## 📚 本地知识库
  严格基于检索结果回答，标注来源

## ⚠️ AI参考建议（仅知识库有结果时才允许）
  基于已有信息做适度扩展解释
  不得编造配置项名称或取值

如果知识库完全无结果 → 禁止AI扩展，直接回复"未找到信息"
```

| 场景 | 行为 |
|------|------|
| 知识库有结果，完全覆盖 | 只写📚段 |
| 知识库有结果，部分覆盖 | 📚+⚠️段，⚠️段只能基于已有信息扩展 |
| 知识库无结果 | 禁止AI扩展，直接告知"未找到信息" |

---

## 八、关键约束总结

| 约束 | 说明 |
|------|------|
| chinese_summary 不与 content 混合查询 | 避免 RedisSearch `@field:中文` 语法兼容问题 |
| chinese_summary 必须空格分词 | 中文词组间用空格分隔，如"软件包 管理 配置" |
| 中文分词粒度：词组完整不拆单字 | "软件包 管理"而非"软 件 包 管 理" |
| 新增 metadata 字段需同步修改 RedisVectorStoreConfig | 否则索引中无该字段 |
| AI翻译失败降级为空列表 | 不影响主检索流程 |
| 向量搜索不传过滤器给 Spring AI | 避免 TAG/TEXT 语法冲突导致 KNN 报错，改为搜索后手动过滤 |
| AI翻译在一次检索中可能被调用2次 | `enhanceQuery()` + `buildFullTextQuery()` 各一次 |
| searchByChineseSummary 增加 2-gram 拆分 | 对≥3字中文词生成2-gram子串，兼容旧数据单字分词 |
| **Java源码chunk跳过驼峰增强** | 上下文头已含语义信息，额外追加会稀释embedding质量 |
| **POM chunk跳过驼峰增强** | POM标识符用连字符（spring-boot-starter），驼峰增强无价值反而增加噪音 |
| **源码/POM/配置区分摘要prompt** | 源码用CODE_SUMMARY_SYSTEM_PROMPT(≤100字,聚焦功能)，POM用POM_SUMMARY_SYSTEM_PROMPT(≤80字,聚焦依赖)，配置用SUMMARY_SYSTEM_PROMPT(≤150字,配置项说明) |
| **source知识库新增3个字段** | method_name / scope_chain / imports，需清空旧数据重新导入 |
| **POM分块新增4个metadata字段** | pom_section / pom_dep_gav / pom_dep_scope / pom_dep_optional |
| **.7z解压依赖系统7-Zip** | 部署环境需预装7-Zip |

---

## 九、相关文件

| 文件 | 作用 |
|------|------|
| `KnowledgeBaseService.java` | 检索核心逻辑（四级回退） |
| `DocumentEtlService.java` | 文档入库（分块 + 区分增强策略 + 区分摘要prompt + 7z支持） |
| `JavaCodeSplitter.java` | Java代码语义分块器（按类/方法边界切分，注入上下文头） |
| `PomFileSplitter.java` | POM文件结构化分块器（按dependency/plugin边界切分，注入GAV上下文头） ← 新增 |
| `MarkdownSectionSplitter.java` | Markdown按标题语义分块 |
| `RedisVectorStoreConfig.java` | Redis VectorStore配置（含两个索引的metadata字段定义） |
| `ConfigSearchTool.java` | 配置知识库搜索Tool（search_config） |
| `LogAnalysisTool.java` | 日志分析Tool（analyze_log，双库搜索） |
| `prompts/config-qa.txt` | 配置问答提示词 |
| `prompts/log-analysis.txt` | 日志分析提示词 |
| `prompts/general-guide.txt` | 通用指导提示词 |
