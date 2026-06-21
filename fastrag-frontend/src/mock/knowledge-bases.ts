import type { KnowledgeBase, KnowledgeBaseForm } from '@/types/knowledge'
import { checkApiPermission } from '@/mock/interceptor'

// ===========================================================================
// 知识库数据层 —— 全局唯一数据源
//
// 取代 index.vue 的 knowledgeBases ref 与 edit.vue 的 mockData。
// index / edit / form / ACL 全部引用本文件，保证 kbId → 数据一致。
// ===========================================================================

/** 知识库 mock 存储（与 auth-acl.ts 的 kbId 一一对应） */
const kbStore: KnowledgeBase[] = [
  {
    id: '1',
    name: '产品知识库',
    description: '包含所有产品文档和操作手册',
    category: '产品文档',
    tags: ['产品', '文档'],
    embeddingModel: 'text-embedding-v4',
    dimension: 1024,
    creator: '管理员',
    createdAt: '2026-01-15',
    usedSize: '2.3 GB',
    totalSize: '5 GB',
    type: '团队',
  },
  {
    id: '2',
    name: '技术知识库',
    description: '技术架构、API 文档和开发规范',
    category: '技术文档',
    tags: ['技术', 'API'],
    embeddingModel: 'text-embedding-v4',
    dimension: 1024,
    creator: '开发工程师',
    createdAt: '2026-02-20',
    usedSize: '1.8 GB',
    totalSize: '5 GB',
    type: '团队',
  },
  {
    id: '3',
    name: '客户案例库',
    description: '客户成功案例和最佳实践',
    category: '客户案例',
    tags: ['案例', '客户'],
    embeddingModel: 'text-embedding-v3',
    dimension: 768,
    creator: '产品经理',
    createdAt: '2026-03-10',
    usedSize: '0.5 GB',
    totalSize: '10 GB',
    type: '团队',
  },
  {
    id: '4',
    name: '培训资料库',
    description: '新员工培训和技能提升资料',
    category: '培训资料',
    tags: ['培训'],
    embeddingModel: 'text-embedding-v4',
    dimension: 1024,
    creator: 'HR',
    createdAt: '2026-04-05',
    usedSize: '0.3 GB',
    totalSize: '5 GB',
    type: '团队',
  },
  {
    id: '5',
    name: '我的个人笔记',
    description: '个人学习笔记和工作记录',
    category: '培训资料',
    tags: ['笔记'],
    embeddingModel: 'text-embedding-v3',
    dimension: 768,
    creator: '我',
    createdAt: '2026-05-01',
    usedSize: '0.1 GB',
    totalSize: '1 GB',
    type: '个人',
  },
]

let kbSeq = 100

/** 获取所有知识库 */
export function getKnowledgeBases(): KnowledgeBase[] {
  return kbStore.map((kb) => ({ ...kb }))
}

/** 按 id 获取单个知识库 */
export function getKnowledgeBase(id: string): KnowledgeBase | null {
  const kb = kbStore.find((x) => x.id === id)
  return kb ? { ...kb } : null
}

/** 创建知识库，返回完整记录（含分配的 id） */
export function createKnowledgeBase(
  form: KnowledgeBaseForm,
  creator: string,
): KnowledgeBase {
  checkApiPermission('kb:create')
  const id = String(++kbSeq)
  const now = new Date().toLocaleString('zh-CN')
  const kb: KnowledgeBase = {
    id,
    name: form.name,
    description: form.description,
    category: form.category,
    tags: [...form.tags],
    embeddingModel: form.embeddingModel,
    // 维度由模型决定；mock 简化：v4→1024，v3→768
    dimension: form.embeddingModel.includes('v4') ? 1024 : 768,
    creator,
    createdAt: now,
    usedSize: '0 GB',
    totalSize: '5 GB',
    type: form.permission === 'public' || form.permission === 'team' ? '团队' : '个人',
  }
  kbStore.push(kb)
  return { ...kb }
}

/** 更新知识库（嵌入模型不可改，由调用方保证） */
export function updateKnowledgeBase(
  id: string,
  form: KnowledgeBaseForm,
): KnowledgeBase | null {
  checkApiPermission('kb:edit')
  const idx = kbStore.findIndex((x) => x.id === id)
  if (idx === -1) return null
  kbStore[idx] = {
    ...kbStore[idx],
    name: form.name,
    description: form.description,
    category: form.category,
    tags: [...form.tags],
    type: form.permission === 'public' || form.permission === 'team' ? '团队' : '个人',
  }
  return { ...kbStore[idx] }
}

/** 删除知识库 */
export function deleteKnowledgeBase(id: string): boolean {
  checkApiPermission('kb:delete')
  const idx = kbStore.findIndex((x) => x.id === id)
  if (idx === -1) return false
  kbStore.splice(idx, 1)
  return true
}

// ===========================================================================
// 分类 —— 与 form.vue / index.vue 共享，取代各自硬编码
// ===========================================================================

export interface KbCategory {
  id: string
  name: string
}

export const KB_CATEGORIES: KbCategory[] = [
  { id: '产品文档', name: '产品文档' },
  { id: '技术文档', name: '技术文档' },
  { id: '客户案例', name: '客户案例' },
  { id: '培训资料', name: '培训资料' },
]

/** 获取分类列表（带每个分类下的知识库数量） */
export function getCategories(): (KbCategory & { count: number })[] {
  return KB_CATEGORIES.map((c) => ({
    ...c,
    count: kbStore.filter((kb) => kb.category === c.id).length,
  }))
}
