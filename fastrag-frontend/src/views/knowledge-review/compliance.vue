<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const dataList = ref<any[]>([])
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const ruleTypeOptions = [{ label: '内容检查', value: 'content' }, { label: '格式检查', value: 'format' }, { label: '关键词检查', value: 'keyword' }, { label: '长度检查', value: 'length' }]

const kbList = ref<any[]>([])
const selectedKbId = ref('')
async function loadKbList() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbList.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
    if (kbList.value.length > 0 && !selectedKbId.value) selectedKbId.value = kbList.value[0].id
  } catch { /* ignore */ }
}

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    const res: any = await api.getComplianceRules(selectedKbId.value)
    dataList.value = Array.isArray(res) ? res : []
  } finally { loading.value = false }
}
onMounted(async () => { await loadKbList(); loadData() })

function handleAdd() { editingId.value = null; formData.value = { ruleType: 'content', enabled: true }; dialogTitle.value = '新增合规规则'; showDialog.value = true }
function handleEdit(row: any) { editingId.value = row.id; formData.value = { ...row }; dialogTitle.value = '编辑合规规则'; showDialog.value = true }
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除规则「${row.name}」吗？`, '提示', { type: 'warning' })
    await api.deleteComplianceRule(selectedKbId.value, row.id); ElMessage.success('删除成功'); loadData()
  } catch {}
}
async function handleToggle(row: any) {
  try {
    await api.createComplianceRule(selectedKbId.value, { ...row, enabled: !row.enabled })
    row.enabled = !row.enabled; ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch {}
}
async function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入规则名称'); return }
  if (!formData.value.rule) { ElMessage.warning('请输入规则描述'); return }
  try {
    const data = { ruleName: formData.value.name, ruleType: formData.value.ruleType, pattern: formData.value.rule, description: formData.value.description, enabled: formData.value.enabled ? 1 : 0 }
    if (editingId.value) { await api.createComplianceRule(selectedKbId.value, { ...data, id: editingId.value }); ElMessage.success('更新成功') }
    else { await api.createComplianceRule(selectedKbId.value, data); ElMessage.success('创建成功') }
    showDialog.value = false; loadData()
  } catch { ElMessage.error('操作失败') }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">合规性检查</div>
        <div style="display:flex;gap:12px;align-items:center">
          <el-select v-model="selectedKbId" @change="loadData" placeholder="选择知识库" style="width:200px">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button type="primary" @click="handleAdd">新增规则</el-button>
        </div>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column label="规则名称" min-width="150">
          <template #default="{ row }">{{ row.ruleName || row.name }}</template>
        </el-table-column>
        <el-table-column label="规则类型" width="100">
          <template #default="{ row }"><el-tag size="small">{{ ruleTypeOptions.find(o => o.value === row.ruleType)?.label || row.ruleType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="规则内容" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">{{ row.pattern || row.rule || '-' }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80"><template #default="{ row }"><el-switch :model-value="row.enabled === true || row.enabled === 1" @change="handleToggle(row)" size="small" /></template></el-table-column>
        <el-table-column label="命中次数" width="100">
          <template #default="{ row }">{{ row.hitCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createdAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="showDialog" :title="dialogTitle" width="600px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="规则名称" required><el-input v-model="formData.name" placeholder="请输入规则名称" /></el-form-item>
        <el-form-item label="规则类型" required>
          <el-select v-model="formData.ruleType" style="width: 100%"><el-option v-for="opt in ruleTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" /></el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="formData.description" placeholder="请输入规则描述" /></el-form-item>
        <el-form-item label="规则内容" required><el-input v-model="formData.rule" type="textarea" :rows="3" placeholder="请输入规则内容" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="formData.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
