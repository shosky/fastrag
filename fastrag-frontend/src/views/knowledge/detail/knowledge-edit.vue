<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const kbId = (route.params.id as string) || 'kb_sample'

const activeTab = ref('edit')
const loading = ref(false)

// 采编管理
const editList = ref<any[]>([])
const editQuery = ref({ status: '', page: 1 })
// 勾选的采编行（用于按 ids 导出）
const selectedEdits = ref<any[]>([])

async function loadEdits() {
  loading.value = true
  try {
    const res: any = await api.getKnowledgeEdits(kbId, { status: editQuery.value.status || undefined })
    editList.value = res || []
  } finally {
    loading.value = false
  }
}

// ===== 导入采编（JSON 数组批量导入） =====
const showImportDialog = ref(false)
const importItems = ref('')
function handleImport() {
  importItems.value = ''
  showImportDialog.value = true
}
async function handleImportSubmit() {
  let items: any[] = []
  try {
    items = JSON.parse(importItems.value)
    if (!Array.isArray(items)) throw new Error()
  } catch {
    ElMessage.warning('请输入有效的JSON数组')
    return
  }
  try {
    await api.importKnowledgeEdits(kbId, items)
    showImportDialog.value = false
    await loadEdits()
    ElMessage.success(`成功导入 ${items.length} 条采编记录`)
  } catch {
    ElMessage.error('导入失败')
  }
}

const showEditDialog = ref(false)
const editingId = ref<string | null>(null)
const editForm = ref({ title: '', content: '', editType: 'create', tags: '' })

function handleAddEdit() {
  editingId.value = null
  editForm.value = { title: '', content: '', editType: 'create', tags: '' }
  showEditDialog.value = true
}

function handleEditEdit(row: any) {
  editingId.value = row.id
  editForm.value = { title: row.title || '', content: row.content || '', editType: row.editType || 'update', tags: row.tags || '' }
  showEditDialog.value = true
}

async function handleSaveEdit() {
  if (!editForm.value.title) { ElMessage.warning('请输入标题'); return }
  const data = { title: editForm.value.title, content: editForm.value.content, editType: editForm.value.editType, tags: editForm.value.tags }
  if (editingId.value) await api.updateKnowledgeEdit(kbId, editingId.value, data)
  else await api.createKnowledgeEdit(kbId, data)
  showEditDialog.value = false
  await loadEdits()
  ElMessage.success('保存成功')
}

