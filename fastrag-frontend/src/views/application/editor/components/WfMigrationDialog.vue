<script setup lang="ts">
/**
 * 业务流配置迁移弹窗（共用组件）
 * 供两处画布使用：应用级「业务流管理」（workflow-manage.vue）与 BPM 画布编辑（CanvasEditor.vue）。
 * 三合一：设置迁移策略（覆盖/合并 + 目标环境）→ 开始迁移（后端真实复制，前端按阶段推进进度条）→ 迁移日志（优先读后端 wf_migration，为空回退本地留存）。
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{
  modelValue: boolean
  /** 源业务流 ID（当前画布流程）；缺省时不可发起迁移 */
  currentWfId?: string
  currentWfName?: string
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

// 业务流列表（选目标用；兼容层返回分页对象 {records}，做数组/分页双形态解析）
const workflowList = ref<any[]>([])
async function loadWorkflows() {
  try {
    const res: any = await api.getWorkflows()
    workflowList.value = Array.isArray(res) ? res : res?.list || res?.records || []
  } catch { workflowList.value = [] }
}
function wfNameById(id?: string) { return workflowList.value.find((w: any) => w.id === id)?.name || id || '-' }

const wfMigList = ref<any[]>([])
const wfMigLoading = ref(false)
const wfMigForm = ref({ targetWorkflowId: '', targetEnv: 'test', strategy: 'overwrite', remark: '' })
const wfMigSubmitting = ref(false)
const wfMigProgress = ref(0)
const wfMigStage = ref('')
const MIG_LOG_KEY = 'fastrag_wf_migration_logs'

function loadMigLogs() {
  try { return JSON.parse(localStorage.getItem(MIG_LOG_KEY) || '[]') || [] } catch { return [] }
}
function saveMigLogs(list: any[]) {
  try { localStorage.setItem(MIG_LOG_KEY, JSON.stringify(list.slice(0, 50))) } catch { /* 本地存储不可用时忽略 */ }
}

async function loadWfMigs() {
  wfMigLoading.value = true
  try {
    // 优先读后端（真实落库记录），为空时回退前端留存日志
    let list: any[] = []
    try { list = ((await api.getWorkflowMigrations()) as any) || [] } catch { list = [] }
    if (!Array.isArray(list) || list.length === 0) list = loadMigLogs()
    wfMigList.value = list
  } finally { wfMigLoading.value = false }
}

/** 后端驱动迁移：按策略复制节点（含位置/扩展配置）→ 连线，进度与结果落库 wf_migration */
async function handleCreateWfMig() {
  if (!props.currentWfId) { ElMessage.warning('当前画布未选中业务流，无法确定源'); return }
  if (!wfMigForm.value.targetWorkflowId) { ElMessage.warning('请选择目标业务流'); return }
  if (wfMigForm.value.targetWorkflowId === props.currentWfId) { ElMessage.warning('目标业务流不能与源相同'); return }
  wfMigSubmitting.value = true
  const startedAt = Date.now()
  const src = props.currentWfId
  const tgt = wfMigForm.value.targetWorkflowId
  try {
    // 后端单次请求内完成复制，前端按阶段推进进度条（10 → 90 → 100）
    wfMigStage.value = '后端读取源业务流配置…'; wfMigProgress.value = 10
    const timer = window.setInterval(() => {
      if (wfMigProgress.value < 90) {
        wfMigProgress.value = Math.min(90, wfMigProgress.value + 5)
        wfMigStage.value = wfMigProgress.value < 40 ? '迁移节点（含扩展配置）…' : wfMigProgress.value < 70 ? '迁移连线…' : '写入迁移结果…'
      }
    }, 300)
    let resp: any
    try {
      resp = await api.createWorkflowMigration({
        sourceWorkflowId: src,
        targetWorkflowId: tgt,
        targetEnv: wfMigForm.value.targetEnv,
        strategy: wfMigForm.value.strategy,
        remark: wfMigForm.value.remark,
      })
    } finally { window.clearInterval(timer) }
    wfMigProgress.value = 100
    wfMigStage.value = '迁移完成'

    const nodeOk = Number(resp?.nodeCount || 0)
    const edgeOk = Number(resp?.edgeCount || 0)
    const extOk = Number(resp?.extCount || 0)
    const warnings: string[] = Array.isArray(resp?.warnings) ? resp.warnings : []
    const entry = {
      id: resp?.id || `mig_${Date.now()}`,
      sourceWorkflowId: src, sourceName: wfNameById(src),
      targetWorkflowId: tgt, targetName: wfNameById(tgt),
      targetEnv: wfMigForm.value.targetEnv, strategy: wfMigForm.value.strategy, remark: wfMigForm.value.remark,
      nodeCount: nodeOk, edgeCount: edgeOk, extCount: extOk,
      status: resp?.status || 'completed', progress: resp?.progress || 100,
      durationMs: resp?.durationMs || Date.now() - startedAt,
      warnings,
      validateResult: `节点 ${nodeOk} 个 / 连线 ${edgeOk} 条 / 扩展配置 ${extOk} 项` + (warnings.length ? `；${warnings.length} 项提示` : ''),
      createdAt: new Date().toLocaleString('zh-CN'),
      operator: resp?.operator || 'admin',
    }
    saveMigLogs([entry, ...loadMigLogs()])
    await loadWfMigs()
    ElMessage.success(`迁移完成：节点 ${nodeOk} 个 / 连线 ${edgeOk} 条 / 扩展配置 ${extOk} 项`)
  } catch (e: any) {
    const failEntry = {
      id: `mig_${Date.now()}`, sourceWorkflowId: src, sourceName: wfNameById(src),
      targetWorkflowId: tgt, targetName: wfNameById(tgt), targetEnv: wfMigForm.value.targetEnv,
      strategy: wfMigForm.value.strategy, remark: wfMigForm.value.remark,
      nodeCount: 0, edgeCount: 0, extCount: 0,
      status: 'failed', progress: wfMigProgress.value, durationMs: Date.now() - startedAt,
      warnings: [String(e?.message || e)], validateResult: `迁移中断：${e?.message || '未知错误'}`,
      createdAt: new Date().toLocaleString('zh-CN'), operator: 'admin',
    }
    saveMigLogs([failEntry, ...loadMigLogs()])
    await loadWfMigs()
    ElMessage.error('迁移失败：' + (e?.message || ''))
  } finally {
    wfMigSubmitting.value = false
  }
}

