<script setup lang="ts">
import RichTextEditor from '@/components/common/RichTextEditor.vue'
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const kbId = (route.params.id as string) || 'kb_sample'
const activeTab = ref('association')
const loading = ref(false)

// 搜索联想
const DIMENSION_OPTIONS = [
  { label: '内容', value: 'content' },
  { label: '主题词/别名', value: 'content' },
  { label: '搜索规则', value: 'rule' },
  { label: '附件', value: 'attachment' },
  { label: '发布时间', value: 'publishTime' },
  { label: '知识类型', value: 'knowledgeType' },
  { label: '检索词联想', value: 'termLink' },
] as const
const DIMENSION_LABEL_MAP: Record<string, string> = Object.fromEntries(DIMENSION_OPTIONS.map(d => [d.value, d.label]))
const assocList = ref<any[]>([])
const assocTotal = ref(0)
const assocPage = ref(1)
const assocPageSize = ref(5)
const assocQuery = ref({ dimension: '', keyword: '' })
async function loadAssoc() {
  loading.value = true
  try {
    const res: any = await api.getSearchAssociations(kbId, { ...assocQuery.value, page: assocPage.value, pageSize: assocPageSize.value })
    assocList.value = res?.list || []
    assocTotal.value = res?.total || assocList.value.length
  } finally { loading.value = false }
}
function handleAssocPageChange(page: number) { assocPage.value = page; loadAssoc() }
const showAssocDialog = ref(false)
const assocForm = ref({ id: '', dimension: 'content', name: '', description: '', pattern: '', suggestions: '', priority: 0 })
function handleAddAssoc() { assocForm.value = { id: '', dimension: 'content', name: '', description: '', pattern: '', suggestions: '', priority: 0 }; showAssocDialog.value = true }
function handleEditAssoc(row: any) { assocForm.value = { ...row }; showAssocDialog.value = true }
async function handleSaveAssoc() {
  if (!assocForm.value.name) { ElMessage.warning('请输入名称'); return }
  if (assocForm.value.id) await api.updateSearchAssociation(kbId, assocForm.value.id, assocForm.value)
  else await api.createSearchAssociation(kbId, assocForm.value)
  showAssocDialog.value = false; await loadAssoc(); ElMessage.success('保存成功')
}
async function handleDeleteAssoc(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' })
    await api.deleteSearchAssociation(kbId, row.id); await loadAssoc(); ElMessage.success('删除成功') } catch {}
}

// 联想效果验证
const testQuery = ref('')
const testDimension = ref('') // 空=全部维度（多条件组合），指定值=单维度验证
const testResult = ref<any>(null)
// 按维度分组，每个维度合并所有匹配规则的 suggestions
const groupedTestResult = computed(() => {
  if (!testResult.value?.associations?.length) return []
  const map = new Map<string, string[]>()
  for (const a of testResult.value.associations) {
    const dim = a.dimension || 'content'
    const list = map.get(dim) || []
    if (a.suggestions?.length) list.push(...a.suggestions)
    map.set(dim, list)
  }
  return Array.from(map.entries()).map(([dimension, suggestions]) => ({ dimension, suggestions }))
})
const DIMENSION_TAG_TYPES: Record<string, string> = { content: '', rule: 'warning', attachment: 'success', publishTime: 'info', knowledgeType: 'danger' }
function dimensionTagType(dim: string) { return DIMENSION_TAG_TYPES[dim] || '' }
async function handleTest() {
  if (!testQuery.value) { ElMessage.warning('请输入搜索词'); return }
  testResult.value = await api.searchAssociations(kbId, testQuery.value, testDimension.value || undefined)
}

// 判断（按维度校验联想规则）
const showJudgeDialog = ref(false)
const judgeForm = ref({ dimension: 'content', query: '', targetText: '' })
const judgeResult = ref<any>(null)
const judgeLoading = ref(false)
function handleShowJudge(row: any) {
  judgeForm.value = { dimension: row.dimension || 'content', query: '', targetText: row.name || '' }
  judgeResult.value = null
  showJudgeDialog.value = true
}
async function handleJudge() {
  if (!judgeForm.value.query) { ElMessage.warning('请输入测试查询内容'); return }
  judgeLoading.value = true
  try {
    judgeResult.value = await api.judgeSearchAssociation(kbId, judgeForm.value.dimension, judgeForm.value.query, judgeForm.value.targetText)
  } catch {
    ElMessage.error('判断请求失败')
  } finally {
    judgeLoading.value = false
  }
}

// 自动纠错
const correctionList = ref<any[]>([])
async function loadCorrections() { correctionList.value = ((await api.getAutoCorrections(kbId)) as any) || [] }
const showCorrectionDialog = ref(false)
const correctionForm = ref({ id: '', wrongText: '', correctText: '', matchType: 'exact', priority: 0 })
function handleAddCorrection() { correctionForm.value = { id: '', wrongText: '', correctText: '', matchType: 'exact', priority: 0 }; showCorrectionDialog.value = true }
function handleEditCorrection(row: any) { correctionForm.value = { ...row }; showCorrectionDialog.value = true }
async function handleSaveCorrection() {
  if (!correctionForm.value.wrongText || !correctionForm.value.correctText) { ElMessage.warning('请输入错误和正确文本'); return }
  if (correctionForm.value.id) await api.updateAutoCorrection(kbId, correctionForm.value.id, correctionForm.value)
  else await api.createAutoCorrection(kbId, correctionForm.value)
  showCorrectionDialog.value = false; await loadCorrections(); ElMessage.success('保存成功')
}
async function handleDeleteCorrection(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' })
    await api.deleteAutoCorrection(kbId, row.id); await loadCorrections(); ElMessage.success('删除成功') } catch {}
}

// ===========================================================================
// 知识更新提醒
// ===========================================================================
const updateLogs = ref<any[]>([])
const updateLogTotal = ref(0)
const updateLogPage = ref(1)
const updateLogPageSize = ref(20)
const updateRemindConfig = ref({
  enabled: false,
  notifyChannels: ['in_app'],
  checkInterval: 60,
})
const showRemindConfigDialog = ref(false)
const unreadUpdateCount = ref(0)

