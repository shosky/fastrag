<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'
import { Download, UploadFilled, Edit, Delete, Plus, VideoPlay } from '@element-plus/icons-vue'

const props = defineProps<{
  appInfo: { id: string }
}>()

const appId = () => props.appInfo.id

// ===========================================================================
// 对话配置
// ===========================================================================
const dialogForm = ref({
  backgroundColor: '#f5f5f5',
  showAvatar: 1,
  showFeedback: 1,
  showSuggestions: 1,
})

async function loadDialog() {
  try {
    const r: any = await api.getAppDialogConfig(appId())
    if (r) Object.assign(dialogForm.value, r)
  } catch { /* ignore */ }
}

async function saveDialog() {
  await api.saveAppDialogConfig(appId(), dialogForm.value)
  ElMessage.success('对话配置已保存')
}

async function handleExportDialog() {
  try {
    const r: any = await api.exportAppDialog(appId())
    const blob = new Blob([JSON.stringify(r, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `dialog_config_${appId()}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('对话配置已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}

async function handleImportDialog() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]
      if (!file) return
      const text = await file.text()
      const data = JSON.parse(text)
      await api.importAppDialog(appId(), data)
      await loadDialog()
      ElMessage.success('对话配置已导入')
    } catch {
      ElMessage.error('导入失败，请检查文件格式')
    }
  }
  input.click()
}

// ===========================================================================
// 触发器管理
// ===========================================================================
const triggerList = ref<any[]>([])
const showTriggerDialog = ref(false)
const isEditingTrigger = ref(false)
const editingTriggerId = ref('')
const triggerFormDefault = {
  name: '',
  triggerType: 'keyword',
  matchContent: '',
  actionType: 'reply',
  actionConfig: '',
}
const triggerForm = ref({ ...triggerFormDefault })

async function loadTriggers() {
  triggerList.value = ((await api.getAppTriggers(appId())) as any) || []
}

function openAddTrigger() {
  isEditingTrigger.value = false
  editingTriggerId.value = ''
  triggerForm.value = { ...triggerFormDefault }
  showTriggerDialog.value = true
}

function openEditTrigger(row: any) {
  isEditingTrigger.value = true
  editingTriggerId.value = row.id
  triggerForm.value = {
    name: row.name,
    triggerType: row.triggerType,
    matchContent: row.matchContent,
    actionType: row.actionType,
    actionConfig: row.actionConfig || '',
  }
  showTriggerDialog.value = true
}

async function handleSaveTrigger() {
  if (!triggerForm.value.name) {
    ElMessage.warning('请输入名称')
    return
  }
  // MySQL JSON 列不允许空字符串，转换为 null
  const payload = {
    ...triggerForm.value,
    actionConfig: triggerForm.value.actionConfig || null,
  }
  if (isEditingTrigger.value) {
    await api.updateAppTrigger(appId(), editingTriggerId.value, payload)
    ElMessage.success('更新成功')
  } else {
    await api.createAppTrigger(appId(), payload)
    ElMessage.success('创建成功')
  }
  showTriggerDialog.value = false
  await loadTriggers()
}

async function handleDeleteTrigger(row: any) {
  try {
    await ElMessageBox.confirm('确认删除该触发器？', '删除确认', { type: 'warning' })
    await api.deleteAppTrigger(appId(), row.id)
    await loadTriggers()
    ElMessage.success('删除成功')
  } catch { /* cancelled */ }
}

async function handleToggleTrigger(row: any) {
  try {
    await api.updateAppTrigger(appId(), row.id, { enabled: row.enabled ? 0 : 1 })
    await loadTriggers()
    ElMessage.success(row.enabled ? '已禁用' : '已启用')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleTestTrigger(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入测试输入', `测试触发器: ${row.name}`, {
      inputType: 'textarea',
      inputPlaceholder: '输入测试内容...',
    })
    if (value) {
      const r: any = await api.testAppTrigger(appId(), row.id, value)
      ElMessageBox.alert(JSON.stringify(r, null, 2), '测试结果', { dangerouslyUseHTMLString: false })
    }
  } catch { /* cancelled */ }
}

async function handleRunTrigger(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入运行输入', `运行触发器: ${row.name}`, {
      inputType: 'textarea',
      inputPlaceholder: '输入运行内容...',
    })
    if (value) {
      const r: any = await api.runAppTrigger(appId(), row.id, value)
      ElMessageBox.alert(JSON.stringify(r, null, 2), '运行结果', { dangerouslyUseHTMLString: false })
    }
  } catch { /* cancelled */ }
}

onMounted(() => {
  loadDialog()
  loadTriggers()
})
</script>

<template>
  <div class="config-section">
    <!-- 对话配置 -->
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">对话配置</div>
        <div class="section-actions">
          <el-button size="small" :icon="Download" @click="handleExportDialog">导出</el-button>
          <el-button size="small" :icon="UploadFilled" @click="handleImportDialog">导入</el-button>
        </div>
      </div>
      <el-form label-width="120px" style="margin-top:16px">
        <el-form-item label="对话框背景">
          <el-color-picker v-model="dialogForm.backgroundColor" />
        </el-form-item>
        <el-form-item label="显示头像">
          <el-switch v-model="dialogForm.showAvatar" />
        </el-form-item>
        <el-form-item label="显示反馈">
          <el-switch v-model="dialogForm.showFeedback" />
        </el-form-item>
        <el-form-item label="显示推荐">
          <el-switch v-model="dialogForm.showSuggestions" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveDialog">保存</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 触发器管理 -->
    <div class="card-panel" style="margin-top:16px">
      <div class="section-header">
        <div class="section-title">触发器管理</div>
        <el-button type="primary" size="small" :icon="Plus" @click="openAddTrigger">新增触发器</el-button>
      </div>
      <el-table :data="triggerList" stripe size="small" style="margin-top:16px">
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="triggerType" label="类型" width="80" />
        <el-table-column prop="actionType" label="动作类型" width="80" />
        <el-table-column label="启用" width="70">
          <template #default="{ row }">
            <el-switch :model-value="!!row.enabled" size="small" @change="handleToggleTrigger(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Edit" @click="openEditTrigger(row)">编辑</el-button>
            <el-button link type="success" size="small" :icon="VideoPlay" @click="handleTestTrigger(row)">测试</el-button>
            <el-button link type="warning" size="small" :icon="UploadFilled" @click="handleRunTrigger(row)">运行</el-button>
            <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteTrigger(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!triggerList.length" description="暂无触发器" :image-size="60" />
    </div>

    <!-- 新增/编辑触发器对话框 -->
    <el-dialog v-model="showTriggerDialog" :title="isEditingTrigger ? '编辑触发器' : '新增触发器'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="triggerForm.name" placeholder="触发器名称" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="triggerForm.triggerType" style="width:160px">
            <el-option label="关键词" value="keyword" />
            <el-option label="正则" value="regex" />
            <el-option label="意图" value="intent" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配内容">
          <el-input v-model="triggerForm.matchContent" type="textarea" :rows="2" placeholder="匹配内容" />
        </el-form-item>
        <el-form-item label="动作类型">
          <el-select v-model="triggerForm.actionType" style="width:160px">
            <el-option label="回复" value="reply" />
            <el-option label="业务流" value="workflow" />
            <el-option label="API" value="api" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作配置">
          <el-input v-model="triggerForm.actionConfig" type="textarea" :rows="2" placeholder="动作配置参数（JSON格式）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTriggerDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTrigger">
          {{ isEditingTrigger ? '保存' : '创建' }}
        </el-button>
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
  flex-wrap: wrap;
  gap: 8px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}

.card-panel {
  background: var(--el-bg-color-overlay);
  border-radius: 8px;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
}
</style>
