// ===========================================================================
// markdown 单源渲染器（app-chat-optimize Step 1，对齐 WeKnora botmsg.vue:357-423）
//
// marked（保留 gfm/breaks 配置，与 runtime.vue 既有渲染一致）→ DOMPurify 消毒。
// 消毒白名单策略：FORBID 危险标签/属性，其余交由 DOMPurify 默认协议过滤
// （javascript:/data: 等 URI 由其内置 ALLOWED_URI_REGEXP 拦截）。
//
// 收敛范围：会话中心 MessageBubble.vue（G01：runtime.vue / AppChat.vue 不动）。
// ===========================================================================
import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({ breaks: true, gfm: true })

/** 消毒配置：禁用可执行/注入面标签与内联事件属性（设计蓝图 Step 1 指定） */
const SANITIZE_OPTIONS = {
  FORBID_TAGS: ['script', 'style', 'iframe'],
  FORBID_ATTR: ['onerror', 'onclick', 'onload'],
}

/**
 * markdown → 消毒后 HTML（同步；marked 无异步扩展时 parse 返回 string）
 * @param md 原始 markdown 文本
 * @returns 消毒后的 HTML；空入参返回空串；解析异常时回退原文（不做二次转义）
 */
export function renderMarkdown(md: string): string {
  if (!md) return ''
  try {
    const parsed = marked.parse(md)
    const html = typeof parsed === 'string' ? parsed : String(parsed)
    return DOMPurify.sanitize(html, SANITIZE_OPTIONS)
  } catch {
    return md
  }
}
