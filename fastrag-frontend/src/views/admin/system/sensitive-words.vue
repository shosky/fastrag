<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import { PERMISSIONS } from '@/types/auth'
import * as api from '@/api'

const showDialog = ref(false)
const dialogTitle = ref('添加敏感词')
const loading = ref(false)
const editingId = ref<string | null>(null)

const formData = ref({
  word: '',
  reply: '',
  blockInput: false,
  blockSearch: false,
  replaceAnswer: false,
})

// 拦截回复与答案替换文本共用同一个存储字段（replacement），标签随勾选的开关变化
const replyFieldLabel = computed(() => {
  if (formData.value.blockInput && formData.value.replaceAnswer) return '回复 / 替换文本'
  if (formData.value.blockInput) return '命中后的回复文案'
  return '答案替换为'
})
const replyFieldPlaceholder = computed(() => {
  if (formData.value.blockInput && formData.value.replaceAnswer) return '命中后直接回复用户的文案；答案中命中时也替换为该文本'
  if (formData.value.blockInput) return '命中后直接回复用户的文案（不调用模型），留空使用默认文案'
  return '答案中命中时替换为该文本，留空则替换为 ***'
})

const wordList = ref<any[]>([])

// --- 分页 ---
const { currentPage, pageSize, handleCurrentChange, handleSizeChange } = usePagination(10)

const paginatedWords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return wordList.value.slice(start, start + pageSize.value)
})

async function loadWords() {
  loading.value = true
  try {
    const res = await api.getSensitiveWords()
    wordList.value = (res as any) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadWords)

function handleAdd() {
  dialogTitle.value = '添加敏感词'
  editingId.value = null
  formData.value = { word: '', reply: '', blockInput: false, blockSearch: false, replaceAnswer: false }
  showDialog.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑敏感词'
  editingId.value = row.id
  formData.value = { ...row }
  showDialog.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该敏感词？', '删除确认', { type: 'warning' })
    await api.deleteSensitiveWord(row.id)
    await loadWords()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleSave() {
  if (!formData.value.word) {
    ElMessage.warning('请输入敏感词')
    return
  }
  const data = {
    word: formData.value.word,
    reply: formData.value.reply,
    blockInput: formData.value.blockInput,
    blockSearch: formData.value.blockSearch,
    replaceAnswer: formData.value.replaceAnswer,
  }
  if (editingId.value) {
    await api.updateSensitiveWord(editingId.value, data)
  } else {
    await api.createSensitiveWord(data)
  }
  showDialog.value = false
  await loadWords()
  ElMessage.success('保存成功')
}

function handleDownloadTemplate() {
  api.downloadSensitiveWordTemplate().then((res: any) => {
    const blob = new Blob([res], { type: 'text/csv;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'sensitive_words_template.csv'
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('模板下载成功')
  }).catch(() => ElMessage.error('下载失败'))
}

function handleBatchImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.csv'
  input.onchange = async (e: any) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const res: any = await api.importSensitiveWords(file)
      ElMessage.success(`导入成功，共 ${res?.imported ?? 0} 条`)
      await loadWords()
    } catch {
      ElMessage.error('导入失败')
    }
  }
  input.click()
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">敏感词设置</div>
        <div>
          <el-button @click="handleDownloadTemplate">下载模板</el-button>
          <el-button v-permission="PERMISSIONS.SENSITIVE_WORD_IMPORT" @click="handleBatchImport">批量导入</el-button>
          <el-button type="primary" v-permission="PERMISSIONS.SENSITIVE_WORD_CREATE" @click="handleAdd">添加敏感词</el-button>
        </div>
      </div>

      <el-table :data="paginatedWords" stripe>
        <el-table-column prop="word" label="敏感词" />
        <el-table-column prop="reply" label="回复 / 替换文本" show-overflow-tooltip />
        <el-table-column label="阻止用户输入" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.blockInput ? 'danger' : 'info'" size="small">{{ row.blockInput ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="联网检索屏蔽" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.blockSearch ? 'danger' : 'info'" size="small">{{ row.blockSearch ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" v-permission="PERMISSIONS.SENSITIVE_WORD_EDIT" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" v-permission="PERMISSIONS.SENSITIVE_WORD_DELETE" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!wordList.length && !loading" description="暂无敏感词" />

      <div class="sensitive-words__pagination">
        <el-pagination
          v-if="wordList.length > pageSize"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="wordList.length"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px">
      <el-form label-width="140px">
        <el-form-item label="敏感词" required>
          <el-input v-model="formData.word" placeholder="请输入敏感词" />
        </el-form-item>
        <el-form-item label="阻止用户输入">
          <el-switch v-model="formData.blockInput" />
          <span class="flag-hint">用户提问命中时直接拦截，不调用模型</span>
        </el-form-item>
        <el-form-item label="联网检索屏蔽">
          <el-switch v-model="formData.blockSearch" disabled />
          <span class="flag-hint">预留功能，暂未接入执行链路</span>
        </el-form-item>
        <el-form-item label="模型生成答案时替换">
          <el-switch v-model="formData.replaceAnswer" />
          <span class="flag-hint">回答中命中时替换为下方文本</span>
        </el-form-item>
        <el-form-item v-if="formData.blockInput || formData.replaceAnswer" :label="replyFieldLabel">
          <el-input v-model="formData.reply" type="textarea" :rows="3" :placeholder="replyFieldPlaceholder" />
          <span v-if="formData.blockInput && formData.replaceAnswer" class="flag-hint">两种方式共用此文本</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.sensitive-words__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.flag-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
