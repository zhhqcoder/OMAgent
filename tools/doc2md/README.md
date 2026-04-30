# doc2md - Word 文档转 Markdown 工具

将 `.doc` / `.docx` 格式的 Word 文档转换为 Markdown 格式，自动识别标题层级、表格、目录等结构。

## 功能特性

- 支持 `.doc`（OLE2 格式）和 `.docx`（Office Open XML 格式）
- 自动识别编号标题（如 `1`、`1.1`、`1.1.1`）并转换为对应层级的 Markdown 标题
- 自动识别 tab 分隔的表格数据，转换为 Markdown 表格
- 自动识别目录区域并格式化输出
- 超宽表格自动切换为代码块展示
- 支持命令行和编程两种调用方式

## 环境要求

- **Node.js** >= 14.0
- **npm** >= 6.0

## 安装

```bash
cd tools/doc2md
npm install
```

## 使用方法

### 命令行方式

```bash
# 基本用法 - 转换 doc 文件（输出到同目录同名 .md 文件）
node convert.js 说明书.doc

# 指定输出路径
node convert.js 说明书.docx output.md

# 设置表格最大列数（超过则用代码块展示）
node convert.js 说明书.doc --max-table-cols 4

# 查看帮助
node convert.js --help
```

### 编程方式

在 Node.js 代码中引入使用：

```javascript
const { convertToMarkdown } = require('./tools/doc2md/convert');
const WordExtractor = require('word-extractor');

async function convert() {
  const extractor = new WordExtractor();
  const extracted = await extractor.extract('说明书.doc');
  const rawText = extracted.getBody();
  
  // 调用转换函数
  const markdown = convertToMarkdown(rawText, {
    headingStyle: 'atx',
    maxTableCols: 6,
  });
  
  console.log(markdown);
}

convert();
```

## 命令行参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `input` | 输入的 .doc 或 .docx 文件路径（必填） | - |
| `output` | 输出的 .md 文件路径（可选） | 与输入同目录同名 |
| `--heading-style <atx\|setext>` | 标题风格 | `atx` |
| `--max-table-cols <n>` | 表格最大列数，超过用代码块 | `6` |
| `--no-toc` | 不生成目录 | - |
| `--help` | 显示帮助信息 | - |

## 转换规则

### 标题

| Word 中的格式 | Markdown 输出 |
|---------------|---------------|
| `1 标题` | `## 1 标题` |
| `1.1 标题` | `### 1.1 标题` |
| `1.1.1 标题` | `#### 1.1.1 标题` |

编号标题会自动根据层级深度转换为对应的 Markdown 标题级别。

### 表格

Word 中用 tab 分隔的多列数据会自动转换为 Markdown 表格：

```
配置项	含义	默认值
name	名称	test
port	端口	8080
```

转换为：

```markdown
| 配置项 | 含义 | 默认值 |
|---|---|---|
| name | 名称 | test |
| port | 端口 | 8080 |
```

超过 `--max-table-cols` 列数的表格会以代码块形式展示，避免 Markdown 渲染变形。

### 目录

自动识别 `目  录` / `目录` 关键词，将后续的目录项格式化为列表。

## 文件结构

```
tools/doc2md/
├── package.json      # npm 包配置
├── convert.js        # 核心转换脚本
└── README.md         # 本说明文档
```

## 已知限制

1. **.doc 格式**：使用 `word-extractor` 提取纯文本，无法获取字体样式（加粗、斜体等），只能基于文本内容模式（编号、tab分隔等）推断结构
2. **图片**：不支持提取和转换 Word 文档中的图片
3. **合并单元格**：复杂的合并单元格表格可能无法完美转换
4. **嵌套列表**：多层嵌套列表的识别有限

如需更精确的转换（保留字体样式、图片等），建议安装 `pandoc`：

```bash
# 使用 pandoc 转换（需先安装 pandoc）
pandoc input.docx -o output.md
```

## 示例

转换 OMS 配置说明书：

```bash
cd tools/doc2md
npm install
node convert.js "C:\Users\Administrator\Desktop\OMS配置说明书.doc" "../../docs/OMS配置说明书.md"
```

## 许可证

MIT
