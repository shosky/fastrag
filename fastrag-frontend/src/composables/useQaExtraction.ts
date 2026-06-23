import { ref } from 'vue'
import type { QaPair } from '@/types/knowledge'
import * as api from '@/api'

// ===========================================================================
// 问答抽取 composable
//
// 负责：QA 对 CRUD + 从文档智能抽取候选 QA。
// 已切换为真实 API 调用。
// ===========================================================================

export interface QaCandidate {
  question: string
  answer: string
  fileId?: string
  fileName?: string
}

export function useQaExtraction(kbId: string = 'default') {
  const qaPairs = ref<QaPair[]>([])
  const candidates = ref<QaCandidate[]>([])
  const loading = ref(false)
  const extracting = ref(false)

  async function load() {
    loading.value = true
    try {
      const res = await api.getQaPairs(kbId)
      qaPairs.value = (res as any)?.list || (res as any) || []
    } finally {
      loading.value = false
    }
  }

  function refresh() {
    return load()
  }

  async function add(form: { question: string; answer: string; fileId?: string; fileName?: string }) {
    await api.createQaPair(kbId, form)
    await load()
  }

  async function update(id: string, patch: Partial<QaPair>) {
    await api.updateQaPair(kbId, id, { question: patch.question || '', answer: patch.answer || '' })
    await load()
  }

  async function remove(id: string) {
    await api.deleteQaPair(kbId, id)
    await load()
  }

  async function confirm(id: string) {
    await api.confirmQaPair(kbId, id)
    await load()
  }

  /** 从指定文件中智能抽取候选 QA */
  async function extractFromFiles(fileIds: string[]) {
    extracting.value = true
    try {
      const res = await api.qaExtract(kbId, { fileId: fileIds[0] })
      candidates.value = (res as any) || []
    } finally {
      extracting.value = false
    }
  }

  /** 确认候选 QA 入库 */
  async function acceptCandidate(candidate: QaCandidate) {
    await api.createQaPair(kbId, {
      question: candidate.question,
      answer: candidate.answer,
    })
    candidates.value = candidates.value.filter((c) => c.question !== candidate.question || c.fileId !== candidate.fileId)
    await load()
  }

  /** 丢弃候选 */
  function rejectCandidate(candidate: QaCandidate) {
    candidates.value = candidates.value.filter((c) => c.question !== candidate.question || c.fileId !== candidate.fileId)
  }

  /** 全部确认入库 */
  async function acceptAll() {
    for (const c of candidates.value) {
      await api.createQaPair(kbId, {
        question: c.question,
        answer: c.answer,
      })
    }
    candidates.value = []
    await load()
  }

  /** 编辑候选（在确认前修改问答内容） */
  function editCandidate(index: number, question: string, answer: string) {
    if (candidates.value[index]) {
      candidates.value[index] = { ...candidates.value[index], question, answer }
    }
  }

  return {
    qaPairs,
    candidates,
    loading,
    extracting,
    load,
    refresh,
    add,
    update,
    remove,
    confirm,
    extractFromFiles,
    acceptCandidate,
    rejectCandidate,
    acceptAll,
    editCandidate,
  }
}
