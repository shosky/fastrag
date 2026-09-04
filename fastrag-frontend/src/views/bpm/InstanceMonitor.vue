<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as bpm from '@/api/bpm'
import {
  INSTANCE_STATUS_LABELS, INSTANCE_STATUS_TAG_TYPES,
  type InstanceVO, type InstanceEventVO,
  type PageResult, type InstanceListReq,
} from '@/types/bpm'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const list = ref<InstanceVO[]>([])
const total = ref(0)
const query = reactive<InstanceListReq>({
  page: 1, size: 20, flowDefId: '', status: undefined, keyword: '', startUserId: '',
})

function syncQueryFromRoute() {
  if (route.query.flowDefId) query.flowDefId = String(route.query.flowDefId)
  if (route.query.status) query.status = String(route.query.status) as any
  if (route.query.keyword) query.keyword = String(route.query.keyword)
  if (route.query.instanceId) {
    openDetail(String(route.query.instanceId))
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = (await bpm.listInstances(query)) as unknown as PageResult<InstanceVO>
    list.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
    list.value = []
    total.value = 0
  } finally { loading.value = false }
}

function handleSearch() { query.page = 1; loadList() }
function handleReset() {
  query.keyword = ''; query.status = undefined; query.flowDefId = ''; query.startUserId = ''; query.page = 1
  loadList()
}

// ============================================================================
// 详情对话框
// ============================================================================
const showDetail = ref(false)
const currentInstance = ref<InstanceVO | null>(null)
const instanceEvents = ref<InstanceEventVO[]>([])
const eventStream = ref<InstanceEventVO[]>([])
let pollTimer: number | null = null

async function openDetail(id: string) {
  loading.value = true
  try {
    const detail = await bpm.getInstance(id)
    const events = await bpm.getInstanceEvents(id)
    currentInstance.value = detail
    instanceEvents.value = events || []
    eventStream.value = [...instanceEvents.value]
    showDetail.value = true
    startPolling(id)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载实例失败')
  } finally { loading.value = false }
}

function startPolling(id: string) {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    try {
      const detail = await bpm.getInstance(id)
      currentInstance.value = detail
      const events = await bpm.getInstanceEvents(id)
      eventStream.value = events || []
    } catch { /* ignore */ }
  }, 5000) as unknown as number
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onBeforeUnmount(stopPolling)

// ============================================================================
// 实例控制
// ============================================================================
async function handlePause(id: string) {
  try {
    await bpm.pauseInstance(id)
    ElMessage.success('已暂停')
    loadList()
    if (currentInstance.value?.id === id) openDetail(id)
  } catch (e: any) { ElMessage.error(e?.message || '暂停失败') }
}

async function handleResume(id: string) {
  try {
    await bpm.resumeInstance(id)
    ElMessage.success('已恢复')
    loadList()
    if (currentInstance.value?.id === id) openDetail(id)
  } catch (e: any) { ElMessage.error(e?.message || '恢复失败') }
}

