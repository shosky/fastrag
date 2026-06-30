<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const optList = ref<any[]>([])
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const formDefault = { name: '', type: 'prompt', description: '', config: '' }
const form = ref({ ...formDefault })

async function loadData() {
  try {
    const res: any = await api.getAppOptimizations(appId())
    optList.value = Array.isArray(res) ? res : []
  } catch { optList.value = [] }
}
function openAdd() { isEditing.value = false; editingId.value = ''; form.value = { ...formDefault }; showDialog.value = true }
function openEdit(row: any) { isEditing.value = true; editingId.value = row.id; form.value = { name: row.title || row.name, type: row.suggestionType || row.type, description: row.description, config: row.config || '' }; showDialog.value = true }
async function handleSave() {
  if (!form.value.name) { ElMessage.warning('请输入名称'); return }
  // 映射前后端字段名: 前端 name/type → 后端 title/suggestionType
  const data = { title: form.value.name, suggestionType: form.value.type, description: form.value.description, config: form.value.config }
  if (isEditing.value) { await api.updateAppOptimization(appId(), editingId.value, data); ElMessage.success('已更新') }
  else { await api.createAppOptimization(appId(), data); ElMessage.success('已创建') }
  showDialog.value = false; await loadData()
}
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '确认', { type: 'warning' }); await api.deleteAppOptimization(appId(), row.id); await loadData(); ElMessage.success('已删除') } catch {}
}
async function handleApply(row: any) { await api.applyAppOptimization(appId(), row.id); ElMessage.success('优化建议已应用'); await loadData() }
async function handleExport() {
  try {
    const blob = await api.exportAppOptimizations(appId()) as Blob
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `optimization_report_${Date.now()}.csv`; a.click(); URL.revokeObjectURL(url); ElMessage.success('优化报告已导出')
  } catch { ElMessage.error('导出失败') }
}
function getTypeLabel(type: string) {
  const map: Record<string, string> = { prompt: 'Prompt优化', param: '参数优化', flow: '流程优化' }
  return map[type] || type
}
onMounted(loadData)
</script>
<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header"><div class="section-title">对话优化</div><div style="display:flex;gap:8px"><el-button size="small" @click="handleExport">导出优化报告</el-button><el-button type="primary" size="small" @click="openAdd">新增优化</el-button></div></div>
      <el-table :data="optList" stripe size="small" style="margin-top:12px">
        <el-table-column label="名称" min-width="120"><template #default="{row}">{{ row.title || row.name }}</template></el-table-column>
        <el-table-column label="类型" width="100"><template #default="{row}">{{ getTypeLabel(row.suggestionType || row.type) }}</template></el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80"><template #default="{row}"><el-tag :type="row.status==='applied'?'success':'info'" size="small">{{ row.status==='applied'?'已应用':'待应用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="200"><template #default="{row}"><el-button v-if="row.status!=='applied'" link type="success" size="small" @click="handleApply(row)">应用</el-button><el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!optList.length" description="暂无优化建议" :image-size="60" />
    </div>
    <el-dialog v-model="showDialog" :title="isEditing?'编辑优化':'新增优化'" width="500px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="form.type" style="width:160px"><el-option label="Prompt优化" value="prompt" /><el-option label="参数优化" value="param" /><el-option label="流程优化" value="flow" /></el-select></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="配置"><el-input v-model="form.config" type="textarea" :rows="3" placeholder="JSON格式配置" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog=false">取消</el-button><el-button type="primary" @click="handleSave">{{ isEditing?'保存':'创建' }}</el-button></template>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:$spacing-base; gap:8px; }
.section-title { font-size:15px; font-weight:600; }
.card-panel { background:var(--el-bg-color-overlay); border-radius:8px; padding:20px; border:1px solid var(--el-border-color-light); }
</style>
