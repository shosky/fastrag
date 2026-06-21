import { ref } from 'vue'
import type { QaPair } from '@/types/knowledge'
import {
  getQaPairs,
  addQaPair,
  updateQaPair,
  deleteQaPair,
  confirmQaPair,
  extractQaFromChunks,
  type QaCandidate,
} from '@/mock/qa-pairs'

// ===========================================================================
// 问答抽取 composable
//
// 负责：QA 对 CRUD + 从文档智能抽取候选 QA。
// ===========================================================================

export function useQaExtraction(kbId: string = 'default') {
  const qaPairs = ref<QaPair[]>([])
  const candidates = ref<QaCandidate[]>([])
  const loading = ref(false)
  const extracting = ref(false)

  function load() {
    qaPairs.value = getQaPairs(kbId)
  }

  function refresh() {
    load()
  }

  function add(form: { question: string; answer: string; fileId?: string; fileName?: string }) {
    addQaPair(kbId, { ...form, source: 'manual' })
    load()
  }

  function update(id: string, patch: Partial<QaPair>) {
    updateQaPair(kbId, id, patch)
    load()
  }

  function remove(id: string) {
    deleteQaPair(kbId, id)
    load()
  }

  function confirm(id: string) {
    confirmQaPair(kbId, id)
    load()
  }

  /** 从指定文件中智能抽取候选 QA */
  async function extractFromFiles(fileIds: string[]) {
    extracting.value = true
    try {
      // 模拟延迟
      await new Promise((r) => setTimeout(r, 800))
      candidates.value = extractQaFromChunks(kbId, fileIds)
    } finally {
      extracting.value = false
    }
  }

  /** 确认候选 QA 入库 */
  function acceptCandidate(candidate: QaCandidate) {
    addQaPair(kbId, {
      question: candidate.question,
      answer: candidate.answer,
      source: 'ai',
      fileId: candidate.fileId,
      fileName: candidate.fileName,
    })
    // 从候选列表移除
    candidates.value = candidates.value.filter((c) => c.question !== candidate.question || c.fileId !== candidate.fileId)
    load()
  }

  /** 丢弃候选 */
  function rejectCandidate(candidate: QaCandidate) {
    candidates.value = candidates.value.filter((c) => c.question !== candidate.question || c.fileId !== candidate.fileId)
  }

  /** 全部确认入库 */
  function acceptAll() {
    candidates.value.forEach((c) => {
      addQaPair(kbId, {
        question: c.question,
        answer: c.answer,
        source: 'ai',
        fileId: c.fileId,
        fileName: c.fileName,
      })
    })
    candidates.value = []
    load()
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
