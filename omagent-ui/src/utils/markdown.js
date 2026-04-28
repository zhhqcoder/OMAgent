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

export function renderMarkdown(text) {
  if (!text) return ''
  return md.render(text)
}

export default md
