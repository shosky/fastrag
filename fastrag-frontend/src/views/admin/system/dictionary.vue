<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const activeType = ref('')
const searchKeyword = ref('')
const showDialog = ref(false)
const dialogTitle = ref('新建字典条目')
const loading = ref(false)
const editingId = ref<string | null>(null)

const dictTypes = ref<string[]>([])

const formData = ref({
  key: '',
  label: '',
  value: '',
  enabled: true,
  remark: '',
})

const dictData = ref<Record<string, any[]>>({})

async function loadDictionaries() {
  loading.value = true
  try {
    const [typesRes, dataRes] = await Promise.all([
      api.getDictionaryTypes(),
      api.getDictionaries(),
    ])
    dictTypes.value = (typesRes as any) || []
    dictData.value = (dataRes as any) || {}
    if (!activeType.value && dictTypes.value.length > 0) {
      activeType.value = dictTypes.value[0]
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadDictionaries)

const currentList = computed(() => {
  let list = dictData.value[activeType.value] || []
  if (searchKeyword.value) {
    list = list.filter((item: any) =>
      item.key?.includes(searchKeyword.value) ||
      item.label?.includes(searchKeyword.value) ||
      item.value?.includes(searchKeyword.value)
    )
  }
  return list
})

function handleAdd() {
  dialogTitle.value = '新建字典条目'
  editingId.value = null
  formData.value = { key: '', label: '', value: '', enabled: true, remark: '' }
  showDialog.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑字典条目'
  editingId.value = row.id
  formData.value = { ...row }
  showDialog.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除条目 "${row.label}" 吗？`, '删除确认', { type: 'warning' })
    await api.deleteDictionary(row.id)
    await loadDictionaries()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleSave() {
  if (!formData.value.key) {
    ElMessage.warning('请输入条目键')
    return
  }
  const data = {
    type: activeType.value,
    key: formData.value.key,
    value: formData.value.value,
  }
  if (editingId.value) {
    await api.updateDictionary(editingId.value, data)
  } else {
    await api.createDictionary(data)
  }
  showDialog.value = false
  await loadDictionaries()
  ElMessage.success('保存成功')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="dict-layout">
      <div class="dict-types">
        <div class="types-title">字典类型</div>
        <div
          v-for="t in dictTypes"
          :key="t"
          class="type-item"
          :class="{ active: activeType === t }"
          @click="activeType = t"
        >
          {{ t }}
        </div>
        <el-empty v-if="!dictTypes.length" description="暂无字典类型" :image-size="40" />
      </div>
      <div class="dict-content">
        <div class="section-header">
          <div class="section-title">{{ activeType || '请选择字典类型' }}</div>
          <el-button type="primary" @click="handleAdd" :disabled="!activeType">新建条目</el-button>
        </div>
        <div class="filter-bar">
          <el-input v-model="searchKeyword" placeholder="搜索键、名称、值或备注" clearable style="width: 300px" />
          <el-button type="primary">搜索</el-button>
          <el-button @click="searchKeyword = ''">重置</el-button>
        </div>
        <el-table :data="currentList" stripe>
          <el-table-column prop="key" label="键(Key)" width="180" />
          <el-table-column prop="label" label="名称(Label)" width="150" />
          <el-table-column prop="value" label="值(Value)" show-overflow-tooltip />
          <el-table-column label="启用" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" width="150" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!currentList.length && !loading && activeType" description="暂无条目" />
      </div>
    </div>

    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px">
      <el-form label-width="80px">
        <el-form-item label="条目键" required>
          <el-input v-model="formData.key" placeholder="请输入条目键" :disabled="dialogTitle === '编辑字典条目'" />
        </el-form-item>
        <el-form-item label="显示名称" required>
          <el-input v-model="formData.label" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="条目值">
          <el-input v-model="formData.value" type="textarea" :rows="3" placeholder="请输入条目值" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="formData.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" placeholder="请输入备注" />
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

.dict-layout {
  display: flex;
  gap: $spacing-base;
  height: calc(100vh - 120px);
}

.dict-types {
  width: 200px;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-base;
  flex-shrink: 0;

  .types-title { font-weight: 600; margin-bottom: $spacing-base; }
}

.type-item {
  padding: $spacing-sm $spacing-base;
  border-radius: $radius-sm;
  cursor: pointer;
  font-size: 13px;
  &:hover { background: $bg-hover; }
  &.active { background: #ecf5ff; color: $color-primary; }
}

.dict-content {
  flex: 1;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}
</style>
