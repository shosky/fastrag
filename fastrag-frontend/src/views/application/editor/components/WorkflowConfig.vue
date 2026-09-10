<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string; name: string } }>()
const router = useRouter()

const loading = ref(false)
const workflows = ref<any[]>([])
const boundIds = ref<string[]>([])

const boundSet = computed(() => new Set(boundIds.value))
const boundCount = computed(() => workflows.value.filter(w => boundSet.value.has(w.id)).length)

const NODE_TYPE_MAP: Record<string, string> = {
  start: '开始', end: '结束', llm: '大模型', kb_retrieval: '知识库检索',
  intent: '意图识别', selector: '选择器', function_request: '功能请求', sub_workflow: '子工作流',
}

async function loadAll() {
  loading.value = true
  try {
    const [list, cfg]: any[] = await Promise.all([
      api.getWorkflows(),
      api.getAppWorkflowConfig(props.appInfo.id).catch(() => null),
    ])
    workflows.value = Array.isArray(list) ? list : []
    boundIds.value = Array.isArray(cfg?.workflowIds) ? cfg.workflowIds.map(String) : []
  } finally { loading.value = false }
}

// ===== 创建工作流 =====
const showCreateDialog = ref(false)
const createForm = ref({ name: '', description: '' })

async function handleCreateWorkflow() {
  if (!createForm.value.name) { ElMessage.warning('请输入工作流名称'); return }
  try {
    const wf: any = await api.createWorkflow(createForm.value)
    await loadAll()
    // 创建后自动绑定到本应用
    if (wf?.id && !boundSet.value.has(wf.id)) await bindWorkflows([wf.id])
    ElMessage.success('工作流已创建并绑定到本应用')
    showCreateDialog.value = false
    createForm.value = { name: '', description: '' }
  } catch (e: any) { ElMessage.error(e?.message || '创建失败') }
}

// ===== 添加已有工作流（绑定到应用） =====
const showAddDialog = ref(false)
const addSelection = ref<string[]>([])

const unboundWorkflows = computed(() => workflows.value.filter(w => !boundSet.value.has(w.id)))

async function bindWorkflows(ids: string[]) {
  const merged = [...new Set([...boundIds.value, ...ids])]
  const res: any = await api.saveAppWorkflowConfig(props.appInfo.id, { workflowIds: merged })
  boundIds.value = Array.isArray(res?.workflowIds) ? res.workflowIds.map(String) : merged
}

function handleOpenAddDialog() {
  addSelection.value = []
  showAddDialog.value = true
}

async function handleConfirmAdd() {
  if (!addSelection.value.length) { ElMessage.warning('请选择要添加的工作流'); return }
  try {
    await bindWorkflows(addSelection.value)
    ElMessage.success(`已添加 ${addSelection.value.length} 个工作流到本应用`)
    showAddDialog.value = false
  } catch (e: any) { ElMessage.error(e?.message || '添加失败') }
}

async function handleUnbind(row: any) {
  try {
    await ElMessageBox.confirm(`确定从本应用移除工作流「${row.name}」？工作流本身不会被删除。`, '确认', { type: 'warning' })
    const merged = boundIds.value.filter(id => id !== row.id)
    const res: any = await api.saveAppWorkflowConfig(props.appInfo.id, { workflowIds: merged })
    boundIds.value = Array.isArray(res?.workflowIds) ? res.workflowIds.map(String) : merged
    ElMessage.success('已从应用移除')
  } catch {}
}

// ===== 编辑工作流 =====
const showEditDialog = ref(false)
const editingWorkflow = ref<any>(null)
const editForm = ref({ name: '', description: '' })

function handleEdit(row: any) {
  editingWorkflow.value = row
  editForm.value = { name: row.name, description: row.description || '' }
  showEditDialog.value = true
}

