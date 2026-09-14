<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const kbId = (route.params.id as string) || 'kb_sample'
const activeTab = ref('multiturn')
const loading = ref(false)

// 多轮问答
const mtList = ref<any[]>([])
async function loadMt() { mtList.value = ((await api.getMultiTurnQa(kbId)) as any) || [] }
const showMtDialog = ref(false)
const mtForm = ref({ id: '', title: '', turns: '', category: '', description: '' })
function handleAddMt() { mtForm.value = { id: '', title: '', turns: '', category: '', description: '' }; showMtDialog.value = true }
function handleEditMt(row: any) { mtForm.value = { ...row }; showMtDialog.value = true }
async function handleSaveMt() {
  if (!mtForm.value.title) { ElMessage.warning('请输入标题'); return }
  if (mtForm.value.id) await api.updateMultiTurnQa(kbId, mtForm.value.id, mtForm.value)
  else await api.createMultiTurnQa(kbId, mtForm.value)
  showMtDialog.value = false; await loadMt(); ElMessage.success('保存成功')
}
async function handleDeleteMt(row: any) { try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }); await api.deleteMultiTurnQa(kbId, row.id); await loadMt(); ElMessage.success('删除成功') } catch {} }

// ===== 多模态问答：按 modal_type 拆分为 图片/表格/文本 三个页签 =====
const mmList = ref<any[]>([])
async function loadMm() { mmList.value = ((await api.getMultimodalQa(kbId)) as any) || [] }
const imageList = computed(() => mmList.value.filter((q: any) => q.modalType === 'image'))
const tableList = computed(() => mmList.value.filter((q: any) => q.modalType === 'table'))
const textList = computed(() => mmList.value.filter((q: any) => q.modalType === 'text' || !q.modalType))
const MODAL_LABEL: Record<string, string> = { image: '图片问答', table: '表格问答', text: '文本问答' }

const showMmDialog = ref(false)
const editingMmId = ref<string | null>(null)
const mmModalType = ref<'image' | 'table' | 'text'>('image')
const mmForm = ref({ title: '', question: '', answer: '', category: '' })
function handleAddMm(type: 'image' | 'table' | 'text') {
  editingMmId.value = null
  mmModalType.value = type
  mmForm.value = { title: '', question: '', answer: '', category: '' }
  showMmDialog.value = true
}
function handleEditMm(row: any) {
  editingMmId.value = row.id
  mmModalType.value = (row.modalType || 'text') as 'image' | 'table' | 'text'
  mmForm.value = { title: row.title || '', question: row.question || '', answer: row.answer || '', category: row.category || '' }
  showMmDialog.value = true
}
async function handleSaveMm() {
  if (!mmForm.value.title) { ElMessage.warning('请输入标题'); return }
  const payload = { ...mmForm.value, modalType: mmModalType.value }
  if (editingMmId.value) await api.updateMultimodalQa(kbId, editingMmId.value, payload)
  else await api.createMultimodalQa(kbId, payload)
  showMmDialog.value = false; await loadMm(); ElMessage.success('保存成功')
}
// 查看问答详情
const showMmView = ref(false)
const viewingMm = ref<any>(null)
function handleViewMm(row: any) { viewingMm.value = row; showMmView.value = true }
async function handleDeleteMm(row: any) {
  try { await ElMessageBox.confirm(`确认删除「${row.title}」？`, '删除确认', { type: 'warning' }); await api.deleteMultimodalQa(kbId, row.id); await loadMm(); ElMessage.success('删除成功') } catch {}
}

// ===== 文档导读：新增/编辑/索引/删除 =====
const dgList = ref<any[]>([])
async function loadDg() { dgList.value = ((await api.getDocGuides(kbId)) as any) || [] }
const showDgDialog = ref(false)
const editingDgId = ref<string | null>(null)
const dgForm = ref({ fileId: '', title: '', category: '', summary: '', outline: '', keyPoints: '' })
function handleAddDg() {
  editingDgId.value = null
  dgForm.value = { fileId: '', title: '', category: '', summary: '', outline: '', keyPoints: '' }
  showDgDialog.value = true
}
function handleEditDg(row: any) {
  editingDgId.value = row.id
  dgForm.value = {
    fileId: row.fileId || '', title: row.title || '', category: row.category || '',
    summary: row.summary || '', outline: row.outline || '', keyPoints: row.keyPoints || '',
  }
  showDgDialog.value = true
}
async function handleSaveDg() {
  if (!dgForm.value.title) { ElMessage.warning('请输入标题'); return }
  if (editingDgId.value) {
    await api.updateDocGuide(kbId, editingDgId.value, dgForm.value)
    ElMessage.success('保存成功')
  } else {
    await api.createDocGuide(kbId, dgForm.value)
    ElMessage.success('创建成功')
  }
  showDgDialog.value = false; await loadDg()
}
async function handleIndexDg(row: any) { await api.indexDocGuide(kbId, row.id); await loadDg(); ElMessage.success('索引完成') }
async function handleDeleteDg(row: any) { try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }); await api.deleteDocGuide(kbId, row.id); await loadDg(); ElMessage.success('删除成功') } catch {} }

