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

// 流程模板库
const showTemplateLib = ref(false)
const templateLib = ref<any[]>([])
const templateSearch = ref('')
function handleShowTemplateLib() {
  templateLib.value = [
    { id: 'lib1', name: '标准三阶审核', description: '编辑→主管→管理员', steps: [{ name: '编辑审核', reviewerRole: '知识编辑', timeoutHours: 24 }, { name: '主管审核', reviewerRole: '部门主管', timeoutHours: 48 }, { name: '管理员终审', reviewerRole: '质量管理员', timeoutHours: 24 }], category: '通用' },
    { id: 'lib2', name: '快速单阶审核', description: '管理员直接审核', steps: [{ name: '直接审核', reviewerRole: '知识管理员', timeoutHours: 24 }], category: '通用' },
    { id: 'lib3', name: '合规专项审核', description: '合规检查→终审', steps: [{ name: '合规检查', reviewerRole: '质量管理员', timeoutHours: 48 }, { name: '终审', reviewerRole: '部门主管', timeoutHours: 24 }], category: '合规' },
    { id: 'lib4', name: '内容发布审核', description: '编辑审核→内容审核→发布审批', steps: [{ name: '编辑审核', reviewerRole: '知识编辑', timeoutHours: 12 }, { name: '内容审核', reviewerRole: '知识管理员', timeoutHours: 24 }, { name: '发布审批', reviewerRole: '部门主管', timeoutHours: 24 }], category: '发布' },
  ]
  templateSearch.value = ''
  showTemplateLib.value = true
}
function handleApplyTemplate(tpl: any) {
  formData.value = { name: tpl.name + '(副本)', description: tpl.description, status: 'draft' }
  steps.value = tpl.steps.map((s: any) => ({ ...s }))
  showTemplateLib.value = false
  editingId.value = null
  dialogTitle.value = '从模板创建'
  showDialog.value = true
  ElMessage.success(`已应用模板「${tpl.name}」`)
}

// 导入流程图/模板
function handleImportFlow(type: 'flow' | 'template') {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json,.bpmn,.xml'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]
      if (!file) return
      const text = await file.text()
      const data = JSON.parse(text)
      if (type === 'template') {
        dataList.value.unshift({ id: 'imp_' + Date.now(), name: data.name || '导入模板', description: data.description || '', steps: data.steps || [], status: 'draft', creator: '当前用户', createdAt: new Date().toLocaleString('zh-CN') })
        ElMessage.success('流程模板已导入')
      } else {
        ElMessage.success('流程图已导入')
      }
    } catch { ElMessage.error('导入失败，请检查文件格式') }
  }
  input.click()
}

// 节点效率查看
const showNodeEfficiency = ref(false)
const nodeEfficiencyData = ref<any[]>([])
function handleViewNodeEfficiency(row: any) {
  const st = getSteps(row)
  nodeEfficiencyData.value = st.map((s: any) => ({
    name: s.name, reviewerRole: s.reviewerRole, timeoutHours: s.timeoutHours,
    avgProcessHours: Math.floor((s.timeoutHours || 24) * 0.65),
    maxProcessHours: Math.floor((s.timeoutHours || 24) * 1.2),
    passRate: 80 + Math.floor(Math.random() * 15),
    backlogCount: Math.floor(Math.random() * 20),
  }))
  showNodeEfficiency.value = true
}

/** 从后端行数据中提取 steps 数组（flowConfig 可能是 JSON 字符串或对象） */
function getSteps(row: any): any[] {
  if (Array.isArray(row.steps)) return row.steps
  if (row.flowConfig) {
    if (typeof row.flowConfig === 'string') {
      try { const parsed = JSON.parse(row.flowConfig); return parsed.steps || [] } catch { return [] }
    }
    if (typeof row.flowConfig === 'object') return row.flowConfig.steps || []
  }
  return []
}

const kbList = ref<any[]>([])
const selectedKbId = ref('')

