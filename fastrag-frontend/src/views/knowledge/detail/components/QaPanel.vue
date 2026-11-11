<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'
import type { QaPair } from '@/types/knowledge'
import { usePagination } from '@/composables/usePagination'
import { useAuth } from '@/composables/useAuth'
import QaImportDialog from './QaImportDialog.vue'

const props = defineProps<{ kbId: string }>()

const loading = ref(false)
const dataList = ref<QaPair[]>([])
const searchKeyword = ref('')
const filterStatus = ref('')
const filterSource = ref('')

// 分页（使用统一 composable）
const { currentPage, pageSize, total, handleCurrentChange, handleSizeChange, reset } = usePagination(10)

// 导入对话框
const showImportDialog = ref(false)

// 对话框
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})

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
]
const STATUS_LABELS: Record<string, string> = { draft: '草稿', confirmed: '已确认', active: '启用', disabled: '停用' }
const STATUS_COLORS: Record<string, string> = { draft: 'info', confirmed: 'warning', active: 'success', disabled: 'danger' }
const SOURCE_LABELS: Record<string, string> = { manual: '手动录入', ai: 'AI 提炼' }

// 过滤全量集（筛选/搜索命中行）——批量确认的「全选=筛选结果全选」语义以它为域
const filteredAll = computed(() => {
  let list = dataList.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(q => q.question.toLowerCase().includes(kw) || q.answer.toLowerCase().includes(kw))
  }
  if (filterStatus.value) list = list.filter(q => q.status === filterStatus.value)
  if (filterSource.value) list = list.filter(q => q.source === filterSource.value)
  return list
})

const filteredList = computed(() => {
  total.value = filteredAll.value.length
  const start = (currentPage.value - 1) * pageSize.value
  return filteredAll.value.slice(start, start + pageSize.value)
})

// 批量确认（qa-pairs 链 2026-11-02：REQ-B04/B05，交互语义见 docs/ui/ui.md「qa-pairs 链」）
const { hasKBPermission } = useAuth()
const canBatchConfirm = computed(() => hasKBPermission(props.kbId, 'kb:edit'))
const selectedIds = ref<string[]>([])
const batchConfirming = ref(false)
const selectedRows = computed(() => filteredAll.value.filter(q => selectedIds.value.includes(q.id)))
const draftSelectedCount = computed(() => selectedRows.value.filter(q => q.status === 'draft').length)
const confirmedSelectedCount = computed(() => selectedRows.value.length - draftSelectedCount.value)
const allFilteredSelected = computed(() =>
  filteredAll.value.length > 0 && filteredAll.value.every(q => selectedIds.value.includes(q.id)))
const someFilteredSelected = computed(() =>
  filteredAll.value.some(q => selectedIds.value.includes(q.id)) && !allFilteredSelected.value)

function toggleSelectAllFiltered(v: boolean) {
  const ids = filteredAll.value.map(q => q.id)
  // 全选：并入筛选结果（保留筛选外跨页选中）；取消：仅移除筛选结果命中项
  selectedIds.value = v
    ? Array.from(new Set([...selectedIds.value, ...ids]))
    : selectedIds.value.filter(id => !ids.includes(id))
}

function toggleRow(id: string, v: boolean) {
  selectedIds.value = v ? [...selectedIds.value, id] : selectedIds.value.filter(x => x !== id)
}