async function loadUpdateLogs() {
  try {
    const res: any = await api.getKnowledgeUpdateLogs(kbId, updateLogPage.value, updateLogPageSize.value)
    const list = Array.isArray(res) ? res : (res?.list || [])
    // 保持已有 read 状态，新数据默认为未读
    const readMap = new Map(updateLogs.value.filter(l => l.read).map(l => [l.id, true]))
    updateLogs.value = list.map((item: any) => ({ ...item, read: readMap.has(item.id) || false }))
    updateLogTotal.value = res?.total || list.length
  } catch {
    updateLogs.value = []
    updateLogTotal.value = 0
  }
}

async function loadUpdateRemindConfig() {
  try {
    const r: any = await api.getKbUpdateRemind(kbId)
    if (r) Object.assign(updateRemindConfig.value, r)
  } catch { /* ignore */ }
}

// 计算未读更新数（取最近7天且用户未确认的更新）
const unreadCount = computed(() => {
  return updateLogs.value.filter((log: any) => {
    if (!log.read) return true
    return false
  }).length
})

async function handleShowRemindConfig() {
  await loadUpdateRemindConfig()
  showRemindConfigDialog.value = true
}

async function handleSaveRemindConfig() {
  try {
    if (updateRemindConfig.value.enabled) {
      await api.saveUpdateRemind({ kbId, ...updateRemindConfig.value })
    } else {
      await api.saveUpdateRemind({ kbId, enabled: false })
    }
    ElMessage.success('更新提醒配置已保存')
    showRemindConfigDialog.value = false
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handleMarkAllRead() {
  try {
    // 批量标记所有未读更新日志为已读
    const unreadIds = updateLogs.value.filter(l => !l.read).map(l => l.id)
    for (const id of unreadIds) {
      await api.markUpdateLogRead(kbId, id)
    }
    await loadUpdateLogs()
    unreadUpdateCount.value = 0
    ElMessage.success('已全部标为已读')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleMarkRead(row: any) {
  if (row.read) return
  try {
    await api.markUpdateLogRead(kbId, row.id)
    row.read = true
    unreadUpdateCount.value = Math.max(0, unreadUpdateCount.value - 1)
  } catch { /* ignore */ }
}

function handleUpdatePageChange(page: number) {
  updateLogPage.value = page
  loadUpdateLogs()
}

onMounted(() => {
  loadAssoc()
  loadCorrections()
  loadUpdateLogs()
  loadUpdateRemindConfig()
  loadPrefs()
  loadPushes()
  loadStdQuestions()
  loadSimQuestions()
})

// ===== 检索偏好设置 =====
const prefList = ref<any[]>([])
const prefLoading = ref(false)
const showPrefDialog = ref(false)
const prefForm = ref({ name: '', searchMode: 'hybrid', topK: 10, similarityThreshold: 0.5, preferTags: '', enabled: true })
const editingPrefId = ref('')
function openAddPref() { editingPrefId.value = ''; prefForm.value = { name: '', searchMode: 'hybrid', topK: 10, similarityThreshold: 0.5, preferTags: '', enabled: true }; showPrefDialog.value = true }
function openEditPref(row: any) { editingPrefId.value = row.id; prefForm.value = { name: row.name, searchMode: row.searchMode || 'hybrid', topK: row.topK || 10, similarityThreshold: row.similarityThreshold ?? 0.5, preferTags: (row.preferTags || '').toString(), enabled: row.enabled !== 0 }; showPrefDialog.value = true }
async function loadPrefs() { prefLoading.value = true; try { prefList.value = ((await api.getSearchPreferences(kbId, undefined, true)) as any) || [] } catch { prefList.value = [] } finally { prefLoading.value = false } }
async function savePref() {
  if (!prefForm.value.name) { ElMessage.warning('请输入偏好名称'); return }
  const payload = { ...prefForm.value, preferTags: prefForm.value.preferTags ? prefForm.value.preferTags.split(/[,，]/).map(s => s.trim()).filter(Boolean) : [] }
  try {
    if (editingPrefId.value) { await api.updateSearchPreference(editingPrefId.value, payload); ElMessage.success('已更新') }
    else { await api.createSearchPreference(kbId, payload); ElMessage.success('已创建') }
    showPrefDialog.value = false; await loadPrefs()
  } catch { ElMessage.error('保存失败') }
}
async function deletePref(row: any) {
  try { await ElMessageBox.confirm(`确认删除检索偏好「${row.name}」？`, '删除确认', { type: 'warning' }); await api.deleteSearchPreference(row.id); await loadPrefs(); ElMessage.success('已删除') } catch {}
}

// ===== 知识推送 =====
const pushList = ref<any[]>([])
const pushLoading = ref(false)
const showPushDialog = ref(false)
const pushForm = ref({ title: '', content: '', knowledgeId: '', pushType: 'manual', targetUsers: '', status: 'draft' })
const editingPushId = ref('')
function openAddPush() { editingPushId.value = ''; pushForm.value = { title: '', content: '', knowledgeId: '', pushType: 'manual', targetUsers: '', status: 'draft' }; showPushDialog.value = true }
function openEditPush(row: any) { editingPushId.value = row.id; pushForm.value = { title: row.title, content: row.content || '', knowledgeId: row.knowledgeId || '', pushType: row.pushType || 'manual', targetUsers: (row.targetUsers || '').toString(), status: row.status || 'draft' }; showPushDialog.value = true }
async function loadPushes() { pushLoading.value = true; try { pushList.value = ((await api.getKnowledgePushes(kbId)) as any) || [] } catch { pushList.value = [] } finally { pushLoading.value = false } }
async function savePush() {
  if (!pushForm.value.title) { ElMessage.warning('请输入推送标题'); return }
  const payload = { ...pushForm.value }
  try {
    if (editingPushId.value) { await api.updateKnowledgePush(editingPushId.value, payload); ElMessage.success('已更新') }
    else { await api.createKnowledgePush(kbId, payload); ElMessage.success('已创建') }
    showPushDialog.value = false; await loadPushes()
  } catch { ElMessage.error('保存失败') }
}
async function deletePush(row: any) {
  try { await ElMessageBox.confirm(`确认删除知识推送「${row.title}」？`, '删除确认', { type: 'warning' }); await api.deleteKnowledgePush(row.id); await loadPushes(); ElMessage.success('已删除') } catch {}
}
async function sendPush(row: any) {
  try { await ElMessageBox.confirm(`确认发送知识推送「${row.title}」？`, '发送确认', { type: 'info' }); await api.sendKnowledgePush(row.id); ElMessage.success('已发送'); await loadPushes() } catch {}
}
function pushStatusColor(s: string) { return (({ sent: 'success', draft: 'info', failed: 'danger' } as Record<string, string>)[s] || 'info') as any }

// ===== 标准问法管理 =====
const stdList = ref<any[]>([])
const stdLoading = ref(false)
const showStdDialog = ref(false)
const stdForm = ref({ category: '', standardQuestion: '', answer: '', enabled: true })
const editingStdId = ref('')
function openAddStd() { editingStdId.value = ''; stdForm.value = { category: '', standardQuestion: '', answer: '', enabled: true }; showStdDialog.value = true }
function openEditStd(row: any) { editingStdId.value = row.id; stdForm.value = { category: row.category || '', standardQuestion: row.standardQuestion || '', answer: row.answer || '', enabled: row.enabled !== 0 }; showStdDialog.value = true }
async function loadStdQuestions() { stdLoading.value = true; try { stdList.value = ((await api.getStandardQuestions(kbId)) as any) || [] } catch { stdList.value = [] } finally { stdLoading.value = false } }
async function saveStdQuestion() {
  if (!stdForm.value.standardQuestion) { ElMessage.warning('请输入标准问法'); return }
  try {
    if (editingStdId.value) { await api.updateStandardQuestion(editingStdId.value, stdForm.value); ElMessage.success('已更新') }
    else { await api.createStandardQuestion(kbId, stdForm.value); ElMessage.success('已创建') }
    showStdDialog.value = false; await loadStdQuestions()
  } catch { ElMessage.error('保存失败') }
}
async function deleteStdQuestion(row: any) {
  try { await ElMessageBox.confirm(`确认删除标准问法「${row.standardQuestion}」？`, '删除确认', { type: 'warning' }); await api.deleteStandardQuestion(row.id); await loadStdQuestions(); ElMessage.success('已删除') } catch {}
}

// ===== 知识搜索（手动输入搜索 / 查看搜索结果 / 点击使用）=====
const ksQuery = ref('')
const ksMode = ref<'hybrid' | 'vector' | 'fulltext'>('hybrid')
const ksTopK = ref(5)
const ksThreshold = ref(0)
const ksLoading = ref(false)
const ksResults = ref<any[]>([])
const ksSearched = ref(false)
const ksUseVisible = ref(false)
const ksUseRow = ref<any>({})

async function handleKnowledgeSearch() {
  if (!ksQuery.value.trim()) {
    ElMessage.warning('请输入检索词')
    return
  }
  ksLoading.value = true
  try {
    const res: any = await api.searchRetrieval({
      knowledgeId: kbId,
      query: ksQuery.value,
      config: { mode: ksMode.value, topK: Number(ksTopK.value) || 5, similarityThreshold: Number(ksThreshold.value) || 0 },
    } as any)
    ksResults.value = Array.isArray(res) ? res : (res?.list || [])
    ksSearched.value = true
    if (!ksResults.value.length) ElMessage.info('未命中内容，可调整阈值或换关键词')
  } catch (e: any) {
    ksResults.value = []
    ksSearched.value = true
    ElMessage.error('搜索失败：' + (e?.message || ''))
  } finally {
    ksLoading.value = false
  }
}

// 点击使用：打开使用抽屉（可复制内容 / 记录一次使用）
const ksUsedLog = ref<string[]>([])
function handleUse(row: any) {
  ksUseRow.value = row
  ksUseVisible.value = true
}
async function copyUsedContent() {
  const text = String(ksUseRow.value?.content || '')
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // 剪贴板不可用时降级为选中提示
  }
  ksUsedLog.value.unshift(`${new Date().toLocaleTimeString('zh-CN')} 已使用：${(text || '').slice(0, 20)}…`)
  ElMessage.success('已复制内容，可粘贴使用')
}

/** 命中片段的 <mark> 高亮渲染 */
function renderHighlight(content: string, highlights?: string[]) {
  let html = String(content || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  for (const h of (highlights || [])) {
    if (!h) continue
    html = html.split(h).join(`<mark>${h}</mark>`)
  }
  return html
}

// ===== 标准回复管理（查看 / 编辑 / 富文本编辑，复用标准问法的 answer 字段）=====
const replyKeyword = ref('')
const filteredReplies = computed(() =>
  replyKeyword.value
    ? stdList.value.filter(
        (r: any) =>
          (r.standardQuestion || '').includes(replyKeyword.value) ||
          (r.answer || '').includes(replyKeyword.value),
      )
    : stdList.value,
)
/** 是否含 HTML 标签 → 判定为富文本回复 */
function hasRichText(html?: string) {
  return !!html && /<\/?[a-z][\s\S]*>/i.test(html)
}
// 查看标准回复
const showReplyView = ref(false)
const replyView = ref<any>({})
function openViewReply(row: any) {
  replyView.value = row
  showReplyView.value = true
}
// 编辑标准回复（富文本）
const showReplyDialog = ref(false)
const replyForm = ref<any>({ id: '', standardQuestion: '', category: '', answer: '', enabled: true })
const replySaving = ref(false)
function openEditReply(row: any) {
  replyForm.value = {
    id: row.id,
    standardQuestion: row.standardQuestion || '',
    category: row.category || '',
    answer: row.answer || '',
    enabled: row.enabled !== 0,
  }
  showReplyDialog.value = true
}
async function saveReply() {
  if (!replyForm.value.answer) {
    ElMessage.warning('请填写标准回复内容')
    return
  }
  replySaving.value = true
  try {
    await api.updateStandardQuestion(replyForm.value.id, {
      standardQuestion: replyForm.value.standardQuestion,
      category: replyForm.value.category,
      answer: replyForm.value.answer,
      enabled: replyForm.value.enabled ? 1 : 0,
    })
    ElMessage.success('标准回复已保存')
    showReplyDialog.value = false
    await loadStdQuestions()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || ''))
  } finally {
    replySaving.value = false
  }
}

// ===== 相似问法管理 =====
const simList = ref<any[]>([])
const simLoading = ref(false)
const showSimDialog = ref(false)
const simForm = ref({ standardQuestionId: '', question: '', similarity: 0.8, enabled: true })
const editingSimId = ref('')
function openAddSim(stdId?: string) { editingSimId.value = ''; simForm.value = { standardQuestionId: stdId || '', question: '', similarity: 0.8, enabled: true }; showSimDialog.value = true }
function openEditSim(row: any) { editingSimId.value = row.id; simForm.value = { standardQuestionId: row.standardQuestionId || '', question: row.question || '', similarity: row.similarity ?? 0.8, enabled: row.enabled !== 0 }; showSimDialog.value = true }
async function loadSimQuestions(standardQuestionId?: string) { simLoading.value = true; try { simList.value = ((await api.getSimilarQuestions(kbId, standardQuestionId)) as any) || [] } catch { simList.value = [] } finally { simLoading.value = false } }
async function saveSimQuestion() {
  if (!simForm.value.question) { ElMessage.warning('请输入相似问法'); return }
  if (!simForm.value.standardQuestionId) { ElMessage.warning('请选择关联标准问法'); return }
  try {
    if (editingSimId.value) { await api.updateSimilarQuestion(editingSimId.value, simForm.value); ElMessage.success('已更新') }
    else { await api.createSimilarQuestion(kbId, simForm.value); ElMessage.success('已创建') }
    showSimDialog.value = false; await loadSimQuestions()
  } catch { ElMessage.error('保存失败') }
}
async function deleteSimQuestion(row: any) {
  try { await ElMessageBox.confirm(`确认删除相似问法？`, '删除确认', { type: 'warning' }); await api.deleteSimilarQuestion(row.id); await loadSimQuestions(); ElMessage.success('已删除') } catch {}
}
// 推荐相似问法
const recommendLoading = ref(false)
const recommendResult = ref<any[]>([])
const recommendKeyword = ref('')
async function handleRecommend(stdId: string) {
  if (!recommendKeyword.value.trim()) { ElMessage.warning('请输入关键词'); return }
  recommendLoading.value = true
  try { recommendResult.value = ((await api.recommendSimilarQuestions(kbId, stdId, recommendKeyword.value, 10)) as any) || [] } catch { recommendResult.value = [] } finally { recommendLoading.value = false }
}
function useRecommend(row: any) {
  simForm.value = { standardQuestionId: row.standardQuestionId || '', question: row.question, similarity: row.similarity ?? 0.8, enabled: true }
  showSimDialog.value = true
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="搜索联想" name="association">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">查询联想规则</div><el-button type="primary" @click="handleAddAssoc">新增联想</el-button></div>
          <div class="filter-bar">
            <el-select v-model="assocQuery.dimension" placeholder="维度" clearable style="width:140px" @change="loadAssoc">
              <el-option v-for="d in DIMENSION_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
            <el-input v-model="assocQuery.keyword" placeholder="名称搜索" clearable style="width:180px" @keyup.enter="loadAssoc" />
            <el-button type="primary" @click="loadAssoc">查询</el-button>
          </div>
          <el-table :data="assocList" stripe>
            <el-table-column prop="name" label="规则名称" show-overflow-tooltip />
            <el-table-column prop="dimension" label="维度" width="110"><template #default="{ row }">{{ DIMENSION_LABEL_MAP[row.dimension] || row.dimension }}</template></el-table-column>
            <el-table-column prop="pattern" label="匹配模式" show-overflow-tooltip />
            <el-table-column prop="priority" label="优先级" width="80" />
            <el-table-column prop="enabled" label="启用" width="70"><template #default="{ row }"><el-tag :type="row.enabled?'success':'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="180"><template #default="{ row }"><el-button link type="primary" size="small" @click="handleEditAssoc(row)">编辑</el-button><el-button link type="primary" size="small" @click="handleShowJudge(row)">判断</el-button><el-button link type="danger" size="small" @click="handleDeleteAssoc(row)">删除</el-button></template></el-table-column>
          </el-table>
          <div class="table-footer" v-if="assocTotal > assocPageSize">
            <el-pagination
              v-model:current-page="assocPage"
              v-model:page-size="assocPageSize"
              :total="assocTotal"
              :page-sizes="[5, 10, 20]"
              layout="total, sizes, prev, pager, next"
              @current-change="handleAssocPageChange"
              @size-change="handleAssocPageChange"
            />
          </div>
        </div>
        <div class="card-panel" style="margin-top:16px">
          <div class="section-title">联想效果验证</div>
          <div class="filter-bar">
            <el-input v-model="testQuery" placeholder="模拟用户输入的搜索词，如：宽带、专线、云电脑、2026" style="width:300px" @keyup.enter="handleTest" />
            <el-select v-model="testDimension" placeholder="验证维度" clearable style="width:140px">
              <el-option label="全部维度" value="" />
              <el-option v-for="d in DIMENSION_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
            <el-button type="primary" @click="handleTest">验证联想效果</el-button>
          </div>
          <el-empty v-if="testQuery && testResult && (!testResult.associations?.length) && (!testResult.corrections?.length)" description="未匹配到联想规则" :image-size="60" />
          <div v-if="testResult && (testResult.associations?.length || testResult.corrections?.length)" class="result-box">
            <!-- 纠错提示 -->
            <div v-if="testResult.corrections?.length" style="margin-bottom:12px;padding:8px 12px;background:#fdf6ec;border-radius:6px;border:1px solid #faecd8">
              <span style="font-size:13px;color:#e6a23c">💡 是否要搜索：</span>
              <span v-for="(c,i) in testResult.corrections" :key="'c'+i" style="margin-right:12px;font-weight:600;color:#e6a23c">{{ c.corrected }}</span>
              <span style="font-size:12px;color:#909399">（原输入：{{ testResult.corrections.map((c: any) => c.original).join('、') }}）</span>
            </div>
            <!-- 按维度分组展示联想建议 -->
            <div v-for="group in groupedTestResult" :key="group.dimension" style="margin-bottom:12px">
              <div style="font-size:13px;color:#606266;margin-bottom:6px">
                <el-tag size="small" :type="(dimensionTagType(group.dimension) as any)">{{ DIMENSION_LABEL_MAP[group.dimension] || group.dimension }}</el-tag>
                <span style="margin-left:8px">为您推荐以下内容：</span>
              </div>
              <div style="display:flex;flex-wrap:wrap;gap:6px">
                <el-tag v-for="(s, si) in group.suggestions" :key="si" effect="plain" style="max-width:300px" show-overflow-tooltip>{{ s }}</el-tag>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="自动纠错" name="correction">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">纠错规则管理</div><el-button type="primary" @click="handleAddCorrection">新增规则</el-button></div>
          <el-table :data="correctionList" stripe>
            <el-table-column prop="wrongText" label="错误文本" width="160" />
            <el-table-column prop="correctText" label="正确文本" width="160" />
            <el-table-column prop="matchType" label="匹配类型" width="100" />
            <el-table-column prop="priority" label="优先级" width="80" />
            <el-table-column prop="hitCount" label="命中次数" width="90" />
            <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" size="small" @click="handleEditCorrection(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDeleteCorrection(row)">删除</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 知识更新提醒 -->
      <el-tab-pane name="update-remind">
        <template #label>
          <span>
            更新提醒
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" type="danger" style="margin-left:4px" />
          </span>
        </template>
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">
              知识更新日志
              <el-tag v-if="updateRemindConfig.enabled" type="success" size="small" style="margin-left:8px">提醒已开启</el-tag>
              <el-tag v-else type="info" size="small" style="margin-left:8px">提醒已关闭</el-tag>
            </div>
            <div style="display:flex;gap:8px">
              <el-button size="small" @click="handleShowRemindConfig">更新提醒设置</el-button>
              <el-button size="small" @click="handleMarkAllRead" :disabled="unreadCount === 0">全部标为已读</el-button>
              <el-button size="small" @click="loadUpdateLogs">刷新</el-button>
            </div>
          </div>
          <el-table :data="updateLogs" stripe size="small" @row-click="handleMarkRead">
            <el-table-column label="状态" width="60">
              <template #default="{ row }">
                <el-badge v-if="!row.read" is-dot type="danger" />
              </template>
            </el-table-column>
            <el-table-column prop="updateType" label="更新类型" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="row.updateType === 'file_added' ? 'success' : row.updateType === 'file_removed' ? 'danger' : 'warning'">
                  {{ { file_added: '新增文件', file_removed: '删除文件', file_updated: '更新文件', chunk_added: '新增切片', chunk_removed: '删除切片', chunk_updated: '更新切片', config_changed: '配置变更' }[row.updateType as string] || row.updateType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="target" label="更新目标" min-width="200" show-overflow-tooltip />
            <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="timestamp" label="更新时间" width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.timestamp || row.createdAt || '-' }}</template>
            </el-table-column>
          </el-table>
          <div class="table-footer" v-if="updateLogTotal > updateLogPageSize">
            <el-pagination
              v-model:current-page="updateLogPage"
              v-model:page-size="updateLogPageSize"
              :total="updateLogTotal"
              layout="total, prev, pager, next"
              @current-change="handleUpdatePageChange"
            />
          </div>
          <el-empty v-if="!updateLogs.length" description="暂无更新记录" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- 检索偏好设置 -->
      <el-tab-pane label="检索偏好设置" name="preference">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">检索偏好设置</div><el-button type="primary" @click="openAddPref">新增偏好</el-button></div>
          <el-table :data="prefList" stripe size="small" v-loading="prefLoading">
            <el-table-column prop="name" label="偏好名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="检索模式" width="110"><template #default="{row}">{{ ({hybrid:'混合检索',vector:'向量检索',keyword:'关键词检索'} as Record<string,string>)[row.searchMode] || row.searchMode }}</template></el-table-column>
            <el-table-column prop="topK" label="返回条数" width="90" align="center" />
            <el-table-column label="相似度阈值" width="110" align="center"><template #default="{row}">{{ (row.similarityThreshold * 100).toFixed(0) }}%</template></el-table-column>
            <el-table-column label="启用" width="70" align="center"><template #default="{row}"><el-tag :type="row.enabled===1||row.enabled===true?'success':'info'" size="small">{{ row.enabled===1||row.enabled===true?'是':'否' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditPref(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="deletePref(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!prefList.length && !prefLoading" description="暂无检索偏好，点击「新增偏好」创建" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- 知识推送 -->
      <el-tab-pane label="知识推送" name="push">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">知识推送</div><div><el-button type="primary" @click="openAddPush">新增推送</el-button></div></div>
          <el-table :data="pushList" stripe size="small" v-loading="pushLoading">
            <el-table-column prop="title" label="推送标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
            <el-table-column label="类型" width="90"><template #default="{row}">{{ row.pushType === 'auto' ? '自动' : '手动' }}</template></el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag :type="pushStatusColor(row.status) as any" size="small">{{ ({draft:'草稿',sent:'已发送',failed:'失败'} as Record<string,string>)[row.status] || row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditPush(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="deletePush(row)">删除</el-button>
                <el-button v-if="row.status === 'draft'" link type="success" size="small" @click="sendPush(row)">发送</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!pushList.length && !pushLoading" description="暂无知识推送，点击「新增推送」创建" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- 标准问法管理 -->
      <el-tab-pane label="标准问法管理" name="standard-question">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">标准问法</div>
            <div style="display:flex;gap:8px">
              <el-button size="small" type="primary" @click="openAddStd">新增标准问法</el-button>
              <el-button size="small" @click="loadStdQuestions">刷新</el-button>
            </div>
          </div>
          <el-table :data="stdList" stripe size="small" v-loading="stdLoading">
            <el-table-column prop="category" label="分类" width="120" />
            <el-table-column prop="standardQuestion" label="标准问法" show-overflow-tooltip />
            <el-table-column prop="answer" label="答案" show-overflow-tooltip>
              <template #default="{ row }"><span v-html="row.answer"></span></template>
            </el-table-column>
            <el-table-column prop="hitCount" label="命中次数" width="100" />
            <el-table-column prop="enabled" label="启用" width="70">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditStd(row)">编辑</el-button>
                <el-button link type="primary" size="small" @click="loadSimQuestions(row.id)">查看相似问法</el-button>
                <el-button link type="primary" size="small" @click="handleRecommend(row.id)">推荐相似</el-button>
                <el-button link type="danger" size="small" @click="deleteStdQuestion(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!stdList.length && !stdLoading" description="暂无标准问法，点击「新增标准问法」创建" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- 知识搜索：手动输入搜索 / 查看搜索结果 / 点击使用 -->
      <el-tab-pane label="知识搜索" name="knowledge-search">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识搜索（手动输入 → 查看结果 → 点击使用）</div>
          </div>
          <div class="filter-bar">
            <el-input
              v-model="ksQuery"
              placeholder="手动输入检索词，如：宽带办理条件 / 退款流程 / LOS 红灯"
              clearable
              style="width: 380px"
              @keyup.enter="handleKnowledgeSearch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="ksMode" style="width: 140px">
              <el-option label="混合检索" value="hybrid" />
              <el-option label="语义（向量）" value="vector" />
              <el-option label="关键词（词法）" value="fulltext" />
            </el-select>
            <el-input-number v-model="ksTopK" :min="1" :max="20" size="default" />
            <el-input-number v-model="ksThreshold" :min="0" :max="1" :step="0.05" :precision="2" />
            <el-button type="primary" :loading="ksLoading" @click="handleKnowledgeSearch">搜索</el-button>
            <el-button @click="ksQuery = ''; ksResults = []; ksSearched = false">清空</el-button>
          </div>

          <div v-if="ksSearched" style="margin: 8px 0; color: var(--el-text-color-secondary); font-size: 13px">
            共命中 {{ ksResults.length }} 条（模式 {{ ksMode }}，TopK {{ ksTopK }}，阈值 {{ ksThreshold }}）
          </div>

          <div v-if="ksResults.length" class="ks-results">
            <div v-for="(row, i) in ksResults" :key="i" class="ks-result">
              <div class="ks-result__head">
                <el-tag size="small" type="info">#{{ i + 1 }}</el-tag>
                <el-tag size="small" :type="row.source === 'hybrid' ? 'success' : 'warning'">
                  {{ row.source === 'hybrid' ? '混合命中' : row.source === 'vector' ? '语义命中' : '词法命中' }}
                </el-tag>
                <span class="ks-result__sim">相似度 {{ row.similarity ?? '-' }}</span>
                <span v-if="row.fileId" class="ks-result__meta">来源文件 {{ row.fileId }} · 分片 #{{ row.chunkIndex }}</span>
                <el-tag v-if="row.fallback" size="small" type="warning">为您推荐</el-tag>
                <div style="margin-left: auto; display: flex; gap: 8px">
                  <el-button link type="primary" size="small" @click="handleUse(row)">点击使用</el-button>
                </div>
              </div>
              <div class="ks-result__content" v-html="renderHighlight(row.content, row.highlights)" />
            </div>
          </div>
          <el-empty
            v-else-if="ksSearched && !ksLoading"
            description="未命中内容：可降低相似度阈值、切换检索模式或更换检索词"
            :image-size="60"
          />
        </div>

        <!-- 点击使用抽屉 -->
        <el-dialog v-model="ksUseVisible" title="使用该知识" width="640px">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="相似度">{{ ksUseRow.similarity ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="命中方式">{{ ksUseRow.source || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源文件">{{ ksUseRow.fileId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分片序号">{{ ksUseRow.chunkIndex ?? '-' }}</el-descriptions-item>
          </el-descriptions>
          <div style="margin: 12px 0 6px; font-weight: 600; font-size: 13px">知识内容</div>
          <div class="ks-use-content" v-html="renderHighlight(ksUseRow.content, ksUseRow.highlights)" />
          <template #footer>
            <el-button @click="ksUseVisible = false">关闭</el-button>
            <el-button type="primary" @click="copyUsedContent">复制内容并使用</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- 标准回复管理 -->
      <el-tab-pane label="标准回复管理" name="standard-reply">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">标准回复（富文本）</div>
            <div style="display:flex;gap:8px">
              <el-input v-model="replyKeyword" placeholder="按标准问法/回复内容查询" clearable size="small" style="width:220px" />
              <el-button size="small" @click="loadStdQuestions">刷新</el-button>
            </div>
          </div>
          <el-table :data="filteredReplies" stripe size="small" v-loading="stdLoading">
            <el-table-column prop="standardQuestion" label="标准问法" min-width="180" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column label="标准回复（预览）" min-width="280">
              <template #default="{ row }">
                <span v-if="row.answer" v-html="row.answer" class="reply-preview" />
                <span v-else style="color:#909399">（未配置回复）</span>
              </template>
            </el-table-column>
            <el-table-column label="富文本" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="hasRichText(row.answer) ? 'success' : 'info'">
                  {{ hasRichText(row.answer) ? 'HTML' : row.answer ? '纯文本' : '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="hitCount" label="命中次数" width="90" align="center" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openViewReply(row)">查看</el-button>
                <el-button link type="primary" size="small" @click="openEditReply(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!filteredReplies.length && !stdLoading" description="暂无标准回复，请先在「标准问法管理」新增标准问法" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- 相似问法管理 -->
      <el-tab-pane label="相似问法管理" name="similar-question">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">相似问法</div>
            <div style="display:flex;gap:8px">
              <el-button size="small" type="primary" @click="openAddSim()">新增相似问法</el-button>
              <el-button size="small" @click="loadSimQuestions()">刷新</el-button>
            </div>
          </div>
          <div style="margin-bottom:12px">
            <span style="font-size:13px;color:#606266;margin-right:8px">关键词推荐：</span>
            <el-input v-model="recommendKeyword" placeholder="输入关键词推荐相似问法" style="width:220px" size="small" />
            <el-button size="small" type="primary" @click="handleRecommend(stdList[0]?.id)" :disabled="!stdList.length" :loading="recommendLoading">推荐</el-button>
          </div>
          <el-table :data="simList" stripe size="small" v-loading="simLoading">
            <el-table-column prop="question" label="相似问法" show-overflow-tooltip />
            <el-table-column prop="similarity" label="相似度" width="100">
              <template #default="{ row }">
                <el-progress type="circle" :percentage="Math.round((row.similarity || 0) * 100)" :width="36" />
              </template>
            </el-table-column>
            <el-table-column prop="hitCount" label="命中次数" width="100" />
            <el-table-column prop="enabled" label="启用" width="70">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditSim(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="deleteSimQuestion(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="recommendResult.length" style="margin-top:16px">
            <div class="section-title" style="margin-bottom:8px">推荐结果</div>
            <el-table :data="recommendResult" stripe size="small">
              <el-table-column prop="question" label="相似问法" show-overflow-tooltip />
              <el-table-column prop="similarity" label="相似度" width="100">
                <template #default="{ row }">
                  <el-progress type="circle" :percentage="Math.round((row.similarity || 0) * 100)" :width="36" />
                </template>
              </el-table-column>
              <el-table-column prop="hitCount" label="命中次数" width="100" />
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="useRecommend(row)">采用</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <el-empty v-if="!simList.length && !simLoading && !recommendResult.length" description="暂无相似问法，点击「新增相似问法」创建" :image-size="60" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 标准问法 新增/编辑 -->
    <el-dialog v-model="showStdDialog" :title="editingStdId ? '编辑标准问法' : '新增标准问法'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="分类"><el-input v-model="stdForm.category" placeholder="如：售后、账单" /></el-form-item>
        <el-form-item label="标准问法" required><el-input v-model="stdForm.standardQuestion" placeholder="用户最可能问的问题" /></el-form-item>
        <el-form-item label="答案" required><RichTextEditor v-model="stdForm.answer" placeholder="标准答案（支持富文本）" :min-height="110" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="stdForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showStdDialog=false">取消</el-button><el-button type="primary" @click="saveStdQuestion">保存</el-button></template>
    </el-dialog>

    <!-- 相似问法 新增/编辑 -->
    <el-dialog v-model="showSimDialog" :title="editingSimId ? '编辑相似问法' : '新增相似问法'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="关联标准问法" required>
          <el-select v-model="simForm.standardQuestionId" placeholder="选择标准问法" style="width:360px" filterable>
            <el-option v-for="item in stdList" :key="item.id" :label="item.standardQuestion" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="相似问法" required><el-input v-model="simForm.question" placeholder="与标准问法意思相近的问法" /></el-form-item>
        <el-form-item label="相似度"><el-input-number v-model="simForm.similarity" :min="0" :max="1" :step="0.05" style="width:160px" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="simForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showSimDialog=false">取消</el-button><el-button type="primary" @click="saveSimQuestion">保存</el-button></template>
    </el-dialog>

    <!-- 检索偏好 新增/编辑 -->
    <el-dialog v-model="showPrefDialog" :title="editingPrefId ? '编辑检索偏好' : '新增检索偏好'" width="520px">
      <el-form label-width="110px">
        <el-form-item label="偏好名称" required><el-input v-model="prefForm.name" placeholder="如：默认检索偏好" /></el-form-item>
        <el-form-item label="检索模式">
          <el-select v-model="prefForm.searchMode" style="width:180px"><el-option label="混合检索" value="hybrid" /><el-option label="向量检索" value="vector" /><el-option label="关键词检索" value="keyword" /></el-select>
        </el-form-item>
        <el-form-item label="返回条数"><el-input-number v-model="prefForm.topK" :min="1" :max="100" style="width:160px" /></el-form-item>
        <el-form-item label="相似度阈值"><el-input-number v-model="prefForm.similarityThreshold" :min="0" :max="1" :step="0.05" style="width:160px" /></el-form-item>
        <el-form-item label="偏好标签"><el-input v-model="prefForm.preferTags" placeholder="多个用逗号分隔" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="prefForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showPrefDialog=false">取消</el-button><el-button type="primary" @click="savePref">保存</el-button></template>
    </el-dialog>

    <!-- 知识推送 新增/编辑 -->
    <el-dialog v-model="showPushDialog" :title="editingPushId ? '编辑知识推送' : '新增知识推送'" width="520px">
      <el-form label-width="110px">
        <el-form-item label="推送标题" required><el-input v-model="pushForm.title" placeholder="如：知识库每周更新摘要" /></el-form-item>
        <el-form-item label="推送内容"><el-input v-model="pushForm.content" type="textarea" :rows="3" placeholder="推送正文" /></el-form-item>
        <el-form-item label="关联知识ID"><el-input v-model="pushForm.knowledgeId" placeholder="可选" /></el-form-item>
        <el-form-item label="推送类型">
          <el-select v-model="pushForm.pushType" style="width:160px"><el-option label="手动" value="manual" /><el-option label="自动" value="auto" /></el-select>
        </el-form-item>
        <el-form-item label="目标用户"><el-input v-model="pushForm.targetUsers" placeholder="多个用户名用逗号分隔；留空则推送给全员" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="pushForm.status" style="width:160px"><el-option label="草稿" value="draft" /><el-option label="已发送" value="sent" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="showPushDialog=false">取消</el-button><el-button type="primary" @click="savePush">保存</el-button></template>
    </el-dialog>

    <!-- 搜索联想规则对话框 -->
    <el-dialog v-model="showAssocDialog" :title="assocForm.id ? '编辑联想规则' : '新增联想规则'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="assocForm.name" /></el-form-item>
        <el-form-item label="维度">
          <el-select v-model="assocForm.dimension" style="width:180px"><el-option v-for="d in DIMENSION_OPTIONS" :key="d.value" :label="d.label" :value="d.value" /></el-select>
        </el-form-item>
        <el-form-item label="匹配模式"><el-input v-model="assocForm.pattern" placeholder="正则表达式，如：退款|退货" /></el-form-item>
        <el-form-item label="联想建议"><el-input v-model="assocForm.suggestions" type="textarea" :rows="3" placeholder='JSON数组' /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="assocForm.priority" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showAssocDialog=false">取消</el-button><el-button type="primary" @click="handleSaveAssoc">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="showCorrectionDialog" :title="correctionForm.id?'编辑纠错':'新增纠错'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="错误文本" required><el-input v-model="correctionForm.wrongText" /></el-form-item>
        <el-form-item label="正确文本" required><el-input v-model="correctionForm.correctText" /></el-form-item>
        <el-form-item label="匹配类型"><el-select v-model="correctionForm.matchType" style="width:140px"><el-option label="精确" value="exact" /><el-option label="模糊" value="fuzzy" /><el-option label="正则" value="regex" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="correctionForm.priority" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCorrectionDialog=false">取消</el-button><el-button type="primary" @click="handleSaveCorrection">保存</el-button></template>
    </el-dialog>

    <!-- 判断对话框 -->
    <el-dialog v-model="showJudgeDialog" title="搜索联想判断" width="500px">
      <el-form label-width="110px">
        <el-form-item label="判断维度">
          <el-select v-model="judgeForm.dimension" style="width:180px">
            <el-option v-for="d in DIMENSION_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="测试查询" required>
          <el-input v-model="judgeForm.query" placeholder="输入用户查询内容" />
        </el-form-item>
        <el-form-item label="目标文本">
          <el-input v-model="judgeForm.targetText" placeholder="可选，预期匹配的规则名称" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleJudge" :loading="judgeLoading">执行判断</el-button>
        </el-form-item>
      </el-form>
      <div v-if="judgeResult" class="judge-result">
        <p><b>判断结果：</b>
          <el-tag :type="judgeResult.matched ? 'success' : 'info'" size="small">
            {{ judgeResult.matched ? '匹配' : '不匹配' }}
          </el-tag>
        </p>
        <p><b>置信度：</b>{{ (judgeResult.confidence * 100).toFixed(0) }}%</p>
        <p v-if="judgeResult.matchedRule"><b>匹配规则：</b>{{ judgeResult.matchedRule.name }}（{{ judgeResult.matchedRule.dimension }}）</p>
      </div>
    </el-dialog>

    <!-- 更新提醒设置对话框 -->
    <el-dialog v-model="showRemindConfigDialog" title="知识更新提醒设置" width="480px">
      <el-form label-width="120px">
        <el-form-item label="启用更新提醒">
          <el-switch v-model="updateRemindConfig.enabled" />
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-checkbox-group v-model="updateRemindConfig.notifyChannels">
            <el-checkbox label="in_app">应用内通知</el-checkbox>
            <el-checkbox label="email">邮件通知</el-checkbox>
            <el-checkbox label="sms">短信通知</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="检查间隔(分钟)">
          <el-input-number v-model="updateRemindConfig.checkInterval" :min="10" :max="1440" :step="10" style="width:160px" />
          <div style="font-size:12px;color:#909399;margin-top:4px">最短10分钟，最长1440分钟（24小时）</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSaveRemindConfig">保存设置</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 查看标准回复 -->
    <el-dialog v-model="showReplyView" title="查看标准回复" width="600px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="标准问法">{{ replyView.standardQuestion }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ replyView.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="命中次数">{{ replyView.hitCount ?? 0 }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:12px;font-weight:600;font-size:13px">回复内容（富文本渲染）</div>
      <div class="reply-view" v-html="replyView.answer || '<span style=\'color:#909399\'>（未配置回复）</span>'" />
      <div style="margin-top:12px;font-weight:600;font-size:13px">HTML 源码</div>
      <el-input type="textarea" :rows="4" :model-value="replyView.answer || ''" readonly />
      <template #footer>
        <el-button @click="showReplyView = false">关闭</el-button>
        <el-button type="primary" @click="showReplyView = false; openEditReply(replyView)">编辑回复</el-button>
      </template>
    </el-dialog>

    <!-- 编辑标准回复（富文本编辑器） -->
    <el-dialog v-model="showReplyDialog" title="编辑标准回复" width="640px">
      <el-form label-width="90px">
        <el-form-item label="标准问法">
          <el-input v-model="replyForm.standardQuestion" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="replyForm.category" placeholder="如：常见问题" />
        </el-form-item>
        <el-form-item label="标准回复" required>
          <RichTextEditor v-model="replyForm.answer" placeholder="标准回复（支持富文本：加粗/列表/链接等）" :min-height="140" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="replyForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReplyDialog = false">取消</el-button>
        <el-button type="primary" :loading="replySaving" @click="saveReply">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.result-box { margin-top: $spacing-base; padding: $spacing-base; background: $bg-white; border-radius: $radius-base; p { margin: 4px 0; } }
.judge-result { margin-top: $spacing-base; padding: $spacing-base; background: #fafafa; border-radius: $radius-base; border: 1px solid $border-lighter; p { margin: 6px 0; } }
.reply-preview { display: inline-block; max-height: 44px; overflow: hidden; vertical-align: middle; p { display: inline; margin: 0; } }
.reply-view { margin-top: 6px; padding: 12px; background: $bg-white; border: 1px solid $border-lighter; border-radius: $radius-base; min-height: 60px; line-height: 1.7; }
.ks-results { display: flex; flex-direction: column; gap: 10px; }
.ks-result { border: 1px solid $border-lighter; border-radius: $radius-base; padding: 10px 12px; background: $bg-white;
  &__head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 12px; }
  &__sim { color: #409EFF; font-weight: 600; }
  &__meta { color: #909399; }
  &__content { margin-top: 8px; font-size: 13px; line-height: 1.7; color: $text-primary; max-height: 120px; overflow: hidden; }
}
.ks-use-content { padding: 12px; background: $bg-white; border: 1px solid $border-lighter; border-radius: $radius-base; max-height: 260px; overflow: auto; line-height: 1.8; font-size: 13px; }
.ks-result__content :deep(mark), .ks-use-content :deep(mark) { background: #FFF3C4; color: #C45656; padding: 0 2px; border-radius: 2px; }
</style>
