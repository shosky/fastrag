<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const activeGroup = ref('')
const showEditor = ref(false)
const editorTitle = ref('创建模板')
const templateName = ref('')
const loading = ref(false)
const editingId = ref<string | null>(null)

const groups = ref<any[]>([])
const templateList = ref<any[]>([])

const batchMode = ref(false)
const selectedTemplates = ref<string[]>([])

async function loadTemplates() {
  loading.value = true
  try {
    const [groupsRes, templatesRes] = await Promise.all([
      api.getDictionaries({ type: '模板分组' }),
      api.getDictionaries({ type: '文档模板' }),
    ])
    groups.value = (groupsRes as any)?.['模板分组'] || []
    templateList.value = (templatesRes as any)?.['文档模板'] || []
  } finally {
    loading.value = false
  }
}

onMounted(loadTemplates)

function toggleTemplate(id: string, checked: boolean) {
  if (checked) {
    if (!selectedTemplates.value.includes(id)) selectedTemplates.value.push(id)
  } else {
    selectedTemplates.value = selectedTemplates.value.filter((t) => t !== id)
  }
}

function handleCreate() {
  editorTitle.value = '创建模板'
  editingId.value = null
  templateName.value = ''
  showEditor.value = true
}

function handleEdit(tpl: any) {
  editorTitle.value = '编辑模板'
  editingId.value = tpl.id
  templateName.value = tpl.label || tpl.key
  showEditor.value = true
}

async function handleDelete(tpl: any) {
  try {
    await ElMessageBox.confirm(`确定要删除模板吗？`, '删除确认', { type: 'warning' })
    await api.deleteDictionary(tpl.id)
    await loadTemplates()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleSave() {
  if (!templateName.value) {
    ElMessage.warning('请输入模板名称')
    return
  }
  const data = { type: '文档模板', key: templateName.value, value: '' }
  if (editingId.value) {
    await api.updateDictionary(editingId.value, data)
  } else {
    await api.createDictionary(data)
  }
  showEditor.value = false
  await loadTemplates()
  ElMessage.success('保存成功')
}

async function handleBatchDelete() {
  if (selectedTemplates.value.length === 0) {
    ElMessage.warning('请选择要删除的模板')
    return
  }
  try {
    await ElMessageBox.confirm('确定要删除选中的模板吗？', '批量删除', { type: 'warning' })
    await Promise.all(selectedTemplates.value.map(id => api.deleteDictionary(id)))
    selectedTemplates.value = []
    await loadTemplates()
    ElMessage.success('删除成功')
  } catch {}
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="template-layout">
      <div class="template-sidebar">
        <div class="sidebar-header">
          <span>分组</span>
          <el-button link size="small"><el-icon><Plus /></el-icon></el-button>
        </div>
        <div class="group-item" :class="{ active: activeGroup === '' }" @click="activeGroup = ''">全部</div>
        <div v-for="g in groups" :key="g.id" class="group-item" :class="{ active: activeGroup === g.id }" @click="activeGroup = g.id">
          {{ g.key || g.label }}
        </div>
      </div>
      <div class="template-content">
        <div class="section-header">
          <div class="section-title">文档模板</div>
          <div>
            <el-button @click="batchMode = !batchMode">{{ batchMode ? '退出批量操作' : '批量操作' }}</el-button>
            <el-button type="primary" @click="handleCreate">创建模板</el-button>
          </div>
        </div>
        <div class="template-grid">
          <div v-for="tpl in templateList" :key="tpl.id" class="template-card">
            <el-checkbox
              v-if="batchMode"
              :model-value="selectedTemplates.includes(tpl.id)"
              class="batch-checkbox"
              @change="(val) => toggleTemplate(tpl.id, val === true || val === tpl.id)"
            />
            <div class="card-preview">
              <el-icon :size="48" color="#c0c4cc"><Document /></el-icon>
            </div>
            <div class="card-info">
              <span class="name">{{ tpl.label || tpl.key }}</span>
            </div>
            <div class="card-actions">
              <el-button link size="small" @click="handleEdit(tpl)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(tpl)">删除</el-button>
            </div>
          </div>
        </div>
        <el-empty v-if="!templateList.length && !loading" description="暂无模板" />
        <div v-if="batchMode" class="batch-bar">
          <span>已选 {{ selectedTemplates.length }} 项</span>
          <el-button type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="showEditor" :title="editorTitle" width="600px">
      <el-form label-width="80px">
        <el-form-item label="模板名称" required>
          <el-input v-model="templateName" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="分组">
          <el-select style="width: 100%">
            <el-option v-for="g in groups" :key="g.id" :label="g.key || g.label" :value="g.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditor = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.template-layout {
  display: flex;
  gap: $spacing-base;
  height: calc(100vh - 120px);
}

.template-sidebar {
  width: 200px;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-base;
  flex-shrink: 0;
  .sidebar-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; margin-bottom: $spacing-base; }
}

.group-item {
  padding: $spacing-sm $spacing-base;
  border-radius: $radius-sm;
  cursor: pointer;
  font-size: 13px;
  &:hover { background: $bg-hover; }
  &.active { background: #ecf5ff; color: $color-primary; }
}

.template-content {
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

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: $spacing-base;
}

.template-card {
  position: relative;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  padding: $spacing-base;
  text-align: center;

  .batch-checkbox { position: absolute; top: $spacing-sm; left: $spacing-sm; }
  .card-preview { padding: $spacing-lg; background: $bg-hover; border-radius: $radius-sm; margin-bottom: $spacing-sm; }
  .card-info { .name { display: block; font-weight: 600; } }
  .card-actions { margin-top: $spacing-sm; display: flex; justify-content: center; gap: $spacing-sm; }
}

.batch-bar {
  position: sticky;
  bottom: 0;
  background: $bg-white;
  padding: $spacing-base;
  border-top: 1px solid $border-lighter;
  display: flex;
  align-items: center;
  gap: $spacing-base;
}
</style>
