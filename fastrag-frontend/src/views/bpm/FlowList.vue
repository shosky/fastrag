<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import * as bpm from '@/api/bpm'
import {
  FLOW_VISIBILITY_LABELS, TRIGGER_TYPE_LABELS,
  VERSION_STATUS_LABELS, VERSION_STATUS_TAG_TYPES,
  type FlowDefVO, type FlowDefPageReq, type FlowDefRequest,
  type ImportFlowRequest,
  type FlowVisibility, type TriggerType, type PageResult,
} from '@/types/bpm'

const router = useRouter()
const loading = ref(false)
const list = ref<FlowDefVO[]>([])
const total = ref(0)

// ============================================================================
// 查询条件
// ============================================================================
const query = reactive<FlowDefPageReq>({
  page: 1,
  size: 20,
  keyword: '',
  category: '',
  visibility: undefined,
  mineOnly: true,
})

async function loadList() {
  loading.value = true
  try {
    const res = (await bpm.pageFlowDefs(query)) as unknown as PageResult<FlowDefVO>
    list.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
    list.value = []
    total.value = 0
  } finally { loading.value = false }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleReset() {
  query.keyword = ''
  query.category = ''
  query.visibility = undefined
  query.mineOnly = true
  query.page = 1
  loadList()
}

// ============================================================================
// 创建/编辑 流程
// ============================================================================
const showCreate = ref(false)
const editingFlow = ref<FlowDefVO | null>(null)
const createForm = reactive<FlowDefRequest>({
  name: '',
  description: '',
  category: '',
  visibility: 'private_flow',
  triggerType: 'manual',
  timeoutMs: 24 * 60 * 60 * 1000,
  logSnapshotEnabled: true,
})

function resetCreateForm() {
  createForm.name = ''
  createForm.description = ''
  createForm.category = ''
  createForm.visibility = 'private_flow'
  createForm.triggerType = 'manual'
  createForm.timeoutMs = 24 * 60 * 60 * 1000
  createForm.logSnapshotEnabled = true
}

function openCreate() {
  editingFlow.value = null
  resetCreateForm()
  showCreate.value = true
}

function openEdit(row: any) {
  editingFlow.value = row
  createForm.name = row.name
  createForm.description = row.description || ''
  createForm.category = row.category || ''
  createForm.visibility = row.visibility
  createForm.triggerType = row.triggerType || 'manual'
  createForm.timeoutMs = row.timeoutMs
  createForm.logSnapshotEnabled = row.logSnapshotEnabled
  showCreate.value = true
}

async function submitCreate() {
  if (!createForm.name) { ElMessage.warning('请输入流程名称'); return }
  try {
    if (editingFlow.value) {
      await bpm.updateFlowDef(editingFlow.value.id, createForm)
      ElMessage.success('已更新')
    } else {
      const created = await bpm.createFlowDef(createForm)
      ElMessage.success('已创建')
      // 跳转到画布编辑
      router.push({ name: 'BpmCanvas', params: { flowDefId: created.id } })
      return
    }
    showCreate.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ============================================================================
// 删除 / 复制
// ============================================================================
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？关联版本/实例可能受影响`, '确认删除', { type: 'warning' })
    await bpm.deleteFlowDef(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function handleCopy(row: any) {
  try {
    const { value: name } = await ElMessageBox.prompt('新流程名称', '复制流程', {
      inputValue: `${row.name} - 副本`,
      confirmButtonText: '复制',
      cancelButtonText: '取消',
    })
    if (!name) return
    await bpm.copyFlowDef(row.id, name)
    ElMessage.success('已复制')
    loadList()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

// ============================================================================
// 跳转到画布
// ============================================================================
function handleEditCanvas(row: any) {
  if (!row.currentVersionId) {
    ElMessage.warning('该流程还没有版本,请先创建草稿版本')
    return
  }
  router.push({ name: 'BpmCanvas', params: { flowDefId: row.id, versionId: row.currentVersionId } })
}

function handleVersions(row: any) {
  router.push({ name: 'BpmVersion', params: { flowDefId: row.id } })
}

function handleMonitor(row: any) {
  router.push({ name: 'BpmInstance', query: { flowDefId: row.id } })
}

function handlePermissions(row: any) {
  selectedFlow.value = row
  showPermission.value = true
  loadPermissions()
}

function handleTestCases(row: any) {
  router.push({ name: 'BpmTestCase', params: { flowDefId: row.id } })
}

function handleStats(row: any) {
  router.push({ name: 'BpmStats', params: { flowDefId: row.id } })
}

function handleExport(row: any) {
  bpm.exportFlow(row.id).then((res) => {
    const blob = new Blob([JSON.stringify(res, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.name}-export.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出')
  }).catch((e) => ElMessage.error(e?.message || '导出失败'))
}

// ============================================================================
// 导入
// ============================================================================
const showImport = ref(false)
const importText = ref('')
const importNewName = ref('')
const importOverwrite = ref(false)

function openImport() {
  importText.value = ''
  importNewName.value = ''
  importOverwrite.value = false
  showImport.value = true
}

function handleImportFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => { importText.value = String(reader.result || '') }
  reader.readAsText(file)
}

async function submitImport() {
  if (!importText.value) { ElMessage.warning('请粘贴或选择 JSON 文件'); return }
  try {
    const body: ImportFlowRequest = {
      payload: importText.value,
      newName: importNewName.value || undefined,
      overwrite: importOverwrite.value,
    }
    const res = await bpm.importFlow(body)
    ElMessage.success('导入成功')
    showImport.value = false
    loadList()
    router.push({ name: 'BpmCanvas', params: { flowDefId: res.flowDefId } })
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  }
}

// ============================================================================
// 权限对话框
// ============================================================================
const showPermission = ref(false)
const selectedFlow = ref<FlowDefVO | null>(null)
const permissionList = ref<any[]>([])
const permForm = reactive({ subjectType: 'user' as 'user' | 'role', subjectId: '', permission: 'view' as any })

async function loadPermissions() {
  if (!selectedFlow.value) return
  try {
    permissionList.value = await bpm.listPermissions(selectedFlow.value.id)
  } catch { permissionList.value = [] }
}

async function submitGrant() {
  if (!selectedFlow.value || !permForm.subjectId) { ElMessage.warning('请输入主体ID'); return }
  try {
    await bpm.grantPermission(selectedFlow.value.id, permForm)
    ElMessage.success('已授权')
    loadPermissions()
  } catch (e: any) { ElMessage.error(e?.message || '授权失败') }
}

async function handleRevoke(row: any) {
  try {
    if (!selectedFlow.value) return
    await bpm.revokePermission(selectedFlow.value.id, {
      subjectType: row.subjectType,
      subjectId: row.subjectId,
      permission: row.permission,
    })
    ElMessage.success('已撤销')
    loadPermissions()
  } catch (e: any) { ElMessage.error(e?.message || '撤销失败') }
}

const PERM_LABELS: Record<string, string> = {
  view: '查看', edit: '编辑', execute: '执行', publish: '发布', delete: '删除',
}

// ============================================================================
// 模板对话框
// ============================================================================
const showTemplates = ref(false)
const templateList = ref<any[]>([])

async function openTemplates() {
  showTemplates.value = true
  try {
    templateList.value = await bpm.listTemplates({ builtinOnly: false })
  } catch { templateList.value = [] }
}

async function applyTemplate(id: string) {
  try {
    const created = await bpm.applyTemplate(id)
    ElMessage.success('已从模板创建流程')
    showTemplates.value = false
    loadList()
    router.push({ name: 'BpmCanvas', params: { flowDefId: created.id } })
  } catch (e: any) { ElMessage.error(e?.message || '应用模板失败') }
}

// ============================================================================
// 格式化
// ============================================================================
function formatDate(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').substring(0, 19)
}

const visibilityOptions = computed(() =>
  (Object.entries(FLOW_VISIBILITY_LABELS) as Array<[FlowVisibility, string]>).map(([value, label]) => ({ value, label })),
)
const triggerOptions = computed(() =>
  (Object.entries(TRIGGER_TYPE_LABELS) as Array<[TriggerType, string]>).map(([value, label]) => ({ value, label })),
)

onMounted(loadList)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- ====== 顶部操作栏 ====== -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="query.keyword" placeholder="搜索名称/描述" clearable style="width:220px" @keyup.enter="handleSearch" />
        <el-select v-model="query.category" placeholder="分类" clearable style="width:140px">
          <el-option label="问答" value="问答" />
          <el-option label="对话" value="对话" />
          <el-option label="数据处理" value="数据处理" />
          <el-option label="审批" value="审批" />
          <el-option label="自定义" value="自定义" />
        </el-select>
        <el-select v-model="query.visibility" placeholder="可见性" clearable style="width:140px">
          <el-option v-for="o in visibilityOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-checkbox v-model="query.mineOnly">仅我的</el-checkbox>
        <el-button type="primary" @click="handleSearch">🔍 查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <div class="toolbar-right">
        <el-button @click="openTemplates">📋 模板</el-button>
        <el-button @click="openImport">📥 导入</el-button>
        <el-button type="primary" @click="openCreate">＋ 创建业务流程</el-button>
      </div>
    </div>

    <!-- ====== 流程表格 ====== -->
    <el-table :data="list" stripe border max-height="640" empty-text="暂无业务流程">
      <el-table-column type="index" label="#" width="48" />
      <el-table-column prop="name" label="流程名称" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="handleEditCanvas(row)">{{ row.name }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="90" />
      <el-table-column label="当前版本" width="160">
        <template #default="{ row }">
          <el-tag v-if="row.currentVersionNo" :type="VERSION_STATUS_TAG_TYPES[row.currentVersionStatus as keyof typeof VERSION_STATUS_TAG_TYPES] || 'info'" size="small">
            v{{ row.currentVersionNo }} · {{ VERSION_STATUS_LABELS[row.currentVersionStatus as keyof typeof VERSION_STATUS_LABELS] || row.currentVersionStatus }}
          </el-tag>
          <span v-else style="color:#909399">未创建</span>
        </template>
      </el-table-column>
      <el-table-column label="可见性" width="80">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ FLOW_VISIBILITY_LABELS[row.visibility as keyof typeof FLOW_VISIBILITY_LABELS] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="触发" width="90">
        <template #default="{ row }">
          <span style="font-size:12px">{{ TRIGGER_TYPE_LABELS[row.triggerType as keyof typeof TRIGGER_TYPE_LABELS] || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="节点/边" width="90" align="center">
        <template #default="{ row }">
          <span style="font-size:12px">{{ row.nodeCount || 0 }} / {{ row.edgeCount || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="160">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEditCanvas(row)">画布</el-button>
          <el-button link type="primary" size="small" @click="handleVersions(row)">版本</el-button>
          <el-button link type="primary" size="small" @click="handleMonitor(row)">监控</el-button>
          <el-button link type="primary" size="small" @click="handleTestCases(row)">用例</el-button>
          <el-button link type="primary" size="small" @click="handleStats(row)">统计</el-button>
          <el-dropdown size="small" trigger="click">
            <el-button link type="primary" size="small">更多 ▾</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openEdit(row)">编辑属性</el-dropdown-item>
                <el-dropdown-item @click="handleCopy(row)">复制</el-dropdown-item>
                <el-dropdown-item @click="handlePermissions(row)">权限</el-dropdown-item>
                <el-dropdown-item @click="handleExport(row)">导出 JSON</el-dropdown-item>
                <el-dropdown-item divided @click="handleDelete(row)">
                  <span style="color:#F56C6C">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <!-- ====== 分页 ====== -->
    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadList"
        @size-change="loadList"
      />
    </div>

    <!-- ====== 创建/编辑 流程对话框 ====== -->
    <el-dialog v-model="showCreate" :title="editingFlow ? '编辑业务流程' : '创建业务流程'" width="540px">
      <el-form label-width="100px">
        <el-form-item label="流程名称" required>
          <el-input v-model="createForm.name" maxlength="128" placeholder="如：智能问答工作流" show-word-limit />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="createForm.category" maxlength="64" placeholder="如：问答 / 数据处理" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" maxlength="4000" show-word-limit />
        </el-form-item>
        <el-form-item label="可见性">
          <el-select v-model="createForm.visibility" style="width:200px">
            <el-option v-for="o in visibilityOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发方式">
          <el-select v-model="createForm.triggerType" style="width:200px">
            <el-option v-for="o in triggerOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时时间">
          <el-input-number v-model="createForm.timeoutMs" :min="0" :step="60000" style="width:200px" />
          <span style="margin-left:8px;color:#909399;font-size:12px">毫秒,0 表示不限(默认 24h)</span>
        </el-form-item>
        <el-form-item label="记录快照">
          <el-switch v-model="createForm.logSnapshotEnabled" />
          <span style="margin-left:8px;color:#909399;font-size:12px">开启后实例日志将保存节点输入输出快照</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">{{ editingFlow ? '保存' : '创建并进入画布' }}</el-button>
      </template>
    </el-dialog>

    <!-- ====== 导入对话框 ====== -->
    <el-dialog v-model="showImport" title="导入业务流程 JSON" width="640px">
      <el-form label-width="100px">
        <el-form-item label="选择文件">
          <input type="file" accept=".json" @change="handleImportFile" />
        </el-form-item>
        <el-form-item label="或粘贴 JSON">
          <el-input v-model="importText" type="textarea" :rows="10" placeholder="粘贴从其他流程导出的 JSON 字符串" />
        </el-form-item>
        <el-form-item label="新流程名">
          <el-input v-model="importNewName" placeholder="不填则沿用导出名 + ' 导入'" />
        </el-form-item>
        <el-form-item label="覆盖同名">
          <el-switch v-model="importOverwrite" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showImport = false">取消</el-button>
        <el-button type="primary" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- ====== 模板对话框 ====== -->
    <el-dialog v-model="showTemplates" title="流程模板" width="780px">
      <div v-if="!templateList.length" class="empty-tip">暂无可用模板</div>
      <div v-else class="tpl-grid">
        <div v-for="t in templateList" :key="t.id" class="tpl-card" @click="applyTemplate(t.id)">
          <div class="tpl-name">{{ t.name }}</div>
          <el-tag v-if="t.isBuiltin" size="small" type="success">内置</el-tag>
          <div class="tpl-desc">{{ t.description || '-' }}</div>
          <div class="tpl-meta">
            <span>分类: {{ t.category || '-' }}</span>
            <span v-if="t.createdAt">{{ formatDate(t.createdAt) }}</span>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- ====== 权限对话框 ====== -->
    <el-dialog v-model="showPermission" :title="`权限管理 - ${selectedFlow?.name || ''}`" width="720px">
      <el-form label-width="80px" inline>
        <el-form-item label="主体类型">
          <el-select v-model="permForm.subjectType" style="width:120px">
            <el-option label="用户" value="user" />
            <el-option label="角色" value="role" />
          </el-select>
        </el-form-item>
        <el-form-item label="主体ID">
          <el-input v-model="permForm.subjectId" placeholder="用户ID或角色ID" style="width:180px" />
        </el-form-item>
        <el-form-item label="权限">
          <el-select v-model="permForm.permission" style="width:140px">
            <el-option v-for="(v, k) in PERM_LABELS" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitGrant">授权</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="permissionList" stripe size="small" empty-text="暂无授权">
        <el-table-column prop="subjectType" label="主体类型" width="100" />
        <el-table-column prop="subjectId" label="主体ID" width="200" />
        <el-table-column label="权限" width="120">
          <template #default="{ row }">{{ PERM_LABELS[row.permission] || row.permission }}</template>
        </el-table-column>
        <el-table-column prop="grantedBy" label="授权人" width="120" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="handleRevoke(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-container { padding: $spacing-base; }

.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px; flex-wrap: wrap; gap: 8px;
}
.toolbar-left, .toolbar-right { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

.pager { display: flex; justify-content: flex-end; margin-top: 12px; }

.tpl-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.tpl-card {
  border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; cursor: pointer;
  transition: all 0.15s; background: #fff;
  &:hover { border-color: #409EFF; box-shadow: 0 2px 8px rgba(64,158,255,0.15); }
}
.tpl-name { font-weight: 600; font-size: 14px; margin-bottom: 4px; }
.tpl-desc { font-size: 12px; color: #606266; margin: 6px 0; min-height: 32px; }
.tpl-meta { font-size: 11px; color: #909399; display: flex; justify-content: space-between; }
.empty-tip { padding: 40px 0; text-align: center; color: #909399; }
</style>