async function handleCancel(id: string) {
  try {
    const { value: reason } = await ElMessageBox.prompt('终止原因(可选)', '终止实例', {
      confirmButtonText: '终止', cancelButtonText: '取消', inputType: 'textarea',
    })
    await bpm.cancelInstance(id, reason)
    ElMessage.success('已终止')
    loadList()
    if (currentInstance.value?.id === id) openDetail(id)
  } catch (e: any) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

// ============================================================================
// 用户输入表单
// ============================================================================
const showInputForm = ref(false)
const inputForm = reactive({
  token: '',
  fields: [] as Array<{ key: string; label: string; value: any }>,
})

async function openInputForm() {
  if (!currentInstance.value?.pendingInputForm) {
    ElMessage.warning('当前实例未处于等待用户输入状态')
    return
  }
  let parsed: any = []
  try { parsed = JSON.parse(currentInstance.value.pendingInputForm) } catch {}
  if (!Array.isArray(parsed)) parsed = parsed?.fields || []
  inputForm.fields = parsed.map((f: any) => ({ key: f.key, label: f.label, value: '' }))
  inputForm.token = currentInstance.value.pendingInputToken || ''
  showInputForm.value = true
}

async function submitInput() {
  try {
    const inputs: Record<string, any> = {}
    for (const f of inputForm.fields) inputs[f.key] = f.value
    await bpm.submitUserInput({ token: inputForm.token, inputs })
    ElMessage.success('已提交输入')
    showInputForm.value = false
    if (currentInstance.value) openDetail(currentInstance.value.id)
  } catch (e: any) { ElMessage.error(e?.message || '提交失败') }
}

// ============================================================================
// 触发新实例
// ============================================================================
const showTrigger = ref(false)
const triggerForm = reactive({
  flowDefId: '' as string,
  versionNo: undefined as number | undefined,
  inputParams: '{"query":"你好"}',
})

async function openTrigger() {
  showTrigger.value = true
  if (!triggerForm.flowDefId) {
    try {
      const flows: any = await bpm.listSimpleFlows()
      triggerForm.flowDefId = flows?.[0]?.id || ''
    } catch { /* ignore */ }
  }
}

async function submitTrigger() {
  try {
    let inputs: any = {}
    try { inputs = JSON.parse(triggerForm.inputParams) } catch { ElMessage.warning('输入参数 JSON 格式错误'); return }
    const res = await bpm.triggerInstance({
      flowDefId: triggerForm.flowDefId,
      versionNo: triggerForm.versionNo,
      triggerType: 'manual',
      inputParams: inputs,
    })
    ElMessage.success(`已触发实例 ${res.instanceId}`)
    showTrigger.value = false
    openDetail(res.instanceId)
    loadList()
  } catch (e: any) { ElMessage.error(e?.message || '触发失败') }
}

// ============================================================================
// 工具函数
// ============================================================================
function formatDate(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').substring(0, 19)
}

function formatDuration(ms?: number) {
  if (!ms || ms <= 0) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  if (ms < 3600000) return `${Math.floor(ms / 60000)}m${Math.floor((ms % 60000) / 1000)}s`
  return `${Math.floor(ms / 3600000)}h${Math.floor((ms % 3600000) / 60000)}m`
}

function parseJSON(s?: string) {
  if (!s) return {}
  try { return JSON.parse(s) } catch { return {} }
}

function getEventTypeColor(type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  if (type.includes('FAILED')) return 'danger'
  if (type.includes('COMPLETED')) return 'success'
  if (type.includes('PAUSED')) return 'warning'
  if (type.includes('CANCELLED')) return 'info'
  if (type.includes('INPUT')) return 'primary'
  return 'primary'
}

const STATUS_OPTIONS = computed(() =>
  (Object.entries(INSTANCE_STATUS_LABELS) as Array<[string, string]>).map(([value, label]) => ({ value, label })),
)

watch(() => route.query, () => { syncQueryFromRoute(); loadList() }, { deep: true })

onMounted(() => { syncQueryFromRoute(); loadList() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="toolbar">
      <div class="left">
        <el-button text @click="router.push({ name: 'BpmFlowList' })">📂 流程列表</el-button>
        <span class="title">流程实例监控</span>
      </div>
      <el-button type="primary" @click="openTrigger">▶ 触发新实例</el-button>
    </div>

    <div class="filter">
      <el-input v-model="query.keyword" placeholder="实例ID / TraceID" style="width:240px" clearable @keyup.enter="handleSearch" />
      <el-input v-model="query.flowDefId" placeholder="流程ID" style="width:200px" clearable @keyup.enter="handleSearch" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width:140px">
        <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" @click="handleSearch">🔍 查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <el-table :data="list" stripe border size="small" empty-text="暂无实例">
      <el-table-column prop="id" label="实例ID" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openDetail(row.id)">{{ row.id }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="flowDefId" label="流程ID" width="160" show-overflow-tooltip />
      <el-table-column label="版本" width="80">
        <template #default="{ row }">v{{ row.flowVersionNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="(INSTANCE_STATUS_TAG_TYPES as any)[row.status] || 'info'" size="small">
            {{ INSTANCE_STATUS_LABELS[row.status as keyof typeof INSTANCE_STATUS_LABELS] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startUserId" label="启动用户" width="120" />
      <el-table-column label="触发" width="90">
        <template #default="{ row }">{{ row.triggerType || '-' }}</template>
      </el-table-column>
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column label="开始时间" width="160">
        <template #default="{ row }">{{ formatDate(row.startedAt) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" width="160">
        <template #default="{ row }">{{ formatDate(row.finishedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDetail(row.id)">详情</el-button>
          <el-button link type="warning" size="small" @click="handlePause(row.id)" v-if="row.status === 'running'">暂停</el-button>
          <el-button link type="success" size="small" @click="handleResume(row.id)" v-if="row.status === 'paused'">恢复</el-button>
          <el-button link type="danger" size="small" @click="handleCancel(row.id)" v-if="row.status === 'running' || row.status === 'paused'">终止</el-button>
        </template>
      </el-table-column>
    </el-table>

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

    <el-dialog v-model="showDetail" :title="`实例详情 - ${currentInstance?.id || ''}`" width="1100px" top="4vh">
      <div v-if="currentInstance" class="detail-wrap">
        <div class="detail-left">
          <div class="card">
            <div class="card-title">基本信息</div>
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="实例ID">{{ currentInstance.id }}</el-descriptions-item>
              <el-descriptions-item label="TraceID">{{ currentInstance.traceId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="流程">{{ currentInstance.flowDefId }}</el-descriptions-item>
              <el-descriptions-item label="版本">v{{ currentInstance.flowVersionNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="(INSTANCE_STATUS_TAG_TYPES as any)[currentInstance.status] || 'info'" size="small">
                  {{ INSTANCE_STATUS_LABELS[currentInstance.status as keyof typeof INSTANCE_STATUS_LABELS] || currentInstance.status }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="启动用户">{{ currentInstance.startUserId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="触发方式">{{ currentInstance.triggerType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="当前节点">{{ currentInstance.currentNodeKeys || '-' }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ formatDate(currentInstance.startedAt) }}</el-descriptions-item>
              <el-descriptions-item label="结束时间">{{ formatDate(currentInstance.finishedAt) }}</el-descriptions-item>
              <el-descriptions-item label="耗时">{{ formatDuration(currentInstance.durationMs) }}</el-descriptions-item>
              <el-descriptions-item label="超时时间">{{ formatDate(currentInstance.timeoutAt) }}</el-descriptions-item>
              <el-descriptions-item v-if="currentInstance.failureReason" label="失败原因">
                <span style="color:#F56C6C">{{ currentInstance.failureReason }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <div class="card">
            <div class="card-title">控制</div>
            <div class="ctrl-buttons">
              <el-button type="warning" size="small" @click="handlePause(currentInstance.id)" :disabled="currentInstance.status !== 'running'">暂停</el-button>
              <el-button type="success" size="small" @click="handleResume(currentInstance.id)" :disabled="currentInstance.status !== 'paused'">恢复</el-button>
              <el-button type="danger" size="small" @click="handleCancel(currentInstance.id)" :disabled="currentInstance.status !== 'running' && currentInstance.status !== 'paused'">终止</el-button>
              <el-button type="primary" size="small" @click="openInputForm" :disabled="!currentInstance.pendingInputToken">提交用户输入</el-button>
            </div>
          </div>

          <div class="card">
            <div class="card-title">输入参数</div>
            <pre class="json-block">{{ JSON.stringify(parseJSON(currentInstance.inputParams), null, 2) }}</pre>
          </div>

          <div class="card">
            <div class="card-title">输出参数</div>
            <pre class="json-block">{{ JSON.stringify(parseJSON(currentInstance.outputParams), null, 2) }}</pre>
          </div>

          <div class="card" v-if="currentInstance.variables">
            <div class="card-title">上下文变量</div>
            <pre class="json-block">{{ JSON.stringify(parseJSON(currentInstance.variables), null, 2) }}</pre>
          </div>
        </div>

        <div class="detail-right">
          <div class="card">
            <div class="card-title">
              <span>事件流 / 节点执行轨迹</span>
              <el-tag size="small" type="success" effect="plain">5s 自动刷新</el-tag>
            </div>
            <el-timeline>
              <el-timeline-item
                v-for="ev in eventStream" :key="ev.id"
                :type="getEventTypeColor(ev.eventType)" :timestamp="formatDate(ev.occurredAt)" placement="top">
                <div class="event-item">
                  <div class="event-title">{{ ev.eventType }}<span v-if="ev.nodeKey" class="node-key">[{{ ev.nodeKey }}]</span></div>
                  <div v-if="ev.message" class="event-msg">{{ ev.message }}</div>
                  <pre v-if="ev.payload" class="event-payload">{{ JSON.stringify(parseJSON(ev.payload), null, 2) }}</pre>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-if="!eventStream.length" description="暂无事件" :image-size="50" />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showDetail = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showInputForm" title="提交用户输入" width="500px">
      <el-form label-width="100px">
        <el-form-item v-for="f in inputForm.fields" :key="f.key" :label="f.label">
          <el-input v-model="f.value" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showInputForm = false">取消</el-button>
        <el-button type="primary" @click="submitInput">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTrigger" title="触发流程实例" width="540px">
      <el-form label-width="100px">
        <el-form-item label="流程" required>
          <el-input v-model="triggerForm.flowDefId" placeholder="flowDefId" />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input-number v-model="triggerForm.versionNo" :min="1" placeholder="不填则用当前已发布" />
        </el-form-item>
        <el-form-item label="输入参数">
          <el-input v-model="triggerForm.inputParams" type="textarea" :rows="6" placeholder='{"query":"你好"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTrigger = false">取消</el-button>
        <el-button type="primary" @click="submitTrigger">触发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-container { padding: $spacing-base; }

.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.toolbar .left { display: flex; align-items: center; gap: 8px; }
.toolbar .title { font-weight: 600; font-size: 15px; }

.filter { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; align-items: center; }

.pager { display: flex; justify-content: flex-end; margin-top: 12px; }

.detail-wrap { display: flex; gap: 12px; min-height: 500px; }
.detail-left { width: 400px; flex-shrink: 0; display: flex; flex-direction: column; gap: 12px; overflow: auto; max-height: 70vh; }
.detail-right { flex: 1; display: flex; flex-direction: column; gap: 12px; overflow: auto; max-height: 70vh; }

.card { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; }
.card-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between; }
.ctrl-buttons { display: flex; gap: 8px; flex-wrap: wrap; }

.json-block {
  background: #f5f7fa; padding: 8px 12px; border-radius: 4px;
  font-size: 11px; max-height: 200px; overflow: auto; margin: 0;
}

.event-item { font-size: 12px; }
.event-title { font-weight: 600; }
.event-title .node-key { color: #409EFF; margin-left: 4px; font-weight: 400; }
.event-msg { color: #606266; margin-top: 2px; }
.event-payload {
  background: #f5f7fa; padding: 6px 8px; border-radius: 4px;
  font-size: 10px; max-height: 120px; overflow: auto; margin: 4px 0 0;
}
</style>