# 构建与启动

## 项目结构

```
OMAgent/
├── src/main/resources/     # 源码资源（版本管理）
│   ├── application.yml         # 主配置
│   ├── application-dev.yml     # 开发环境配置（环境变量版）
│   ├── schema.sql              # 数据库schema
│   └── prompts/                # 提示词
│       ├── config-qa.txt
│       ├── general-guide.txt
│       └── log-analysis.txt
├── config/                  # 外部配置（运行时，由build.ps1同步生成）
│   ├── application.yml
│   ├── application-dev.yml
│   └── prompts/
│       ├── config-qa.txt
│       ├── general-guide.txt
│       └── log-analysis.txt
├── target/                  # 构建产物
│   └── omagent-1.0.0.jar
├── scripts/                 # 运维脚本
│   ├── build.ps1                # 构建脚本
│   ├── start.ps1                # 启动脚本
│   └── stop.ps1                 # 停止脚本
├── tests/                   # 测试代码与脚本
│   ├── AnalyzeDoc.java          # 文档分析测试
│   ├── TestDoc.java             # Doc读取测试
│   └── test.jsh                 # JShell测试脚本
├── logs/                    # 运行日志
│   └── start.log                # 启动日志
├── docs/                    # 项目文档
├── tools/                   # 工具（doc2md等）
└── omagent-ui/              # 前端界面
```

## 设计原则

**配置文件和提示词放在jar外部**（`config/` 目录），运行时优先从外部读取：
- `--spring.config.location=file:config/` — 指定外部配置目录
- `OmSystemPrompt` 优先从 `config/prompts/` 读取提示词
- 修改配置/提示词后**无需重新打包**，重启即可生效

## 构建

```powershell
.\scripts\build.ps1
```

执行内容：
1. `mvn clean package -DskipTests` — Maven打包
2. 同步 `src/main/resources/` → `config/` — 更新外部配置

## 启动

```powershell
.\scripts\start.ps1        # 默认模式（使用config/application.yml）
.\scripts\start.ps1 dev    # 开发模式（使用环境变量配置）
```

启动参数：
- `--spring.config.location=file:config/` — 外部配置目录（覆盖jar内置）
- `--spring.config.additional-location=optional:classpath:/` — fallback到jar内配置
- 工作目录设为项目根目录，`config/prompts/` 相对路径可被识别
- 启动日志输出到 `logs/start.log`

## 停止

```powershell
.\scripts\stop.ps1
```

## 手动操作

如不使用脚本：

```powershell
# 构建
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
mvn clean package -DskipTests

# 同步资源到config目录
Copy-Item src\main\resources\application.yml config\
Copy-Item src\main\resources\application-dev.yml config\
Copy-Item src\main\resources\prompts\* config\prompts\

# 启动
java -jar target\omagent-1.0.0.jar --spring.config.location=file:config/
```

## 外部配置说明

修改 `config/` 下的文件即可调整运行时行为，无需重新打包：

| 文件 | 说明 |
|------|------|
| `config/application.yml` | 主配置（MySQL、Redis、DashScope、向量检索参数等） |
| `config/application-dev.yml` | 开发环境配置模板（使用环境变量） |
| `config/prompts/config-qa.txt` | 配置问答提示词 |
| `config/prompts/log-analysis.txt` | 日志分析提示词 |
| `config/prompts/general-guide.txt` | 通用指引提示词 |
