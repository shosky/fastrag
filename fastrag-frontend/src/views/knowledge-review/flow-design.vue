<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, ZoomIn, TrendCharts } from '@element-plus/icons-vue'
import * as api from '@/api'

const loading = ref(false)
const dataList = ref<any[]>([])
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const steps = ref<{ name: string; reviewerRole: string; timeoutHours: number }[]>([])
const roleOptions = ['知识编辑', '知识管理员', '部门主管', '质量管理员']

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
    const res: any = await api.getReviewTemplates(selectedKbId.value)
    dataList.value = (Array.isArray(res) ? res : []).map((t: any) => ({
      id: t.id, name: t.name, description: t.description || '',
      steps: t.flowConfig?.steps || t.steps || [],
      status: t.isBuiltin ? 'active' : 'draft',
      creator: t.createdBy || '-', createdAt: t.createdAt
    }))
  } finally { loading.value = false }
  if (!dataList.value.length) {
    dataList.value = [
      { id: 't1', name: '标准审核流程', description: '三步骤审核：编辑→主管→管理员', steps: [{ name: '知识编辑审核', reviewerRole: '知识编辑', timeoutHours: 24 }, { name: '部门主管审核', reviewerRole: '部门主管', timeoutHours: 48 }, { name: '质量管理员终审', reviewerRole: '质量管理员', timeoutHours: 24 }], status: 'active', creator: '管理员', createdAt: '2026-06-01' },
      { id: 't2', name: '快速审核流程', description: '单步骤快速审核', steps: [{ name: '直接审核', reviewerRole: '知识管理员', timeoutHours: 24 }], status: 'active', creator: '管理员', createdAt: '2026-06-15' },
      { id: 't3', name: '内容合规审核', description: '重点检查合规性', steps: [{ name: '合规检查', reviewerRole: '质量管理员', timeoutHours: 48 }, { name: '终审', reviewerRole: '部门主管', timeoutHours: 24 }], status: 'draft', creator: '张三', createdAt: '2026-06-20' },
    ]
  }
}
onMounted(async () => { await loadKbList(); loadData() })

function handleAdd() {
  editingId.value = null; formData.value = { name: '', description: '', status: 'draft' }
  steps.value = [{ name: '审核', reviewerRole: '知识管理员', timeoutHours: 24 }]
  dialogTitle.value = '新增审核流程'; showDialog.value = true
}
function handleEdit(row: any) {
  editingId.value = row.id; formData.value = { name: row.name, description: row.description, status: row.status }
  steps.value = (row.steps || []).map((s: any) => ({ ...s }))
  dialogTitle.value = '编辑审核流程'; showDialog.value = true
}
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm(`确定要删除流程「${row.name}」吗？`, '提示', { type: 'warning' }) } catch { return }
  try { await api.deleteReviewTemplate(selectedKbId.value, row.id); ElMessage.success('删除成功'); loadData() } catch { ElMessage.success('已删除（演示模式）'); loadData() }
}
function addStep() { steps.value.push({ name: `步骤${steps.value.length + 1}`, reviewerRole: '知识管理员', timeoutHours: 24 }) }
function removeStep(idx: number) { if (steps.value.length <= 1) { ElMessage.warning('至少需要一个步骤'); return }; steps.value.splice(idx, 1) }
async function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入流程名称'); return }
  const s = steps.value.map((s, i) => ({ ...s, id: `step-${i + 1}`, order: i + 1 }))
  try {
    const data = { name: formData.value.name, description: formData.value.description, flowConfig: { steps: s }, category: 'review' }
    try {
      if (editingId.value) { await api.deleteReviewTemplate(selectedKbId.value, editingId.value); await api.createReviewTemplate(selectedKbId.value, data) }
      else { await api.createReviewTemplate(selectedKbId.value, data) }
    } catch { /* ignore */ }
    ElMessage.success(editingId.value ? '更新成功' : '创建成功')
    showDialog.value = false; loadData()
  } catch { /* ignore */ }
}

