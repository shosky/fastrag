<script setup lang="ts">
import { ref, onMounted } from 'vue'
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
  if (!editList.value.length) {
    editList.value = [
      { id: 'e1', title: '新增云电脑产品FAQ', content: '整理云电脑产品的常见问题及解答，包括部署、使用、运维等方面', editType: 'create', status: 'draft', editor: '张编辑', tags: '云电脑,FAQ', createdAt: '2026-06-29 10:00:00' },
      { id: 'e2', title: '更新ICT办理流程', content: '根据最新政策更新小微企业ICT业务办理流程，增加线上申请渠道说明', editType: 'update', status: 'submitted', editor: '李采编', tags: 'ICT,流程', createdAt: '2026-06-28 15:30:00' },
      { id: 'e3', title: '合并产品目录与定价表', content: '将企业宽带、云电脑、专线三类产品的目录与定价合并为一份文档', editType: 'merge', status: 'approved', editor: '王编辑', reviewer: '赵主管', tags: '产品,定价', createdAt: '2026-06-27 09:00:00' },
      { id: 'e4', title: '拆分技术方案文档', content: '将综合技术方案拆分为网络方案、安全方案、运维方案三篇独立文档', editType: 'split', status: 'submitted', editor: '陈技术', tags: '技术方案', createdAt: '2026-06-26 14:20:00' },
      { id: 'e5', title: '修订安全规范', content: '根据最新安全生产法修订施工安全规范相关条款，增加高空作业安全要求', editType: 'update', status: 'rejected', editor: '刘安全', reviewer: '周审核', tags: '安全,规范', createdAt: '2026-06-25 11:00:00' },
      { id: 'e6', title: '新增5G行业案例集', content: '收集整理5G在智慧港口、智慧工厂、智慧医疗等行业的典型应用案例', editType: 'create', status: 'draft', editor: '张编辑', tags: '5G,案例', createdAt: '2026-06-24 16:45:00' },
    ]
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
  if (!validateList.value.length) {
    validateList.value = [
      { id: 'v1', validateType: 'duplicate', targetScope: '知识库全部文档', totalCount: 156, passedCount: 148, warningCount: 6, failedCount: 2, status: '已完成', completedAt: '2026-06-29 10:00:00' },
      { id: 'v2', validateType: 'expired', targetScope: '产品资料分类', totalCount: 42, passedCount: 35, warningCount: 5, failedCount: 2, status: '已完成', completedAt: '2026-06-28 16:30:00' },
      { id: 'v3', validateType: 'quality', targetScope: '技术文档分类', totalCount: 68, passedCount: 52, warningCount: 12, failedCount: 4, status: '已完成', completedAt: '2026-06-27 14:00:00' },
      { id: 'v4', validateType: 'consistency', targetScope: '业务流程分类', totalCount: 28, passedCount: 22, warningCount: 4, failedCount: 2, status: '进行中', completedAt: '' },
    ]
  }
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

onMounted(() => {
  loadEdits()
  loadValidates()
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
              <template #default="{ row }"><el-tag type="success" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="completedAt" label="完成时间" width="160" />
          </el-table>
          <el-empty v-if="!validateList.length" description="暂无校验记录" />
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
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
</style>
