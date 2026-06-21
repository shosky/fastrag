import type { QaPair, QaSource, QaStatus } from '@/types/knowledge'
import { mockChunks } from './chunks'

// ===========================================================================
// 问答对 mock 数据层
//
// 支持：手动添加 QA 对 + 从文档 chunks 中智能抽取候选 QA。
// ===========================================================================

const qaStore: Record<string, QaPair[]> = {}
let qaSeq = 100

function now(): string {
  return new Date().toLocaleString('zh-CN')
}

function getStore(kbId: string): QaPair[] {
  if (!qaStore[kbId]) {
    // 种子数据：从 chunks 中预生成几条 QA
    qaStore[kbId] = seedQaPairs(kbId)
  }
  return qaStore[kbId]
}

function seedQaPairs(kbId: string): QaPair[] {
  return [
    {
      id: 'qa_1',
      kbId,
      fileId: '1',
      fileName: 'AIS 平台用户手册.pdf',
      question: '什么是小微ICT？',
      answer: '小微ICT是响应集团推进中小企业标准ICT服务要求打造的产品，通过标准化产品、属地化自主交付服务提升差异化、稳固两线，带动战新业务规模发展。',
      source: 'ai',
      status: 'confirmed',
      createdAt: '2026-06-15 10:30',
    },
    {
      id: 'qa_2',
      kbId,
      fileId: '1',
      fileName: 'AIS 平台用户手册.pdf',
      question: '全光组网标准包的价格是多少？',
      answer: '全光组网标准化礼包-小微ICT标准包：1主1从标准包价格 2299 元。包含设备-主光猫（华为B866-S2）1078 元、设备-从光猫（华为B671-S2）619 元等。',
      source: 'ai',
      status: 'confirmed',
      createdAt: '2026-06-15 10:31',
    },
    {
      id: 'qa_3',
      kbId,
      question: '如何查询小微ICT业务的办理进度？',
      answer: '可以通过10009客服热线或线上营业厅查询办理进度。',
      source: 'manual',
      status: 'draft',
      createdAt: '2026-06-16 14:00',
    },
  ]
}

// --- CRUD ---
export function getQaPairs(kbId: string): QaPair[] {
  return getStore(kbId).map((q) => ({ ...q }))
}

export function addQaPair(
  kbId: string,
  form: { question: string; answer: string; source: QaSource; fileId?: string; fileName?: string },
): QaPair {
  const pair: QaPair = {
    id: `qa_${++qaSeq}`,
    kbId,
    question: form.question,
    answer: form.answer,
    source: form.source,
    status: 'draft',
    fileId: form.fileId,
    fileName: form.fileName,
    createdAt: now(),
  }
  getStore(kbId).push(pair)
  return { ...pair }
}

export function updateQaPair(kbId: string, id: string, patch: Partial<QaPair>): QaPair | null {
  const store = getStore(kbId)
  const idx = store.findIndex((q) => q.id === id)
  if (idx === -1) return null
  store[idx] = { ...store[idx], ...patch }
  return { ...store[idx] }
}

export function deleteQaPair(kbId: string, id: string): boolean {
  const store = getStore(kbId)
  const idx = store.findIndex((q) => q.id === id)
  if (idx === -1) return false
  store.splice(idx, 1)
  return true
}

export function confirmQaPair(kbId: string, id: string): QaPair | null {
  return updateQaPair(kbId, id, { status: 'confirmed' })
}

// --- 智能抽取（mock：从 chunks 中按句式匹配抽取候选 QA）---

/** QA 抽取候选结果 */
export interface QaCandidate {
  question: string
  answer: string
  fileId: string
  fileName: string
  chunkIndex: number
}

// 简单的问答句式匹配规则
const QA_PATTERNS = [
  { qPattern: /(.{2,10}?)是什么[？?]/g, makeQ: (m: RegExpMatchArray) => m[0].replace(/[。.]$/, '') },
  { qPattern: /什么是(.{2,15})[？?。.]/g, makeQ: (m: RegExpMatchArray) => `什么是${m[1]}？` },
  { qPattern: /(.{2,10})的(.{2,8})是多少[？?。.]/g, makeQ: (m: RegExpMatchArray) => `${m[1]}的${m[2]}是多少？` },
  { qPattern: /如何(.{2,15})[？?。.]/g, makeQ: (m: RegExpMatchArray) => `如何${m[1]}？` },
  { qPattern: /怎么(.{2,15})[？?。.]/g, makeQ: (m: RegExpMatchArray) => `怎么${m[1]}？` },
]

/**
 * 从指定文件的 chunks 中抽取候选 QA 对。
 * 模拟 AI 抽取：按句式匹配，找到疑问句 → 取其所在句子作为答案。
 */
export function extractQaFromChunks(
  kbId: string,
  fileIds: string[],
): QaCandidate[] {
  const candidates: QaCandidate[] = []
  const relevantChunks = mockChunks.filter((c) => fileIds.includes(c.fileId))

  for (const chunk of relevantChunks) {
    // 按句号/问号分句
    const sentences = chunk.content.split(/[。？?！!]/).filter((s) => s.trim().length > 4)

    for (const pattern of QA_PATTERNS) {
      // 重置 lastIndex（因为用了 /g 标志）
      pattern.qPattern.lastIndex = 0
      let match: RegExpMatchArray | null
      while ((match = pattern.qPattern.exec(chunk.content)) !== null) {
        const question = pattern.makeQ(match)
        // 答案：取匹配位置所在的整个句子（或前后两句作为上下文）
        const matchIdx = chunk.content.indexOf(match[0])
        const answerSentence = sentences.find((s) => {
          const sIdx = chunk.content.indexOf(s)
          return sIdx !== -1 && matchIdx >= sIdx && matchIdx <= sIdx + s.length + 2
        }) || match[0]

        // 确保答案是一个完整的陈述句
        const answer = answerSentence.trim().replace(/^[，,、]/, '')

        if (question && answer && answer.length > 8) {
          candidates.push({
            question,
            answer,
            fileId: chunk.fileId,
            fileName: chunk.fileName,
            chunkIndex: chunk.chunkIndex,
          })
        }
      }
    }
  }

  // 去重（相同问题只保留最长答案）
  const seen = new Map<string, QaCandidate>()
  for (const c of candidates) {
    const existing = seen.get(c.question)
    if (!existing || c.answer.length > existing.answer.length) {
      seen.set(c.question, c)
    }
  }

  return Array.from(seen.values())
}
