<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getModels, triggerQaGeneration, getQaGenerationStatus } from '@/api'

/**
 * 问答对提炼参数对话框（qa-generation 前端 P1-P3 共用组件）：
 * 单文件（分片管理页/文件列表行内）与多选批量（文件列表批量栏）三个入口都挂载本组件，
 * 参数应用于全部传入文件；触发后对话框内轮询任务进度（3s），关闭即停止轮询（后台任务继续）。
 */
const props = defineProps<{
  modelValue: boolean
  kbId: string
  files: Array<{ id: string; name: string }>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submitted'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const count = ref(3)
const customInstruction = ref('')
const mode = ref<'replace' | 'append'>('replace')
const modelCode = ref('')
const modelOptions = ref<Array<{ label: string; value: string }>>([])
const triggering = ref(false)

interface TaskStatus {
  fileId: string
  fileName: string
  status: string
  totalChunks: number
  succeededChunks: number
  failedChunks: number
  error: string | null
}

const tasks = ref<TaskStatus[]>([])
const STATUS_LABELS: Record<string, string> = {
  triggering: '受理中',
  running: '提炼中',
  success: '已完成',
  partial: '部分完成',
  failed: '失败',
}
const STATUS_TYPES: Record<string, 'success' | 'info' | 'warning' | 'danger' | ''> = {
  triggering: 'info',
  running: '',
  success: 'success',
  partial: 'warning',
  failed: 'danger',
}
const TERMINAL = ['success', 'partial', 'failed']

let timer: number | null = null

watch(visible, (v) => {
  if (v) {
    tasks.value = []
    loadModels()
  } else {
    stopPolling()
  }
})

onBeforeUnmount(stopPolling)

async function loadModels() {
  try {
    const res: any = await getModels()
    const rows = res?.list || res?.data || res || []
    modelOptions.value = (Array.isArray(rows) ? rows : [])
      .map((m: any) => ({ label: m.name || m.code || m.id, value: m.code || m.id }))
      .filter((o: any) => !!o.value)
  } catch {
    modelOptions.value = []
  }
}

async function handleTrigger() {
  if (props.files.length === 0 || triggering.value) return
  triggering.value = true
  tasks.value = props.files.map((f) => ({
    fileId: f.id,
    fileName: f.name,
    status: 'triggering',
    totalChunks: 0,
    succeededChunks: 0,
    failedChunks: 0,
    error: null,
  }))
  let ok = 0
  let fail = 0
  for (const t of tasks.value) {
    try {
      await triggerQaGeneration(props.kbId, t.fileId, {
        count: count.value,
        customInstruction: customInstruction.value.trim() || undefined,
        mode: mode.value,
        model: modelCode.value || undefined,
      })
      t.status = 'running'
      ok++
    } catch (e: any) {
      t.status = 'failed'
      t.error = e?.message || '触发失败'
      fail++
    }
  }
  triggering.value = false
  if (ok > 0) {
    emit('submitted')
    startPolling()
  }
  if (fail > 0) ElMessage.warning(`已受理 ${ok} 个文件，${fail} 个失败`)
  else ElMessage.success(`已受理 ${ok} 个文件的问答对提炼，完成后以草稿落库`)
}

async function poll() {
  const pending = tasks.value.filter((t) => !TERMINAL.includes(t.status))
  if (pending.length === 0) {
    stopPolling()
    return
  }
  for (const t of pending) {
    try {
      const res: any = await getQaGenerationStatus(props.kbId, t.fileId)
      const d = res?.data?.status ? res.data : res
      if (d?.status) {
        t.status = d.status
        t.totalChunks = d.totalChunks ?? 0
        t.succeededChunks = d.succeededChunks ?? 0
        t.failedChunks = d.failedChunks ?? 0
        t.error = d.error ?? null
      }
    } catch {
      /* 单次轮询失败忽略，下一轮重试 */
    }
  }
  if (tasks.value.length > 0 && tasks.value.every((t) => TERMINAL.includes(t.status))) {
    stopPolling()
    ElMessage.success('问答对提炼全部结束，草稿可在「问答对」页确认生效')
  }
}

function startPolling() {
  stopPolling()
  timer = window.setInterval(poll, 3000)
  poll()
}

function stopPolling() {
  if (timer != null) {
    window.clearInterval(timer)
    timer = null
  }
}

function progressText(t: any): string {
  if (t.status === 'triggering') return '—'
  return `${t.succeededChunks}/${t.totalChunks} 片成功${t.failedChunks > 0 ? `，${t.failedChunks} 片失败` : ''}`
}
</script>

<template>
  <el-dialog v-model="visible" title="生成问答对（AI 提炼）" width="580px" :close-on-click-modal="false">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 14px"
      description="以分片为单位从文档内容提炼「问题 + 答案」，答案仅来自分片原文，来源文档随问答对记录；产出逐片实时以草稿落库（关闭弹窗不影响后台进度），需在「问答对」页确认后才会参与检索。"
    />
    <el-form label-width="92px">
      <el-form-item label="每分片条数">
        <el-input-number v-model="count" :min="1" :max="10" />
        <span style="margin-left: 10px; color: #909399; font-size: 13px">1-10 条，缺省 3 条</span>
      </el-form-item>
      <el-form-item label="自定义指令">
        <el-input
          v-model="customInstruction"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          placeholder="可选，如：只围绕产品参数出题"
        />
      </el-form-item>
      <el-form-item label="重跑模式">
        <el-radio-group v-model="mode">
          <el-radio value="replace">覆盖 AI 旧对</el-radio>
          <el-radio value="append">保留追加</el-radio>
        </el-radio-group>
        <span style="margin-left: 10px; color: #909399; font-size: 13px">仅影响 AI 产出的问答对，手动录入的不受影响</span>
      </el-form-item>
      <el-form-item label="提炼模型">
        <el-select v-model="modelCode" clearable placeholder="系统默认模型" style="width: 240px">
          <el-option v-for="opt in modelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-table v-if="tasks.length > 0" :data="tasks" size="small" style="margin-top: 6px">
      <el-table-column prop="fileName" label="文件" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <el-tag :type="STATUS_TYPES[row.status] || 'info'" size="small">{{ STATUS_LABELS[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" min-width="150">
        <template #default="{ row }">
          <span v-if="row.error" style="color: #f56c6c">{{ row.error }}</span>
          <span v-else>{{ progressText(row) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="triggering" @click="handleTrigger">
        提炼{{ files.length > 1 ? `（${files.length} 个文件）` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>
