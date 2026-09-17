<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ kbId: string }>()

const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const filterStatus = ref('')
const filterSource = ref('')
const filterFaqType = ref('')

// 对话框
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const effectiveRange = ref<[string, string] | null>(null)

// 关联知识候选（知识条目）
const knowledgeOptions = ref<any[]>([])

// AI抽取对话框
const showExtractDialog = ref(false)
const extractFileIds = ref<string[]>([])
const fileList = ref<any[]>([])
const extracting = ref(false)

// 状态选项与标签
const statusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '已确认', value: 'confirmed' },
]
const STATUS_LABELS: Record<string, string> = { draft: '草稿', confirmed: '已确认' }
const STATUS_COLORS: Record<string, string> = { draft: 'info', confirmed: 'warning' }
const SOURCE_LABELS: Record<string, string> = { manual: '手动录入', ai: 'AI抽取' }
const FAQ_TYPE_LABELS: Record<string, string> = { common: '常见问题', uncommon: '非常见问题' }
const SCOPE_OPTIONS = [
  { label: '全部渠道', value: 'all' },
  { label: '在线客服', value: 'online_service' },
  { label: '智能机器人', value: 'robot' },
]
const SCOPE_LABELS: Record<string, string> = Object.fromEntries(SCOPE_OPTIONS.map((o) => [o.value, o.label]))

const filteredList = computed(() => {
  let list = dataList.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(q => q.question.toLowerCase().includes(kw) || q.answer.toLowerCase().includes(kw))
  }
  if (filterStatus.value) list = list.filter(q => q.status === filterStatus.value)
  if (filterSource.value) list = list.filter(q => q.source === filterSource.value)
  if (filterFaqType.value) list = list.filter(q => (q.faqType || 'common') === filterFaqType.value)
  total.value = list.length
  const start = (currentPage.value - 1) * pageSize.value
  return list.slice(start, start + pageSize.value)
})

async function loadData() {
  loading.value = true
  try {
    const res: any = await api.getQaPairs(props.kbId)
    dataList.value = res || []
    total.value = dataList.value.length
  } finally { loading.value = false }
}

async function loadKnowledgeOptions() {
  try {
    const res: any = await api.getKnowledgeList(props.kbId)
    knowledgeOptions.value = res?.list || res || []
  } catch { knowledgeOptions.value = [] }
}

onMounted(() => { loadData(); loadKnowledgeOptions() })

function handleAdd() {
  editingId.value = null
  formData.value = { question: '', answer: '', faqType: 'common', keywords: '', effectiveScope: 'all', relatedKnowledgeIds: [] }
  effectiveRange.value = null
  dialogTitle.value = '新增问答对'
  showDialog.value = true
}
function handleEdit(row: any) {
  editingId.value = row.id
  formData.value = {
    question: row.question || '',
    answer: row.answer || '',
    faqType: row.faqType || 'common',
    keywords: row.keywords || '',
    effectiveScope: row.effectiveScope || 'all',
    relatedKnowledgeIds: Array.isArray(row.relatedKnowledgeIds) ? row.relatedKnowledgeIds : [],
  }
  effectiveRange.value = row.effectiveStart && row.effectiveEnd ? [row.effectiveStart, row.effectiveEnd] : null
  dialogTitle.value = '编辑问答对'
  showDialog.value = true
}
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定要删除该问答对吗？', '提示', { type: 'warning' })
    await api.deleteQaPair(props.kbId, row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
}
async function handleConfirm(row: any) {
  await api.confirmQaPair(props.kbId, row.id)
  ElMessage.success('已确认')
  loadData()
}
// 按应答添加知识：把答案固化为知识条目
async function handleToKnowledge(row: any) {
  try {
    await ElMessageBox.confirm('将答案固化为知识条目？', '按应答添加知识', { type: 'info' })
    await api.qaPairToKnowledge(props.kbId, row.id)
    ElMessage.success('已生成知识条目并建立关联')
    loadData()
    loadKnowledgeOptions()
  } catch {}
}
async function handleSave() {
  if (!formData.value.question) { ElMessage.warning('请输入问题'); return }
  if (!formData.value.answer) { ElMessage.warning('请输入答案'); return }
  try {
    const data: any = {
      question: formData.value.question,
      answer: formData.value.answer,
      faqType: formData.value.faqType || 'common',
      keywords: formData.value.keywords || '',
      effectiveScope: formData.value.effectiveScope || 'all',
      relatedKnowledgeIds: formData.value.relatedKnowledgeIds || [],
    }
    if (effectiveRange.value) {
      data.effectiveStart = effectiveRange.value[0]
      data.effectiveEnd = effectiveRange.value[1]
    } else {
      data.effectiveStart = null
      data.effectiveEnd = null
    }
    if (editingId.value) {
      await api.updateQaPair(props.kbId, editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      await api.createQaPair(props.kbId, { ...data, source: 'manual' })
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadData()
  } catch { ElMessage.error('保存失败') }
}

// 生效状态展示
function activeLabel(row: any) {
  if (!row.effectiveStart && !row.effectiveEnd) return '长期有效'
  return row.active ? '生效中' : '未生效'
}
function activeTagType(row: any): any {
  if (!row.effectiveStart && !row.effectiveEnd) return 'info'
  return row.active ? 'success' : 'danger'
}

// AI抽取
async function handleOpenExtract() {
  const res: any = await api.getFiles(props.kbId)
  fileList.value = (res?.list || res || []).filter((f: any) => f.status === 'completed')
  extractFileIds.value = []
  showExtractDialog.value = true
}
async function handleExtract() {
  if (extractFileIds.value.length === 0) { ElMessage.warning('请选择文件'); return }
  extracting.value = true
  try {
    await api.qaExtract(props.kbId, extractFileIds.value)
    ElMessage.success('抽取完成')
    showExtractDialog.value = false
    loadData()
  } catch { ElMessage.error('抽取失败') } finally { extracting.value = false }
}

function handleSearch() { currentPage.value = 1 }
function handlePageChange(p: number) { currentPage.value = p }
function handleSizeChange(s: number) { pageSize.value = s; currentPage.value = 1 }

// ===== 应答文档管理（新增 / 删除 / 编辑 / 查看 文档）=====
// 复用文件接口：GET/POST /kb/{id}/files、PUT/DELETE /kb/{id}/files/{id}
const docLoading = ref(false)
const docList = ref<any[]>([])
const docKeyword = ref('')
const docDialog = ref(false)
const docFile = ref<any>(null)
const docUploading = ref(false)
const docInput = ref<HTMLInputElement>()

const filteredDocs = computed(() =>
  docKeyword.value
    ? docList.value.filter((d: any) => (d.name || '').includes(docKeyword.value))
    : docList.value,
)

async function loadDocs() {
  docLoading.value = true
  try {
    const res: any = await api.getFiles(props.kbId)
    docList.value = res?.records || res?.list || res || []
  } catch {
    docList.value = []
  } finally {
    docLoading.value = false
  }
}

// 新增文档：选择文件上传（上传成功后触发解析）
function openDocUpload() {
  docInput.value?.click()
}
async function handleDocUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  docUploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const created: any = await api.uploadFile(props.kbId, fd)
    const newId = created?.id || created?.data?.id
    if (newId) {
      try {
        await api.processFile(props.kbId, newId)
      } catch {
        /* 解析失败不阻塞新增 */
      }
    }
    ElMessage.success(`文档「${file.name}」已新增`)
    await loadDocs()
  } catch (err: any) {
    ElMessage.error('新增失败：' + (err?.message || '请检查文件类型/大小'))
  } finally {
    docUploading.value = false
    input.value = ''
  }
}