async function clearMigLogs() {
  saveMigLogs([])
  try { await api.clearWorkflowMigrations() } catch { /* 后端不可用时仅清本地 */ }
  wfMigList.value = []
  ElMessage.success('已清空迁移日志')
}

function wfMigStatus(s: string) { return ({ running: '执行中', pending: '待执行', completed: '已完成', failed: '失败' } as Record<string, string>)[s] || s }
function wfMigStatusColor(s: string) { return (({ completed: 'success', failed: 'danger', running: 'warning', pending: 'info' } as Record<string, string>)[s] || 'info') as any }

watch(visible, (v) => {
  if (!v) return
  wfMigProgress.value = 0
  wfMigStage.value = ''
  wfMigForm.value.targetWorkflowId = ''
  loadWorkflows()
  loadWfMigs()
})
</script>

<template>
  <el-dialog v-model="visible" title="配置迁移" width="780px">
    <div class="card-panel" style="margin-bottom:16px">
      <div class="section-title" style="margin-bottom:10px">设置迁移策略并执行（源：{{ currentWfName || wfNameById(currentWfId) }}）</div>
      <el-form inline>
        <el-form-item label="目标业务流">
          <el-select v-model="wfMigForm.targetWorkflowId" filterable placeholder="选择目标业务流" style="width:200px">
            <el-option v-for="w in workflowList.filter(x => x.id !== currentWfId)" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标环境">
          <el-select v-model="wfMigForm.targetEnv" style="width:110px">
            <el-option label="测试环境" value="test" /><el-option label="生产环境" value="production" />
          </el-select>
        </el-form-item>
        <el-form-item label="迁移策略">
          <el-radio-group v-model="wfMigForm.strategy">
            <el-radio value="overwrite">覆盖</el-radio>
            <el-radio value="merge">合并（保留已有）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="wfMigSubmitting" @click="handleCreateWfMig">开始迁移</el-button>
        </el-form-item>
      </el-form>
      <el-form-item label="备注" style="margin-bottom:0">
        <el-input v-model="wfMigForm.remark" placeholder="迁移说明（可选）" style="max-width:420px" />
      </el-form-item>
      <!-- 迁移进度（执行中实时展示 10% → 100%） -->
      <div v-if="wfMigSubmitting || wfMigProgress > 0" style="margin-top:10px">
        <el-progress :percentage="wfMigProgress" :status="wfMigProgress >= 100 ? 'success' : undefined" :stroke-width="12" />
        <div style="font-size:12px;color:var(--el-text-color-secondary);margin-top:4px">{{ wfMigStage }}</div>
      </div>
    </div>
    <div class="section-title" style="margin-bottom:8px">
      迁移日志
      <el-button v-if="wfMigList.length" link type="danger" size="small" style="margin-left:8px" @click="clearMigLogs">清空日志</el-button>
      <span style="font-size:12px;color:var(--el-text-color-secondary);margin-left:8px">（迁移由后端真实复制节点/连线/扩展配置并落库；日志优先读后端，为空时回退本地留存）</span>
    </div>
    <el-table :data="wfMigList" stripe size="small" v-loading="wfMigLoading">
      <el-table-column label="源 → 目标" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.sourceName || wfNameById(row.sourceWorkflowId) }} → {{ row.targetName || wfNameById(row.targetWorkflowId) }}</template>
      </el-table-column>
      <el-table-column label="目标环境" width="90"><template #default="{ row }">{{ row.targetEnv === 'production' ? '生产' : '测试' }}</template></el-table-column>
      <el-table-column label="策略" width="80"><template #default="{ row }">{{ row.strategy === 'merge' ? '合并' : '覆盖' }}</template></el-table-column>
      <el-table-column label="进度" width="140">
        <template #default="{ row }"><el-progress :percentage="row.progress || 0" :status="row.status === 'failed' ? 'exception' : (row.progress >= 100 ? 'success' : undefined)" :stroke-width="10" /></template>
      </el-table-column>
      <el-table-column label="迁移内容" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.nodeCount !== undefined">
            节点 {{ row.nodeCount }} / 连线 {{ row.edgeCount || 0 }} / 扩展配置 {{ row.extCount || 0 }}
            <el-tag v-if="row.warnings?.length" size="small" type="warning" style="margin-left:6px">{{ row.warnings.length }} 项提示</el-tag>
          </template>
          <span v-else>{{ row.validateResult }}</span>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="center">
        <template #default="{ row }">{{ row.durationMs ? (row.durationMs / 1000).toFixed(1) + 's' : '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><el-tag :type="wfMigStatusColor(row.status)" size="small">{{ wfMigStatus(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="80" />
      <el-table-column prop="createdAt" label="时间" width="160" />
    </el-table>
    <el-empty v-if="!wfMigList.length && !wfMigLoading" description="暂无迁移记录：选择目标业务流与策略后点「开始迁移」" :image-size="60" />
  </el-dialog>
</template>

<style lang="scss" scoped>
.section-title { font-size: 14px; font-weight: 600; }
</style>
