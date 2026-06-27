<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getComplianceList, createCompliance, updateCompliance, deleteCompliance } from '@/mock/knowledge-review'

const loading = ref(false)
const dataList = ref<any[]>([])
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const ruleTypeOptions = [{ label: '内容检查', value: 'content' }, { label: '格式检查', value: 'format' }, { label: '关键词检查', value: 'keyword' }, { label: '长度检查', value: 'length' }]

async function loadData() { loading.value = true; try { dataList.value = getComplianceList() } finally { loading.value = false } }
onMounted(loadData)

function handleAdd() { editingId.value = null; formData.value = { ruleType: 'content', enabled: true }; dialogTitle.value = '新增合规规则'; showDialog.value = true }
function handleEdit(row: any) { editingId.value = row.id; formData.value = { ...row }; dialogTitle.value = '编辑合规规则'; showDialog.value = true }
async function handleDelete(row: any) { try { await ElMessageBox.confirm(`确定要删除规则「${row.name}」吗？`, '提示', { type: 'warning' }); deleteCompliance(row.id); ElMessage.success('删除成功'); loadData() } catch {} }
function handleToggle(row: any) { updateCompliance(row.id, { enabled: !row.enabled }); row.enabled = !row.enabled; ElMessage.success(row.enabled ? '已启用' : '已停用') }
function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入规则名称'); return }
  if (!formData.value.rule) { ElMessage.warning('请输入规则描述'); return }
  if (editingId.value) { updateCompliance(editingId.value, formData.value); ElMessage.success('更新成功') }
  else { createCompliance(formData.value); ElMessage.success('创建成功') }
  showDialog.value = false; loadData()
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">合规性检查</div>
        <el-button type="primary" @click="handleAdd">新增规则</el-button>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="name" label="规则名称" min-width="150" />
        <el-table-column prop="ruleType" label="规则类型" width="100">
          <template #default="{ row }"><el-tag size="small">{{ ruleTypeOptions.find(o => o.value === row.ruleType)?.label || row.ruleType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="rule" label="规则" min-width="250" show-overflow-tooltip />
        <el-table-column label="启用" width="80"><template #default="{ row }"><el-switch :model-value="row.enabled" @change="handleToggle(row)" size="small" /></template></el-table-column>
        <el-table-column prop="hitCount" label="命中次数" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="160" show-overflow-tooltip />
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
