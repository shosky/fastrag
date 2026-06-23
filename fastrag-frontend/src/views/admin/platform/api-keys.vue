<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const showCreateDialog = ref(false)
const loading = ref(false)
const editingId = ref<string | null>(null)

const formData = ref({
  name: '',
  description: '',
  expiry: '7天',
  permissions: [] as string[],
})

const keyList = ref<any[]>([])

async function loadKeys() {
  loading.value = true
  try {
    const res: any = await api.getDictionaries({ type: 'API密钥' })
    keyList.value = res?.['API密钥'] || []
  } finally {
    loading.value = false
  }
}

onMounted(loadKeys)

function handleCreate() {
  editingId.value = null
  formData.value = { name: '', description: '', expiry: '7天', permissions: [] }
  showCreateDialog.value = true
}

function handleEdit(row: any) {
  editingId.value = row.id
  formData.value = { name: row.label || row.key, description: '', expiry: '7天', permissions: [] }
  showCreateDialog.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除？', '删除确认', { type: 'warning' })
    await api.deleteDictionary(row.id)
    await loadKeys()
    ElMessage.success('删除成功')
  } catch {}
}

function handleCopyKey(key: string) {
  navigator.clipboard.writeText(key)
  ElMessage.success('复制成功')
}

async function handleSave() {
  if (!formData.value.name) {
    ElMessage.warning('请输入名称')
    return
  }
  const key = 'sk-ais-' + Math.random().toString(36).substring(2, 10) + '****' + Math.random().toString(36).substring(2, 10)
  const data = { type: 'API密钥', key: formData.value.name, value: key }
  if (editingId.value) {
    await api.updateDictionary(editingId.value, data)
  } else {
    await api.createDictionary(data)
  }
  showCreateDialog.value = false
  await loadKeys()
  ElMessage.success(editingId.value ? '更新成功' : '创建成功')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div>
          <div class="section-title">开放密钥</div>
          <el-alert title="API key 生成后，将不会再次显示" type="warning" :closable="false" style="margin-top: 8px" />
        </div>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </div>

      <el-table :data="keyList" stripe>
        <el-table-column prop="label" label="名称" width="150" />
        <el-table-column label="开放密钥" width="250">
          <template #default="{ row }">
            <div class="key-cell">
              <span>{{ row.value }}</span>
              <el-button link size="small" @click="handleCopyKey(row.value)"><el-icon><CopyDocument /></el-icon></el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="key" label="描述信息" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!keyList.length && !loading" description="暂无 API 密钥" />
    </div>

    <el-dialog v-model="showCreateDialog" :title="editingId ? '编辑 API Key' : '创建 API Key'" width="500px">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="formData.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="到期时间">
          <el-select v-model="formData.expiry" style="width: 100%">
            <el-option label="7天" value="7天" />
            <el-option label="30天" value="30天" />
            <el-option label="90天" value="90天" />
            <el-option label="365天" value="365天" />
            <el-option label="永不过期" value="永不过期" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">关闭</el-button>
        <el-button type="primary" @click="handleSave">{{ editingId ? '更新' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.key-cell {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
}
</style>
