/**
 * 手册目录解析（契约：docs/modules/portal-frontend.md ## 数据——唯一命名转换点）。
 * parseManualToc 与 injectHeadingIds 按同一文档序、同一 slug 规则各自独立分配 id，
 * 保证目录锚点与渲染后 HTML 的标题 id 一一对应。
 */
export interface ManualTocNode {
  /** 锚点 id */
  id: string
  /** 标题文字 */
  text: string
  /** 2 = 章，3 = 节 */
  level: 2 | 3
  /** 节级子节点（仅章有） */
  children: ManualTocNode[]
}

/** 标题文字 → 稳定锚点 id（保留中日韩文字/字母/数字，其余折叠为连字符；重名追加序号） */
function slugify(text: string, used: Set<string>): string {
  const base =
    text
      .trim()
      .replace(/\s+/g, '-')
      .replace(/[^\p{L}\p{N}-]/gu, '') || 'section'
  let id = base
  let n = 2
  while (used.has(id)) id = `${base}-${n++}`
  used.add(id)
  return id
}

/** 按出现顺序为一批标题文字分配互不冲突的 id */
function assignIds(texts: string[]): string[] {
  const used = new Set<string>()
  return texts.map((t) => slugify(t, used))
}

/** 解析「## 章 / ### 节」两级标题树；空串或无标题返回空数组 */
export function parseManualToc(raw: string): ManualTocNode[] {
  const matches = [...raw.matchAll(/^(#{2,3})\s+(.+?)\s*$/gm)]
  const ids = assignIds(matches.map((m) => m[2]))
  const toc: ManualTocNode[] = []
  let current: ManualTocNode | null = null
  matches.forEach((m, i) => {
    const level = m[1].length as 2 | 3
    const node: ManualTocNode = { id: ids[i], text: m[2], level, children: [] }
    if (level === 2) {
      toc.push(node)
      current = node
    } else if (current) {
      current.children.push(node)
    }
  })
  return toc
}

/** 给渲染后的 HTML 中 h2/h3 注入与目录一致的锚点 id */
export function injectHeadingIds(html: string): string {
  const texts = [...html.matchAll(/<h([23])>([\s\S]*?)<\/h\1>/g)].map((m) =>
    m[2].replace(/<[^>]+>/g, '').trim()
  )
  const ids = assignIds(texts)
  let i = 0
  return html.replace(/<h([23])>([\s\S]*?)<\/h\1>/g, (full, level: string, inner: string) => {
    const id = ids[i++]
    return `<h${level} id="${id}">${inner}</h${level}>`
  })
}

/** 部分（加粗行）分组：每组含其下的章树；与 parseManualToc 同序分配 id，锚点一致 */
export interface ManualGroup {
  /** 部分标题，如「第一部分 平台概览与快速入口」 */
  title: string
  chapters: ManualTocNode[]
}

/** 解析「**第X部分** → ## 章 → ### 节」三级结构为可折叠分组 */
export function parseManualGroups(raw: string): ManualGroup[] {
  const used = new Set<string>()
  const groups: ManualGroup[] = []
  let current: ManualGroup | null = null
  let currentChapter: ManualTocNode | null = null
  for (const line of raw.split('\n')) {
    const t = line.trim()
    const part = /^\*\*(第[一二三四五六七八九十]+部分[^*]*)\*\*$/.exec(t)
    const ch = /^##\s+(.+?)\s*$/.exec(t)
    const sec = /^###\s+(.+?)\s*$/.exec(t)
    if (part) {
      current = { title: part[1], chapters: [] }
      groups.push(current)
      currentChapter = null
    } else if (ch) {
      const node: ManualTocNode = { id: slugify(ch[1], used), text: ch[1], level: 2, children: [] }
      if (current) current.chapters.push(node)
      currentChapter = node
    } else if (sec && currentChapter) {
      currentChapter.children.push({
        id: slugify(sec[1], used),
        text: sec[1],
        level: 3,
        children: [],
      })
    }
  }
  return groups
}
