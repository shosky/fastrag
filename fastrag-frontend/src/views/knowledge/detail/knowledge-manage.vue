<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Picture, Download, Delete, View } from '@element-plus/icons-vue'
import * as api from '@/api'
import { storage } from '@/utils/storage'

const route = useRoute()
const kbId = (route.params.id as string) || 'kb_sample'

/**
 * 带 JWT 鉴权的文件下载。
 * 后端 /api/** 走 authenticated()，浏览器 <a>.click() 不带 Authorization，
 * 会导致下载失败（"无法从该网站下载"）。
 * 这里用 fetch 取回字节流并包装为 Blob URL，再触发下载。
 */
async function downloadWithAuth(rawUrl: string, filename: string) {
  const token = storage.get<string>('token') || ''
  const absUrl = rawUrl.startsWith('http')
    ? rawUrl
    : `${window.location.origin}${rawUrl.startsWith('/') ? '' : '/'}${rawUrl}`
  const resp = await fetch(absUrl, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!resp.ok) throw new Error(`下载失败 HTTP ${resp.status}`)
  const blob = await resp.blob()
  const blobUrl = URL.createObjectURL(blob)
  try {
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  } finally {
    // 给浏览器一点时间真正发起下载再回收
    setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
  }
}
const activeTab = ref('knowledge')
const loading = ref(false)

// 知识管理
const knowledgeList = ref<any[]>([])
const knowledgeQuery = ref({ keyword: '', category: '' })
async function loadKnowledge() {
  loading.value = true
  try { knowledgeList.value = ((await api.getKnowledgeList(kbId, knowledgeQuery.value)) as any) || [] } finally { loading.value = false }
}
const showKnowledgeDialog = ref(false)
const knowledgeForm = ref({ id: '', title: '', content: '', summary: '', category: '', tags: '', status: 'draft' })
// ===== 查看详情 + 关联知识推荐 =====
const showDetailDrawer = ref(false)
const detailEntry = ref<any>(null)
const detailRelated = ref<any[]>([])
const detailRelatedQa = ref<any[]>([])
const detailLoading = ref(false)
async function handleViewDetail(row: any) {
  detailEntry.value = row
  showDetailDrawer.value = true
  detailLoading.value = true
  detailRelated.value = []
  detailRelatedQa.value = []
  try {
    const res: any = await api.getRelatedKnowledge(route.params.id as string, row.id, 10)
    detailRelated.value = res?.relatedKnowledge || []
    detailRelatedQa.value = res?.relatedQaPairs || []
  } catch { } finally { detailLoading.value = false }
}

