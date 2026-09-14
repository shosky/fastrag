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

// 对话框
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})

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

const filteredList = computed(() => {
  let list = dataList.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(q => q.question.toLowerCase().includes(kw) || q.answer.toLowerCase().includes(kw))
  }
  if (filterStatus.value) list = list.filter(q => q.status === filterStatus.value)
  if (filterSource.value) list = list.filter(q => q.source === filterSource.value)
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
onMounted(loadData)

function handleAdd() {
  editingId.value = null
  formData.value = { question: '', answer: '' }
  dialogTitle.value = '新增问答对'
  showDialog.value = true
}
function handleEdit(row: any) {
  editingId.value = row.id
  formData.value = { question: row.question || '', answer: row.answer || '' }
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
async function handleSave() {
  if (!formData.value.question) { ElMessage.warning('请输入问题'); return }
  if (!formData.value.answer) { ElMessage.warning('请输入答案'); return }
  try {
    const data = { question: formData.value.question, answer: formData.value.answer }
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
</script>

<template>
  <div v-loading="loading">
    <div class="section-header">
      <div class="section-title">问答对管理</div>
      <div style="display: flex; gap: 8px">
        <el-button size="small" @click="handleOpenExtract">AI 抽取</el-button>
        <el-button type="primary" size="small" @click="handleAdd">手动添加</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input v-model="searchKeyword" placeholder="搜索问题/答案..." clearable style="width: 220px" @input="handleSearch" />
      <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="filterSource" placeholder="来源" clearable style="width: 120px" @change="handleSearch">
        <el-option label="手动录入" value="manual" /><el-option label="AI抽取" value="ai" />
      </el-select>
      <span style="color: #909399; font-size: 13px">共 {{ total }} 条</span>
    </div>

    <!-- 表格 -->
    <el-table :data="filteredList" stripe size="small">
      <el-table-column type="index" width="50" />
      <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="answer" label="答案" min-width="250" show-overflow-tooltip />
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
      <el-table-column prop="createdAt" label="创建时间" width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'draft'" link type="success" size="small" @click="handleConfirm(row)">确认</el-button>
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
    <el-dialog v-model="showDialog" :title="dialogTitle" width="600px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="问题" required>
          <el-input v-model="formData.question" placeholder="请输入问题" />
        </el-form-item>
        <el-form-item label="答案" required>
          <el-input v-model="formData.answer" type="textarea" :rows="5" placeholder="请输入答案" />
        </el-form-item>
        <p style="font-size:12px;color:#909399;">新建后状态为「草稿」，可在列表中确认；AI 抽取的问答对来源于所选文件。</p>
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
  </div>
</template>
