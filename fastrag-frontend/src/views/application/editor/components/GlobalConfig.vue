<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// 变量 CRUD (#4931~4935)
const variableList = ref<any[]>([])
const showVariableDialog = ref(false)
const isEditingVariable = ref(false)
const editingVariableId = ref('')
const variableFormDefault = { name: '', type: 'string', defaultValue: '', description: '' }
const variableForm = ref({ ...variableFormDefault })

async function loadVariables() {
  try {
    const res = await api.getAppVariables(appId())
    variableList.value = (res as any) || []
  } catch (e) { variableList.value = [] }
}
function openAddVariable() { isEditingVariable.value = false; editingVariableId.value = ''; variableForm.value = { ...variableFormDefault }; showVariableDialog.value = true }
function openEditVariable(row: any) { isEditingVariable.value = true; editingVariableId.value = row.id; variableForm.value = { name: row.name, type: row.type, defaultValue: row.defaultValue, description: row.description }; showVariableDialog.value = true }
async function handleSaveVariable() {
  if (!variableForm.value.name) { ElMessage.warning('请输入变量名称'); return }
  if (isEditingVariable.value) { await api.updateAppVariable(appId(), editingVariableId.value, variableForm.value); ElMessage.success('更新成功') }
  else { await api.createAppVariable(appId(), variableForm.value); ElMessage.success('创建成功') }
  showVariableDialog.value = false; await loadVariables()
}
async function handleDeleteVariable(row: any) {
  try { await ElMessageBox.confirm('确认删除变量？', '确认', { type: 'warning' }); await api.deleteAppVariable(appId(), row.id); await loadVariables(); ElMessage.success('删除成功') } catch (e) {}
}

// 安全策略：运行时唯一生效项是安全开关——门控平台敏感词库的输入拦截与输出过滤
// （AppGlobalPolicy 的敏感词模式/兜底文案/未匹配字段运行时不消费，且词库已归平台词库管理，UI 不再暴露）
const safetyEnabled = ref(1)
const savingPolicy = ref(false)
async function loadPolicy() {
  try {
    const r: any = await api.getAppGlobalPolicy(appId())
    if (r && r.safetyEnabled != null) safetyEnabled.value = r.safetyEnabled
  } catch (e) {}
}
async function handleToggleSafety() {
  savingPolicy.value = true
  try {
    await api.saveAppGlobalPolicy(appId(), { safetyEnabled: safetyEnabled.value })
    ElMessage.success(safetyEnabled.value === 1 ? '安全策略已开启' : '安全策略已关闭')
  } catch (e) { ElMessage.error('保存失败') } finally { savingPolicy.value = false }
}

onMounted(() => { loadVariables(); loadPolicy() })
</script>
<template>
  <div class="config-section">
    <!-- 变量管理 -->
    <div class="card-panel">
      <div class="section-header"><div class="section-title">变量管理</div><el-button type="primary" size="small" @click="openAddVariable">新增变量</el-button></div>
      <el-table :data="variableList" stripe size="small" style="margin-top:12px">
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="type" label="类型" width="80" />
        <el-table-column prop="defaultValue" label="默认值" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="操作" width="130"><template #default="{ row }"><el-button link type="primary" size="small" @click="openEditVariable(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDeleteVariable(row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!variableList.length" description="暂无变量" :image-size="60" />
    </div>
    <!-- 安全策略 -->
    <div class="card-panel" style="margin-top:16px">
      <div class="section-header"><div class="section-title">安全策略</div>
        <el-switch v-model="safetyEnabled" :active-value="1" :inactive-value="0" :loading="savingPolicy" @change="handleToggleSafety" />
      </div>
      <p class="section-hint">
        开启后，对话将按平台敏感词库过滤用户输入与模型输出（命中词条的输入直接拦截，不进入模型）。
        词库统一在「系统管理 → 敏感词」中维护，应用侧不再单独配置敏感词库与未匹配策略。
      </p>
    </div>
    <!-- 变量弹窗 -->
    <el-dialog v-model="showVariableDialog" :title="isEditingVariable ? '编辑变量' : '新增变量'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="variableForm.name" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="variableForm.type" style="width:160px"><el-option label="字符串" value="string" /><el-option label="数字" value="number" /><el-option label="布尔" value="boolean" /><el-option label="JSON" value="json" /></el-select></el-form-item>
        <el-form-item label="默认值"><el-input v-model="variableForm.defaultValue" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="variableForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showVariableDialog=false">取消</el-button><el-button type="primary" @click="handleSaveVariable">{{ isEditingVariable ? '保存' : '创建' }}</el-button></template>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; gap:8px; }
.section-title { font-size: 15px; font-weight: 600; }
.section-hint { font-size: 13px; color: var(--el-text-color-secondary); line-height: 1.6; margin: 0; }
.card-panel { background: var(--el-bg-color-overlay); border-radius: 8px; padding: 20px; border: 1px solid var(--el-border-color-light); }
</style>