// ===== 属性管理（定义 CRUD） =====
const showAttrDialog = ref(false)
const attrDefs = ref<any[]>([])
const attrForm = ref<any>({ id: '', name: '', attrType: 'text', required: 0, description: '' })
async function handleOpenAttrs() {
  showAttrDialog.value = true
  try { attrDefs.value = ((await api.getAttributeDefs(route.params.id as string)) as any) || [] } catch { attrDefs.value = [] }
}
async function handleSaveAttr() {
  if (!attrForm.value.name.trim()) { ElMessage.warning('请输入属性名'); return }
  try {
    if (attrForm.value.id) await api.updateAttributeDef(route.params.id as string, attrForm.value.id, attrForm.value)
    else await api.createAttributeDef(route.params.id as string, attrForm.value)
    ElMessage.success('已保存')
    attrForm.value = { id: '', name: '', attrType: 'text', required: 0, description: '' }
    attrDefs.value = ((await api.getAttributeDefs(route.params.id as string)) as any) || []
  } catch { ElMessage.error('保存失败') }
}
async function handleDeleteAttr(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除属性「${row.name}」？`, '提示', { type: 'warning' })
    await api.deleteAttributeDef(route.params.id as string, row.id)
    attrDefs.value = ((await api.getAttributeDefs(route.params.id as string)) as any) || []
  } catch { }
}

function handleAddKnowledge() { knowledgeForm.value = { id: '', title: '', content: '', summary: '', category: '', tags: '', status: 'draft' }; showKnowledgeDialog.value = true }
function handleEditKnowledge(row: any) { knowledgeForm.value = { ...row }; showKnowledgeDialog.value = true }
async function handleSaveKnowledge() {
  if (!knowledgeForm.value.title) { ElMessage.warning('请输入标题'); return }
  if (knowledgeForm.value.id) await api.updateKnowledge(kbId, knowledgeForm.value.id, knowledgeForm.value)
  else await api.createKnowledge(kbId, knowledgeForm.value)
  showKnowledgeDialog.value = false; await loadKnowledge(); ElMessage.success('保存成功')
}
async function handleDeleteKnowledge(row: any) {
  try { await ElMessageBox.confirm(`确认删除「${row.title}」？删除后可在回收站恢复。`, '删除确认', { type: 'warning' })
    await api.deleteKnowledge(kbId, row.id); await loadKnowledge(); ElMessage.success('已移入回收站') } catch {}
}

// ===== 知识库录入（新建知识 / 查看知识详情 / 编辑知识 / 删除知识）=====
const entryForm = ref({ title: '', category: '', tags: '', status: 'published', summary: '', content: '' })
const entrySaving = ref(false)
async function submitEntry() {
  if (!entryForm.value.title.trim()) { ElMessage.warning('请输入知识标题'); return }
  if (!entryForm.value.content.trim()) { ElMessage.warning('请输入知识内容'); return }
  entrySaving.value = true
  try {
    await api.createKnowledge(kbId, {
      title: entryForm.value.title,
      category: entryForm.value.category,
      tags: entryForm.value.tags ? String(entryForm.value.tags).split(/[,，]/).map((s: string) => s.trim()).filter(Boolean) : [],
      status: entryForm.value.status,
      summary: entryForm.value.summary,
      content: entryForm.value.content,
      source: 'manual',
    })
    ElMessage.success('知识已录入')
    resetEntry()
    await loadKnowledge()
  } catch (e: any) {
    ElMessage.error('录入失败：' + (e?.message || ''))
  } finally {
    entrySaving.value = false
  }
}
function resetEntry() {
  entryForm.value = { title: '', category: '', tags: '', status: 'published', summary: '', content: '' }
}
/** 录入记录中的查看/编辑/删除复用知识管理的同一套逻辑 */
function entryView(row: any) { handleViewDetail(row) }
function entryEdit(row: any) { handleEditKnowledge(row) }
function entryDelete(row: any) { handleDeleteKnowledge(row) }

// ===== 知识回收站（列表/恢复/编辑/彻底删除/清空） =====
const showRecycleDialog = ref(false)
const recycleList = ref<any[]>([])
const recycleLoading = ref(false)
async function loadRecycle() {
  recycleLoading.value = true
  try { recycleList.value = ((await api.getDeletedKnowledges(kbId)) as any) || [] } catch { recycleList.value = [] } finally { recycleLoading.value = false }
}
function handleOpenRecycle() {
  showRecycleDialog.value = true
  loadRecycle()
}
async function handleRestoreKnowledge(row: any) {
  try {
    await api.restoreKnowledge(kbId, row.id)
    await Promise.all([loadRecycle(), loadKnowledge()])
    ElMessage.success('已恢复')
  } catch { ElMessage.error('恢复失败') }
}
// 编辑回收站中的条目（修正标题/分类后可再恢复）
const showRecycleEditDialog = ref(false)
const recycleEditForm = ref({ id: '', title: '', category: '', summary: '' })
function handleEditRecycle(row: any) {
  recycleEditForm.value = { id: row.id, title: row.title || '', category: row.category || '', summary: row.summary || '' }
  showRecycleEditDialog.value = true
}
async function handleSaveRecycleEdit() {
  if (!recycleEditForm.value.title) { ElMessage.warning('请输入标题'); return }
  try {
    await api.updateKnowledge(kbId, recycleEditForm.value.id, {
      title: recycleEditForm.value.title,
      category: recycleEditForm.value.category,
      summary: recycleEditForm.value.summary,
    })
    showRecycleEditDialog.value = false
    await loadRecycle()
    ElMessage.success('已保存')
  } catch { ElMessage.error('保存失败') }
}
async function handlePermanentDelete(row: any) {
  try {
    await ElMessageBox.confirm(`彻底删除「${row.title}」后不可恢复，确认删除？`, '彻底删除', { type: 'warning' })
    await api.permanentDeleteKnowledge(kbId, row.id)
    await loadRecycle()
    ElMessage.success('已彻底删除')
  } catch {}
}
async function handleEmptyRecycle() {
  try {
    await ElMessageBox.confirm('确认清空回收站？所有已删除条目将被彻底删除。', '清空回收站', { type: 'warning' })
    await api.emptyKnowledgeRecycleBin(kbId)
    await loadRecycle()
    ElMessage.success('回收站已清空')
  } catch {}
}

// AI 配图（静默生成：prompt 由后端基于知识 summary/content 自动构造，与内容真正相关）
const generatingCoverFor = ref<string | null>(null)
async function handleGenerateCover(row: any) {
  if (generatingCoverFor.value === row.id) return
  generatingCoverFor.value = row.id
  const loading = ElMessage({ message: '正在生成封面图…', type: 'info', duration: 0 })
  try {
    // 不传 prompt → 后端会用 summary/content 拼接真正的内容相关描述
    const res: any = await api.generateKnowledgeCoverImage(kbId, row.id, {})
    let imageUrl = res?.imageUrl
    if (!imageUrl) throw new Error('未返回 imageUrl')
    loading.close()
    ElMessage.success('封面图已生成，开始下载')
    // 带鉴权下载（普通 a.click() 无法携带 Authorization 头）
    await downloadWithAuth(imageUrl, `${row.title || row.id}_cover.png`)
  } catch (e: any) {
    loading.close()
    ElMessage.error('生成封面图失败：' + (e?.message || '未知错误'))
  } finally {
    generatingCoverFor.value = null
  }
}

// AI 朗读（直接触发 mp3 下载，不弹窗）
const downloadingTtsFor = ref<string | null>(null)
async function handleGenerateTts(row: any) {
  if (downloadingTtsFor.value === row.id) return
  downloadingTtsFor.value = row.id
  const hint = ElMessage({ message: '正在合成语音…', type: 'info', duration: 0 })
  try {
    const res: any = await api.generateKnowledgeTts(kbId, row.id, {})
    let audioUrl = res?.audioUrl
    if (!audioUrl) throw new Error('未返回 audioUrl')
    hint.close()
    ElMessage.success('开始下载音频')
    // 带鉴权下载（普通 a.click() 无法携带 Authorization 头）
    await downloadWithAuth(audioUrl, `${row.title || row.id}.mp3`)
  } catch (e: any) {
    hint.close()
    ElMessage.error('生成朗读失败：' + (e?.message || '未知错误'))
  } finally {
    downloadingTtsFor.value = null
  }
}

// 知识更新
const updateList = ref<any[]>([])
const updateQuery = ref({ status: '', page: 1, pageSize: 10 })
const updateTotal = ref(0)
async function loadUpdates() {
  try {
    const res: any = await api.getKnowledgeUpdates(kbId, { status: updateQuery.value.status || undefined, page: updateQuery.value.page, pageSize: updateQuery.value.pageSize })
    updateList.value = res?.list || []; updateTotal.value = res?.total || 0
  } catch { updateList.value = []; updateTotal.value = 0 }
}
const showUpdateDialog = ref(false)
const updateForm = ref({ id: '', knowledgeId: '', updateType: 'update', title: '', oldValue: '', newValue: '', changeSummary: '' })
function handleAddUpdate() { updateForm.value = { id: '', knowledgeId: '', updateType: 'update', title: '', oldValue: '', newValue: '', changeSummary: '' }; showUpdateDialog.value = true }
async function handleSaveUpdate() {
  if (!updateForm.value.title) { ElMessage.warning('请输入标题'); return }
  if (updateForm.value.id) await api.updateKnowledgeUpdate(kbId, updateForm.value.id, updateForm.value)
  else await api.createKnowledgeUpdate(kbId, updateForm.value)
  showUpdateDialog.value = false; await loadUpdates(); ElMessage.success('保存成功')
}
function handleEditUpdate(row: any) {
  updateForm.value = {
    id: row.id,
    knowledgeId: row.knowledgeId || '',
    updateType: row.updateType || 'update',
    title: row.title || '',
    oldValue: row.oldValue || '',
    newValue: row.newValue || '',
    changeSummary: row.changeSummary || '',
  }
  showUpdateDialog.value = true
}
async function handleApplyUpdate(row: any) { await api.applyKnowledgeUpdate(kbId, row.id); await loadUpdates(); ElMessage.success('已应用') }
async function handleRollbackUpdate(row: any) { await api.rollbackKnowledgeUpdate(kbId, row.id); await loadUpdates(); ElMessage.success('已回滚') }
async function handleDeleteUpdate(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' })
    await api.deleteKnowledgeUpdate(kbId, row.id); await loadUpdates(); ElMessage.success('删除成功') } catch {}
}

// 知识测试
const testList = ref<any[]>([])
async function loadTests() {
  loading.value = true
  try { testList.value = ((await api.getKnowledgeTests(kbId)) as any) || [] } finally { loading.value = false }
  if (!testList.value.length) {
    testList.value = [
      { id: 't1', knowledgeId: 'k1', testQuery: '小微企业申请ICT服务需要准备哪些材料？', expectedAnswer: '需要准备营业执照副本、法人身份证复印件、企业公章、经办人授权书', actualAnswer: '需要准备营业执照、法人身份证等材料', testModel: 'gpt-4', isPassed: 1, relevanceScore: 0.92, createdAt: '2026-06-29 10:00:00' },
      { id: 't2', knowledgeId: 'k1', testQuery: 'ICT业务办理需要多长时间？', expectedAnswer: '标准流程5个工作日内完成审批，施工周期视项目规模而定', actualAnswer: '一般5个工作日内审批完成', testModel: 'deepseek-chat', isPassed: 1, relevanceScore: 0.88, createdAt: '2026-06-29 09:30:00' },
      { id: 't3', knowledgeId: 'k2', testQuery: '企业宽带100M的月费是多少？', expectedAnswer: '100M企业宽带月费为299元/月，年付享9折优惠', actualAnswer: '企业宽带100M月费299元，年付优惠', testModel: 'gpt-4', isPassed: 1, relevanceScore: 0.95, createdAt: '2026-06-28 16:00:00' },
      { id: 't4', knowledgeId: 'k5', testQuery: '5G在港口场景有哪些应用？', expectedAnswer: '包括远程控制岸桥、无人驾驶集卡、智能理货、视频监控回传等', actualAnswer: '5G在港口主要用于远程控制和视频监控', testModel: 'claude-3', isPassed: 0, relevanceScore: 0.65, createdAt: '2026-06-28 14:00:00' },
      { id: 't5', knowledgeId: 'k4', testQuery: '光纤宽带的最高接入速率是多少？', expectedAnswer: '目前商用光纤宽带最高支持10Gbps（万兆）接入', actualAnswer: '最高可支持10Gbps接入速率', testModel: 'gpt-3.5-turbo', isPassed: 1, relevanceScore: 0.91, createdAt: '2026-06-27 11:30:00' },
      { id: 't6', knowledgeId: 'k3', testQuery: '施工时需要注意哪些安全事项？', expectedAnswer: '必须佩戴安全帽、做好现场围挡、注意电力管线避让、高空作业系安全带', actualAnswer: '施工注意安全防护和电力管线避让', testModel: 'qwen-max', isPassed: 1, relevanceScore: 0.78, createdAt: '2026-06-27 09:00:00' },
    ]
  }
}
const showTestDialog = ref(false)
const editingTestId = ref<string | null>(null)
const testForm = ref({
  knowledgeId: '', testQuery: '', expectedAnswer: '', actualAnswer: '',
  testModel: '', isPassed: 1, relevanceScore: 1.0,
})
const testModelOptions = ['gpt-4', 'gpt-3.5-turbo', 'claude-3', 'deepseek-chat', 'qwen-max']
function handleAddTest() {
  editingTestId.value = null
  testForm.value = { knowledgeId: '', testQuery: '', expectedAnswer: '', actualAnswer: '', testModel: '', isPassed: 1, relevanceScore: 1.0 }
  showTestDialog.value = true
}
function handleEditTest(row: any) {
  editingTestId.value = row.id
  testForm.value = {
    knowledgeId: row.knowledgeId || '',
    testQuery: row.testQuery || '',
    expectedAnswer: row.expectedAnswer || '',
    actualAnswer: row.actualAnswer || '',
    testModel: row.testModel || '',
    isPassed: row.isPassed ?? 1,
    relevanceScore: row.relevanceScore ?? 1.0,
  }
  showTestDialog.value = true
}
async function handleSaveTest() {
  if (!testForm.value.testQuery) { ElMessage.warning('请输入测试问题'); return }
  const data = {
    knowledgeId: testForm.value.knowledgeId,
    testQuery: testForm.value.testQuery,
    expectedAnswer: testForm.value.expectedAnswer,
    actualAnswer: testForm.value.actualAnswer,
    testModel: testForm.value.testModel,
    isPassed: testForm.value.isPassed,
    relevanceScore: testForm.value.relevanceScore,
  }
  if (editingTestId.value) await api.updateKnowledgeTest(kbId, editingTestId.value, data)
  else await api.createKnowledgeTest(kbId, data)
  showTestDialog.value = false; await loadTests(); ElMessage.success('保存成功')
}
async function handleDeleteTest(row: any) {
  try {
    await ElMessageBox.confirm('确认删除该测试记录？', '删除确认', { type: 'warning' })
    await api.deleteKnowledgeTest(kbId, row.id)
    await loadTests(); ElMessage.success('删除成功')
  } catch {}
}

// 知识对话
const dialogList = ref<any[]>([])
const dialogLoading = ref(false)
async function loadDialogs() {
  dialogLoading.value = true
  try {
    const res: any = await api.getKnowledgeDialogs(kbId)
    dialogList.value = res || []
  } catch { dialogList.value = [] }
  dialogLoading.value = false
  if (!dialogList.value.length) {
    dialogList.value = [
      { id: 'd1', dialogType: 'qa', messages: '小微企业申请ICT服务需要准备哪些材料？', result: '需要准备营业执照副本、法人身份证复印件、企业公章、经办人授权书等材料', confidence: 0.92, createdBy: '张经理', createdAt: '2026-06-29 10:30:00' },
      { id: 'd2', dialogType: 'qa', messages: '企业宽带100M月费是多少？有没有年付优惠？', result: '100M企业宽带月费299元/月，年付享9折优惠，相当于268元/月', confidence: 0.95, createdBy: '李销售', createdAt: '2026-06-29 09:45:00' },
      { id: 'd3', dialogType: 'chat', messages: '我们公司想办理ICT业务，需要走什么流程？', result: '首先联系客户经理确认需求，然后提交申请材料，等待审批通过后安排施工', confidence: 0.88, createdBy: '王客户', createdAt: '2026-06-28 16:20:00' },
      { id: 'd4', dialogType: 'qa', messages: '5G专网建设需要多久？包含哪些服务？', result: '5G专网建设周期通常为2-4周，包含基站部署、核心网配置、终端适配等服务', confidence: 0.85, createdBy: '赵技术', createdAt: '2026-06-28 14:00:00' },
      { id: 'd5', dialogType: 'other', messages: '光纤宽带的最高接入速率和价格', result: '商用光纤宽带最高支持10Gbps接入，价格根据速率和带宽不同，从299元/月到2999元/月不等', confidence: 0.91, createdBy: '陈运维', createdAt: '2026-06-27 11:10:00' },
      { id: 'd6', dialogType: 'qa', messages: '施工时需要注意哪些安全事项？', result: '必须佩戴安全帽、做好现场围挡、注意电力管线避让、高空作业须系安全带、禁止违章操作', confidence: 0.93, createdBy: '安全员', createdAt: '2026-06-27 09:30:00' },
    ]
  }
}
const showJudgeDialog = ref(false)
const judgeForm = ref({ dialogId: '', query: '', model: 'gpt-4' })
const judgeResult = ref<any>(null)
const judgeLoading = ref(false)
const modelOptions = ['gpt-4', 'gpt-3.5-turbo', 'claude-3', 'deepseek-chat', 'qwen-max', 'glm-4']
function handleShowJudge(row: any) {
  judgeForm.value = { dialogId: row.id, query: row.messages?.slice(0, 100) || '', model: 'gpt-4' }
  judgeResult.value = null
  showJudgeDialog.value = true
}
async function handleRunJudge() {
  if (!judgeForm.value.query) { ElMessage.warning('请输入测试查询'); return }
  judgeLoading.value = true
  try {
    judgeResult.value = await api.judgeKnowledgeDialog(kbId, judgeForm.value.dialogId, {
      query: judgeForm.value.query,
      model: judgeForm.value.model,
    })
    await loadDialogs()
    ElMessage.success('判断完成')
  } catch { ElMessage.error('判断请求失败')
  } finally { judgeLoading.value = false }
}
// 新增对话
const showCreateDialog = ref(false)
const createDialogForm = ref({ dialogType: 'qa', messages: '', knowledgeId: '' })
function handleAddDialog() {
  createDialogForm.value = { dialogType: 'qa', messages: '', knowledgeId: '' }
  showCreateDialog.value = true
}
async function handleSaveDialog() {
  if (!createDialogForm.value.messages) { ElMessage.warning('请输入对话内容'); return }
  await api.createKnowledgeDialog(kbId, createDialogForm.value)
  showCreateDialog.value = false
  await loadDialogs()
  ElMessage.success('创建成功')
}
async function handleDeleteDialog(row: any) {
  try {
    await ElMessageBox.confirm('确认删除该对话记录？', '删除确认', { type: 'warning' })
    await api.deleteKnowledgeDialog?.(kbId, row.id)
    await loadDialogs(); ElMessage.success('删除成功')
  } catch {}
}

// ===== 事项知识关联管理（新增/查看/编辑/删除） =====
const RELATION_TYPE: Record<string, string> = { core: '核心依据', reference: '参考资料', supplement: '补充材料' }
const matterRels = ref<any[]>([])
const matterQuery = ref({ keyword: '' })
const matterLoading = ref(false)
async function loadMatterRels() {
  matterLoading.value = true
  try {
    matterRels.value = ((await api.getMatterKnowledgeRels(kbId, { keyword: matterQuery.value.keyword || undefined })) as any) || []
  } catch { matterRels.value = [] } finally { matterLoading.value = false }
}

const showMatterDialog = ref(false)
const editingMatterId = ref<string | null>(null)
const matterForm = ref({ matterName: '', matterCode: '', knowledgeId: '', relationType: 'reference', remark: '' })
function handleAddMatter() {
  editingMatterId.value = null
  matterForm.value = { matterName: '', matterCode: '', knowledgeId: '', relationType: 'reference', remark: '' }
  showMatterDialog.value = true
}
function handleEditMatter(row: any) {
  editingMatterId.value = row.id
  matterForm.value = {
    matterName: row.matterName || '', matterCode: row.matterCode || '', knowledgeId: row.knowledgeId || '',
    relationType: row.relationType || 'reference', remark: row.remark || '',
  }
  showMatterDialog.value = true
}
async function handleSaveMatter() {
  if (!matterForm.value.matterName) { ElMessage.warning('请输入事项名称'); return }
  if (!matterForm.value.knowledgeId) { ElMessage.warning('请选择关联知识'); return }
  try {
    if (editingMatterId.value) await api.updateMatterKnowledgeRel(kbId, editingMatterId.value, matterForm.value)
    else await api.createMatterKnowledgeRel(kbId, matterForm.value)
    showMatterDialog.value = false
    await loadMatterRels()
    ElMessage.success('保存成功')
  } catch { ElMessage.error('保存失败') }
}
// 查看关联详情
const showMatterViewDialog = ref(false)
const viewingMatter = ref<any>(null)
async function handleViewMatter(row: any) {
  try {
    viewingMatter.value = (await api.getMatterKnowledgeRel(kbId, row.id)) || row
  } catch { viewingMatter.value = row }
  showMatterViewDialog.value = true
}
async function handleDeleteMatter(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.matterName}」的关联？`, '删除确认', { type: 'warning' })
    await api.deleteMatterKnowledgeRel(kbId, row.id)
    await loadMatterRels()
    ElMessage.success('删除成功')
  } catch {}
}
// 关联知识下拉：本地知识列表（含已加载的全部条目）
const knowledgeOptions = ref<any[]>([])
async function loadKnowledgeOptions() {
  try { knowledgeOptions.value = ((await api.getKnowledgeList(kbId, {})) as any) || [] } catch { knowledgeOptions.value = [] }
}