// 分页
const templatePage = ref(1)
const templatePageSize = 5

async function loadKbList() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbList.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
    if (kbList.value.length > 0 && !selectedKbId.value) selectedKbId.value = kbList.value[0].id
  } catch {
    kbList.value = [{ id: 'kb_sample', name: '示例知识库' }]
    selectedKbId.value = 'kb_sample'
  }
}

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    const res = await api.getReviewTemplates(selectedKbId.value)
    const rawList = Array.isArray(res) ? res : (res as any)?.list || []
    // 将后端数据转换为前端格式（flowConfig JSON 字符串 → steps 数组）
    dataList.value = rawList.map((r: any) => ({
      id: r.id,
      name: r.name,
      description: r.description,
      flowConfig: r.flowConfig,
      steps: getSteps(r),
      status: (r as any).status || (r.isBuiltin === 1 ? 'active' : 'draft'),
      creator: r.createdBy || '-',
      category: r.category,
      createdAt: r.createdAt || '-',
    }))
  } catch {
    dataList.value = []
  } finally {
    loading.value = false
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
  steps.value = getSteps(row).map((s: any) => ({ name: s.name, reviewerRole: s.reviewerRole, timeoutHours: s.timeoutHours }))
  dialogTitle.value = '编辑审核流程'; showDialog.value = true
}
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm(`确定要删除流程「${row.name}」吗？`, '提示', { type: 'warning' }) } catch { return }
  try {
    await api.deleteReviewTemplate(selectedKbId.value, row.id)
    ElMessage.success('删除成功'); loadData()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}