// ===== 高亮定位：输入问题 → 检索命中内容并高亮关键词 =====
const hlQuery = ref('')
const hlLoading = ref(false)
const hlResults = ref<any[]>([])
async function handleLocate() {
  if (!hlQuery.value.trim()) { ElMessage.warning('请输入要定位的问题或关键词'); return }
  hlLoading.value = true
  try {
    hlResults.value = ((await api.searchRetrieval({ knowledgeId: kbId, query: hlQuery.value, config: { topK: 5 } } as any)) as any) || []
    if (!hlResults.value.length) ElMessage.info('未命中任何内容')
  } catch { ElMessage.error('定位请求失败') } finally { hlLoading.value = false }
}
// 命中关键词高亮渲染（<mark> 包裹）
function renderContent(content: string, highlights?: string[]): string {
  if (!content) return ''
  let html = content.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const tokens = [...(highlights || [])].sort((a, b) => b.length - a.length)
  for (const t of tokens) {
    const esc = t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    html = html.split(esc).join('<mark>' + esc + '</mark>')
  }
  return html
}

onMounted(() => { loadMt(); loadMm(); loadDg() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="多轮问答" name="multiturn">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">多轮问答管理</div><el-button type="primary" @click="handleAddMt">新增</el-button></div>
          <el-table :data="mtList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="120" />
            <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><el-tag size="small">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" size="small" @click="handleEditMt(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDeleteMt(row)">删除</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="图片问答" name="imageqa">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">图片问答管理</div><el-button type="primary" @click="handleAddMm('image')">新增图片问答</el-button></div>
          <el-table :data="imageList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="question" label="问题" show-overflow-tooltip />
            <el-table-column prop="answer" label="答案" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleViewMm(row)">查看</el-button>
                <el-button link type="primary" size="small" @click="handleEditMm(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteMm(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!imageList.length" description="暂无图片问答" :image-size="60" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="表格问答" name="tableqa">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">表格问答管理</div><el-button type="primary" @click="handleAddMm('table')">新增表格问答</el-button></div>
          <el-table :data="tableList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="question" label="问题" show-overflow-tooltip />
            <el-table-column prop="answer" label="答案" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleViewMm(row)">查看</el-button>
                <el-button link type="primary" size="small" @click="handleEditMm(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteMm(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!tableList.length" description="暂无表格问答" :image-size="60" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="文本问答" name="textqa">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">文本问答管理</div><el-button type="primary" @click="handleAddMm('text')">新增文本问答</el-button></div>
          <el-table :data="textList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="question" label="问题" show-overflow-tooltip />
            <el-table-column prop="answer" label="答案" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleViewMm(row)">查看</el-button>
                <el-button link type="primary" size="small" @click="handleEditMm(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteMm(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!textList.length" description="暂无文本问答" :image-size="60" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="文档导读" name="docguide">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">文档导读管理</div><el-button type="primary" @click="handleAddDg">新增导读</el-button></div>
          <el-table :data="dgList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="fileId" label="文件" width="140" />
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column prop="indexStatus" label="索引状态" width="100"><template #default="{ row }"><el-tag :type="row.indexStatus==='completed'?'success':'warning'" size="small">{{ row.indexStatus }}</el-tag></template></el-table-column>
            <el-table-column prop="indexProgress" label="进度" width="80" />
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button v-if="row.indexStatus!=='completed'" link type="success" size="small" @click="handleIndexDg(row)">索引</el-button>
                <el-button link type="primary" size="small" @click="handleEditDg(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteDg(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="高亮定位" name="highlight">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">高亮定位</div></div>
          <div class="filter-bar">
            <el-input v-model="hlQuery" placeholder="输入问题或关键词，定位知识库中的原文位置" clearable style="width: 420px" @keyup.enter="handleLocate" />
            <el-button type="primary" :loading="hlLoading" @click="handleLocate">定位</el-button>
          </div>
          <el-empty v-if="!hlResults.length && !hlLoading" description="输入内容后点击「定位」，将检索原文并高亮命中关键词" :image-size="60" />
          <div v-for="(r, i) in hlResults" :key="i" class="hl-item">
            <div class="hl-meta">
              <el-tag size="small" type="info">片段 #{{ r.chunkIndex }}</el-tag>
              <span class="hl-file">文件：{{ r.fileId || '-' }}</span>
              <template v-if="r.highlights?.length">
                <span style="margin-left:8px;color:#909399">命中：</span>
                <el-tag v-for="h in r.highlights" :key="h" size="small" type="warning" style="margin-right:4px">{{ h }}</el-tag>
              </template>
            </div>
            <!-- eslint-disable-next-line vue/no-v-html -->
            <div class="hl-content" v-html="r.previewSnippet || renderContent(r.content, r.highlights)" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showMtDialog" :title="mtForm.id?'编辑多轮问答':'新增多轮问答'" width="600px">
      <el-form label-width="70px">
        <el-form-item label="标题" required><el-input v-model="mtForm.title" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="mtForm.category" /></el-form-item>
        <el-form-item label="对话轮次"><el-input v-model="mtForm.turns" type="textarea" :rows="6" placeholder='JSON数组，如 [{"turnIndex":1,"question":"如何退款","answer":"..."}]' /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showMtDialog=false">取消</el-button><el-button type="primary" @click="handleSaveMt">保存</el-button></template>
    </el-dialog>

    <!-- 多模态问答 新增/编辑弹窗（模态随所在页签固定） -->
    <el-dialog v-model="showMmDialog" :title="(editingMmId ? '编辑' : '新增') + MODAL_LABEL[mmModalType]" width="600px">
      <el-form label-width="70px">
        <el-form-item label="模态"><el-tag size="small">{{ MODAL_LABEL[mmModalType] }}</el-tag></el-form-item>
        <el-form-item label="标题" required><el-input v-model="mmForm.title" /></el-form-item>
        <el-form-item label="问题"><el-input v-model="mmForm.question" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="答案"><el-input v-model="mmForm.answer" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="mmForm.category" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showMmDialog=false">取消</el-button><el-button type="primary" @click="handleSaveMm">保存</el-button></template>
    </el-dialog>

    <!-- 多模态问答 查看弹窗 -->
    <el-dialog v-model="showMmView" :title="viewingMm ? MODAL_LABEL[viewingMm.modalType] + '详情' : '详情'" width="560px">
      <el-descriptions v-if="viewingMm" :column="1" border>
        <el-descriptions-item label="标题">{{ viewingMm.title }}</el-descriptions-item>
        <el-descriptions-item label="模态">{{ MODAL_LABEL[viewingMm.modalType] || viewingMm.modalType }}</el-descriptions-item>
        <el-descriptions-item label="问题">{{ viewingMm.question || '-' }}</el-descriptions-item>
        <el-descriptions-item label="答案">{{ viewingMm.answer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ viewingMm.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联媒体">{{ viewingMm.mediaIds || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewingMm.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button type="primary" @click="showMmView=false">关闭</el-button></template>
    </el-dialog>

    <!-- 文档导读 新增/编辑弹窗 -->
    <el-dialog v-model="showDgDialog" :title="editingDgId ? '编辑文档导读' : '新增文档导读'" width="600px">
      <el-form label-width="70px">
        <el-form-item label="标题" required><el-input v-model="dgForm.title" /></el-form-item>
        <el-form-item label="文件ID"><el-input v-model="dgForm.fileId" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="dgForm.category" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="dgForm.summary" type="textarea" :rows="3" placeholder="文档内容概要" /></el-form-item>
        <el-form-item label="大纲"><el-input v-model="dgForm.outline" type="textarea" :rows="4" placeholder="章节结构，换行分隔" /></el-form-item>
        <el-form-item label="要点"><el-input v-model="dgForm.keyPoints" type="textarea" :rows="4" placeholder="关键要点，换行分隔" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDgDialog=false">取消</el-button><el-button type="primary" @click="handleSaveDg">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.hl-item { border: 1px solid var(--el-border-color-light); border-radius: $radius-base; padding: 12px 16px; margin-bottom: 12px; background: var(--el-bg-color-overlay); }
.hl-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; margin-bottom: 8px; }
.hl-file { color: $text-secondary; font-size: 13px; }
.hl-content { font-size: 14px; line-height: 1.8; word-break: break-word; :deep(mark) { background: #ffe58f; padding: 0 2px; border-radius: 2px; } }
</style>