async function handleSubmit(row: any) { await api.submitKnowledgeEdit(kbId, row.id); await loadEdits(); ElMessage.success('已提交审核') }
async function handleApprove(row: any) { await api.approveKnowledgeEdit(kbId, row.id); await loadEdits(); ElMessage.success('已通过') }
async function handleReject(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '审核驳回', { type: 'warning' })
    await api.rejectKnowledgeEdit(kbId, row.id, { comment: value })
    await loadEdits()
    ElMessage.success('已驳回')
  } catch {}
}
async function handleDeleteEdit(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该采编记录？', '删除确认', { type: 'warning' })
    await api.deleteKnowledgeEdit(kbId, row.id)
    await loadEdits()
    ElMessage.success('删除成功')
  } catch {}
}
async function handleExportEdits() {
  try {
    // 有勾选则按 ids 导出，否则按当前筛选条件全量导出
    const params: { ids?: string; status?: string } = {}
    if (selectedEdits.value.length) {
      params.ids = selectedEdits.value.map((r: any) => r.id).join(',')
    } else if (editQuery.value.status) {
      params.status = editQuery.value.status
    }
    const blob = await api.exportKnowledgeEdits(kbId, params) as unknown as Blob
    if (!blob || blob.size === 0) {
      ElMessage.warning('没有可导出的数据')
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `知识采编数据_${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    const count = selectedEdits.value.length ? selectedEdits.value.length : '全部'
    ElMessage.success(`成功导出 ${count} 条采编记录`)
  } catch {
    ElMessage.error('导出失败')
  }
}

// 存量校验
const validateList = ref<any[]>([])
async function loadValidates() {
  const res: any = await api.getKnowledgeValidates(kbId)
  validateList.value = res || []
}

// 校验明细查看
const VALIDATE_STATUS: Record<string, string> = { completed: '已完成', running: '进行中', pending: '待执行', failed: '失败' }
const showResultDialog = ref(false)
const resultDetail = ref<any>(null)
function handleViewResult(row: any) {
  try {
    resultDetail.value = row.result ? JSON.parse(row.result) : null
  } catch {
    resultDetail.value = { raw: row.result }
  }
  showResultDialog.value = true
}

const showCheckDialog = ref(false)
const checkForm = ref({ validateType: 'duplicate', targetScope: 'all', similarityThreshold: 0.85 })

async function handleStartCheck() {
  await api.checkKnowledgeValidate(kbId, {
    validateType: checkForm.value.validateType,
    targetScope: checkForm.value.targetScope,
    result: JSON.stringify({ similarityThreshold: checkForm.value.similarityThreshold }),
  })
  showCheckDialog.value = false
  await loadValidates()
  ElMessage.success('校验完成')
}

// ===== 知识工单（新增/查看/编辑/删除 + 审批 + 流程环节） =====
// 新增/修改/取消发布发起流程后由后端自动生成（status=pending_approval），需审批
const TICKET_STATUS: Record<string, string> = { pending_approval: '待审批', approved: '已通过', rejected: '已驳回', open: '待处理', processing: '处理中', resolved: '已解决', closed: '已关闭' }
const TICKET_STATUS_COLOR: Record<string, string> = { pending_approval: 'warning', approved: 'success', rejected: 'danger', open: 'warning', processing: 'primary', resolved: 'success', closed: 'info' }
const TICKET_TYPE: Record<string, string> = { create: '新建知识', update: '更新知识', review: '内容纠错', offline: '取消发布', other: '其他' }
const PRIORITY: Record<string, string> = { high: '高', medium: '中', low: '低' }

const ticketList = ref<any[]>([])
const ticketQuery = ref({ status: '', ticketType: '', keyword: '' })
const ticketLoading = ref(false)
async function loadTickets() {
  ticketLoading.value = true
  try {
    const res: any = await api.getKnowledgeTickets(kbId, {
      status: ticketQuery.value.status || undefined,
      ticketType: ticketQuery.value.ticketType || undefined,
      keyword: ticketQuery.value.keyword || undefined,
    })
    ticketList.value = res || []
  } catch {
    ticketList.value = []
  } finally {
    ticketLoading.value = false
  }
}

const showTicketDialog = ref(false)
const editingTicketId = ref<string | null>(null)
const ticketForm = ref({ title: '', description: '', ticketType: 'update', priority: 'medium', knowledgeId: '', assignee: '', status: 'open', remark: '' })
function handleAddTicket() {
  editingTicketId.value = null
  ticketForm.value = { title: '', description: '', ticketType: 'update', priority: 'medium', knowledgeId: '', assignee: '', status: 'open', remark: '' }
  showTicketDialog.value = true
}
function handleEditTicket(row: any) {
  editingTicketId.value = row.id
  ticketForm.value = {
    title: row.title || '', description: row.description || '', ticketType: row.ticketType || 'update',
    priority: row.priority || 'medium', knowledgeId: row.knowledgeId || '', assignee: row.assignee || '',
    status: row.status || 'open', remark: row.remark || '',
  }
  showTicketDialog.value = true
}
async function handleSaveTicket() {
  if (!ticketForm.value.title) { ElMessage.warning('请输入工单标题'); return }
  try {
    if (editingTicketId.value) await api.updateKnowledgeTicket(kbId, editingTicketId.value, ticketForm.value)
    else await api.createKnowledgeTicket(kbId, ticketForm.value)
    showTicketDialog.value = false
    await loadTickets()
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  }
}
// 查看工单详情（含流程环节时间轴）
const showTicketViewDialog = ref(false)
const viewingTicket = ref<any>(null)
const ticketLogs = ref<any[]>([])
const TICKET_ACTION: Record<string, string> = { submit: '提交', approve: '通过', reject: '驳回', status_change: '状态变更' }
const TICKET_ACTION_COLOR: Record<string, string> = { submit: 'info', approve: 'success', reject: 'danger', status_change: 'warning' }
async function handleViewTicket(row: any) {
  try {
    viewingTicket.value = (await api.getKnowledgeTicket(kbId, row.id)) || row
  } catch {
    viewingTicket.value = row
  }
  ticketLogs.value = []
  try {
    const logs: any = await api.getKnowledgeTicketLogs(kbId, row.id)
    ticketLogs.value = logs || []
  } catch { ticketLogs.value = [] }
  showTicketViewDialog.value = true
}
// 审批：通过 / 驳回（驳回需填意见）
async function handleApproveTicket(row: any) {
  try {
    await ElMessageBox.confirm(`通过工单「${row.title}」？` + (row.currentStep ? `（当前环节：${row.currentStep}）` : ''), '审批通过', { type: 'success' })
    await api.approveKnowledgeTicket(kbId, row.id)
    await loadTickets()
    ElMessage.success('已通过')
  } catch {}
}
async function handleRejectTicket(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回意见', '审批驳回', { inputPlaceholder: '如：内容与现行政策不符', inputValidator: (v: string) => (v && v.trim() ? true : '驳回意见不能为空') })
    await api.rejectKnowledgeTicket(kbId, row.id, value)
    await loadTickets()
    ElMessage.success('已驳回')
  } catch {}
}
async function handleDeleteTicket(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除工单「${row.title}」？`, '删除确认', { type: 'warning' })
    await api.deleteKnowledgeTicket(kbId, row.id)
    await loadTickets()
    ElMessage.success('删除成功')
  } catch {}
}