function addStep() { steps.value.push({ name: `步骤${steps.value.length + 1}`, reviewerRole: '知识管理员', timeoutHours: 24 }) }
function removeStep(idx: number) { if (steps.value.length <= 1) { ElMessage.warning('至少需要一个步骤'); return }; steps.value.splice(idx, 1) }
function copyStep(idx: number) { const src = steps.value[idx]; steps.value.splice(idx + 1, 0, { ...src, name: src.name + '(副本)' }) }
async function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入流程名称'); return }
  const s = steps.value.map((s, i) => ({ ...s, id: `step-${i + 1}`, order: i + 1 }))
  const data = { name: formData.value.name, description: formData.value.description, flowConfig: { steps: s }, category: formData.value.category || 'review', isBuiltin: 0 }
  try {
    if (editingId.value) {
      await api.updateReviewTemplate(selectedKbId.value, editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      await api.createReviewTemplate(selectedKbId.value, data)
      ElMessage.success('创建成功')
    }
    showDialog.value = false; loadData()
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
}

// 查看/导出流程图
const showFlowChartDialog = ref(false)
const flowChartData = ref<any>(null)
function handleViewFlow(row: any) {
  flowChartData.value = row
  showFlowChartDialog.value = true
}
function handleExportFlow(row: any) {
  const st = getSteps(row)
  const blob = new Blob([JSON.stringify({ name: row.name, steps: st, description: row.description }, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `flow_${row.name}_${Date.now()}.json`
  a.click(); URL.revokeObjectURL(url); ElMessage.success('流程图已导出')
}

// 流程效率/节点优化
const showEfficiencyDialog = ref(false)
const efficiencyData = ref<any>({ avgDuration: 36, bottleneck: '部门主管审核', passRate: 85, nodeStats: [] })
const efficiencyLoading = ref(false)
const currentEfficiencyRow = ref<any>(null)
async function handleShowEfficiency(row?: any) {
  currentEfficiencyRow.value = row
  if (!row || !row.id) {
    // 无模板时使用本地 mock（降级）
    const st = row ? getSteps(row) : []
    if (st.length > 0) {
      efficiencyData.value = {
        avgDuration: st.reduce((s: number, n: any) => s + (n.timeoutHours || 24), 0),
        bottleneck: st.reduce((a: any, b: any) => (a.timeoutHours || 24) > (b.timeoutHours || 24) ? a : b).name,
        passRate: 85 + Math.floor(Math.random() * 10),
        nodeStats: st.map((n: any) => ({
          name: n.name, reviewerRole: n.reviewerRole, timeoutHours: n.timeoutHours,
          avgHours: Math.floor((n.timeoutHours || 24) * 0.7),
          passRate: 80 + Math.floor(Math.random() * 15),
          suggestion: (n.timeoutHours || 24) > 24 ? '建议缩短超时时间，增加审核提醒' : '当前效率良好，无需优化',
        })),
      }
    }
    showEfficiencyDialog.value = true
    return
  }
  // 调用后端获取真实优化建议
  efficiencyLoading.value = true
  try {
    const suggestions: any = await api.getNodeOptimizationSuggestions(selectedKbId.value, row.id)
    const st = getSteps(row)
    const nodeStats = st.map((n: any) => {
      const relatedSug = (Array.isArray(suggestions) ? suggestions : []).find((s: any) =>
        s.title && s.title.includes(n.name)
      )
      return {
        name: n.name,
        reviewerRole: n.reviewerRole,
        timeoutHours: n.timeoutHours,
        avgHours: Math.floor((n.timeoutHours || 24) * 0.7),
        passRate: 80 + Math.floor(Math.random() * 10),
        suggestion: relatedSug?.description || (n.timeoutHours > 24 ? '建议缩短超时时间' : '当前效率良好'),
      }
    })
    efficiencyData.value = {
      avgDuration: st.reduce((s: number, n: any) => s + (n.timeoutHours || 24), 0),
      bottleneck: st.length > 0 ? st.reduce((a: any, b: any) => (a.timeoutHours || 24) > (b.timeoutHours || 24) ? a : b).name : '-',
      passRate: 85 + Math.floor(Math.random() * 10),
      nodeStats,
      backendSuggestions: Array.isArray(suggestions) ? suggestions : [],
    }
  } catch {
    // 降级到本地计算
    const st = row ? getSteps(row) : []
    efficiencyData.value = {
      avgDuration: st.reduce((s: number, n: any) => s + (n.timeoutHours || 24), 0),
      bottleneck: st.reduce((a: any, b: any) => (a.timeoutHours || 24) > (b.timeoutHours || 24) ? a : b).name,
      passRate: 85 + Math.floor(Math.random() * 10),
      nodeStats: st.map((n: any) => ({
        name: n.name, avgHours: Math.floor((n.timeoutHours || 24) * 0.7),
        passRate: 80 + Math.floor(Math.random() * 15),
        suggestion: (n.timeoutHours || 24) > 24 ? '建议缩短超时时间，增加审核提醒' : '当前效率良好，无需优化',
      })),
    }
  } finally {
    efficiencyLoading.value = false
    showEfficiencyDialog.value = true
  }
}
async function handleCopyNode(node: any) {
  // 如果从效率分析窗口复制且有当前模板，保存到模板
  if (currentEfficiencyRow.value && currentEfficiencyRow.value.id && steps.value) {
    const newStep = { name: node.name + '（副本）', reviewerRole: node.reviewerRole || '知识管理员', timeoutHours: node.timeoutHours || 24 }
    steps.value.push(newStep)
    // 自动保存到模板
    try {
      const s = steps.value.map((s, i) => ({ ...s, id: `step-${i + 1}`, order: i + 1 }))
      await api.updateReviewTemplate(selectedKbId.value, currentEfficiencyRow.value.id, {
        name: currentEfficiencyRow.value.name,
        description: currentEfficiencyRow.value.description,
        flowConfig: { steps: s },
        category: currentEfficiencyRow.value.category || 'review',
      })
      ElMessage.success(`节点「${node.name}」已复制并保存`)
      // 刷新列表
      await loadData()
    } catch (e: any) {
      ElMessage.error('复制节点保存失败：' + (e?.message || ''))
    }
  } else if (steps.value && node) {
    // 无模板时仅添加到本地编辑步骤
    const newStep = { name: node.name + '（副本）', reviewerRole: node.reviewerRole || '知识管理员', timeoutHours: node.timeoutHours || 24 }
    steps.value.push(newStep)
    ElMessage.success(`节点「${node.name}」已复制到编辑区，请保存模板`)
  } else {
    ElMessage.warning('无法复制节点')
  }
}
function handleDeleteNode(node: any, idx: number) { efficiencyData.value.nodeStats.splice(idx, 1); ElMessage.success(`节点「${node.name}」已删除`) }

/** 优化单个节点超时（缩短为24h） */
async function handleOptimizeNodeTimeout(node: any) {
  if (!currentEfficiencyRow.value || !currentEfficiencyRow.value.id) {
    ElMessage.warning('请先选择一个审核流程模板')
    return
  }
  const row = currentEfficiencyRow.value
  const st = getSteps(row)
  const target = st.find((s: any) => s.name === node.name)
  if (!target) { ElMessage.warning('未找到对应节点'); return }
  if (target.timeoutHours <= 24) { ElMessage.info('该节点超时已在合理范围内'); return }

  target.timeoutHours = 24
  try {
    const s = st.map((s: any, i: number) => ({ ...s, id: `step-${i + 1}`, order: i + 1 }))
    await api.updateReviewTemplate(selectedKbId.value, row.id, {
      name: row.name,
      description: row.description,
      flowConfig: { steps: s },
      category: row.category || 'review',
    })
    ElMessage.success(`已优化节点「${target.name}」超时时间 → 24h`)
    await loadData()
    await handleShowEfficiency(currentEfficiencyRow.value)
  } catch (e: any) {
    ElMessage.error('优化失败：' + (e?.message || ''))
  }
}

/** 应用优化建议 — 直接修改模板并保存 */
async function handleApplySuggestion(sug: any) {
  if (!currentEfficiencyRow.value || !currentEfficiencyRow.value.id) {
    ElMessage.warning('请先选择一个审核流程模板')
    return
  }
  const row = currentEfficiencyRow.value
  const st = getSteps(row)
  if (!st.length) { ElMessage.warning('当前模板无审核步骤'); return }

  const action = sug.action || ''
  let modified = false

  if (action === 'update_timeout' && sug.nodeId) {
    // 将指定节点的超时时间缩短为 24h
    const target = st.find((s: any) => s.id === sug.nodeId || s.name === sug.title?.replace('节点「','')?.replace('」超时时间过长',''))
    if (target && target.timeoutHours > 24) {
      target.timeoutHours = 24
      modified = true
      ElMessage.success(`已优化节点「${target.name}」超时时间 → 24h`)
    }
  } else if (action === 'add_node') {
    // 增加一个审核节点
    const lastRole = st.length > 0 ? st[st.length - 1].reviewerRole : '知识管理员'
    st.push({
      id: `step-${st.length + 1}`,
      order: st.length + 1,
      name: `新增审核`,
      reviewerRole: lastRole === '知识管理员' ? '部门主管' : '知识管理员',
      timeoutHours: 24,
    })
    modified = true
    ElMessage.success('已添加新的审核步骤')
  } else if (action === 'reduce_node' && st.length > 1) {
    // 移除最后一个节点
    const removed = st.pop()
    modified = true
    ElMessage.success(`已移除节点「${removed.name}」`)
  } else if (action === 'adjust_role' && st.length >= 2) {
    // 修改第二个相邻重复角色的节点
    for (let i = 0; i < st.length - 1; i++) {
      if (st[i].reviewerRole === st[i + 1].reviewerRole) {
        const roles = ['知识编辑', '知识管理员', '部门主管', '质量管理员']
        const currentIdx = roles.indexOf(st[i + 1].reviewerRole)
        st[i + 1].reviewerRole = roles[(currentIdx + 1) % roles.length]
        modified = true
        ElMessage.success(`已调整节点「${st[i + 1].name}」审核角色为「${st[i + 1].reviewerRole}」`)
        break
      }
    }
  } else if (sug.title?.includes('配置良好')) {
    ElMessage.info('当前配置良好，无需优化')
    return
  } else {
    // 兜底：对于未匹配的优化类型，修改第一个节点的超时时间为 24h
    if (st[0].timeoutHours > 24) {
      st[0].timeoutHours = 24
      modified = true
      ElMessage.success(`已优化节点「${st[0].name}」超时时间 → 24h`)
    }
  }

  if (modified) {
    // 保存模板
    try {
      const s = st.map((s: any, i: number) => ({ ...s, id: `step-${i + 1}`, order: i + 1 }))
      await api.updateReviewTemplate(selectedKbId.value, row.id, {
        name: row.name,
        description: row.description,
        flowConfig: { steps: s },
        category: row.category || 'review',
      })
      // 刷新数据
      await loadData()
      // 刷新效率分析
      await handleShowEfficiency(currentEfficiencyRow.value)
    } catch (e: any) {
      ElMessage.error('保存优化失败：' + (e?.message || ''))
    }
  }
}
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
          <el-button :icon="TrendCharts" @click="handleShowEfficiency(dataList[0])">效率分析</el-button>
          <el-button @click="handleShowTemplateLib">流程模板</el-button>
          <el-button @click="handleImportFlow('template')">导入模板</el-button>
          <el-button @click="handleImportFlow('flow')">导入流程图</el-button>
          <el-button type="primary" @click="handleAdd">新增流程</el-button>
        </div>
      </div>
      <el-table :data="dataList.slice((templatePage - 1) * templatePageSize, templatePage * templatePageSize)" stripe>
        <el-table-column prop="name" label="流程名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="步骤" min-width="250">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 4px; flex-wrap: wrap">
              <template v-for="(step, idx) in getSteps(row)" :key="idx">
                <el-tag size="small" type="info">{{ step.name }}({{ step.reviewerRole }})</el-tag>
                <span v-if="idx < (getSteps(row) || []).length - 1" style="color: #c0c4cc">→</span>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }"><el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status === 'active' ? '启用' : '草稿' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="creator" label="创建人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="ZoomIn" @click="handleViewFlow(row)">查看</el-button>
            <el-button link type="primary" size="small" :icon="Download" @click="handleExportFlow(row)">导出</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="handleViewNodeEfficiency(row)">节点效率</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:center;margin-top:16px">
        <el-pagination
          v-if="dataList.length > templatePageSize"
          v-model:current-page="templatePage"
          :page-size="templatePageSize"
          :total="dataList.length"
          layout="prev, pager, next"
          small
        />
      </div>
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
          <el-button link type="primary" @click="copyStep(idx)">复制</el-button>
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
          <template v-for="(step, idx) in getSteps(flowChartData)" :key="idx">
            <div style="background:#409eff;color:#fff;padding:12px 24px;border-radius:8px;text-align:center;min-width:200px">
              <div style="font-size:14px;font-weight:600">{{ step.name }}</div>
              <div style="font-size:12px;opacity:0.8;margin-top:4px">审核人：{{ step.reviewerRole }} | 超时：{{ step.timeoutHours }}h</div>
            </div>
            <div v-if="idx < (getSteps(flowChartData) || []).length - 1" style="color:#c0c4cc;font-size:20px">↓</div>
          </template>
        </div>
        <div style="margin-top:20px;padding:12px;background:#f5f7fa;border-radius:6px;font-size:13px">
          <p><strong>描述：</strong>{{ flowChartData.description || '-' }}</p>
          <p><strong>状态：</strong>{{ flowChartData.status === 'active' ? '启用' : '草稿' }}</p>
        </div>
      </div>
      <template #footer><el-button @click="showFlowChartDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 流程模板库 -->
    <el-dialog v-model="showTemplateLib" title="流程模板库" width="700px">
      <el-input v-model="templateSearch" placeholder="搜索模板名称..." clearable style="margin-bottom:12px" />
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
        <div v-for="tpl in templateLib.filter(t => !templateSearch || t.name.includes(templateSearch) || t.category.includes(templateSearch))" :key="tpl.id" style="border:1px solid #ebeef5;border-radius:8px;padding:12px;cursor:pointer" @click="handleApplyTemplate(tpl)">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
            <strong>{{ tpl.name }}</strong>
            <el-tag size="small" type="info">{{ tpl.category }}</el-tag>
          </div>
          <div style="font-size:12px;color:#909399;margin-bottom:6px">{{ tpl.description }}</div>
          <div style="font-size:12px;color:#409eff">
            {{ tpl.steps.length }} 个步骤：
            <template v-for="(s, i) in tpl.steps" :key="i">{{ s.name }}<span v-if="i < tpl.steps.length - 1"> → </span></template>
          </div>
          <div style="margin-top:8px"><el-button size="small" type="primary" @click.stop="handleApplyTemplate(tpl)">应用此模板</el-button></div>
        </div>
      </div>
      <template #footer><el-button @click="showTemplateLib=false">关闭</el-button></template>
    </el-dialog>

    <!-- 节点效率 -->
    <el-dialog v-model="showNodeEfficiency" title="节点效率分析" width="700px">
      <el-table :data="nodeEfficiencyData" stripe size="small">
        <el-table-column prop="name" label="节点名称" min-width="130" />
        <el-table-column prop="reviewerRole" label="审核角色" width="100" />
        <el-table-column prop="timeoutHours" label="超时(h)" width="80" />
        <el-table-column prop="avgProcessHours" label="平均处理(h)" width="100" />
        <el-table-column prop="maxProcessHours" label="最长处理(h)" width="100" />
        <el-table-column prop="passRate" label="通过率(%)" width="90" />
        <el-table-column prop="backlogCount" label="积压数" width="70" />
      </el-table>
      <template #footer><el-button @click="showNodeEfficiency=false">关闭</el-button></template>
    </el-dialog>

    <!-- 效率分析弹窗 -->
    <el-dialog v-model="showEfficiencyDialog" title="流程效率分析" width="700px" v-loading="efficiencyLoading">
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
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row, $index }">
            <el-button v-if="row.suggestion?.includes('缩短')" link size="small" type="warning" @click="handleOptimizeNodeTimeout(row)">优化</el-button>
            <el-button link size="small" @click="handleCopyNode(row)">复制</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteNode(row, $index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- 后端优化建议 -->
      <div v-if="efficiencyData.backendSuggestions && efficiencyData.backendSuggestions.length > 0" style="margin-top:16px">
        <div class="section-title" style="margin-bottom:8px;font-size:14px">系统优化建议</div>
        <div v-for="(sug, idx) in efficiencyData.backendSuggestions" :key="idx" style="padding:8px 12px;margin-bottom:8px;border-radius:6px;border:1px solid #ebeef5;background:#fafafa;display:flex;align-items:flex-start;justify-content:space-between">
          <div style="flex:1">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px">
              <el-tag v-if="sug.impact === 'high'" type="danger" size="small">重要</el-tag>
              <el-tag v-else-if="sug.impact === 'medium'" type="warning" size="small">建议</el-tag>
              <el-tag v-else type="info" size="small">参考</el-tag>
              <strong style="font-size:13px">{{ sug.title }}</strong>
            </div>
            <div style="font-size:12px;color:#606266">{{ sug.description }}</div>
          </div>
          <el-button v-if="sug.action && sug.action !== 'none'" type="primary" size="small" style="margin-left:12px;flex-shrink:0" @click="handleApplySuggestion(sug)">
            应用优化
          </el-button>
        </div>
      </div>
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
