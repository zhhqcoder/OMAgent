# OMAgent 提示词配置说明

本目录存放 Agent 的系统提示词（System Prompt）文件，采用外置文本方式管理，方便运行时修改而无需重新打包。

---

## 文件清单

| 文件名 | 用途 | 调用时机 |
|--------|------|----------|
| `config-qa.txt` | 配置问答提示词 | 创建配置问答专用 Agent 时 |
| `log-analysis.txt` | 日志分析提示词 | 创建日志分析专用 Agent 时 |
| `general-guide.txt` | 通用综合指引 | 创建通用运维 Agent 时，与上述两个提示词组合使用 |

---

## 各文件详细说明

### 1. config-qa.txt — 配置问答提示词

**作用**：定义配置问答 Agent 的角色、职责和行为规范。该 Agent 只拥有 `search_config` 工具，专门解答 OMS 系统配置项相关问题。

**调用时机**：
- `OmAgentFactory.createConfigQaAgent()` — 创建纯配置问答 Agent 时，作为 `systemPrompt` 传入
- `OmAgentFactory.createGeneralAgent()` — 创建通用 Agent 时，作为提示词的第一部分（与 `log-analysis.txt` + `general-guide.txt` 拼接）

**适用场景**：
- 用户询问某个配置项的含义、取值范围、默认值
- 用户询问配置项之间的依赖关系
- 用户需要配置修改建议

**编写建议**：
- 保持"严格约束"段，强制 Agent 只基于知识库检索结果回答，防止模型用训练知识编造信息
- `OMS系统背景` 段提供领域上下文，帮助模型理解术语，但不要写太多细节（模型会从知识库获取）
- `回答规范` 段控制输出格式，可根据实际需求调整（如是否要求 Markdown、是否要求表格形式等）
- 修改后即时生效（下次创建 Agent 时读取），无需重启（注意：已有会话缓存的 Agent 不会刷新）

---

### 2. log-analysis.txt — 日志分析提示词

**作用**：定义日志分析 Agent 的角色、职责和行为规范。该 Agent 拥有 `analyze_log` 工具，专门分析 OMS 程序错误日志并定位问题根因。

**调用时机**：
- `OmAgentFactory.createLogAnalysisAgent()` — 创建纯日志分析 Agent 时，作为 `systemPrompt` 传入
- `OmAgentFactory.createGeneralAgent()` — 创建通用 Agent 时，作为提示词的第二部分
- `OmAgentRunner.invokeWithScreenshotAnalysis()` — 截图分析流程中，截图 OCR 结果交给日志分析 Agent 处理

**适用场景**：
- 用户粘贴了错误日志/异常堆栈
- 用户上传了日志文件
- 用户上传了错误截图（截图先经多模态模型 OCR，再交给此 Agent 分析）

**编写建议**：
- `问题分类标准` 段是核心，定义了四类问题（Bug/配置/环境/网络），可根据实际运维经验扩展分类
- 保持"严格约束"段，防止模型凭训练知识推测源码逻辑
- 如果新增了问题类型（如"数据库问题"、"中间件问题"），在分类标准中补充
- 回答规范中的"每一步都要标注知识库依据"要求很重要，是验证回答可信度的关键

---

### 3. general-guide.txt — 通用综合指引

**作用**：定义通用 Agent 的额外指引，说明何时使用哪个工具。该文件与 `config-qa.txt` + `log-analysis.txt` 拼接后，构成通用 Agent 的完整系统提示词。

**调用时机**：
- `OmAgentFactory.createGeneralAgent()` — 仅在创建通用 Agent 时使用，拼接在配置问答 + 日志分析提示词之后

**最终拼接顺序**：
```
[config-qa.txt 内容]
---
[log-analysis.txt 内容]
---
[general-guide.txt 内容]
```

**适用场景**：
- 前端聊天窗口的默认 Agent（`OmAgentRunner.getOrCreateAgent()` 调用 `createGeneralAgent()`）
- 用户可能问配置问题，也可能问日志问题，Agent 自动判断使用哪个工具

**编写建议**：
- `综合指引` 段是工具路由的关键，要清晰描述什么问题用什么工具
- 如果将来新增工具（如"性能分析工具"），在此文件中补充路由规则
- `全局严格约束` 段是最后兜底的约束，确保即使前两个文件的约束被模型忽略，这里有更强的重申
- 此文件应保持简短，主要是路由规则和兜底约束，详细规范放在前两个文件中

---

## 加载机制

```
优先级：
1. 外部文件 config/prompts/{name}.txt    ← 优先（运行时覆盖）
2. classpath  prompts/{name}.txt          ← 内置默认（打包在 JAR 中）
```

- **开发/调试阶段**：直接修改 `src/main/resources/prompts/` 下的文件，重新编译即可
- **生产部署阶段**：在 JAR 同级目录创建 `config/prompts/` 文件夹，放入同名文件即可覆盖，无需重新打包
- **注意**：提示词在 Agent 创建时加载并缓存。已有会话的 Agent 不会自动刷新，新建会话才会使用新提示词

---

## 修改检查清单

修改提示词时，请确认以下事项：

- [ ] 是否添加了"严格约束"段，防止模型编造信息？
- [ ] 修改后的提示词是否与工具描述一致（如工具名 `search_config` / `analyze_log`）？
- [ ] 是否需要同步修改其他提示词文件（如 `general-guide.txt` 中引用的工具路由）？
- [ ] 中文表述是否清晰无歧义（模型对模糊指令的遵守度较低）？
- [ ] 是否测试了知识库有结果和无结果两种场景？
