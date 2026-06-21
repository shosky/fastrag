// ===========================================================================
// 术语同义词数据层 —— 全局唯一数据源
//
// 取代 terminology.vue 的内联 ref 数据，供术语管理 + 检索联想共用。
// ===========================================================================

export interface TermLibrary {
  id: string
  name: string
  desc: string
  count: number
}

export interface TermRecord {
  id: string
  name: string
  library: string
  /** 别名（同义词，逗号分隔支持多个） */
  alias: string
  status: '启用' | '禁用'
  definition: string
}

// --- 种子数据 ---
const libraries: TermLibrary[] = [
  { id: '1', name: '医院管理术语', desc: '医疗行业常用术语', count: 56 },
  { id: '2', name: '金融术语库', desc: '金融行业专业术语', count: 34 },
  { id: '3', name: 'IT技术术语', desc: '信息技术领域术语', count: 78 },
  { id: '4', name: '电信业务术语', desc: '电信运营商相关业务术语', count: 22 },
]

const terms: TermRecord[] = [
  { id: '1', name: '普陀山', library: '医院管理术语', alias: '海天佛国', status: '启用', definition: '位于浙江省舟山市的著名佛教圣地' },
  { id: '2', name: 'IPO', library: '金融术语库', alias: '首次公开募股', status: '启用', definition: 'Initial Public Offering' },
  { id: '3', name: 'API', library: 'IT技术术语', alias: '应用程序接口,应用编程接口', status: '启用', definition: 'Application Programming Interface' },
  { id: '4', name: '小微ICT', library: '电信业务术语', alias: '小微企业ICT,小ICT', status: '启用', definition: '面向中小企业的标准化ICT服务' },
  { id: '5', name: '全光组网', library: '电信业务术语', alias: 'FTTO,光纤到办公室', status: '启用', definition: '基于光纤的全光网络组网方案' },
  { id: '6', name: '视频监控', library: '电信业务术语', alias: '安防监控,摄像头监控', status: '启用', definition: '基于摄像头的视频安防监控服务' },
  { id: '7', name: '综合布线', library: '电信业务术语', alias: '弱电布线,网络布线', status: '启用', definition: '建筑物内的弱电综合布线系统' },
  { id: '8', name: '机房托管', library: '电信业务术语', alias: 'IDC托管,服务器托管', status: '启用', definition: '将服务器设备托管在专业机房中' },
  { id: '9', name: '标品合同编码', library: '电信业务术语', alias: '合同编码,标品编码', status: '启用', definition: '标准化产品合同的唯一标识编码' },
  { id: '10', name: '合同编码', library: '电信业务术语', alias: '标品合同编码,标品编码', status: '启用', definition: '标品合同编码的简称' },
]

let librarySeq = 100
let termSeq = 100

// --- 术语库 CRUD ---
export function getTermLibraries(): TermLibrary[] {
  return libraries.map((l) => ({ ...l }))
}

export function createTermLibrary(form: { name: string; desc: string }): TermLibrary {
  const lib: TermLibrary = { id: String(++librarySeq), name: form.name, desc: form.desc, count: 0 }
  libraries.push(lib)
  return { ...lib }
}

export function updateTermLibrary(id: string, form: { name: string; desc: string }): TermLibrary | null {
  const lib = libraries.find((l) => l.id === id)
  if (!lib) return null
  lib.name = form.name
  lib.desc = form.desc
  return { ...lib }
}

export function deleteTermLibrary(id: string): boolean {
  const idx = libraries.findIndex((l) => l.id === id)
  if (idx === -1) return false
  const name = libraries[idx].name
  // 同步删除该库下的所有术语
  for (let i = terms.length - 1; i >= 0; i--) {
    if (terms[i].library === name) terms.splice(i, 1)
  }
  libraries.splice(idx, 1)
  return true
}

// --- 术语 CRUD ---
export function getTerms(libraryName?: string): TermRecord[] {
  const list = libraryName ? terms.filter((t) => t.library === libraryName) : terms
  return list.map((t) => ({ ...t }))
}

export function createTerm(form: Omit<TermRecord, 'id'>): TermRecord {
  const record: TermRecord = { ...form, id: String(++termSeq) }
  terms.push(record)
  // 更新术语库计数
  refreshCounts()
  return { ...record }
}

export function updateTerm(id: string, patch: Partial<TermRecord>): TermRecord | null {
  const idx = terms.findIndex((t) => t.id === id)
  if (idx === -1) return null
  terms[idx] = { ...terms[idx], ...patch }
  return { ...terms[idx] }
}

export function deleteTerm(id: string): boolean {
  const idx = terms.findIndex((t) => t.id === id)
  if (idx === -1) return false
  terms.splice(idx, 1)
  refreshCounts()
  return true
}

function refreshCounts() {
  libraries.forEach((lib) => {
    lib.count = terms.filter((t) => t.library === lib.name).length
  })
}

// --- 同义词映射（供检索联想用）---

/**
 * 获取同义词映射表。
 * key = 术语名或别名，value = 所有关联词（术语名 + 所有别名）。
 * 只返回启用状态的术语。
 */
export function getSynonymMap(): Map<string, string[]> {
  const map = new Map<string, string[]>()
  terms
    .filter((t) => t.status === '启用' && t.alias)
    .forEach((t) => {
      const aliases = t.alias.split(/[,，、]/).map((a) => a.trim()).filter(Boolean)
      const allTerms = [t.name, ...aliases]
      // 为每个词建立到全集的映射
      allTerms.forEach((term) => {
        map.set(term, allTerms.filter((a) => a !== term))
      })
    })
  return map
}

/**
 * 查询扩展：在 query 中查找命中的术语，追加其同义词。
 * 返回扩展后的查询词、命中了哪些术语、追加了哪些词。
 */
export function expandQueryWithSynonyms(
  query: string,
): { expandedQuery: string; matchedTerms: string[]; addedTerms: string[] } {
  const synonymMap = getSynonymMap()
  const matchedTerms: string[] = []
  const addedTerms: string[] = []
  let expandedQuery = query

  // 按术语长度降序匹配，避免短词先命中
  const sortedKeys = Array.from(synonymMap.keys()).sort((a, b) => b.length - a.length)

  for (const key of sortedKeys) {
    if (!query.includes(key)) continue
    const synonyms = synonymMap.get(key) || []
    matchedTerms.push(key)
    synonyms.forEach((syn) => {
      if (!query.includes(syn) && !addedTerms.includes(syn)) {
        addedTerms.push(syn)
        expandedQuery += ' ' + syn
      }
    })
  }

  return { expandedQuery, matchedTerms, addedTerms }
}
