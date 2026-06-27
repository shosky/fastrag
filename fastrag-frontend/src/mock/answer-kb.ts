import { checkApiPermission } from './interceptor'

// ===========================================================================
// 类型定义
// ===========================================================================

export interface FaqCategory {
  id: string
  name: string
  parentId?: string
  children?: FaqCategory[]
  sort: number
  createdAt: string
}

export interface FaqItem {
  id: string
  categoryId: string
  question: string
  answer: string
  answerType: 'text' | 'rich' | 'table'
  keywords: string[]
  similarQuestions: string[]
  status: 'draft' | 'active' | 'disabled'
  effectiveTime?: string
  expireTime?: string
  effectiveScope?: string[]
  priority: number
  hitCount: number
  creator: string
  createdAt: string
  updatedAt: string
}

export interface AnswerTemplate {
  id: string
  name: string
  content: string
  variables: { name: string; type: string; defaultValue: string }[]
  category: string
  usageCount: number
  creator: string
  createdAt: string
}

export interface KeywordConfig {
  id: string
  faqId: string
  faqQuestion: string
  keywords: string[]
  matchMode: 'exact' | 'fuzzy' | 'regex'
  weight: number
  createdAt: string
}

export interface KnowledgeAttribute {
  id: string
  name: string
  type: 'text' | 'number' | 'select' | 'date' | 'boolean'
  options?: string[]
  required: boolean
  defaultValue?: string
  description: string
  createdAt: string
}

export interface AnswerTable {
  id: string
  name: string
  description: string
  columns: { name: string; type: string; width: number }[]
  rows: Record<string, any>[]
  createdAt: string
  updatedAt: string
}

export interface AnswerDocument {
  id: string
  name: string
  type: 'pdf' | 'docx' | 'xlsx' | 'txt'
  size: number
  url: string
  status: 'processing' | 'ready' | 'failed'
  creator: string
  createdAt: string
}

// ===========================================================================
// 常量
// ===========================================================================

export const FAQ_STATUS_OPTIONS = [
  { label: '草稿', value: 'draft' },
  { label: '启用', value: 'active' },
  { label: '停用', value: 'disabled' },
]

export const FAQ_STATUS_LABELS: Record<string, string> = {
  draft: '草稿',
  active: '启用',
  disabled: '停用',
}

export const FAQ_STATUS_COLORS: Record<string, string> = {
  draft: 'info',
  active: 'success',
  disabled: 'danger',
}

export const ANSWER_TYPE_OPTIONS = [
  { label: '纯文本', value: 'text' },
  { label: '富文本', value: 'rich' },
  { label: '表格', value: 'table' },
]

export const MATCH_MODE_OPTIONS = [
  { label: '精确匹配', value: 'exact' },
  { label: '模糊匹配', value: 'fuzzy' },
  { label: '正则匹配', value: 'regex' },
]

export const ATTRIBUTE_TYPE_OPTIONS = [
  { label: '文本', value: 'text' },
  { label: '数字', value: 'number' },
  { label: '下拉选择', value: 'select' },
  { label: '日期', value: 'date' },
  { label: '布尔', value: 'boolean' },
]

export const DOC_TYPE_OPTIONS = [
  { label: 'PDF', value: 'pdf' },
  { label: 'Word', value: 'docx' },
  { label: 'Excel', value: 'xlsx' },
  { label: '文本', value: 'txt' },
]

// ===========================================================================
// 内存存储
// ===========================================================================

let categoryStore: FaqCategory[] = []
let faqStore: FaqItem[] = []
let templateStore: AnswerTemplate[] = []
let keywordStore: KeywordConfig[] = []
let attributeStore: KnowledgeAttribute[] = []
let tableStore: AnswerTable[] = []
let documentStore: AnswerDocument[] = []
let faqSeq = 100
let catSeq = 100
let tplSeq = 100
let kwSeq = 100
let attrSeq = 100
let tblSeq = 100
let docSeq = 100