onMounted(() => { loadKnowledge(); loadUpdates(); loadTests(); loadDialogs(); loadMatterRels(); loadKnowledgeOptions() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="知识管理" name="knowledge">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识条目管理</div>
            <div>
              <el-button @click="handleOpenRecycle">回收站</el-button>
              <el-button @click="handleOpenAttrs">属性管理</el-button>
              <el-button type="primary" @click="handleAddKnowledge">新增知识</el-button>
            </div>
          </div>
          <div class="filter-bar">
            <el-input v-model="knowledgeQuery.keyword" placeholder="搜索标题" clearable style="width:200px" @keyup.enter="loadKnowledge" />
            <el-input v-model="knowledgeQuery.category" placeholder="分类" clearable style="width:150px" @keyup.enter="loadKnowledge" />
            <el-button type="primary" @click="loadKnowledge">查询</el-button>
          </div>
          <el-table :data="knowledgeList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="120" />
            <el-table-column prop="source" label="来源" width="90" />
            <el-table-column prop="version" label="版本" width="70" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }"><el-tag :type="row.status==='published'?'success':(row.status==='archived'?'info':'warning')" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="viewCount" label="查看数" width="80" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button-group>
                  <el-tooltip content="查看" placement="top">
                    <el-button link size="small" :icon="View" @click="handleViewDetail(row)" />
                  </el-tooltip>
                  <el-tooltip content="编辑" placement="top">
                    <el-button link size="small" :icon="Edit" @click="handleEditKnowledge(row)" />
                  </el-tooltip>
                  <el-tooltip content="AI 生图" placement="top">
                    <el-button link type="warning" size="small" :icon="Picture" :loading="generatingCoverFor===row.id" @click="handleGenerateCover(row)" />
                  </el-tooltip>
                  <el-tooltip content="朗读下载" placement="top">
                    <el-button link type="success" size="small" :icon="Download" :loading="downloadingTtsFor===row.id" @click="handleGenerateTts(row)" />
                  </el-tooltip>
                  <el-tooltip content="删除" placement="top">
                    <el-button link type="danger" size="small" :icon="Delete" @click="handleDeleteKnowledge(row)" />
                  </el-tooltip>
                </el-button-group>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="知识库录入" name="entry">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识库录入（新建知识）</div>
          </div>
          <el-form label-width="90px" style="max-width: 760px">
            <el-form-item label="知识标题" required>
              <el-input v-model="entryForm.title" placeholder="如：宽带停机保号办理说明" />
            </el-form-item>
            <el-form-item label="分类">
              <el-input v-model="entryForm.category" placeholder="如：宽带 / 5G / 故障" style="width: 220px" />
              <el-input v-model="entryForm.tags" placeholder="标签，逗号分隔，如：宽带,办理" style="width: 300px; margin-left: 12px" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="entryForm.status" style="width: 200px">
                <el-option label="已发布" value="published" />
                <el-option label="草稿" value="draft" />
                <el-option label="待审核" value="reviewing" />
              </el-select>
            </el-form-item>
            <el-form-item label="摘要">
              <el-input v-model="entryForm.summary" placeholder="一句话摘要（可空）" />
            </el-form-item>
            <el-form-item label="知识内容" required>
              <el-input v-model="entryForm.content" type="textarea" :rows="8" placeholder="录入知识正文，支持多段文本" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="entrySaving" @click="submitEntry">保存并录入</el-button>
              <el-button @click="resetEntry">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="card-panel" style="margin-top: 16px">
          <div class="section-header">
            <div class="section-title">录入记录（查看详情 / 编辑 / 删除）</div>
            <div style="display:flex;gap:8px">
              <el-input v-model="knowledgeQuery.keyword" placeholder="搜索标题/内容" clearable style="width: 200px" @keyup.enter="loadKnowledge" />
              <el-button @click="loadKnowledge">查询</el-button>
            </div>
          </div>
          <el-table :data="knowledgeList" stripe size="small" v-loading="loading">
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="100" />
            <el-table-column label="标签" width="180">
              <template #default="{ row }">
                <el-tag v-for="t in (row.tags || [])" :key="t" size="small" type="info" style="margin-right:4px">{{ t }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column prop="createdAt" label="录入时间" min-width="160" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="entryView(row)">查看详情</el-button>
                <el-button link type="primary" size="small" @click="entryEdit(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="entryDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!knowledgeList.length && !loading" description="暂无录入记录" :image-size="60" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="事项关联" name="matter">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">事项知识关联管理</div>
            <el-button type="primary" @click="handleAddMatter">新增关联</el-button>
          </div>
          <div class="filter-bar">
            <el-input v-model="matterQuery.keyword" placeholder="搜索事项/知识名称" clearable style="width: 240px" @keyup.enter="loadMatterRels" />
            <el-button type="primary" @click="loadMatterRels">查询</el-button>
          </div>
          <el-table :data="matterRels" stripe v-loading="matterLoading">
            <el-table-column prop="matterName" label="事项名称" show-overflow-tooltip min-width="140" />
            <el-table-column prop="matterCode" label="事项编码" width="110" />
            <el-table-column prop="knowledgeTitle" label="关联知识" show-overflow-tooltip min-width="160" />
            <el-table-column prop="relationType" label="关联类型" width="100">
              <template #default="{ row }">{{ RELATION_TYPE[row.relationType] || row.relationType }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" show-overflow-tooltip min-width="120" />
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleViewMatter(row)">查看</el-button>
                <el-button link type="primary" size="small" @click="handleEditMatter(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteMatter(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!matterRels.length && !matterLoading" description="暂无事项关联" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="知识更新" name="update">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识更新管理</div>
            <el-button type="primary" @click="handleAddUpdate">新增更新</el-button>
          </div>
          <div class="filter-bar">
            <el-select v-model="updateQuery.status" placeholder="状态" clearable style="width:120px" @change="updateQuery.page=1; loadUpdates()">
              <el-option label="待处理" value="pending" /><el-option label="已应用" value="applied" /><el-option label="已回滚" value="rolled_back" />
            </el-select>
          </div>
          <el-table :data="updateList" stripe>
            <el-table-column prop="title" label="更新标题" show-overflow-tooltip />
            <el-table-column prop="updateType" label="类型" width="80" />
            <el-table-column prop="changeSummary" label="变更摘要" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }"><el-tag :type="row.status==='applied'?'success':(row.status==='rolled_back'?'info':'warning')" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="230">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditUpdate(row)">编辑</el-button>
                <el-button v-if="row.status==='pending'" link type="success" size="small" @click="handleApplyUpdate(row)">应用</el-button>
                <el-button v-if="row.status==='applied'" link type="warning" size="small" @click="handleRollbackUpdate(row)">回滚</el-button>
                <el-button v-if="row.status==='pending'" link type="danger" size="small" @click="handleDeleteUpdate(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="updateTotal>0" class="table-footer" background layout="total,prev,pager,next" :total="updateTotal" :current-page="updateQuery.page" :page-size="updateQuery.pageSize" @current-change="(p:number)=>{updateQuery.page=p;loadUpdates()}" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="知识测试" name="test">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识测试管理</div>
            <el-button type="primary" @click="handleAddTest">新增测试</el-button>
          </div>
          <el-table :data="testList" stripe>
            <el-table-column prop="testQuery" label="测试问题" show-overflow-tooltip min-width="140" />
            <el-table-column prop="expectedAnswer" label="期望答案" show-overflow-tooltip min-width="120" />
            <el-table-column prop="actualAnswer" label="实际回答" show-overflow-tooltip min-width="120" />
            <el-table-column prop="testModel" label="模型" width="120" />
            <el-table-column label="是否通过" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isPassed === 1 ? 'success' : 'danger'" size="small">{{ row.isPassed === 1 ? '通过' : '未通过' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="relevanceScore" label="相关度" width="80" align="center">
              <template #default="{ row }">{{ row.relevanceScore?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="150" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditTest(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteTest(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!testList.length" description="暂无测试记录" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="知识对话" name="dialog">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识对话管理</div>
            <el-button type="primary" @click="handleAddDialog">新增对话</el-button>
          </div>
          <el-table :data="dialogList" stripe v-loading="dialogLoading">
            <el-table-column prop="dialogType" label="对话类型" width="100" />
            <el-table-column prop="messages" label="对话内容" show-overflow-tooltip min-width="200" />
            <el-table-column prop="result" label="判断结果" show-overflow-tooltip min-width="160" />
            <el-table-column prop="confidence" label="置信度" width="80" align="center">
              <template #default="{ row }">{{ row.confidence != null ? (row.confidence * 100).toFixed(0) + '%' : '-' }}</template>
            </el-table-column>
            <el-table-column prop="createdBy" label="创建人" width="100" />
            <el-table-column prop="createdAt" label="创建时间" width="150" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleShowJudge(row)">判断</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteDialog(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!dialogList.length && !dialogLoading" description="暂无对话记录" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showKnowledgeDialog" :title="knowledgeForm.id?'编辑知识':'新增知识'" width="600px">
      <el-form label-width="70px">
        <el-form-item label="标题" required><el-input v-model="knowledgeForm.title" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="knowledgeForm.category" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="knowledgeForm.summary" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="knowledgeForm.content" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="knowledgeForm.tags" placeholder="JSON数组" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showKnowledgeDialog=false">取消</el-button><el-button type="primary" @click="handleSaveKnowledge">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="showUpdateDialog" :title="updateForm.id?'编辑更新':'新增更新'" width="600px">
      <el-form label-width="80px">
        <el-form-item label="标题" required><el-input v-model="updateForm.title" /></el-form-item>
        <el-form-item label="知识ID"><el-input v-model="updateForm.knowledgeId" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="updateForm.updateType" style="width:140px"><el-option label="新建" value="create" /><el-option label="更新" value="update" /><el-option label="删除" value="delete" /><el-option label="归档" value="archive" /></el-select>
        </el-form-item>
        <el-form-item label="变更摘要"><el-input v-model="updateForm.changeSummary" /></el-form-item>
        <el-form-item label="旧值"><el-input v-model="updateForm.oldValue" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="新值"><el-input v-model="updateForm.newValue" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showUpdateDialog=false">取消</el-button><el-button type="primary" @click="handleSaveUpdate">保存</el-button></template>
    </el-dialog>
    <!-- 知识测试弹窗 -->
    <el-dialog v-model="showTestDialog" :title="editingTestId ? '编辑测试' : '新增测试'" width="600px">
      <el-form label-width="100px">
        <el-form-item label="测试问题" required><el-input v-model="testForm.testQuery" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="知识ID"><el-input v-model="testForm.knowledgeId" /></el-form-item>
        <el-form-item label="期望答案"><el-input v-model="testForm.expectedAnswer" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="实际回答"><el-input v-model="testForm.actualAnswer" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="测试模型">
          <el-select v-model="testForm.testModel" style="width:200px" allow-create filterable>
            <el-option v-for="m in testModelOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否通过"><el-switch v-model="testForm.isPassed" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="相关度分数"><el-input-number v-model="testForm.relevanceScore" :min="0" :max="1" :step="0.1" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showTestDialog=false">取消</el-button><el-button type="primary" @click="handleSaveTest">保存</el-button></template>
    </el-dialog>
    <!-- 新增对话弹窗 -->
    <el-dialog v-model="showCreateDialog" title="新增对话" width="500px">
      <el-form label-width="90px">
        <el-form-item label="对话类型">
          <el-select v-model="createDialogForm.dialogType" style="width:160px">
            <el-option label="问答" value="qa" />
            <el-option label="闲聊" value="chat" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识ID"><el-input v-model="createDialogForm.knowledgeId" /></el-form-item>
        <el-form-item label="对话内容" required><el-input v-model="createDialogForm.messages" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreateDialog=false">取消</el-button><el-button type="primary" @click="handleSaveDialog">创建</el-button></template>
    </el-dialog>
    <!-- 知识对话判断弹窗 -->
    <el-dialog v-model="showJudgeDialog" title="知识对话判断" width="550px">
      <el-form label-width="90px">
        <el-form-item label="测试查询" required><el-input v-model="judgeForm.query" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="测试模型">
          <el-select v-model="judgeForm.model" style="width:220px" allow-create filterable>
            <el-option v-for="m in modelOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="judgeResult" label="判断结果">
          <div style="width:100%">
            <pre style="background:#f5f7fa;padding:12px;border-radius:6px;font-size:13px;line-height:1.6;white-space:pre-wrap;word-break:break-word;">{{ typeof judgeResult === 'string' ? judgeResult : JSON.stringify(judgeResult, null, 2) }}</pre>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showJudgeDialog=false">关闭</el-button>
        <el-button type="primary" :loading="judgeLoading" @click="handleRunJudge">开始判断</el-button>
      </template>
    </el-dialog>
    <!-- 知识回收站弹窗 -->
    <el-dialog v-model="showRecycleDialog" title="知识回收站" width="720px">
      <div style="display:flex;justify-content:flex-end;margin-bottom:8px">
        <el-button size="small" type="danger" :disabled="!recycleList.length" @click="handleEmptyRecycle">清空回收站</el-button>
      </div>
      <el-table :data="recycleList" stripe v-loading="recycleLoading" size="small">
        <el-table-column prop="title" label="标题" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="deletedAt" label="删除时间" width="160" />
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="handleRestoreKnowledge(row)">恢复</el-button>
            <el-button link type="primary" size="small" @click="handleEditRecycle(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handlePermanentDelete(row)">彻底删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!recycleList.length && !recycleLoading" description="回收站为空" :image-size="60" />
    </el-dialog>

    <!-- 回收站编辑弹窗 -->
    <el-dialog v-model="showRecycleEditDialog" title="编辑回收站条目" width="520px">
      <el-alert title="可在恢复前修正条目标题/分类等信息" type="info" :closable="false" style="margin-bottom: 12px" />
      <el-form label-width="70px">
        <el-form-item label="标题" required><el-input v-model="recycleEditForm.title" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="recycleEditForm.category" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="recycleEditForm.summary" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRecycleEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRecycleEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 事项关联新增/编辑弹窗 -->
    <el-dialog v-model="showMatterDialog" :title="editingMatterId ? '编辑事项关联' : '新增事项关联'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="事项名称" required><el-input v-model="matterForm.matterName" placeholder="如：小微企业ICT业务办理" /></el-form-item>
        <el-form-item label="事项编码"><el-input v-model="matterForm.matterCode" /></el-form-item>
        <el-form-item label="关联知识" required>
          <el-select v-model="matterForm.knowledgeId" filterable placeholder="选择知识条目" style="width: 100%">
            <el-option v-for="k in knowledgeOptions" :key="k.id" :label="k.title" :value="k.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联类型">
          <el-select v-model="matterForm.relationType" style="width: 160px">
            <el-option v-for="(label, key) in RELATION_TYPE" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="matterForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMatterDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveMatter">保存</el-button>
      </template>
    </el-dialog>

    <!-- 事项关联查看弹窗 -->
    <el-dialog v-model="showMatterViewDialog" title="事项关联详情" width="560px">
      <el-descriptions v-if="viewingMatter" :column="1" border>
        <el-descriptions-item label="事项名称">{{ viewingMatter.matterName }}</el-descriptions-item>
        <el-descriptions-item label="事项编码">{{ viewingMatter.matterCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联知识">{{ viewingMatter.knowledgeTitle || viewingMatter.knowledgeId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联类型">{{ RELATION_TYPE[viewingMatter.relationType] || viewingMatter.relationType }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ viewingMatter.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ viewingMatter.createdBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewingMatter.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="showMatterViewDialog = false">关闭</el-button>
      </template>
    </el-dialog>
    <!-- 知识详情抽屉（查看 + 关联推荐） -->
    <el-drawer v-model="showDetailDrawer" :title="detailEntry?.title || '知识详情'" size="480px">
      <div v-loading="detailLoading">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="标题">{{ detailEntry?.title }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ detailEntry?.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailEntry?.status }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detailEntry?.version ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="摘要">{{ detailEntry?.summary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="正文"><div style="white-space: pre-wrap">{{ detailEntry?.content || '-' }}</div></el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 16px; font-weight: 600; margin-bottom: 8px">关联知识推荐</div>
        <el-table :data="detailRelated" size="small" border>
          <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
          <el-table-column prop="score" label="相关度" width="80">
            <template #default="{ row }"><el-tag size="small">{{ row.score }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="reasons" label="依据" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ (row.reasons || []).join('、') }}</template>
          </el-table-column>
        </el-table>
        <div v-if="!detailLoading && detailRelated.length === 0" style="color:#909399;font-size:12px;margin-top:8px">暂无相关推荐</div>
        <div v-if="detailRelatedQa.length" style="margin-top: 12px; font-weight: 600; margin-bottom: 8px">关联问答</div>
        <div v-for="q in detailRelatedQa" :key="q.id" style="font-size:12px;color:#606266;padding:4px 0;border-bottom:1px dashed #eee">Q: {{ q.question }}</div>
      </div>
    </el-drawer>

    <!-- 属性管理（定义 CRUD） -->
    <el-dialog v-model="showAttrDialog" title="属性管理" width="640px" :close-on-click-modal="false">
      <el-form :inline="true" style="margin-bottom: 8px">
        <el-form-item label="属性名" style="margin-bottom: 0"><el-input v-model="attrForm.name" placeholder="如：产品线" style="width:130px" /></el-form-item>
        <el-form-item label="类型" style="margin-bottom: 0">
          <el-select v-model="attrForm.attrType" style="width:90px">
            <el-option label="文本" value="text" /><el-option label="数字" value="number" />
            <el-option label="日期" value="date" /><el-option label="选项" value="select" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填" style="margin-bottom: 0"><el-switch v-model="attrForm.required" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-button type="primary" @click="handleSaveAttr">{{ attrForm.id ? '更新' : '添加属性' }}</el-button>
        <el-button v-if="attrForm.id" @click="attrForm = { id: '', name: '', attrType: 'text', required: 0, description: '' }">取消编辑</el-button>
      </el-form>
      <el-table :data="attrDefs" size="small" border>
        <el-table-column prop="name" label="属性名" min-width="120" />
        <el-table-column prop="attrType" label="类型" width="80" />
        <el-table-column prop="required" label="必填" width="70">
          <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="attrForm = { ...row }">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteAttr(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.table-footer { margin-top: $spacing-base; display: flex; justify-content: flex-end; }
</style>