// ===== 多媒体处理（编辑：名称/描述/标签/状态）—— 知识加工与采编-多媒体处理-编辑 =====
const MEDIA_TYPES = [
  { value: 'image', label: '图片' },
  { value: 'audio', label: '音频' },
  { value: 'video', label: '视频' },
  { value: 'document', label: '文档' },
]
const MEDIA_STATUS: Record<string, string> = { uploaded: '已上传', processing: '处理中', completed: '已完成', failed: '失败' }
function mediaStatusText(s: any) { return MEDIA_STATUS[String(s)] || String(s || '-') }
const mediaType = ref('image')
const mediaKeyword = ref('')
const mediaList = ref<any[]>([])
const mediaLoading = ref(false)
const showMediaEditDialog = ref(false)
const mediaSaving = ref(false)
const mediaForm = ref({ id: '', name: '', description: '', tags: '', status: 'uploaded' })

async function loadMedia() {
  mediaLoading.value = true
  try {
    const res: any = await api.getStorageList(kbId, mediaType.value, { keyword: mediaKeyword.value || undefined })
    mediaList.value = Array.isArray(res) ? res : res?.list || res?.records || []
  } catch { mediaList.value = [] } finally { mediaLoading.value = false }
}
function handleMediaEdit(row: any) {
  mediaForm.value = { id: row.id, name: row.name || '', description: row.description || '', tags: Array.isArray(row.tags) ? row.tags.join(',') : (row.tags || ''), status: row.status || 'uploaded' }
  showMediaEditDialog.value = true
}
async function handleMediaSave() {
  if (!mediaForm.value.name) { ElMessage.warning('请输入名称'); return }
  mediaSaving.value = true
  try {
    await api.updateStorage(kbId, mediaType.value, mediaForm.value.id, {
      name: mediaForm.value.name,
      description: mediaForm.value.description,
      tags: mediaForm.value.tags,
      status: mediaForm.value.status,
    })
    showMediaEditDialog.value = false
    await loadMedia()
    ElMessage.success('保存成功')
  } catch { ElMessage.error('保存失败') } finally { mediaSaving.value = false }
}