async function handleBatchConfirm() {
  const totalRows = selectedRows.value.length
  try {
    await ElMessageBox.confirm(
      `共 ${totalRows} 条，其中 ${confirmedSelectedCount.value} 条已确认将跳过，本次确认 ${draftSelectedCount.value} 条。确认操作不可撤销，是否继续？`,
      '批量确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch { return /* 用户取消，静默 */ }
  batchConfirming.value = true
  try {
    const res: any = await api.batchConfirmQaPairs(
      props.kbId,
      selectedRows.value.map(q => q.id),
    )
    ElMessage.success(`已确认 ${res?.confirmedCount ?? 0} 条，跳过 ${res?.skippedCount ?? 0} 条`)
    selectedIds.value = []
    loadData()
  } catch {} // 失败提示由 request 拦截器统一弹出（全局规范：错误文案取 e.message）
  finally { batchConfirming.value = false }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await api.getQaPairs(props.kbId)
    const list = res?.list || res || []
    dataList.value = list
    total.value = res?.total ?? list.length
    selectedIds.value = [] // 数据集已更替，陈旧选中一并清空
    reset()
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
    await api.deleteQaPair(props.kbId, row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
}
async function handleConfirm(row: QaPair) {
  await api.confirmQaPair(props.kbId, row.id)
  ElMessage.success('已确认')
  loadData()
}
async function handleSave() {
  if (!formData.value.question) { ElMessage.warning('请输入问题'); return }
  if (!formData.value.answer) { ElMessage.warning('请输入答案'); return }
  try {
    if (editingId.value) {
      await api.updateQaPair(props.kbId, editingId.value, {
        question: formData.value.question,
        answer: formData.value.answer,
      })
      ElMessage.success('更新成功')
    } else {
      await api.createQaPair(props.kbId, {
        question: formData.value.question,
        answer: formData.value.answer,
      })
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadData()
  } catch {}
}

function handleSearch() { currentPage.value = 1 }
</script>

<template>
  <div v-loading="loading">
    <div class="section-header">
      <div class="section-title">问答对管理</div>
      <div class="header-actions">
        <el-button size="small" @click="showImportDialog = true">
          <el-icon class="el-icon--left"><Upload /></el-icon>
          导入 Excel
        </el-button>
        <el-button type="primary" size="small" @click="handleAdd">手动添加</el-button>
      </div>
    </div>

    <!-- 导入对话框 -->
    <QaImportDialog
      v-model:visible="showImportDialog"
      :kb-id="kbId"
      @success="loadData"
    />

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input v-model="searchKeyword" placeholder="搜索问题/答案..." clearable style="width: 220px" @input="handleSearch" />
      <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
        <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="filterSource" placeholder="来源" clearable style="width: 120px" @change="handleSearch">
        <el-option label="手动录入" value="manual" />
        <el-option label="AI 提炼" value="ai" />
      </el-select>
      <span style="color: #909399; font-size: 13px">共 {{ total }} 条</span>
    </div>

    <!-- 批量操作栏（qa-pairs 链：选中后浮现，沿 FileManager 批量操作栏形态） -->
    <div v-if="canBatchConfirm && selectedIds.length > 0" class="qa-panel__bulk-bar">
      <span class="qa-panel__bulk-count">已选 {{ selectedIds.length }} 条</span>
      <el-button
        type="primary"
        size="small"
        :disabled="draftSelectedCount === 0"
        :loading="batchConfirming"
        :title="draftSelectedCount === 0 ? '所选均为已确认状态' : `确认 ${draftSelectedCount} 条，跳过 ${confirmedSelectedCount} 条`"
        @click="handleBatchConfirm"
      >
        批量确认
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="filteredList" stripe size="small">
      <el-table-column v-if="canBatchConfirm" width="44" align="center">
        <template #header>
          <el-checkbox
            :model-value="allFilteredSelected"
            :indeterminate="someFilteredSelected"
            @change="toggleSelectAllFiltered"
          />
        </template>
        <template #default="{ row }">
          <el-checkbox
            :model-value="selectedIds.includes(row.id)"
            @change="(v: boolean) => toggleRow(row.id, v)"
          />
        </template>
      </el-table-column>
      <el-table-column type="index" width="50" />
      <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="answer" label="答案" min-width="250" show-overflow-tooltip />
      <el-table-column label="来源文档" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.fileName">{{ row.fileName }}</span>
          <span v-else-if="row.fileId" style="color: #909399">文件 {{ row.fileId }}</span>
          <span v-else style="color: #c0c4cc">—</span>
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
      <el-table-column prop="createdAt" label="创建时间" width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'draft'" link type="success" size="small" @click="handleConfirm(row)">确认</el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页（规范格式） -->
    <div class="qa-panel__pagination" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
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
  </div>
</template>

<style scoped lang="scss">
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  .section-title {
    font-size: 15px;
    font-weight: 600;
  }
  .header-actions {
    display: flex;
    gap: 8px;
  }
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.qa-panel__bulk-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;

  .qa-panel__bulk-count {
    font-size: 13px;
    color: #606266;
  }
}

.qa-panel__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
