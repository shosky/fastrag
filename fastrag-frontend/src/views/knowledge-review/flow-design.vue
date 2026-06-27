<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFlowList, createFlow, updateFlow, deleteFlow } from '@/mock/knowledge-review'

const loading = ref(false)
const dataList = ref<any[]>([])
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const steps = ref<{ name: string; reviewerRole: string; timeoutHours: number }[]>([])
const roleOptions = ['知识编辑', '知识管理员', '部门主管', '质量管理员']

async function loadData() { loading.value = true; try { dataList.value = getFlowList() } finally { loading.value = false } }
onMounted(loadData)

function handleAdd() {
  editingId.value = null; formData.value = { name: '', description: '', status: 'draft' }
  steps.value = [{ name: '审核', reviewerRole: '知识管理员', timeoutHours: 24 }]
  dialogTitle.value = '新增审核流程'; showDialog.value = true
}
function handleEdit(row: any) {
  editingId.value = row.id; formData.value = { ...row }
  steps.value = (row.steps || []).map((s: any) => ({ ...s }))
  dialogTitle.value = '编辑审核流程'; showDialog.value = true
}
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm(`确定要删除流程「${row.name}」吗？`, '提示', { type: 'warning' }); deleteFlow(row.id); ElMessage.success('删除成功'); loadData() } catch {}
}
function addStep() { steps.value.push({ name: `步骤${steps.value.length + 1}`, reviewerRole: '知识管理员', timeoutHours: 24 }) }
function removeStep(idx: number) { if (steps.value.length <= 1) { ElMessage.warning('至少需要一个步骤'); return }; steps.value.splice(idx, 1) }
function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入流程名称'); return }
  const data = { ...formData.value, steps: steps.value.map((s, i) => ({ ...s, id: `step-${i + 1}`, order: i + 1 })) }
  if (editingId.value) { updateFlow(editingId.value, data); ElMessage.success('更新成功') }
  else { createFlow(data); ElMessage.success('创建成功') }
  showDialog.value = false; loadData()
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">审核流程设计</div>
        <el-button type="primary" @click="handleAdd">新增流程</el-button>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="name" label="流程名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="步骤" min-width="250">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 4px; flex-wrap: wrap">
              <template v-for="(step, idx) in (row.steps || [])" :key="idx">
                <el-tag size="small" type="info">{{ step.name }}({{ step.reviewerRole }})</el-tag>
                <span v-if="idx < (row.steps || []).length - 1" style="color: #c0c4cc">→</span>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }"><el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status === 'active' ? '启用' : '草稿' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="creator" label="创建人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="showDialog" :title="dialogTitle" width="700px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="流程名称" required><el-input v-model="formData.name" placeholder="请输入流程名称" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="formData.description" type="textarea" :rows="2" placeholder="请输入流程描述" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="formData.status" style="width: 200px"><el-option label="草稿" value="draft" /><el-option label="启用" value="active" /></el-select>
        </el-form-item>
        <el-divider content-position="left">审核步骤</el-divider>
        <div v-for="(step, idx) in steps" :key="idx" style="display: flex; gap: 12px; margin-bottom: 12px; align-items: center; background: #f5f7fa; padding: 12px; border-radius: 6px">
          <span style="color: #909399; font-size: 13px; min-width: 50px">步骤{{ idx + 1 }}</span>
          <el-input v-model="step.name" placeholder="步骤名称" style="width: 150px" />
          <el-select v-model="step.reviewerRole" placeholder="审核角色" style="width: 150px"><el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" /></el-select>
          <el-input-number v-model="step.timeoutHours" :min="1" :max="168" style="width: 130px" />
          <span style="font-size: 12px; color: #909399">小时超时</span>
          <el-button link type="danger" @click="removeStep(idx)">删除</el-button>
        </div>
        <el-button @click="addStep" style="width: 100%">+ 添加步骤</el-button>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