// ===== 知识回收（取消发布/到期失效/手动删除三类来源集中管理 + 编辑/恢复/彻底删除） =====
const recycleList = ref<any[]>([])
const recycleLoading = ref(false)
const showRecycleEditDialog = ref(false)
const recycleSaving = ref(false)
const recycleForm = ref({ id: '', title: '', category: '', summary: '' })
// 来源：deleted=手动删除（软删）、offline=取消发布下架、expired=到期失效
const RECYCLE_SOURCE: Record<string, string> = { deleted: '手动删除', offline: '取消发布', expired: '到期失效' }
const RECYCLE_SOURCE_COLOR: Record<string, string> = { deleted: 'info', offline: 'warning', expired: 'danger' }
const KNOWLEDGE_STATUS: Record<string, string> = { draft: '草稿', reviewing: '待审核', published: '已发布', offline: '已下架', expired: '已失效', archived: '已归档' }
const recycleSourceFilter = ref('')
const recycleFiltered = computed(() => recycleSourceFilter.value ? recycleList.value.filter(r => (r.recycleSource || 'deleted') === recycleSourceFilter.value) : recycleList.value)

async function loadRecycle() {
  recycleLoading.value = true
  try { recycleList.value = ((await api.getDeletedKnowledges(kbId)) as any) || [] } catch { recycleList.value = [] } finally { recycleLoading.value = false }
}
function handleRecycleEdit(row: any) {
  recycleForm.value = { id: row.id, title: row.title || '', category: row.category || '', summary: row.summary || '' }
  showRecycleEditDialog.value = true
}
async function handleRecycleSave() {
  if (!recycleForm.value.title) { ElMessage.warning('请输入标题'); return }
  recycleSaving.value = true
  try {
    await api.updateKnowledge(kbId, recycleForm.value.id, {
      title: recycleForm.value.title,
      category: recycleForm.value.category,
      summary: recycleForm.value.summary,
    })
    showRecycleEditDialog.value = false
    await loadRecycle()
    ElMessage.success('已保存（条目仍在回收站）')
  } catch { ElMessage.error('保存失败') } finally { recycleSaving.value = false }
}
async function handleRecycleRestore(row: any) {
  try {
    await api.restoreKnowledge(kbId, row.id)
    await loadRecycle()
    ElMessage.success(row.recycleSource === 'deleted' ? '已恢复' : '已恢复为草稿，可编辑后重新发布')
  } catch { ElMessage.error('恢复失败') }
}
// 手动触发失效扫描：等价每日 01:30 的定时任务，把 valid_until 已到期的知识点置为失效
async function handleExpireScan() {
  try {
    const res: any = await api.expireScanKnowledge(kbId)
    await loadRecycle()
    ElMessage.success(`失效扫描完成，本次处理 ${res?.expiredCount ?? 0} 条`)
  } catch { ElMessage.error('失效扫描失败') }
}
async function handleRecyclePermanentDelete(row: any) {
  try {
    await ElMessageBox.confirm(`彻底删除「${row.title}」后不可恢复，确认删除？`, '彻底删除', { type: 'warning' })
    await api.permanentDeleteKnowledge(kbId, row.id)
    await loadRecycle()
    ElMessage.success('已彻底删除')
  } catch {}
}

