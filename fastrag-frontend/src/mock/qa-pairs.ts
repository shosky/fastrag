import type { QaPair, QaSource, QaStatus } from '@/types/knowledge'

// ===========================================================================
// 问答对 mock 数据层
// ===========================================================================

const qaStore: Record<string, QaPair[]> = {}
let qaSeq = 100

function now(): string {
  return new Date().toLocaleString('zh-CN')
}

function getStore(kbId: string): QaPair[] {
  if (!qaStore[kbId]) {
    qaStore[kbId] = []
  }
  return qaStore[kbId]
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
