import { marked } from 'marked'
import DOMPurify from 'dompurify'

// marked 全局配置只在模块加载时设置一次（避免每次渲染重复 setOptions 的全局副作用）
marked.setOptions({
  breaks: false, // 不把每个换行都转 <br>，让段落自然排版
  gfm: true
})

/**
 * 渲染 Markdown 为可信 HTML。
 *
 * 必须配合 v-html 使用的场景统一走这里：
 * marked 本身不做任何 HTML 消毒，AI 回复/聊天内容中若混入
 * <img onerror> 等 payload 会直接执行（叠加 JWT 存 localStorage 可被窃取），
 * 因此渲染结果一律经 DOMPurify 消毒后再返回。
 *
 * @param {string} content 原始 Markdown 文本
 * @returns {string} 消毒后的 HTML 字符串
 */
export function renderMarkdown(content) {
  if (!content) return ''
  return DOMPurify.sanitize(marked(content))
}

export default renderMarkdown
