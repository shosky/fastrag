<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const kbId = (route.params.id as string) || 'kb_sample'

const activeTab = ref('image')
const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const query = ref({ keyword: '', status: '', page: 1, pageSize: 10 })

const MEDIA_MAP: Record<string, string> = { image: '图片', audio: '音频', video: '视频', document: '文档' }
const STORAGE_TYPE: Record<string, string> = { image: 'image', audio: 'audio', video: 'video', document: 'document' }

async function loadData() {
  loading.value = true
  try {
    const isDoc = activeTab.value === 'document'
    const res: any = isDoc
      ? await api.getDocumentList(kbId, { keyword: query.value.keyword || undefined, page: query.value.page, pageSize: query.value.pageSize })
      : await api.getMediaList(kbId, activeTab.value, { keyword: query.value.keyword || undefined, status: query.value.status || undefined, page: query.value.page, pageSize: query.value.pageSize })
    dataList.value = res?.list || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  query.value = { keyword: '', status: '', page: 1, pageSize: 10 }
  loadData()
}

function handlePageChange(p: number) {
  query.value.page = p
  loadData()
}

// 导入弹窗
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
  const isDoc = activeTab.value === 'document'
  if (isDoc) {
    await api.importDocuments(kbId, items)
  } else {
    await api.importMedia(kbId, activeTab.value, items)
  }
  showImportDialog.value = false
  await loadData()
  ElMessage.success(`成功导入 ${items.length} 项`)
}