function initStore() {
  if (categoryStore.length > 0) return

  categoryStore = [
    { id: 'cat-1', name: '常见问题', sort: 1, createdAt: '2026-01-10T08:00:00Z' },
    { id: 'cat-2', name: '账户相关', parentId: 'cat-1', sort: 1, createdAt: '2026-01-10T08:00:00Z' },
    { id: 'cat-3', name: '支付相关', parentId: 'cat-1', sort: 2, createdAt: '2026-01-10T08:00:00Z' },
    { id: 'cat-4', name: '物流相关', parentId: 'cat-1', sort: 3, createdAt: '2026-01-10T08:00:00Z' },
    { id: 'cat-5', name: '非常见问题', sort: 2, createdAt: '2026-01-10T08:00:00Z' },
    { id: 'cat-6', name: '技术问题', parentId: 'cat-5', sort: 1, createdAt: '2026-01-10T08:00:00Z' },
    { id: 'cat-7', name: '投诉建议', parentId: 'cat-5', sort: 2, createdAt: '2026-01-10T08:00:00Z' },
  ]
  catSeq = 100

  const now = new Date().toISOString()
  faqStore = Array.from({ length: 20 }, (_, i) => ({
    id: `faq-${i + 1}`,
    categoryId: ['cat-2', 'cat-3', 'cat-4', 'cat-6', 'cat-7'][i % 5],
    question: `这是第${i + 1}个常见问题？`,
    answer: `这是第${i + 1}个常见问题的详细解答，包含了用户需要了解的关键信息。`,
    answerType: (['text', 'rich', 'table'] as const)[i % 3],
    keywords: [`关键词${i + 1}A`, `关键词${i + 1}B`],
    similarQuestions: [`类似问题${i + 1}A？`, `类似问题${i + 1}B？`],
    status: (['draft', 'active', 'disabled'] as const)[i % 3],
    effectiveTime: i % 4 === 0 ? '2026-01-01T00:00:00Z' : undefined,
    expireTime: i % 4 === 0 ? '2026-12-31T23:59:59Z' : undefined,
    effectiveScope: i % 3 === 0 ? ['在线客服', '智能机器人'] : undefined,
    priority: (i % 5) + 1,
    hitCount: Math.floor(Math.random() * 1000),
    creator: 'admin',
    createdAt: now,
    updatedAt: now,
  }))
  faqSeq = 100

  templateStore = Array.from({ length: 8 }, (_, i) => ({
    id: `tpl-${i + 1}`,
    name: `应答模板${i + 1}`,
    content: `您好，关于您的问题「{{question}}」，以下是解答：\n{{answer}}`,
    variables: [
      { name: 'question', type: 'string', defaultValue: '' },
      { name: 'answer', type: 'string', defaultValue: '' },
    ],
    category: ['通用', '售后', '售前', '技术'][i % 4],
    usageCount: Math.floor(Math.random() * 200),
    creator: 'admin',
    createdAt: now,
  }))
  tplSeq = 100

  keywordStore = faqStore.slice(0, 10).map((faq, i) => ({
    id: `kw-${i + 1}`,
    faqId: faq.id,
    faqQuestion: faq.question,
    keywords: faq.keywords,
    matchMode: (['exact', 'fuzzy', 'regex'] as const)[i % 3],
    weight: Math.round((i % 5 + 1) * 0.2 * 100) / 100,
    createdAt: now,
  }))
  kwSeq = 100

  attributeStore = [
    { id: 'attr-1', name: '知识来源', type: 'select' as const, options: ['人工录入', 'AI生成', '文档导入'], required: true, description: '知识条目的来源渠道', createdAt: now },
    { id: 'attr-2', name: '有效期', type: 'date' as const, required: false, description: '知识条目的有效截止日期', createdAt: now },
    { id: 'attr-3', name: '优先级', type: 'number' as const, required: true, defaultValue: '5', description: '知识条目的优先级(1-10)', createdAt: now },
    { id: 'attr-4', name: '是否公开', type: 'boolean' as const, required: false, defaultValue: 'true', description: '知识条目是否对外公开', createdAt: now },
    { id: 'attr-5', name: '所属部门', type: 'text' as const, required: false, description: '知识条目所属的部门', createdAt: now },
  ]
  attrSeq = 100

  tableStore = Array.from({ length: 5 }, (_, i) => ({
    id: `tbl-${i + 1}`,
    name: `表格知识${i + 1}`,
    description: `表格知识描述${i + 1}`,
    columns: [
      { name: '项目', type: 'string', width: 150 },
      { name: '说明', type: 'string', width: 300 },
      { name: '状态', type: 'string', width: 100 },
    ],
    rows: Array.from({ length: 3 }, (_, j) => ({
      项目: `项目${j + 1}`,
      说明: `这是第${j + 1}项的说明内容`,
      状态: j % 2 === 0 ? '启用' : '停用',
    })),
    createdAt: now,
    updatedAt: now,
  }))
  tblSeq = 100

  documentStore = Array.from({ length: 8 }, (_, i) => ({
    id: `doc-${i + 1}`,
    name: `应答文档${i + 1}.${['pdf', 'docx', 'xlsx', 'txt'][i % 4]}`,
    type: (['pdf', 'docx', 'xlsx', 'txt'] as const)[i % 4],
    size: Math.floor(Math.random() * 5000000) + 100000,
    url: `/uploads/docs/answer-doc-${i + 1}.${['pdf', 'docx', 'xlsx', 'txt'][i % 4]}`,
    status: (['processing', 'ready', 'failed'] as const)[i % 3],
    creator: 'admin',
    createdAt: now,
  }))
  docSeq = 100
}

