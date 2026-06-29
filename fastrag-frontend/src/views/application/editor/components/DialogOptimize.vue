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
  try { optList.value = ((await api.getAppOptimizations(appId())) as any) || [] } catch { optList.value = [] }
  if (!optList.value.length) {
    optList.value = [
      { id: 'o1', name: 'Prompt优化建议', type: 'prompt', description: '优化系统Prompt，增加角色设定和输出格式约束', status: 'pending' },
      { id: 'o2', name: '温度参数优化', type: 'param', description: '建议将温度从0.7调整为0.5以提高回答准确性', status: 'applied' },
    ]
  }
}
function openAdd() { isEditing.value = false; editingId.value = ''; form.value = { ...formDefault }; showDialog.value = true }
function openEdit(row: any) { isEditing.value = true; editingId.value = row.id; form.value = { name: row.name, type: row.type, description: row.description, config: row.config || '' }; showDialog.value = true }
async function handleSave() {
  if (!form.value.name) { ElMessage.warning('请输入名称'); return }
  if (isEditing.value) { await api.updateAppOptimization(appId(), editingId.value, form.value); ElMessage.success('已更新') }
  else { await api.createAppOptimization(appId(), form.value); ElMessage.success('已创建') }
  showDialog.value = false; await loadData()
}
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '确认', { type: 'warning' }); await api.deleteAppOptimization(appId(), row.id); await loadData(); ElMessage.success('已删除') } catch {}
}
async function handleApply(row: any) { await api.applyAppOptimization(appId(), row.id); ElMessage.success('优化建议已应用'); await loadData() }
onMounted(loadData)
</script>
<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header"><div class="section-title">对话优化</div><el-button type="primary" size="small" @click="openAdd">新增优化</el-button></div>
      <el-table :data="optList" stripe size="small" style="margin-top:12px">
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80"><template #default="{row}"><el-tag :type="row.status==='applied'?'success':'info'" size="small">{{ row.status==='applied'?'已应用':'待应用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="180"><template #default="{row}"><el-button v-if="row.status!=='applied'" link type="success" size="small" @click="handleApply(row)">应用</el-button><el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button></template></el-table-column>
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