async function handleExport() {
  const isDoc = activeTab.value === 'document'
  const res: any = isDoc ? await api.exportDocuments(kbId) : await api.exportMedia(kbId, activeTab.value)
  ElMessage.success(`导出 ${res?.length || 0} 项（${MEDIA_MAP[activeTab.value]}）`)
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？`, '删除确认', { type: 'warning' })
    await api.deleteStorage(kbId, STORAGE_TYPE[activeTab.value], row.id)
    await loadData()
    ElMessage.success('删除成功')
  } catch {}
}

// 问答抽取
const extractTasks = ref<any[]>([])
async function loadTasks() {
  try {
    const res: any = await api.getQaExtractTasks(kbId)
    extractTasks.value = res || []
  } catch { extractTasks.value = [] }
  if (!extractTasks.value.length) {
    extractTasks.value = [
      { id: 'et1', name: '产品文档问答抽取', sourceType: 'document', llmModel: 'qwen3-72b', completedCount: 45, totalCount: 50, status: 'completed' },
      { id: 'et2', name: 'FAQ问答抽取', sourceType: 'qa_pair', llmModel: 'DeepSeek-V3', completedCount: 12, totalCount: 12, status: 'completed' },
      { id: 'et3', name: '技术文档批量抽取', sourceType: 'document', llmModel: 'qwen3-72b', completedCount: 8, totalCount: 30, status: 'running' },
    ]
  }
}

const showExtractDialog = ref(false)
const isEditingExtract = ref(false)
const editingExtractId = ref('')
const extractForm = ref({ name: '', sourceType: 'document', llmModel: 'qwen3-72b', maxQuestions: 50 })

function handleAddExtract() {
  isEditingExtract.value = false
  editingExtractId.value = ''
  extractForm.value = { name: '', sourceType: 'document', llmModel: 'qwen3-72b', maxQuestions: 50 }
  showExtractDialog.value = true
}

function handleEditTask(row: any) {
  isEditingExtract.value = true
  editingExtractId.value = row.id
  extractForm.value = {
    name: row.name || '',
    sourceType: row.sourceType || 'document',
    llmModel: row.llmModel || '',
    maxQuestions: row.maxQuestions || 50,
  }
  showExtractDialog.value = true
}

async function handleStartExtract() {
  if (!extractForm.value.name) {
    ElMessage.warning('请输入任务名称')
    return
  }
  const payload = {
    name: extractForm.value.name,
    sourceType: extractForm.value.sourceType,
    llmModel: extractForm.value.llmModel,
    params: JSON.stringify({ maxQuestions: extractForm.value.maxQuestions }),
  }
  if (isEditingExtract.value && editingExtractId.value) {
    await api.updateQaExtract(kbId, editingExtractId.value, payload)
    ElMessage.success('抽取任务已更新')
  } else {
    await api.startQaExtract(kbId, payload)
    ElMessage.success('抽取任务已启动')
  }
  showExtractDialog.value = false
  await loadTasks()
}

async function handleStopTask(row: any) {
  await api.stopQaExtract(kbId, row.id)
  await loadTasks()
  ElMessage.success('已停止')
}

async function handleDeleteTask(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该抽取任务？', '删除确认', { type: 'warning' })
    await api.deleteQaExtract(kbId, row.id)
    await loadTasks()
    ElMessage.success('删除成功')
  } catch {}
}

onMounted(() => {
  loadData()
  loadTasks()
})
</script>

<template>
  <div class="page-container">
    <!-- 多媒体/文档管理 -->
    <div class="card-panel">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="图片" name="image" />
        <el-tab-pane label="音频" name="audio" />
        <el-tab-pane label="视频" name="video" />
        <el-tab-pane label="文档" name="document" />
      </el-tabs>

      <div class="section-header">
        <div class="section-title">{{ MEDIA_MAP[activeTab] }}资源管理</div>
        <div>
          <el-button @click="handleExport">导出</el-button>
          <el-button type="primary" @click="handleImport">导入{{ MEDIA_MAP[activeTab] }}</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="搜索名称" clearable style="width: 200px" @keyup.enter="query.page = 1; loadData()" />
        <el-select v-if="activeTab !== 'document'" v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="已上传" value="uploaded" />
          <el-option label="处理中" value="processing" />
          <el-option label="已完成" value="completed" />
          <el-option label="失败" value="failed" />
        </el-select>
        <el-button type="primary" @click="query.page = 1; loadData()">查询</el-button>
      </div>

      <el-table :data="dataList" stripe v-loading="loading">
        <el-table-column prop="name" label="名称" show-overflow-tooltip />
        <el-table-column prop="extension" label="格式" width="80" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ row.size ? (row.size / 1024).toFixed(1) + ' KB' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : (row.status === 'failed' ? 'danger' : 'warning')" size="small">{{ row.status || 'uploaded' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total > 0" class="table-footer" background layout="total, prev, pager, next" :total="total" :current-page="query.page" :page-size="query.pageSize" @current-change="handlePageChange" />
    </div>

    <!-- 问答抽取 -->
    <div class="card-panel" style="margin-top: 16px">
      <div class="section-header">
        <div class="section-title">问答抽取任务</div>
        <el-button type="primary" @click="handleAddExtract">启动抽取</el-button>
      </div>
      <el-table :data="extractTasks" stripe>
        <el-table-column prop="name" label="任务名称" show-overflow-tooltip />
        <el-table-column prop="sourceType" label="来源" width="100" />
        <el-table-column prop="llmModel" label="模型" width="140" />
        <el-table-column label="进度" width="140">
          <template #default="{ row }">{{ row.completedCount || 0 }} / {{ row.totalCount || 0 }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : (row.status === 'running' ? 'warning' : (row.status === 'stopped' ? 'info' : 'danger'))" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEditTask(row)">编辑</el-button>
            <el-button v-if="row.status === 'running'" link type="warning" size="small" @click="handleStopTask(row)">停止</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteTask(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 导入弹窗 -->
    <el-dialog v-model="showImportDialog" :title="`导入${MEDIA_MAP[activeTab]}`" width="600px">
      <el-form label-width="80px">
        <el-form-item label="资源JSON">
          <el-input v-model="importItems" type="textarea" :rows="8" placeholder='[{"name":"产品图.png","size":245678,"description":"产品架构图"}]' />
        </el-form-item>
        <p style="font-size:12px;color:#909399;">JSON数组格式，每项含 name/size/description/tags 字段</p>
      </el-form>
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" @click="handleImportSubmit">导入</el-button>
      </template>
    </el-dialog>

    <!-- 抽取弹窗 -->
    <el-dialog v-model="showExtractDialog" :title="isEditingExtract ? '编辑问答抽取' : '启动问答抽取'" width="500px">
      <el-form label-width="90px">
        <el-form-item label="任务名称" required>
          <el-input v-model="extractForm.name" placeholder="如：产品文档问答抽取" />
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="extractForm.sourceType" style="width: 160px">
            <el-option label="文档" value="document" />
            <el-option label="问答对" value="qa_pair" />
            <el-option label="文本" value="text" />
          </el-select>
        </el-form-item>
        <el-form-item label="LLM模型">
          <el-input v-model="extractForm.llmModel" />
        </el-form-item>
        <el-form-item label="最大问题数">
          <el-input-number v-model="extractForm.maxQuestions" :min="1" :max="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showExtractDialog = false">取消</el-button>
        <el-button type="primary" @click="handleStartExtract">{{ isEditingExtract ? '保存' : '启动' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.table-footer { margin-top: $spacing-base; display: flex; justify-content: flex-end; }
</style>