onMounted(() => {
  loadEdits()
  loadValidates()
  loadTickets()
  loadMedia()
  loadRecycle()
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="知识采编" name="edit">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识采编管理</div>
            <div>
              <el-button @click="handleImport">导入</el-button>
              <el-button @click="handleExportEdits">导出</el-button>
              <el-button type="primary" @click="handleAddEdit">新增采编</el-button>
            </div>
          </div>
          <div class="filter-bar">
            <el-select v-model="editQuery.status" placeholder="状态筛选" clearable style="width: 140px" @change="loadEdits">
              <el-option label="草稿" value="draft" />
              <el-option label="待审核" value="submitted" />
              <el-option label="已通过" value="approved" />
              <el-option label="已驳回" value="rejected" />
            </el-select>
          </div>
          <el-table :data="editList" stripe @selection-change="(rows: any[]) => selectedEdits = rows">
            <el-table-column type="selection" width="45" />
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="editType" label="类型" width="80" />
            <el-table-column prop="editor" label="编辑者" width="100" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'approved' ? 'success' : (row.status === 'rejected' ? 'danger' : (row.status === 'submitted' ? 'warning' : 'info'))" size="small">
                  {{ { draft: '草稿', submitted: '待审核', approved: '已通过', rejected: '已驳回' }[row.status as string] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditEdit(row)">编辑</el-button>
                <el-button v-if="row.status === 'draft'" link type="warning" size="small" @click="handleSubmit(row)">提交</el-button>
                <el-button v-if="row.status === 'submitted'" link type="success" size="small" @click="handleApprove(row)">通过</el-button>
                <el-button v-if="row.status === 'submitted'" link type="danger" size="small" @click="handleReject(row)">驳回</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteEdit(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="知识工单" name="ticket">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识工单</div>
            <span style="font-size: 12px; color: #909399; margin-right: auto; padding-left: 12px">
              发起新增/修改/取消发布流程后自动生成工单，需审批；「流程环节」可查看工单在流程中的步骤
            </span>
            <el-button type="primary" @click="handleAddTicket">新增工单</el-button>
          </div>
          <div class="filter-bar">
            <el-input v-model="ticketQuery.keyword" placeholder="搜索标题" clearable style="width: 200px" @keyup.enter="loadTickets" />
            <el-select v-model="ticketQuery.ticketType" placeholder="工单类型" clearable style="width: 140px" @change="loadTickets">
              <el-option v-for="(label, key) in TICKET_TYPE" :key="key" :label="label" :value="key" />
            </el-select>
            <el-select v-model="ticketQuery.status" placeholder="状态" clearable style="width: 120px" @change="loadTickets">
              <el-option v-for="(label, key) in TICKET_STATUS" :key="key" :label="label" :value="key" />
            </el-select>
            <el-button type="primary" @click="loadTickets">查询</el-button>
          </div>
          <el-table :data="ticketList" stripe v-loading="ticketLoading">
            <el-table-column prop="title" label="工单标题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="ticketType" label="类型" width="100">
              <template #default="{ row }">{{ TICKET_TYPE[row.ticketType] || row.ticketType }}</template>
            </el-table-column>
            <el-table-column label="关联知识点" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.knowledgeTitle || row.knowledgeId || '-' }}</template>
            </el-table-column>
            <el-table-column label="流程环节" width="110">
              <template #default="{ row }">{{ row.currentStep || '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="(TICKET_STATUS_COLOR[row.status] || 'info') as any" size="small">{{ TICKET_STATUS[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'pending_approval'" link type="success" size="small" @click="handleApproveTicket(row)">通过</el-button>
                <el-button v-if="row.status === 'pending_approval'" link type="danger" size="small" @click="handleRejectTicket(row)">驳回</el-button>
                <el-button link type="primary" size="small" @click="handleViewTicket(row)">查看</el-button>
                <el-button link type="primary" size="small" @click="handleEditTicket(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteTicket(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!ticketList.length && !ticketLoading" description="暂无知识工单" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="存量校验" name="validate">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">存量知识点校验</div>
            <el-button type="primary" @click="showCheckDialog = true">发起校验</el-button>
          </div>
          <el-table :data="validateList" stripe>
            <el-table-column prop="validateType" label="校验类型" width="120">
              <template #default="{ row }">{{ { duplicate: '重复检查', expired: '过期检查', quality: '质量检查', consistency: '一致性检查' }[row.validateType as string] || row.validateType }}</template>
            </el-table-column>
            <el-table-column prop="targetScope" label="范围" width="80" />
            <el-table-column prop="totalCount" label="总数" width="80" align="center" />
            <el-table-column prop="passedCount" label="通过" width="80" align="center" />
            <el-table-column prop="warningCount" label="警告" width="80" align="center" />
            <el-table-column prop="failedCount" label="失败" width="80" align="center" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }"><el-tag :type="row.status === 'completed' ? 'success' : 'info'" size="small">{{ VALIDATE_STATUS[row.status] || row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="completedAt" label="完成时间" width="160" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.result" link type="primary" size="small" @click="handleViewResult(row)">查看明细</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!validateList.length" description="暂无校验记录" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="多媒体处理" name="media">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">多媒体处理</div>
            <span style="font-size:12px;color:#909399">编辑多媒体的名称/描述/标签/状态（与「知识生产」Tab 同一数据源）</span>
          </div>
          <div class="filter-bar">
            <el-select v-model="mediaType" placeholder="媒体类型" style="width: 130px" @change="loadMedia">
              <el-option v-for="t in MEDIA_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-input v-model="mediaKeyword" placeholder="按名称查询" clearable style="width: 200px" @keyup.enter="loadMedia" />
            <el-button type="primary" @click="loadMedia">查询</el-button>
            <span style="color: #909399; font-size: 12px">共 {{ mediaList.length }} 条</span>
          </div>
          <el-table :data="mediaList" stripe v-loading="mediaLoading">
            <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
            <el-table-column label="标签" min-width="140">
              <template #default="{ row }">
                <template v-if="Array.isArray(row.tags) ? row.tags.length : row.tags">
                  <el-tag v-for="t in (Array.isArray(row.tags) ? row.tags : String(row.tags).split(/[,，]/))" :key="t" size="small" type="info" style="margin-right:4px">{{ t }}</el-tag>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'completed' ? 'success' : (row.status === 'failed' ? 'danger' : (row.status === 'processing' ? 'warning' : 'info'))" size="small">{{ mediaStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleMediaEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!mediaList.length && !mediaLoading" description="该类型暂无多媒体资源（可到「知识生产」Tab 上传）" :image-size="60" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="知识回收" name="recycle">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识回收</div>
            <span style="font-size:12px;color:#909399">取消发布、到期失效与手动删除的知识点在此集中管理；可编辑修正后恢复。</span>
            <el-button size="small" @click="handleExpireScan">触发失效扫描</el-button>
          </div>
          <div class="filter-bar">
            <el-select v-model="recycleSourceFilter" placeholder="来源" clearable style="width: 140px">
              <el-option label="全部" value="" />
              <el-option label="手动删除" value="deleted" />
              <el-option label="取消发布" value="offline" />
              <el-option label="到期失效" value="expired" />
            </el-select>
          </div>
          <el-table :data="recycleFiltered" stripe v-loading="recycleLoading">
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column label="来源" width="110">
              <template #default="{ row }">
                <el-tag :type="(RECYCLE_SOURCE_COLOR[row.recycleSource] || 'info') as any" size="small">{{ RECYCLE_SOURCE[row.recycleSource] || row.recycleSource || '手动删除' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ KNOWLEDGE_STATUS[row.status] || row.status }}</template>
            </el-table-column>
            <el-table-column label="入站时间" width="160">
              <template #default="{ row }">{{ row.recycleTime || row.deletedAt }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleRecycleEdit(row)">编辑</el-button>
                <el-button link type="success" size="small" @click="handleRecycleRestore(row)">{{ row.recycleSource === 'deleted' ? '恢复' : '恢复为草稿' }}</el-button>
                <el-button link type="danger" size="small" @click="handleRecyclePermanentDelete(row)">彻底删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!recycleFiltered.length && !recycleLoading" description="回收站为空" :image-size="60" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 采编弹窗 -->
    <el-dialog v-model="showEditDialog" :title="editingId ? '编辑采编' : '新增采编'" width="600px">
      <el-form label-width="80px">
        <el-form-item label="标题" required><el-input v-model="editForm.title" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editForm.editType" style="width: 140px">
            <el-option label="新建" value="create" />
            <el-option label="更新" value="update" />
            <el-option label="合并" value="merge" />
            <el-option label="拆分" value="split" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容"><el-input v-model="editForm.content" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="editForm.tags" placeholder="逗号分隔" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 校验弹窗 -->
    <el-dialog v-model="showCheckDialog" title="发起存量校验" width="480px">
      <el-form label-width="100px">
        <el-form-item label="校验类型">
          <el-select v-model="checkForm.validateType" style="width: 180px">
            <el-option label="重复检查" value="duplicate" />
            <el-option label="过期检查" value="expired" />
            <el-option label="质量检查" value="quality" />
            <el-option label="一致性检查" value="consistency" />
          </el-select>
        </el-form-item>
        <el-form-item label="校验范围">
          <el-select v-model="checkForm.targetScope" style="width: 180px">
            <el-option label="全部" value="all" />
            <el-option label="按分类" value="category" />
            <el-option label="按标签" value="tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="相似度阈值" v-if="checkForm.validateType === 'duplicate'">
          <el-input-number v-model="checkForm.similarityThreshold" :min="0.5" :max="1" :step="0.05" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCheckDialog = false">取消</el-button>
        <el-button type="primary" @click="handleStartCheck">开始校验</el-button>
      </template>
    </el-dialog>
    <!-- 导入采编弹窗 -->
    <el-dialog v-model="showImportDialog" title="导入知识采编" width="600px">
      <el-form label-width="80px">
        <el-form-item label="采编JSON">
          <el-input v-model="importItems" type="textarea" :rows="8" placeholder='[{"title":"新增产品FAQ","content":"整理常见问题","editType":"create","tags":"FAQ"}]' />
        </el-form-item>
        <p style="font-size:12px;color:#909399;">JSON数组格式，每项含 title（必填）/content/editType(create/update/merge/split)/tags/editor 字段</p>
      </el-form>
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" @click="handleImportSubmit">导入</el-button>
      </template>
    </el-dialog>

    <!-- 工单新增/编辑弹窗 -->
    <el-dialog v-model="showTicketDialog" :title="editingTicketId ? '编辑工单' : '新增工单'" width="600px">
      <el-form label-width="90px">
        <el-form-item label="工单标题" required><el-input v-model="ticketForm.title" /></el-form-item>
        <el-form-item label="工单类型">
          <el-select v-model="ticketForm.ticketType" style="width: 160px">
            <el-option v-for="(label, key) in TICKET_TYPE" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="ticketForm.priority" style="width: 160px">
            <el-option v-for="(label, key) in PRIORITY" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联知识ID"><el-input v-model="ticketForm.knowledgeId" placeholder="可选，关联的知识条目ID" /></el-form-item>
        <el-form-item label="处理人"><el-input v-model="ticketForm.assignee" /></el-form-item>
        <el-form-item v-if="editingTicketId" label="状态">
          <el-select v-model="ticketForm.status" style="width: 160px">
            <el-option v-for="(label, key) in TICKET_STATUS" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题描述"><el-input v-model="ticketForm.description" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="ticketForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTicketDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTicket">保存</el-button>
      </template>
    </el-dialog>

    <!-- 工单查看弹窗（含流程环节时间轴：工单在流程中走过的步骤） -->
    <el-dialog v-model="showTicketViewDialog" title="工单详情" width="620px">
      <el-descriptions v-if="viewingTicket" :column="1" border>
        <el-descriptions-item label="工单标题">{{ viewingTicket.title }}</el-descriptions-item>
        <el-descriptions-item label="工单类型">{{ TICKET_TYPE[viewingTicket.ticketType] || viewingTicket.ticketType }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ PRIORITY[viewingTicket.priority] || viewingTicket.priority }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ TICKET_STATUS[viewingTicket.status] || viewingTicket.status }}</el-descriptions-item>
        <el-descriptions-item label="关联知识点">{{ viewingTicket.knowledgeTitle || viewingTicket.knowledgeId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="流程 / 当前环节">{{ viewingTicket.flowName || '-' }} / {{ viewingTicket.currentStep || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ viewingTicket.approver || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批意见">{{ viewingTicket.approvalComment || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报告人">{{ viewingTicket.reporter || '-' }}</el-descriptions-item>
        <el-descriptions-item label="问题描述">{{ viewingTicket.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewingTicket.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ viewingTicket.submittedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ viewingTicket.approvedAt || viewingTicket.resolvedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top: 16px">
        <div style="font-weight: 600; margin-bottom: 8px">流程环节</div>
        <el-timeline v-if="ticketLogs.length">
          <el-timeline-item v-for="log in ticketLogs" :key="log.id" :timestamp="log.createdAt"
            :type="(TICKET_ACTION_COLOR[log.action] || 'primary') as any">
            <span style="font-weight: 500">{{ log.stepName || '流程' }}</span>
            <el-tag size="small" style="margin-left: 8px" :type="(TICKET_ACTION_COLOR[log.action] || 'info') as any">
              {{ TICKET_ACTION[log.action] || log.action }}
            </el-tag>
            <span style="margin-left: 8px; color: #606266">{{ log.operator || '-' }}</span>
            <div v-if="log.comment" style="color: #909399; font-size: 12px; margin-top: 4px">{{ log.comment }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无流程环节记录" :image-size="50" />
      </div>
      <template #footer>
        <el-button v-if="viewingTicket && viewingTicket.status === 'pending_approval'" type="success"
          @click="showTicketViewDialog = false; handleApproveTicket(viewingTicket)">通过</el-button>
        <el-button v-if="viewingTicket && viewingTicket.status === 'pending_approval'" type="danger"
          @click="showTicketViewDialog = false; handleRejectTicket(viewingTicket)">驳回</el-button>
        <el-button type="primary" @click="showTicketViewDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 校验明细弹窗 -->
    <el-dialog v-model="showResultDialog" title="校验结果明细" width="680px">
      <template v-if="resultDetail">
        <el-alert v-if="resultDetail.issueCount" :title="`共发现 ${resultDetail.issueCount} 个问题`" type="warning" :closable="false" style="margin-bottom: 12px" />
        <el-alert v-else title="未发现问题" type="success" :closable="false" style="margin-bottom: 12px" />
        <el-table v-if="resultDetail.issues?.length" :data="resultDetail.issues" stripe size="small" max-height="400">
          <el-table-column prop="title" label="知识标题" show-overflow-tooltip />
          <el-table-column prop="reason" label="问题" show-overflow-tooltip min-width="220" />
        </el-table>
        <pre v-else-if="resultDetail.raw" style="background:#f5f7fa;padding:12px;border-radius:6px;font-size:13px;white-space:pre-wrap;">{{ resultDetail.raw }}</pre>
      </template>
      <template #footer>
        <el-button type="primary" @click="showResultDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 编辑多媒体弹窗 -->
    <el-dialog v-model="showMediaEditDialog" title="编辑多媒体" width="520px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="mediaForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="mediaForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="mediaForm.tags" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="mediaForm.status" style="width: 160px">
            <el-option label="已上传" value="uploaded" />
            <el-option label="处理中" value="processing" />
            <el-option label="已完成" value="completed" />
            <el-option label="失败" value="failed" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMediaEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="mediaSaving" @click="handleMediaSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 知识回收编辑弹窗 -->
    <el-dialog v-model="showRecycleEditDialog" title="编辑回收站条目" width="520px">
      <el-alert title="可在恢复前修正条目标题/分类/摘要，保存后条目仍在回收站" type="info" :closable="false" style="margin-bottom: 12px" />
      <el-form label-width="70px">
        <el-form-item label="标题" required><el-input v-model="recycleForm.title" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="recycleForm.category" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="recycleForm.summary" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRecycleEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="recycleSaving" @click="handleRecycleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
</style>
