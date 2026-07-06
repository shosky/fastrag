<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const kbList = ref<any[]>([])
const selectedKbId = ref('')
const qaList = ref<any[]>([])
const loading = ref(false)
const showDialog = ref(false)
const dialogTitle = ref('添加问答')
const editingId = ref<string | null>(null)
const formData = ref({ question: '', answer: '' })

async function loadKbs() {
  try {
    const res: any = await api.getKnowledgeBases({ page: 1, pageSize: 100 })
    kbList.value = (res as any)?.list || (res as any) || []
    if (kbList.value.length && !selectedKbId.value) {
      selectedKbId.value = kbList.value[0].id
    }
  } catch { kbList.value = [] }
}

async function loadQaPairs() {
  if (!selectedKbId.value) { qaList.value = []; return }
  loading.value = true
  try {
    const res: any = await api.getQaPairs(selectedKbId.value, { page: 1, pageSize: 100 })
    qaList.value = (res as any)?.list || (res as any)?.records || (res as any) || []
  } finally { loading.value = false }
}

onMounted(loadKbs)

watch(selectedKbId, () => { if (selectedKbId.value) loadQaPairs() })

function handleAdd() {
  dialogTitle.value = '添加问答'
  editingId.value = null
  formData.value = { question: '', answer: '' }
  showDialog.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑问答'
  editingId.value = row.id
  formData.value = { question: row.question || '', answer: row.answer || '' }
  showDialog.value = true
}

async function handleSave() {
  if (!formData.value.question.trim()) { ElMessage.warning('请输入问题'); return }
  try {
    if (editingId.value) {
      await api.updateQaPair(selectedKbId.value, editingId.value, formData.value)
      ElMessage.success('更新成功')
    } else {
      await api.createQaPair(selectedKbId.value, formData.value)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadQaPairs()
  } catch { /* handled by interceptor */ }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该问答？', '删除确认', { type: 'warning' })
    await api.deleteQaPair(selectedKbId.value, row.id)
    ElMessage.success('删除成功')
    loadQaPairs()
  } catch { /* dismissed */ }
}

async function handleConfirm(row: any) {
  try {
    await api.confirmQaPair(selectedKbId.value, row.id)
    ElMessage.success('确认成功')
    loadQaPairs()
  } catch { /* handled */ }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="section-header">
      <h3>问答明细</h3>
      <div style="display: flex; gap: 8px">
        <el-select v-model="selectedKbId" placeholder="选择知识库" clearable style="width: 200px">
          <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-button type="primary" @click="handleAdd">添加问答</el-button>
      </div>
    </div>

    <div class="card-panel">
      <el-table :data="qaList" stripe size="small" empty-text="该知识库暂无问答对">
        <el-table-column prop="question" label="问题" show-overflow-tooltip min-width="200" />
        <el-table-column prop="answer" label="答案" show-overflow-tooltip min-width="300" />
        <el-table-column prop="source" label="来源" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.confirmed ? 'success' : 'info'" size="small">
              {{ row.confirmed ? '已确认' : '待确认' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.confirmed" type="primary" link @click="handleConfirm(row)">确认</el-button>
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 添加/编辑对话框 -->
    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px">
      <el-form label-width="80px">
        <el-form-item label="问题">
          <el-input v-model="formData.question" type="textarea" :rows="3" placeholder="请输入问题" />
        </el-form-item>
        <el-form-item label="答案">
          <el-input v-model="formData.answer" type="textarea" :rows="4" placeholder="请输入答案" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
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
  margin-bottom: $spacing-lg;
  h3 { margin: 0; }
}
</style>