async function handleSaveEdit() {
  if (!editForm.value.name) { ElMessage.warning('请输入工作流名称'); return }
  try {
    await api.updateWorkflow(editingWorkflow.value.id, editForm.value)
    await loadAll()
    ElMessage.success('工作流已更新')
    showEditDialog.value = false
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
}

// ===== 删除工作流 =====
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除工作流「${row.name}」？删除后不可恢复。`, '确认', { type: 'warning' })
    await api.deleteWorkflow(row.id)
    boundIds.value = boundIds.value.filter(id => id !== row.id)
    await loadAll()
    ElMessage.success('已删除')
  } catch {}
}

// ===== 查看工作流详情 =====
const showDetailDrawer = ref(false)
const detailWorkflow = ref<any>(null)
const detailNodes = ref<any[]>([])

async function handleViewDetail(row: any) {
  detailWorkflow.value = row
  detailNodes.value = []
  showDetailDrawer.value = true
  try {
    const [detail, nodes]: any[] = await Promise.all([
      api.getWorkflowDetail(row.id),
      api.getWorkflowNodes(row.id),
    ])
    if (detail && typeof detail === 'object') detailWorkflow.value = { ...row, ...detail }
    detailNodes.value = Array.isArray(nodes) ? nodes : []
  } catch {}
}

// ===== 配置工作流节点（跳转画布） =====
function handleConfigNodes(row: any) {
  router.push({ path: `/application/${props.appInfo.id}/workflow-manage`, query: { wfId: row.id } })
}

// ===== 发布 =====
async function handlePublish(row: any) {
  try {
    await api.publishWorkflow(row.id)
    await loadAll()
    ElMessage.success(`「${row.name}」已发布`)
  } catch (e: any) { ElMessage.error(e?.message || '发布失败') }
}

function formatTime(t?: string) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

onMounted(loadAll)
</script>

<template>
  <div class="config-section" v-loading="loading">
    <h3>工作流配置</h3>
    <p class="desc">管理本应用的工作流：创建新工作流、添加已有工作流到本应用、编辑/删除工作流、配置节点与查看详情。</p>

    <div class="wf-toolbar">
      <div>
        <el-button type="primary" size="small" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon>创建工作流
        </el-button>
        <el-button size="small" @click="handleOpenAddDialog">
          <el-icon><Plus /></el-icon>添加已有工作流
        </el-button>
      </div>
      <el-tag type="info" size="small">已绑定 {{ boundCount }} 个工作流</el-tag>
    </div>

    <el-table :data="workflows" border stripe size="small" style="width: 100%">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="description" label="描述" show-overflow-tooltip min-width="180" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'published' ? 'success' : 'info'" size="small">
            {{ row.status === 'published' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="绑定状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="boundSet.has(row.id) ? 'success' : 'info'" size="small">
            {{ boundSet.has(row.id) ? '已绑定' : '未绑定' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleViewDetail(row)">详情</el-button>
          <el-button link type="primary" size="small" @click="handleConfigNodes(row)">配置节点</el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button v-if="row.status !== 'published'" link type="success" size="small" @click="handlePublish(row)">发布</el-button>
          <el-button v-if="boundSet.has(row.id)" link type="warning" size="small" @click="handleUnbind(row)">移除</el-button>
          <el-button v-else link type="success" size="small" @click="bindWorkflows([row.id])">绑定</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !workflows.length" description="暂无工作流，点击上方「创建工作流」开始" :image-size="60" />

    <!-- 创建工作流 -->
    <el-dialog v-model="showCreateDialog" title="创建工作流" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="如：智能问答工作流" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="工作流用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateWorkflow">创建并绑定</el-button>
      </template>
    </el-dialog>

    <!-- 添加已有工作流 -->
    <el-dialog v-model="showAddDialog" title="添加已有工作流" width="560px">
      <el-table
        :data="unboundWorkflows"
        stripe size="small"
        max-height="360"
        @selection-change="(rows: any[]) => addSelection = rows.map(r => r.id)"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip min-width="170" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'published' ? 'success' : 'info'" size="small">
              {{ row.status === 'published' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!unboundWorkflows.length" description="没有可添加的工作流（均已绑定）" :image-size="50" />
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!addSelection.length" @click="handleConfirmAdd">
          添加（{{ addSelection.length }}）
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑工作流 -->
    <el-dialog v-model="showEditDialog" title="编辑工作流" width="480px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 工作流详情 -->
    <el-drawer v-model="showDetailDrawer" :title="`工作流详情：${detailWorkflow?.name || ''}`" size="480px">
      <template v-if="detailWorkflow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="ID">{{ detailWorkflow.id }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detailWorkflow.name }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ detailWorkflow.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailWorkflow.status === 'published' ? 'success' : 'info'" size="small">
              {{ detailWorkflow.status === 'published' ? '已发布' : '草稿' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="节点数">{{ detailNodes.length }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detailWorkflow.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(detailWorkflow.updatedAt) }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin:16px 0 8px">节点列表</h4>
        <el-table :data="detailNodes" border stripe size="small">
          <el-table-column prop="nodeKey" label="Key" min-width="110" show-overflow-tooltip />
          <el-table-column prop="name" label="名称" min-width="100" show-overflow-tooltip />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ NODE_TYPE_MAP[row.nodeType] || row.nodeType }}</template>
          </el-table-column>
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="(row.enabled ?? 1) ? 'success' : 'info'" size="small">
                {{ (row.enabled ?? 1) ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!detailNodes.length" description="暂无节点，点击「配置节点」添加" :image-size="50" />
      </template>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.wf-toolbar {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
}
</style>
