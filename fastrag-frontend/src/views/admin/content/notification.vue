<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const searchTitle = ref('')
const filterTop = ref('')
const filterStatus = ref('')
const showEditor = ref(false)
const isEdit = ref(false)
const loading = ref(false)
const editingId = ref<string | null>(null)

const formData = ref({
  title: '',
  titleColor: '#303133',
  titleBold: false,
  content: '',
  publishTime: '',
  expiryType: 'long',
  expiryTime: '',
  isTop: false,
})

const notificationList = ref<any[]>([])

async function loadNotifications() {
  loading.value = true
  try {
    const res: any = await api.getDictionaries({ type: '通知管理' })
    notificationList.value = res?.['通知管理'] || []
  } finally {
    loading.value = false
  }
}

onMounted(loadNotifications)

function handleAdd() {
  isEdit.value = false
  editingId.value = null
  formData.value = { title: '', titleColor: '#303133', titleBold: false, content: '', publishTime: '', expiryType: 'long', expiryTime: '', isTop: false }
  showEditor.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  editingId.value = row.id
  formData.value = { title: row.label || row.title, titleColor: '#303133', titleBold: false, content: row.value || '', publishTime: '', expiryType: 'long', expiryTime: '', isTop: false }
  showEditor.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('该操作将永久删除当前公告，请慎重操作！', '删除确认', { type: 'warning' })
    await api.deleteDictionary(row.id)
    await loadNotifications()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleSave() {
  if (!formData.value.title) {
    ElMessage.warning('请输入标题')
    return
  }
  const data = {
    type: '通知管理',
    key: formData.value.title,
    value: formData.value.content,
  }
  if (editingId.value) {
    await api.updateDictionary(editingId.value, data)
  } else {
    await api.createDictionary(data)
  }
  showEditor.value = false
  await loadNotifications()
  ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">通知管理</div>
        <el-button type="primary" @click="handleAdd">新增通知</el-button>
      </div>
      <div class="filter-bar">
        <el-input v-model="searchTitle" placeholder="通知标题" clearable style="width: 200px" />
        <el-button type="primary">查询</el-button>
        <el-button>重置</el-button>
      </div>
      <el-table :data="notificationList" stripe>
        <el-table-column prop="label" label="标题" show-overflow-tooltip />
        <el-table-column prop="key" label="标题" show-overflow-tooltip />
        <el-table-column prop="value" label="内容" show-overflow-tooltip />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!notificationList.length && !loading" description="暂无通知" />
    </div>

    <el-dialog v-model="showEditor" :title="isEdit ? '编辑通知' : '新增通知'" width="700px">
      <el-form label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="formData.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="formData.content" type="textarea" :rows="8" placeholder="请输入通知内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditor = false">取消</el-button>
        <el-button type="primary" @click="handleSave">{{ isEdit ? '更新' : '新增' }}</el-button>
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
