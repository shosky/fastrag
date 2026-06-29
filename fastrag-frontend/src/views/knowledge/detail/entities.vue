<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const kbId = route.params.id as string

const loading = ref(false)
const entityList = ref<any[]>([])
const keyword = ref('')

async function loadData() {
  loading.value = true
  try {
    const res = await api.getEntities(kbId, keyword.value || undefined)
    entityList.value = (res as any) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

const showDialog = ref(false)
const dialogTitle = ref('新增实体')
const editingId = ref<string | null>(null)
const formData = ref({
  name: '',
  entityType: 'enum',
  description: '',
  valuesText: '',
})

function resetForm() {
  formData.value = { name: '', entityType: 'enum', description: '', valuesText: '' }
}

function handleAdd() {
  dialogTitle.value = '新增实体'
  editingId.value = null
  resetForm()
  showDialog.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑实体'
  editingId.value = row.id
  let values = ''
  try {
    if (row.valuesJson) {
      const parsed = typeof row.valuesJson === 'string' ? JSON.parse(row.valuesJson) : row.valuesJson
      values = Array.isArray(parsed) ? parsed.join('\n') : ''
    }
  } catch {}
  formData.value = {
    name: row.name || '',
    entityType: row.entityType || 'enum',
    description: row.description || '',
    valuesText: values,
  }
  showDialog.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除实体「${row.name}」？`, '删除确认', { type: 'warning' })
    await api.deleteEntity(kbId, row.id)
    await loadData()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleSave() {
  if (!formData.value.name) {
    ElMessage.warning('请输入实体名称')
    return
  }
  const values = formData.value.valuesText
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
  const data = {
    name: formData.value.name,
    entityType: formData.value.entityType,
    description: formData.value.description,
    valuesJson: JSON.stringify(values),
  }
  if (editingId.value) {
    await api.updateEntity(kbId, editingId.value, data)
  } else {
    await api.createEntity(kbId, data)
  }
  showDialog.value = false
  await loadData()
  ElMessage.success('保存成功')
}

function getValues(row: any): string[] {
  try {
    if (!row.valuesJson) return []
    const parsed = typeof row.valuesJson === 'string' ? JSON.parse(row.valuesJson) : row.valuesJson
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">实体库管理</div>
        <el-button type="primary" @click="handleAdd">新增实体</el-button>
      </div>

      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索实体名称" clearable style="width: 200px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">查询</el-button>
      </div>

      <el-table :data="entityList" stripe>
        <el-table-column prop="name" label="实体名称" width="160" />
        <el-table-column prop="entityType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.entityType === 'enum' ? '枚举' : row.entityType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实体值" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="v in getValues(row)" :key="v" size="small" style="margin-right: 4px; margin-bottom: 4px">{{ v }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!entityList.length && !loading" description="暂无实体" />
    </div>

    <el-dialog v-model="showDialog" :title="dialogTitle" width="520px">
      <el-form label-width="90px">
        <el-form-item label="实体名称" required>
          <el-input v-model="formData.name" placeholder="如：城市、产品类别" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="formData.entityType" style="width: 160px">
            <el-option label="枚举" value="enum" />
            <el-option label="正则" value="regex" />
            <el-option label="词典" value="dict" />
          </el-select>
        </el-form-item>
        <el-form-item label="实体值">
          <el-input v-model="formData.valuesText" type="textarea" :rows="6" placeholder="每行一个实体值，如：&#10;北京&#10;上海&#10;广州" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="实体说明" />
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
.section-title { font-size: 15px; font-weight: 600; }
</style>