// ===========================================================================
// FAQ分类 CRUD
// ===========================================================================

export function getFaqCategories(): FaqCategory[] {
  initStore()
  return categoryStore
}

export function createFaqCategory(data: Partial<FaqCategory>): FaqCategory {
  checkApiPermission('kb:write')
  initStore()
  const item: FaqCategory = {
    id: `cat-${++catSeq}`,
    name: data.name || '',
    parentId: data.parentId,
    sort: data.sort || 0,
    createdAt: new Date().toISOString(),
  }
  categoryStore.push(item)
  return item
}

export function updateFaqCategory(id: string, data: Partial<FaqCategory>): FaqCategory | null {
  checkApiPermission('kb:write')
  initStore()
  const idx = categoryStore.findIndex(c => c.id === id)
  if (idx === -1) return null
  categoryStore[idx] = { ...categoryStore[idx], ...data }
  return categoryStore[idx]
}

export function deleteFaqCategory(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = categoryStore.findIndex(c => c.id === id)
  if (idx === -1) return false
  categoryStore.splice(idx, 1)
  return true
}

// ===========================================================================
// FAQ CRUD
// ===========================================================================

export function getFaqList(params?: { page?: number; pageSize?: number; keyword?: string; status?: string; categoryId?: string }) {
  initStore()
  let list = [...faqStore]
  if (params?.keyword) list = list.filter(f => f.question.includes(params.keyword!))
  if (params?.status) list = list.filter(f => f.status === params.status)
  if (params?.categoryId) list = list.filter(f => f.categoryId === params.categoryId)
  const total = list.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}

export function getFaqById(id: string): FaqItem | null {
  initStore()
  return faqStore.find(f => f.id === id) || null
}

export function createFaq(data: Partial<FaqItem>): FaqItem {
  checkApiPermission('kb:write')
  initStore()
  const now = new Date().toISOString()
  const item: FaqItem = {
    id: `faq-${++faqSeq}`,
    categoryId: data.categoryId || '',
    question: data.question || '',
    answer: data.answer || '',
    answerType: data.answerType || 'text',
    keywords: data.keywords || [],
    similarQuestions: data.similarQuestions || [],
    status: data.status || 'draft',
    effectiveTime: data.effectiveTime,
    expireTime: data.expireTime,
    effectiveScope: data.effectiveScope,
    priority: data.priority || 5,
    hitCount: 0,
    creator: 'admin',
    createdAt: now,
    updatedAt: now,
  }
  faqStore.push(item)
  return item
}

export function updateFaq(id: string, data: Partial<FaqItem>): FaqItem | null {
  checkApiPermission('kb:write')
  initStore()
  const idx = faqStore.findIndex(f => f.id === id)
  if (idx === -1) return null
  faqStore[idx] = { ...faqStore[idx], ...data, updatedAt: new Date().toISOString() }
  return faqStore[idx]
}

export function deleteFaq(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = faqStore.findIndex(f => f.id === id)
  if (idx === -1) return false
  faqStore.splice(idx, 1)
  return true
}

// ===========================================================================
// 应答模板 CRUD
// ===========================================================================

export function getTemplateList(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore()
  let list = [...templateStore]
  if (params?.keyword) list = list.filter(t => t.name.includes(params.keyword!))
  const total = list.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}

export function createTemplate(data: Partial<AnswerTemplate>): AnswerTemplate {
  checkApiPermission('kb:write')
  initStore()
  const item: AnswerTemplate = {
    id: `tpl-${++tplSeq}`,
    name: data.name || '',
    content: data.content || '',
    variables: data.variables || [],
    category: data.category || '通用',
    usageCount: 0,
    creator: 'admin',
    createdAt: new Date().toISOString(),
  }
  templateStore.push(item)
  return item
}

export function updateTemplate(id: string, data: Partial<AnswerTemplate>): AnswerTemplate | null {
  checkApiPermission('kb:write')
  initStore()
  const idx = templateStore.findIndex(t => t.id === id)
  if (idx === -1) return null
  templateStore[idx] = { ...templateStore[idx], ...data }
  return templateStore[idx]
}

export function deleteTemplate(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = templateStore.findIndex(t => t.id === id)
  if (idx === -1) return false
  templateStore.splice(idx, 1)
  return true
}

// ===========================================================================
// 关键词配置 CRUD
// ===========================================================================

export function getKeywordList(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore()
  let list = [...keywordStore]
  if (params?.keyword) list = list.filter(k => k.faqQuestion.includes(params.keyword!))
  const total = list.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}

