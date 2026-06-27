<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getQaPairs, addQaPair, updateQaPair, deleteQaPair, confirmQaPair, extractQaFromChunks } from '@/mock/qa-pairs'
import { getFiles } from '@/mock/files'
import type { QaPair } from '@/types/knowledge'

const props = defineProps<{ kbId: string }>()

const loading = ref(false)
const dataList = ref<QaPair[]>([])
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

// 扩展字段（FAQ增强）
const categories = ['常见问题', '账户相关', '支付相关', '物流相关', '技术问题', '投诉建议']
const answerTypes = [
  { label: '纯文本', value: 'text' },
  { label: '富文本', value: 'rich' },
  { label: '表格', value: 'table' },
]
const statusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '已确认', value: 'confirmed' },
  { label: '启用', value: 'active' },
  { label: '停用', value: 'disabled' },
]
const STATUS_LABELS: Record<string, string> = { draft: '草稿', confirmed: '已确认', active: '启用', disabled: '停用' }
const STATUS_COLORS: Record<string, string> = { draft: 'info', confirmed: 'warning', active: 'success', disabled: 'danger' }
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
    const pairs = getQaPairs(props.kbId)
    dataList.value = pairs || []
    total.value = dataList.value.length
  } finally { loading.value = false }
}
onMounted(loadData)

function handleAdd() {
  editingId.value = null
  formData.value = { status: 'draft', source: 'manual', answerType: 'text', keywords: '', similarQuestions: '', priority: 5 }
  dialogTitle.value = '新增问答对'
  showDialog.value = true
}
function handleEdit(row: QaPair) {
  editingId.value = row.id
  formData.value = {
    ...row,
    keywords: (row as any).keywords?.join(', ') || '',
    similarQuestions: (row as any).similarQuestions?.join(', ') || '',
  }
  dialogTitle.value = '编辑问答对'
  showDialog.value = true
}
async function handleDelete(row: QaPair) {
  try {
    await ElMessageBox.confirm('确定要删除该问答对吗？', '提示', { type: 'warning' })
    deleteQaPair(props.kbId, row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
}
async function handleConfirm(row: QaPair) {
  confirmQaPair(props.kbId, row.id)
  ElMessage.success('已确认')
  loadData()
}
async function handleSave() {
  if (!formData.value.question) { ElMessage.warning('请输入问题'); return }
  if (!formData.value.answer) { ElMessage.warning('请输入答案'); return }
  try {
    const data = {
      ...formData.value,
      keywords: formData.value.keywords ? formData.value.keywords.split(',').map((s: string) => s.trim()).filter(Boolean) : [],
      similarQuestions: formData.value.similarQuestions ? formData.value.similarQuestions.split(',').map((s: string) => s.trim()).filter(Boolean) : [],
    }
    if (editingId.value) {
      updateQaPair(props.kbId, editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      addQaPair(props.kbId, data)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadData()
  } catch {}
}

// AI抽取
async function handleOpenExtract() {
  const files = getFiles(props.kbId)
  fileList.value = files?.filter((f: any) => f.status === 'completed') || []
  extractFileIds.value = []
  showExtractDialog.value = true
}
async function handleExtract() {
  if (extractFileIds.value.length === 0) { ElMessage.warning('请选择文件'); return }
  extracting.value = true
  try {
    extractQaFromChunks(props.kbId, extractFileIds.value)
    ElMessage.success('抽取完成')
    showExtractDialog.value = false
    loadData()
  } finally { extracting.value = false }
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
    <el-dialog v-model="showDialog" :title="dialogTitle" width="700px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="问题" required>
          <el-input v-model="formData.question" placeholder="请输入问题" />
        </el-form-item>
        <el-form-item label="答案" required>
          <el-input v-model="formData.answer" type="textarea" :rows="4" placeholder="请输入答案" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="formData.category" placeholder="选择分类" clearable style="width: 100%">
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="答案类型">
              <el-select v-model="formData.answerType" style="width: 100%">
                <el-option v-for="opt in answerTypes" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="触发关键词">
          <el-input v-model="formData.keywords" placeholder="多个关键词用逗号分隔" />
        </el-form-item>
        <el-form-item label="相似问法">
          <el-input v-model="formData.similarQuestions" placeholder="多个相似问法用逗号分隔" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="formData.priority" :min="1" :max="10" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="formData.status" style="width: 100%">
                <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="生效时间">
              <el-date-picker v-model="formData.effectiveTime" type="datetime" placeholder="选择生效时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效时间">
              <el-date-picker v-model="formData.expireTime" type="datetime" placeholder="选择失效时间" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
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
