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
  } catch { variableList.value = [] }
  if (!variableList.value.length) {
    variableList.value = [
      { id: 'v1', name: 'companyName', type: 'string', defaultValue: '示例公司', description: '公司名称' },
      { id: 'v2', name: 'maxRetries', type: 'number', defaultValue: '3', description: '最大重试次数' },
      { id: 'v3', name: 'enableLogging', type: 'boolean', defaultValue: 'true', description: '是否启用日志' },
    ]
  }
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
  try { await ElMessageBox.confirm('确认删除变量？', '确认', { type: 'warning' }); await api.deleteAppVariable(appId(), row.id); await loadVariables(); ElMessage.success('删除成功') } catch {}
}

// 安全策略 (#4936/#4939~4940)
const safetyEnabled = ref(1)
const policyForm = ref({ safetyEnabled: 1, sensitiveWordMode: 'reject', fallbackText: '抱歉，没有理解您的问题', unmatchedEnabled: 1 })
async function loadPolicy() { try { const r: any = await api.getAppGlobalPolicy(appId()); if (r) Object.assign(policyForm.value, r); safetyEnabled.value = policyForm.value.safetyEnabled } catch {} }
async function savePolicy() { await api.saveAppGlobalPolicy(appId(), policyForm.value); ElMessage.success('安全策略已保存') }
async function handleToggleSafety() { policyForm.value.safetyEnabled = policyForm.value.safetyEnabled ? 0 : 1; await savePolicy() }

// 敏感词 (#4937)
const showSensitiveDialog = ref(false)
const sensitiveWordText = ref('')
const sensitiveWordMode = ref('reject')
async function handleSaveSensitiveWords() {
  if (!sensitiveWordText.value) { ElMessage.warning('请输入敏感词列表'); return }
  const words = sensitiveWordText.value.split('\n').map(s => s.trim()).filter(Boolean)
  await api.saveAppSensitiveWords(appId(), { words, mode: sensitiveWordMode.value }); showSensitiveDialog.value = false; ElMessage.success(`已保存 ${words.length} 个敏感词`)
}

// 未匹配策略 (#4941~4943)
const showUnmatchedDialog = ref(false)
const unmatchedForm = ref({ fallbackText: '', redirectKbId: '', retryCount: 0 })
async function handleSaveUnmatched() { await api.saveAppUnmatchedConfig(appId(), unmatchedForm.value); showUnmatchedDialog.value = false; ElMessage.success('未匹配策略已保存') }

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
        <el-switch v-model="policyForm.safetyEnabled" :active-value="1" :inactive-value="0" @change="handleToggleSafety" />
      </div>
      <el-form label-width="120px" style="margin-top:12px">
        <el-form-item label="敏感词处理"><el-select v-model="policyForm.sensitiveWordMode" style="width:160px"><el-option label="拒绝" value="reject" /><el-option label="替换" value="replace" /><el-option label="遮盖" value="mask" /></el-select></el-form-item>
        <el-form-item label="兜底话术"><el-input v-model="policyForm.fallbackText" type="textarea" :rows="2" /></el-form-item>
        <el-form-item><el-button type="primary" @click="savePolicy">保存策略</el-button><el-button style="margin-left:8px" @click="showSensitiveDialog=true">上传敏感词库</el-button><el-button style="margin-left:8px" @click="showUnmatchedDialog=true">未匹配设置</el-button></el-form-item>
      </el-form>
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
    <!-- 敏感词弹窗 -->
    <el-dialog v-model="showSensitiveDialog" title="上传敏感词库" width="500px">
      <el-form label-width="100px">
        <el-form-item label="处理模式"><el-select v-model="sensitiveWordMode" style="width:160px"><el-option label="拒绝" value="reject" /><el-option label="替换" value="replace" /><el-option label="遮盖" value="mask" /></el-select></el-form-item>
        <el-form-item label="敏感词列表"><el-input v-model="sensitiveWordText" type="textarea" :rows="6" placeholder="每行一个敏感词" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showSensitiveDialog=false">取消</el-button><el-button type="primary" @click="handleSaveSensitiveWords">保存</el-button></template>
    </el-dialog>
    <!-- 未匹配策略弹窗 -->
    <el-dialog v-model="showUnmatchedDialog" title="未匹配策略配置" width="480px">
      <el-form label-width="110px">
        <el-form-item label="兜底话术"><el-input v-model="unmatchedForm.fallbackText" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="重定向知识库"><el-input v-model="unmatchedForm.redirectKbId" /></el-form-item>
        <el-form-item label="重试次数"><el-input-number v-model="unmatchedForm.retryCount" :min="0" :max="5" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showUnmatchedDialog=false">取消</el-button><el-button type="primary" @click="handleSaveUnmatched">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; gap:8px; }
.section-title { font-size: 15px; font-weight: 600; }
.card-panel { background: var(--el-bg-color-overlay); border-radius: 8px; padding: 20px; border: 1px solid var(--el-border-color-light); }
</style>