export function createKeyword(data: Partial<KeywordConfig>): KeywordConfig {
  checkApiPermission('kb:write')
  initStore()
  const item: KeywordConfig = {
    id: `kw-${++kwSeq}`,
    faqId: data.faqId || '',
    faqQuestion: data.faqQuestion || '',
    keywords: data.keywords || [],
    matchMode: data.matchMode || 'fuzzy',
    weight: data.weight || 1,
    createdAt: new Date().toISOString(),
  }
  keywordStore.push(item)
  return item
}

export function updateKeyword(id: string, data: Partial<KeywordConfig>): KeywordConfig | null {
  checkApiPermission('kb:write')
  initStore()
  const idx = keywordStore.findIndex(k => k.id === id)
  if (idx === -1) return null
  keywordStore[idx] = { ...keywordStore[idx], ...data }
  return keywordStore[idx]
}

export function deleteKeyword(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = keywordStore.findIndex(k => k.id === id)
  if (idx === -1) return false
  keywordStore.splice(idx, 1)
  return true
}

// ===========================================================================
// 属性管理 CRUD
// ===========================================================================

export function getAttributeList(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore()
  let list = [...attributeStore]
  if (params?.keyword) list = list.filter(a => a.name.includes(params.keyword!))
  const total = list.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}

export function createAttribute(data: Partial<KnowledgeAttribute>): KnowledgeAttribute {
  checkApiPermission('kb:write')
  initStore()
  const item: KnowledgeAttribute = {
    id: `attr-${++attrSeq}`,
    name: data.name || '',
    type: data.type || 'text',
    options: data.options,
    required: data.required ?? false,
    defaultValue: data.defaultValue,
    description: data.description || '',
    createdAt: new Date().toISOString(),
  }
  attributeStore.push(item)
  return item
}

export function updateAttribute(id: string, data: Partial<KnowledgeAttribute>): KnowledgeAttribute | null {
  checkApiPermission('kb:write')
  initStore()
  const idx = attributeStore.findIndex(a => a.id === id)
  if (idx === -1) return null
  attributeStore[idx] = { ...attributeStore[idx], ...data }
  return attributeStore[idx]
}

export function deleteAttribute(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = attributeStore.findIndex(a => a.id === id)
  if (idx === -1) return false
  attributeStore.splice(idx, 1)
  return true
}

// ===========================================================================
// 表格管理 CRUD
// ===========================================================================

export function getTableList(params?: { page?: number; pageSize?: number; keyword?: string }) {
  initStore()
  let list = [...tableStore]
  if (params?.keyword) list = list.filter(t => t.name.includes(params.keyword!))
  const total = list.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}

export function getTableById(id: string): AnswerTable | null {
  initStore()
  return tableStore.find(t => t.id === id) || null
}

export function createTable(data: Partial<AnswerTable>): AnswerTable {
  checkApiPermission('kb:write')
  initStore()
  const now = new Date().toISOString()
  const item: AnswerTable = {
    id: `tbl-${++tblSeq}`,
    name: data.name || '',
    description: data.description || '',
    columns: data.columns || [],
    rows: data.rows || [],
    createdAt: now,
    updatedAt: now,
  }
  tableStore.push(item)
  return item
}

export function updateTable(id: string, data: Partial<AnswerTable>): AnswerTable | null {
  checkApiPermission('kb:write')
  initStore()
  const idx = tableStore.findIndex(t => t.id === id)
  if (idx === -1) return null
  tableStore[idx] = { ...tableStore[idx], ...data, updatedAt: new Date().toISOString() }
  return tableStore[idx]
}

export function deleteTable(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = tableStore.findIndex(t => t.id === id)
  if (idx === -1) return false
  tableStore.splice(idx, 1)
  return true
}

// ===========================================================================
// 文档管理 CRUD
// ===========================================================================

export function getDocumentList(params?: { page?: number; pageSize?: number; keyword?: string; status?: string }) {
  initStore()
  let list = [...documentStore]
  if (params?.keyword) list = list.filter(d => d.name.includes(params.keyword!))
  if (params?.status) list = list.filter(d => d.status === params.status)
  const total = list.length
  const page = params?.page || 1
  const pageSize = params?.pageSize || 20
  return { list: list.slice((page - 1) * pageSize, page * pageSize), total }
}

export function createDocument(data: Partial<AnswerDocument>): AnswerDocument {
  checkApiPermission('kb:write')
  initStore()
  const item: AnswerDocument = {
    id: `doc-${++docSeq}`,
    name: data.name || '',
    type: data.type || 'pdf',
    size: data.size || 0,
    url: data.url || '',
    status: 'processing',
    creator: 'admin',
    createdAt: new Date().toISOString(),
  }
  documentStore.push(item)
  return item
}

export function deleteDocument(id: string): boolean {
  checkApiPermission('kb:delete')
  initStore()
  const idx = documentStore.findIndex(d => d.id === id)
  if (idx === -1) return false
  documentStore.splice(idx, 1)
  return true
}