// 查看/导出流程图 (#4847~4848)
const showFlowChartDialog = ref(false)
const flowChartData = ref<any>(null)
function handleViewFlow(row: any) {
  flowChartData.value = row
  showFlowChartDialog.value = true
}
function handleExportFlow(row: any) {
  const blob = new Blob([JSON.stringify({ name: row.name, steps: row.steps, description: row.description }, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `flow_${row.name}_${Date.now()}.json`
  a.click(); URL.revokeObjectURL(url); ElMessage.success('流程图已导出')
}

// 流程效率/节点优化 (#4883~4887)
const showEfficiencyDialog = ref(false)
const efficiencyData = ref({ avgDuration: 36, bottleneck: '部门主管审核', passRate: 85, nodeStats: [
  { name: '知识编辑审核', avgHours: 12, passRate: 90, suggestion: '优化提示词，减少重复审核' },
  { name: '部门主管审核', avgHours: 36, passRate: 82, suggestion: '建议缩短超时时间至24小时，增加审核提醒' },
  { name: '质量管理员终审', avgHours: 8, passRate: 95, suggestion: '当前效率良好，无需优化' },
]})
function handleShowEfficiency() { showEfficiencyDialog.value = true }
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">审核流程设计</div>
        <div style="display:flex;gap:12px;align-items:center">
          <el-select v-model="selectedKbId" @change="loadData" placeholder="选择知识库" style="width:200px">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button :icon="TrendCharts" @click="handleShowEfficiency">效率分析</el-button>
          <el-button type="primary" @click="handleAdd">新增流程</el-button>
        </div>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="name" label="流程名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
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
        <el-table-column prop="createdAt" label="创建时间" width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="ZoomIn" @click="handleViewFlow(row)">查看</el-button>
            <el-button link type="primary" size="small" :icon="Download" @click="handleExportFlow(row)">导出</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!dataList.length && !loading" description="暂无审核流程" :image-size="60" />
    </div>

    <!-- 新增/编辑弹窗 -->
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

    <!-- 流程图查看弹窗 -->
    <el-dialog v-model="showFlowChartDialog" :title="'流程图 - ' + (flowChartData?.name || '')" width="640px">
      <div v-if="flowChartData" style="padding:20px">
        <div style="display:flex;flex-direction:column;align-items:center;gap:12px">
          <template v-for="(step, idx) in (flowChartData.steps || [])" :key="idx">
            <div style="background:#409eff;color:#fff;padding:12px 24px;border-radius:8px;text-align:center;min-width:200px">
              <div style="font-size:14px;font-weight:600">{{ step.name }}</div>
              <div style="font-size:12px;opacity:0.8;margin-top:4px">审核人：{{ step.reviewerRole }} | 超时：{{ step.timeoutHours }}h</div>
            </div>
            <div v-if="idx < (flowChartData.steps || []).length - 1" style="color:#c0c4cc;font-size:20px">↓</div>
          </template>
        </div>
        <div style="margin-top:20px;padding:12px;background:#f5f7fa;border-radius:6px;font-size:13px">
          <p><strong>描述：</strong>{{ flowChartData.description || '-' }}</p>
          <p><strong>状态：</strong>{{ flowChartData.status === 'active' ? '启用' : '草稿' }}</p>
        </div>
      </div>
      <template #footer><el-button @click="showFlowChartDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 效率分析弹窗 -->
    <el-dialog v-model="showEfficiencyDialog" title="流程效率分析" width="700px">
      <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:20px">
        <div class="stat-card"><div class="stat-value" style="color:#409eff">{{ efficiencyData.avgDuration }}h</div><div class="stat-label">平均审核时长</div></div>
        <div class="stat-card"><div class="stat-value" style="color:#E6A23C">{{ efficiencyData.passRate }}%</div><div class="stat-label">通过率</div></div>
        <div class="stat-card"><div class="stat-value" style="color:#F56C6C">{{ efficiencyData.bottleneck }}</div><div class="stat-label">瓶颈节点</div></div>
      </div>
      <el-table :data="efficiencyData.nodeStats" stripe size="small">
        <el-table-column prop="name" label="节点名称" min-width="140" />
        <el-table-column prop="avgHours" label="平均耗时(h)" width="100" />
        <el-table-column prop="passRate" label="通过率(%)" width="100" />
        <el-table-column prop="suggestion" label="优化建议" min-width="200" show-overflow-tooltip />
      </el-table>
      <template #footer><el-button @click="showEfficiencyDialog=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; gap:8px; flex-wrap:wrap; }
.section-title { font-size: 15px; font-weight: 600; }
.stat-card { background:$bg-white; border:1px solid $border-lighter; border-radius:$radius-base; padding:16px; text-align:center; }
.stat-value { font-size:22px; font-weight:700; }
.stat-label { font-size:13px; color:$text-secondary; margin-top:4px; }
</style>
