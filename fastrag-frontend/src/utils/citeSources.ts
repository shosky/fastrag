// ===========================================================================
// 引用角标注入（app-chat-optimize 三批）
//
// 后端引用标注协议：检索命中时系统提示词要求模型以 `[N]`（N=【参考资料N】序号）
// 在句末标注来源。本模块对 renderMarkdown 产出的【消毒后】HTML 做后处理——
// 仅注入自有安全 HTML（sup.cite-ref），不引入模型可控内容，故放在 sanitize
// 之后执行（markdown.ts SANITIZE_OPTIONS 白名单先完成消毒）。
//
// 防幻觉边界：N 超出来源数（或 <1）不替换——模型幻觉编号不得伪装成引用。
// ===========================================================================

/** 角标替换的序号位数上限（来源数不会到三位数，防误伤超长数字串） */
const MAX_CITE_DIGITS = 3

/**
 * 将消毒后 HTML 文本段中的 `[N]` 替换为上标角标（N ∈ 1..sourceCount）。
 *
 * - 仅处理 HTML 标签之外的文本段（按标签切段），属性/标签名内的 `[N]` 不受影响
 * - <code>/<pre> 内的 `[N]` 不替换（代码示例不是引用标注）
 * - 无来源（sourceCount < 1）原样返回；替换产物只含自有固定 HTML
 *
 * @param html renderMarkdown 输出的消毒后 HTML
 * @param sourceCount 来源条数（sources/references 长度）
 */
export function injectCitationRefs(html: string, sourceCount: number): string {
  if (!html || sourceCount < 1) return html
  // code/pre 可嵌套（<pre><code>），按开闭深度而非布尔位跟踪
  let codeDepth = 0
  return html
    .split(/(<[^>]*>)/g)
    .map(seg => {
      if (seg.startsWith('<')) {
        const codeTag = /^<(\/?)(code|pre)\b/i.exec(seg)
        // 钳制下限 0：消毒输出标签必然配对，但防御性容忍非配对闭标签
        if (codeTag) codeDepth = Math.max(0, codeDepth + (codeTag[1] === '/' ? -1 : 1))
        return seg
      }
      if (codeDepth > 0) return seg
      return seg.replace(new RegExp(`\\[(\\d{1,${MAX_CITE_DIGITS}})\\]`, 'g'), (raw: string, num: string) => {
        const n = Number(num)
        if (n < 1 || n > sourceCount) return raw
        return `<sup class="cite-ref" title="来源 ${n}">[${n}]</sup>`
      })
    })
    .join('')
}
