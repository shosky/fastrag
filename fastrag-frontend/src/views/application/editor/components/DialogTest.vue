<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const testList = ref<any[]>([])
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const formDefault = { name: '', query: '', expectedAnswer: '' }
const form = ref({ ...formDefault })

async function loadData() {
  try { testList.value = ((await api.getAppDialogTests(appId())) as any) || [] } catch { testList.value = [] }
  if (!testList.value.length) {
    testList.value = [
      { id: 't1', name: '基础问答测试', query: '公司的主要业务是什么？', expectedAnswer: '我公司主要从事人工智能技术服务' },
      { id: 't2', name: '产品咨询测试', query: '你们有哪些产品？', expectedAnswer: '我们提供智能问答、知识库管理、数据分析等产品' },
    ]
  }
}
function openAdd() { isEditing.value = false; editingId.value = ''; form.value = { ...formDefault }; showDialog.value = true }
function openEdit(row: any) { isEditing.value = true; editingId.value = row.id; form.value = { name: row.name, query: row.query, expectedAnswer: row.expectedAnswer }; showDialog.value = true }
async function handleSave() {
  if (!form.value.name) { ElMessage.warning('请输入名称'); return }
  if (isEditing.value) { await api.updateAppDialogTest(appId(), editingId.value, form.value); ElMessage.success('已更新') }
  else { await api.createAppDialogTest(appId(), form.value); ElMessage.success('已创建') }
  showDialog.value = false; await loadData()
}
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '确认', { type: 'warning' }); await api.deleteAppDialogTest(appId(), row.id); await loadData(); ElMessage.success('已删除') } catch {}
}
async function handleExport() {
  try {
    const blob = await api.exportAppDialogTests(appId()) as Blob
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `test_report_${Date.now()}.csv`; a.click(); URL.revokeObjectURL(url); ElMessage.success('已导出')
  } catch { ElMessage.error('导出失败') }
}
onMounted(loadData)
</script>
<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header"><div class="section-title">对话测试</div><div style="display:flex;gap:8px"><el-button size="small" @click="handleExport">导出测试报告</el-button><el-button type="primary" size="small" @click="openAdd">新增测试</el-button></div></div>
      <el-table :data="testList" stripe size="small" style="margin-top:12px">
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="query" label="问题" show-overflow-tooltip />
        <el-table-column prop="expectedAnswer" label="期望答案" show-overflow-tooltip />
        <el-table-column label="操作" width="130"><template #default="{row}"><el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!testList.length" description="暂无测试案例" :image-size="60" />
    </div>
    <el-dialog v-model="showDialog" :title="isEditing?'编辑测试案例':'新增测试案例'" width="500px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="测试问题"><el-input v-model="form.query" /></el-form-item>
        <el-form-item label="期望答案"><el-input v-model="form.expectedAnswer" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog=false">取消</el-button><el-button type="primary" @click="handleSave">{{ isEditing?'保存':'创建' }}</el-button></template>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom:$spacing-base; gap:8px; }
.section-title { font-size:15px; font-weight:600; }
.card-panel { background:var(--el-bg-color-overlay); border-radius:8px; padding:20px; border:1px solid var(--el-border-color-light); }
</style>
