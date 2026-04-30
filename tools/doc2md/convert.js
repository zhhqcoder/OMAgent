#!/usr/bin/env node

/**
 * doc2md - Word 文档转 Markdown 工具
 * 
 * 支持 .doc (OLE2) 和 .docx (Office Open XML) 格式
 * 自动识别标题层级、表格、列表等结构并转换为对应的 Markdown 语法
 */

const WordExtractor = require('word-extractor');
const fs = require('fs');
const path = require('path');

// ============================================================
// 配置
// ============================================================

const DEFAULT_OPTIONS = {
  headingStyle: 'atx',       // 标题风格: atx (#) 或 setext (===)
  tableStyle: 'full',        // 表格风格: full (完整表格) 或 simple (简单)
  listIndent: '  ',          // 列表缩进
  maxTableCols: 6,           // 超过此列数的表格使用代码块展示
  codeBlockLang: '',         // 代码块语言标识
  lineBreak: '\n',           // 换行符
};

// ============================================================
// 工具函数
// ============================================================

/**
 * 判断文本是否为标题行（基于常见的文档标题模式）
 */
function detectHeadingLevel(text) {
  // 匹配 "1 标题"、"1.1 标题"、"1.1.1 标题" 等编号格式
  const numberedPattern = /^(\d+(?:\.\d+)*)\s+(.+)/;
  const match = text.match(numberedPattern);
  if (match) {
    const level = match[1].split('.').length;
    return { level: Math.min(level, 5), text: match[2], numbered: match[1] };
  }
  return null;
}

/**
 * 判断文本是否为目录项
 */
function isTOCEntry(text) {
  // 匹配 "1.1 xxx	3" 或 "1 xxx	1" 这类目录行
  return /^\d+(?:\.\d+)*\s+.+\t\d+\s*$/.test(text) ||
         /^\d+(?:\.\d+)*\s+.+\s+\d+\s*$/.test(text);
}

/**
 * 判断文本是否为表格行（tab分隔的多列数据）
 */
function isTableRow(text) {
  const parts = text.split('\t').filter(p => p.trim());
  return parts.length >= 2;
}

/**
 * 解析 tab 分隔的行为表格列
 */
function parseTableRow(text) {
  return text.split('\t').map(cell => cell.trim());
}

/**
 * 生成 Markdown 表格
 */
function buildMarkdownTable(rows, options) {
  if (rows.length === 0) return '';

  // 标准化列数
  const maxCols = Math.max(...rows.map(r => r.length));
  const normalizedRows = rows.map(r => {
    while (r.length < maxCols) r.push('');
    return r;
  });

  if (maxCols > options.maxTableCols) {
    // 超宽表格用代码块
    const lines = normalizedRows.map(r => r.join(' | '));
    return '```' + options.codeBlockLang + '\n' + lines.join('\n') + '\n```\n\n';
  }

  const escapeCell = (cell) => cell.replace(/\|/g, '\\|').replace(/\n/g, ' ');
  const header = '| ' + normalizedRows[0].map(escapeCell).join(' | ') + ' |';
  const separator = '| ' + normalizedRows[0].map(() => '---').join(' | ') + ' |';
  const body = normalizedRows.slice(1).map(
    row => '| ' + row.map(escapeCell).join(' | ') + ' |'
  );

  return [header, separator, ...body].join('\n') + '\n\n';
}

/**
 * 检测是否为版本历史等特殊表格（第一行是表头，后续行是数据）
 */
function detectTableContext(lines, startIndex) {
  // 向前查找最近的标题来确定上下文
  for (let i = startIndex - 1; i >= 0; i--) {
    const line = lines[i].trim();
    if (!line) continue;
    // 如果前一行是标题，返回标题文本
    if (detectHeadingLevel(line) || line.length < 30) {
      return line;
    }
    break;
  }
  return '';
}

// ============================================================
// 主转换逻辑
// ============================================================

/**
 * 将提取的文本转换为 Markdown
 */
