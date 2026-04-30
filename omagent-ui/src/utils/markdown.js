import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs-pre"><code class="hljs language-${lang}">${
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
        }</code><button class="copy-btn" onclick="navigator.clipboard.writeText(this.parentElement.querySelector('code').innerText)">复制</button></pre>`
      } catch (_) {}
    }
    return `<pre class="hljs-pre"><code class="hljs">${md.utils.escapeHtml(str)}</code></pre>`
  },
})

/**
 * 后处理：将 📚 本地知识库 和 ⚠️ AI参考建议 区块包裹在不同样式的div中
 * AI输出格式: ### 📚 本地知识库 / ### ⚠️ AI参考建议
 * MarkdownIt渲染后: <h3>📚 本地知识库</h3> ... <h3>⚠️ AI参考建议</h3> ...
 *
 * 特殊处理：流式输出时AI可能多次输出同类型标题（如先输出"正在搜索…"再输出结果），
 * 需要将连续的同类型section合并为一个，只保留第一个标题，内容拼接。
 */
function wrapSourceSections(html) {
  const h3Regex = /<h3>(.*?)<\/h3>/g
  const parts = []
  let lastIndex = 0
  let match

  while ((match = h3Regex.exec(html)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ type: 'html', content: html.slice(lastIndex, match.index) })
    }

    const title = match[1]
    if (title.includes('📚 本地知识库')) {
      parts.push({ type: 'kb-title', content: match[0] })
    } else if (title.includes('⚠️ AI参考建议')) {
      parts.push({ type: 'ai-title', content: match[0] })
    } else {
      parts.push({ type: 'html', content: match[0] })
    }

    lastIndex = h3Regex.lastIndex
  }
  if (lastIndex < html.length) {
    parts.push({ type: 'html', content: html.slice(lastIndex) })
  }

  if (parts.length === 0) return html

  // 将 kb-title + 后续html 合并为一个区块，ai-title + 后续html 合并为一个区块
  // 连续的同类型section合并（只保留第一个标题）
  const sections = []
  let currentSection = null
  let sectionContent = ''

  for (const part of parts) {
    if (part.type === 'kb-title' || part.type === 'ai-title') {
      const sectionType = part.type === 'kb-title' ? 'kb' : 'ai'
      if (currentSection === sectionType) {
        // 同类型section连续出现：合并内容，不重复标题
        sectionContent += part.content // 保留h3标签但后续会用CSS隐藏
      } else {
        // 不同类型或首次出现：先输出上一个section
        if (currentSection) {
          const cls = currentSection === 'kb' ? 'source-section-kb' : 'source-section-ai'
          sections.push({ cls, content: sectionContent })
        }
        currentSection = sectionType
        sectionContent = part.content
      }
    } else if (currentSection) {
      sectionContent += part.content
    } else {
      sections.push({ cls: 'raw', content: part.content })
    }
  }
  // 输出最后一个section
  if (currentSection) {
    const cls = currentSection === 'kb' ? 'source-section-kb' : 'source-section-ai'
    sections.push({ cls, content: sectionContent })
  }

  return sections.map(s => {
    if (s.cls === 'raw') return s.content
    return `<div class="${s.cls}">${s.content}</div>`
  }).join('')
}

export function renderMarkdown(text) {
  if (!text) return ''
  const html = md.render(text)
  return wrapSourceSections(html)
}

export default md