// 编辑文档：重命名
async function handleDocRename(row: any) {
  let name = ''
  try {
    const res: any = await ElMessageBox.prompt('请输入新的文档名称', '编辑文档', {
      inputValue: row.name,
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    name = res.value
  } catch {
    return
  }
  try {
    await api.updateFile(props.kbId, row.id, { name })
    ElMessage.success('文档已编辑')
    await loadDocs()
  } catch (err: any) {
    ElMessage.error('编辑失败：' + (err?.message || ''))
  }
}

// 删除文档
async function handleDocDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除文档「${row.name}」吗？删除后可在文件管理回收站恢复。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteFile(props.kbId, row.id)
    ElMessage.success('文档已删除')
    await loadDocs()
  } catch (err: any) {
    ElMessage.error('删除失败：' + (err?.message || ''))
  }
}

// 查看文档
function handleDocView(row: any) {
  docFile.value = row
  docDialog.value = true
}
async function handleDocDownload(row: any) {
  try {
    const resp: any = await api.downloadFile(props.kbId, row.id)
    const blob = resp instanceof Blob ? resp : new Blob([resp])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.name || 'document'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}
onMounted(loadDocs)
</script>

<template>
  <div v-loading="loading">
    <div class="section-header">
      <div class="section-title">问答对管理（FAQ 知识应答）</div>
      <div style="display: flex; gap: 8px">
        <el-button size="small" @click="handleOpenExtract">AI 抽取</el-button>
        <el-button type="primary" size="small" @click="handleAdd">手动添加</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input v-model="searchKeyword" placeholder="搜索问题/答案..." clearable style="width: 200px" @input="handleSearch" />
      <el-select v-model="filterFaqType" placeholder="问题类型" clearable style="width: 130px" @change="handleSearch">
        <el-option label="常见问题" value="common" /><el-option label="非常见问题" value="uncommon" />
      </el-select>
      <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 110px" @change="handleSearch">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="filterSource" placeholder="来源" clearable style="width: 110px" @change="handleSearch">
        <el-option label="手动录入" value="manual" /><el-option label="AI抽取" value="ai" />
      </el-select>
      <span style="color: #909399; font-size: 13px">共 {{ total }} 条</span>
    </div>

    <!-- 表格 -->
    <el-table :data="filteredList" stripe size="small">
      <el-table-column type="index" width="50" />
      <el-table-column prop="question" label="问题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="answer" label="答案" min-width="220" show-overflow-tooltip />
      <el-table-column prop="faqType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="(row.faqType || 'common') === 'common' ? 'primary' : 'warning'" size="small">{{ FAQ_TYPE_LABELS[row.faqType || 'common'] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="keywords" label="关键词" min-width="110" show-overflow-tooltip />
      <el-table-column label="生效" width="100">
        <template #default="{ row }">
          <el-tooltip :content="(row.effectiveStart || row.effectiveEnd) ? `${row.effectiveStart || '不限'} ~ ${row.effectiveEnd || '不限'}（${SCOPE_LABELS[row.effectiveScope] || '全部渠道'}）` : '长期有效'">
            <el-tag :type="activeTagType(row)" size="small">{{ activeLabel(row) }}</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="source" label="来源" width="90">
        <template #default="{ row }">
          <el-tag :type="row.source === 'ai' ? 'warning' : 'info'" size="small">{{ SOURCE_LABELS[row.source] || row.source }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="STATUS_COLORS[row.status] as any" size="small">{{ STATUS_LABELS[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'draft'" link type="success" size="small" @click="handleConfirm(row)">确认</el-button>
          <el-button link type="primary" size="small" @click="handleToKnowledge(row)">按应答添加知识</el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-footer" v-if="total > pageSize">
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange" @size-change="handleSizeChange" />
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="showDialog" :title="dialogTitle" width="680px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="问题" required>
          <el-input v-model="formData.question" placeholder="请输入问题" />
        </el-form-item>
        <el-form-item label="答案" required>
          <el-input v-model="formData.answer" type="textarea" :rows="5" placeholder="请输入答案" />
        </el-form-item>
        <el-form-item label="问题类型">
          <el-radio-group v-model="formData.faqType">
            <el-radio value="common">常见问题</el-radio>
            <el-radio value="uncommon">非常见问题</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="多关键词">
          <el-input v-model="formData.keywords" placeholder="多个关键词用逗号分隔，如：退款,退货,售后" />
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker v-model="effectiveRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="生效开始" end-placeholder="生效结束" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生效功能">
          <el-select v-model="formData.effectiveScope" style="width: 100%">
            <el-option v-for="o in SCOPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联知识">
          <el-select v-model="formData.relatedKnowledgeIds" multiple filterable clearable placeholder="选择关联的知识条目"
            style="width: 100%">
            <el-option v-for="k in knowledgeOptions" :key="k.id" :label="k.title" :value="k.id" />
          </el-select>
        </el-form-item>
        <p style="font-size:12px;color:#909399;">新建后状态为「草稿」，可在列表中确认；不设置生效时间则长期有效。</p>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- AI抽取对话框 -->
    <el-dialog v-model="showExtractDialog" title="AI 智能抽取问答对" width="500px" :close-on-click-modal="false">
      <p style="color: #606266; margin-bottom: 16px">选择已完成解析的文件，AI 将自动从文档内容中抽取问答对。</p>
      <el-checkbox-group v-model="extractFileIds">
        <div v-for="file in fileList" :key="file.id" style="padding: 6px 0">
          <el-checkbox :label="file.id">{{ file.name }}</el-checkbox>
        </div>
      </el-checkbox-group>
      <div v-if="fileList.length === 0" style="color: #909399; text-align: center; padding: 20px">
        暂无已解析完成的文件
      </div>
      <template #footer>
        <el-button @click="showExtractDialog = false">取消</el-button>
        <el-button type="primary" :loading="extracting" @click="handleExtract">开始抽取</el-button>
      </template>
    </el-dialog>

    <!-- ===== 应答文档管理：新增 / 删除 / 编辑 / 查看 ===== -->
    <div class="section-header" style="margin-top: 24px">
      <div class="section-title">应答文档管理</div>
      <div style="display: flex; gap: 8px">
        <el-input v-model="docKeyword" placeholder="按文档名称查询" clearable size="small" style="width: 200px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button size="small" :loading="docUploading" @click="openDocUpload">
          <el-icon><Upload /></el-icon>新增文档
        </el-button>
        <input ref="docInput" type="file" style="display: none" @change="handleDocUpload" />
      </div>
    </div>
    <el-table :data="filteredDocs" stripe size="small" v-loading="docLoading">
      <el-table-column prop="name" label="文档名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="category" label="类型" width="100" />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">
          {{ row.size ? (row.size / 1024).toFixed(1) + ' KB' : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="解析状态" width="110" />
      <el-table-column prop="createdAt" label="创建时间" min-width="160" />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleDocView(row)">查看</el-button>
          <el-button link type="primary" size="small" @click="handleDocRename(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDocDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 文档查看弹窗 -->
    <el-dialog v-model="docDialog" title="查看文档" width="520px">
      <el-descriptions v-if="docFile" :column="1" border size="small">
        <el-descriptions-item label="文档名称">{{ docFile.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ docFile.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="扩展名">{{ docFile.extension || '-' }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{ docFile.size ? (docFile.size / 1024).toFixed(1) + ' KB' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="解析状态">{{ docFile.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分块数">{{ docFile.chunkCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ docFile.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="docDialog = false">关闭</el-button>
        <el-button type="primary" @click="handleDocDownload(docFile)">下载</el-button>
      </template>
    </el-dialog>
  </div>
</template>