function convertToMarkdown(rawText, options = {}) {
  const opts = { ...DEFAULT_OPTIONS, ...options };
  const lines = rawText.split(/\r?\n/);
  const output = [];

  let i = 0;
  let inTable = false;
  let tableRows = [];
  let prevWasHeading = false;
  let tocMode = false;

  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();

    // 空行
    if (!trimmed) {
      if (inTable && tableRows.length > 0) {
        // 表格结束
        output.push(buildMarkdownTable(tableRows, opts));
        tableRows = [];
        inTable = false;
      }
      i++;
      continue;
    }

    // ---- 目录检测 ----
    if (trimmed === '目  录' || trimmed === '目录') {
      output.push('## 目录\n\n');
      tocMode = true;
      i++;
      continue;
    }

    // 目录项
    if (tocMode && isTOCEntry(trimmed)) {
      const tocMatch = trimmed.match(/^(\d+(?:\.\d+)*)\s+(.+?)\s+(\d+)\s*$/);
      if (tocMatch) {
        const indent = tocMatch[1].split('.').length - 1;
        output.push(`${opts.listIndent.repeat(indent)}- ${tocMatch[1]} ${tocMatch[2]}\n`);
      }
      i++;
      continue;
    }

    // 目录结束（遇到非目录行）
    if (tocMode && !isTOCEntry(trimmed)) {
      output.push('\n---\n\n');
      tocMode = false;
    }

    // ---- 标题检测 ----
    const headingInfo = detectHeadingLevel(trimmed);
    if (headingInfo) {
      // 先结束可能存在的表格
      if (inTable && tableRows.length > 0) {
        output.push(buildMarkdownTable(tableRows, opts));
        tableRows = [];
        inTable = false;
      }

      const hashes = '#'.repeat(headingInfo.level + 1); // +1 因为文档的一级标题对应 Markdown 的 ##
      output.push(`${hashes} ${trimmed}\n\n`);
      prevWasHeading = true;
      i++;
      continue;
    }

    // ---- 表格检测 ----
    // 表格行：tab分隔的多列内容
    if (isTableRow(trimmed)) {
      const cells = parseTableRow(trimmed);
      
      if (!inTable) {
        // 新表格开始
        inTable = true;
        tableRows = [cells];
      } else {
        tableRows.push(cells);
      }
      prevWasHeading = false;
      i++;
      continue;
    }

    // ---- 非表格、非标题的普通文本 ----
    if (inTable && tableRows.length > 0) {
      output.push(buildMarkdownTable(tableRows, opts));
      tableRows = [];
      inTable = false;
    }

    // 判断是否为子标题（短文本，前后有空行，不以标点结尾）
    if (trimmed.length < 30 && !trimmed.match(/[。；，、！？；：]$/) && prevWasHeading) {
      output.push(`### ${trimmed}\n\n`);
      prevWasHeading = false;
      i++;
      continue;
    }

    // 代码块检测（缩进内容或包含特殊字符的配置示例）
    if (trimmed.match(/^\s{2,}\S/) || trimmed.match(/^[a-zA-Z-]+\.[a-zA-Z-]+/)) {
      // 可能是代码/配置内容
      output.push(`${trimmed}\n`);
      i++;
      continue;
    }

    // 普通段落
    output.push(`${trimmed}\n\n`);
    prevWasHeading = false;
    i++;
  }

  // 处理最后的表格
  if (inTable && tableRows.length > 0) {
    output.push(buildMarkdownTable(tableRows, opts));
  }

  return output.join('');
}

// ============================================================
// CLI 入口
// ============================================================

function printUsage() {
  console.log(`
doc2md - Word 文档转 Markdown 工具
===================================

用法:
  node convert.js <input.doc|input.docx> [output.md] [options]

参数:
  input       输入的 .doc 或 .docx 文件路径
  output      输出的 .md 文件路径（可选，默认与输入同目录同名）

选项:
  --heading-style <atx|setext>   标题风格 (默认: atx)
  --max-table-cols <n>           表格最大列数，超过用代码块 (默认: 6)
  --no-toc                       不生成目录
  --help                         显示帮助信息

示例:
  node convert.js 说明书.doc
  node convert.js 说明书.docx output.md
  node convert.js 说明书.doc --max-table-cols 4
`);
}

async function main() {
  const args = process.argv.slice(2);

  if (args.length === 0 || args.includes('--help') || args.includes('-h')) {
    printUsage();
    process.exit(0);
  }

  // 解析参数
  const inputPath = args[0];
  let outputPath = '';
  const cliOptions = {};

  for (let i = 1; i < args.length; i++) {
    if (args[i] === '--heading-style' && args[i + 1]) {
      cliOptions.headingStyle = args[++i];
    } else if (args[i] === '--max-table-cols' && args[i + 1]) {
      cliOptions.maxTableCols = parseInt(args[++i]);
    } else if (args[i] === '--no-toc') {
      cliOptions.noToc = true;
    } else if (!args[i].startsWith('--')) {
      outputPath = args[i];
    }
  }

  // 验证输入文件
  if (!fs.existsSync(inputPath)) {
    console.error(`错误: 输入文件不存在: ${inputPath}`);
    process.exit(1);
  }

  const ext = path.extname(inputPath).toLowerCase();
  if (ext !== '.doc' && ext !== '.docx') {
    console.error(`错误: 不支持的文件格式: ${ext}，仅支持 .doc 和 .docx`);
    process.exit(1);
  }

  // 默认输出路径
  if (!outputPath) {
    outputPath = inputPath.replace(/\.(doc|docx)$/i, '.md');
  }

  console.log(`正在转换: ${inputPath}`);
  console.log(`输出到:   ${outputPath}`);

  try {
    // 提取文本
    const extractor = new WordExtractor();
    const extracted = await extractor.extract(inputPath);
    const rawText = extracted.getBody();

    if (!rawText || rawText.trim().length === 0) {
      console.error('错误: 文档内容为空或无法提取');
      process.exit(1);
    }

    // 转换为 Markdown
    const markdown = convertToMarkdown(rawText, cliOptions);

    // 确保输出目录存在
    const outputDir = path.dirname(outputPath);
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }

    // 写入文件
    fs.writeFileSync(outputPath, markdown, 'utf-8');

    const stats = fs.statSync(outputPath);
    console.log(`\n转换完成!`);
    console.log(`  文件大小: ${(stats.size / 1024).toFixed(1)} KB`);
    console.log(`  总行数:   ${markdown.split('\n').length}`);
  } catch (err) {
    console.error(`转换失败: ${err.message}`);
    process.exit(1);
  }
}

// 导出供编程使用
module.exports = { convertToMarkdown, detectHeadingLevel, isTableRow, buildMarkdownTable };

// CLI 执行
if (require.main === module) {
  main();
}
