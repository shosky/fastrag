<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const wordList = ref<any[]>([])

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
  ElMessage.info('模板下载中...')
}

function handleBatchImport() {
  ElMessage.info('批量导入功能')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">敏感词设置</div>
        <div>
          <el-button @click="handleDownloadTemplate">下载模板</el-button>
          <el-button @click="handleBatchImport">批量导入</el-button>
          <el-button type="primary" @click="handleAdd">添加敏感词</el-button>
        </div>
      </div>

      <el-table :data="wordList" stripe>
        <el-table-column prop="word" label="敏感词" />
        <el-table-column prop="reply" label="指定回复" show-overflow-tooltip />
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
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!wordList.length && !loading" description="暂无敏感词" />
    </div>

    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px">
      <el-form label-width="120px">
        <el-form-item label="敏感词" required>
          <el-input v-model="formData.word" placeholder="请输入敏感词" />
        </el-form-item>
        <el-form-item label="指定回复">
          <el-input v-model="formData.reply" type="textarea" :rows="3" placeholder="请输入指定回复内容" />
        </el-form-item>
        <el-form-item label="阻止用户输入">
          <el-switch v-model="formData.blockInput" />
        </el-form-item>
        <el-form-item label="联网检索屏蔽">
          <el-switch v-model="formData.blockSearch" />
        </el-form-item>
        <el-form-item label="模型生成答案时替换">
          <el-switch v-model="formData.replaceAnswer" />
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
</style>
