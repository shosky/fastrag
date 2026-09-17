<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const taskList = ref<any[]>([])
const keyword = ref('')

async function loadData() {
  loading.value = true
  try {
    const res = await api.getDataMiningTasks({ keyword: keyword.value || undefined })
    taskList.value = (res as any) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

const showDialog = ref(false)
const formData = ref({
  name: '',
  kbId: '',
  ruleType: 'keyword',
  ruleConfig: '',
})

function handleAdd() {
  formData.value = { name: '', kbId: '', ruleType: 'keyword', ruleConfig: '' }
  showDialog.value = true
}

async function handleSave() {
  if (!formData.value.name) {
    ElMessage.warning('请输入任务名称')
    return
  }
  // rule_config / kb_id 为空时不提交空串（空串写入 JSON 列会报错）
  const payload: Record<string, unknown> = {
    name: formData.value.name,
    ruleType: formData.value.ruleType,
  }
  if (formData.value.kbId?.trim()) payload.kbId = formData.value.kbId.trim()
  const cfg = String(formData.value.ruleConfig || '').trim()
  if (cfg) {
    try {
      JSON.parse(cfg)
      payload.ruleConfig = cfg
    } catch {
      ElMessage.error('规则配置不是合法 JSON，请检查后重试')
      return
    }
  }
  try {
    await api.createDataMiningTask(payload)
    showDialog.value = false
    formJudge.value = null
    await loadData()
    ElMessage.success('创建成功')
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.message || ''))
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除任务「${row.name}」？`, '删除确认', { type: 'warning' })
    await api.deleteDataMiningTask(row.id)
    await loadData()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleRun(row: any) {
  await api.runDataMiningTask(row.id)
  await loadData()
  ElMessage.success('执行完成')
}

// ===== 智能判断 / 一键判断 =====
// 思路：以知识库真实分块数为样本量，结合规则类型与规则配置完整度给出可挖掘性评分与预计产出（mock 评分模型）
const judgeLoading = ref(false)

function ruleScore(ruleType: string) {
  return ruleType === 'keyword' ? 0.15 : ruleType === 'frequency' ? 0.12 : ruleType === 'threshold' ? 0.08 : 0.06
}

async function judgeTask(task: { name?: string; kbId?: string; ruleType?: string; ruleConfig?: string }) {
  let chunks = 0
  let kbName = task.kbId || '-'
  if (task.kbId) {
    try { chunks = Number(await api.fetchChunkCount(task.kbId)) || 0 } catch { chunks = 0 }
    try {
      const kb: any = await api.getKnowledgeBaseDetail(task.kbId)
      if (kb?.name) kbName = kb.name
    } catch { /* ignore */ }
  }
  const cfgLen = String(task.ruleConfig || '').trim().length
  let score = 0.4 + Math.min(0.35, chunks / 100) + ruleScore(task.ruleType || '') + (cfgLen > 0 ? 0.06 : 0)
  score = Math.round(Math.min(0.98, score) * 100) / 100
  const estimated = Math.max(3, Math.round(chunks * 0.6) + (task.ruleType === 'keyword' ? 5 : 2))
  const verdict = score >= 0.75 ? '建议挖掘（预期产出高）' : score >= 0.6 ? '可挖掘（建议完善规则配置）' : '不建议（样本不足，先补充知识或规则）'
  const suggestions: string[] = []
  if (!task.kbId) suggestions.push('未绑定知识库，无法统计样本量，建议先选择知识库')
  if (chunks < 20) suggestions.push(`知识库分块仅 ${chunks} 个，样本偏少，建议补充文档后再挖掘`)
  if (!cfgLen) suggestions.push('规则配置为空，建议填写阈值/关键词等参数')
  if (task.ruleType === 'cluster' || task.ruleType === 'threshold') suggestions.push('该规则类型产出较分散，建议配合「关键词」规则交叉验证')
  if (task.ruleType === 'keyword') suggestions.push('关键词规则召回稳定，建议把命中词写入规则配置以便复用')
  if (!suggestions.length) suggestions.push('样本与规则均已具备，可直接执行挖掘')

  return {
    ...task,
    kbName,
    chunks,
    score,
    estimated,
    verdict,
    level: score >= 0.75 ? 'high' : score >= 0.6 ? 'mid' : 'low',
    suggestions,
    judgedAt: new Date().toLocaleString('zh-CN'),
  }
}

// 单条智能判断（行内 / 新增弹窗内共用）
const judgeVisible = ref(false)
const judgeRow = ref<any>(null)
async function handleJudge(row: any) {
  judgeVisible.value = true
  judgeLoading.value = true
  judgeRow.value = { ...row, judging: true }
  try { judgeRow.value = await judgeTask(row) } finally { judgeLoading.value = false }
}

const formJudge = ref<any>(null)
async function judgeForm() {
  if (!formData.value.name && !formData.value.kbId) { ElMessage.warning('请先填写任务名称或选择知识库'); return }
  judgeLoading.value = true
  try {
    formJudge.value = await judgeTask({ name: formData.value.name, kbId: formData.value.kbId, ruleType: formData.value.ruleType, ruleConfig: formData.value.ruleConfig })
  } finally { judgeLoading.value = false }
}
/** 采用判断建议：自动补全规则配置模板 */
function applyJudgeSuggestion() {
  if (!formJudge.value) return
  const tpl: Record<string, string> = {
    keyword: '{"keywords":["退款","资费","故障"],"minHit":2}',
    frequency: '{"topN":20,"days":30,"minCount":3}',
    cluster: '{"k":5,"minSize":5,"maxIter":50}',
    threshold: '{"threshold":0.15,"days":30}',
  }
  if (!String(formData.value.ruleConfig || '').trim()) {
    formData.value.ruleConfig = tpl[formData.value.ruleType] || '{}'
    ElMessage.success('已按建议填充规则配置模板')
  } else {
    ElMessage.info('规则配置已存在，保留现有内容')
  }
}

// 一键判断（批量）
const batchVisible = ref(false)
const batchRows = ref<any[]>([])
const batchSummary = ref<any>(null)
async function handleBatchJudge() {
  if (!taskList.value.length) { ElMessage.warning('暂无任务可判断'); return }
  batchVisible.value = true
  judgeLoading.value = true
  batchRows.value = []
  try {
    for (const t of taskList.value) batchRows.value.push(await judgeTask(t))
    batchSummary.value = {
      total: batchRows.value.length,
      high: batchRows.value.filter(r => r.level === 'high').length,
      mid: batchRows.value.filter(r => r.level === 'mid').length,
      low: batchRows.value.filter(r => r.level === 'low').length,
      estimated: batchRows.value.reduce((s, r) => s + r.estimated, 0),
      judgedAt: new Date().toLocaleString('zh-CN'),
    }
  } finally { judgeLoading.value = false }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">数据挖掘任务</div>
        <div style="display:flex;gap:8px">
          <el-button :loading="judgeLoading" @click="handleBatchJudge">一键判断</el-button>
          <el-button type="primary" @click="handleAdd">新增任务</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索任务名称" clearable style="width: 200px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">查询</el-button>
      </div>

      <el-table :data="taskList" stripe>
        <el-table-column prop="name" label="任务名称" show-overflow-tooltip />
        <el-table-column prop="kbId" label="知识库" width="140" show-overflow-tooltip />
        <el-table-column prop="ruleType" label="规则类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.ruleType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'info'" size="small">
              {{ row.status === 'enabled' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastRunAt" label="最后执行" width="160" />
        <el-table-column prop="resultSummary" label="结果摘要" show-overflow-tooltip />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="warning" size="small" @click="handleJudge(row)">智能判断</el-button>
            <el-button link type="primary" size="small" @click="handleRun(row)">执行</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!taskList.length && !loading" description="暂无数据挖掘任务" />
    </div>

    <!-- 智能判断结果 -->
    <el-dialog v-model="judgeVisible" title="智能判断结果" width="560px">
      <div v-if="judgeRow?.judging" v-loading="true" style="height:80px" />
      <div v-else-if="judgeRow">
        <el-alert
          :type="judgeRow.level === 'high' ? 'success' : judgeRow.level === 'mid' ? 'warning' : 'info'"
          :closable="false" show-icon :title="judgeRow.verdict"
        />
        <el-descriptions :column="2" border size="small" style="margin-top:12px">
          <el-descriptions-item label="任务名称">{{ judgeRow.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="知识库">{{ judgeRow.kbName }}</el-descriptions-item>
          <el-descriptions-item label="规则类型">{{ judgeRow.ruleType }}</el-descriptions-item>
          <el-descriptions-item label="样本分块数">{{ judgeRow.chunks }}</el-descriptions-item>
          <el-descriptions-item label="可挖掘性评分">{{ judgeRow.score }}</el-descriptions-item>
          <el-descriptions-item label="预计产出">{{ judgeRow.estimated }} 条</el-descriptions-item>
          <el-descriptions-item label="判断时间" :span="2">{{ judgeRow.judgedAt }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top:12px;font-weight:600;font-size:13px">判断建议</div>
        <ul style="margin:6px 0 0 18px;font-size:13px;line-height:1.9">
          <li v-for="(s, i) in judgeRow.suggestions" :key="i">{{ s }}</li>
        </ul>
      </div>
      <template #footer><el-button type="primary" @click="judgeVisible = false">知道了</el-button></template>
    </el-dialog>

    <!-- 一键判断（批量结果） -->
    <el-dialog v-model="batchVisible" title="一键判断结果" width="860px">
      <div v-if="judgeLoading && !batchRows.length" v-loading="true" style="height:100px" />
      <template v-else>
        <el-alert
          v-if="batchSummary"
          :closable="false" show-icon
          :type="batchSummary.low > batchSummary.high ? 'warning' : 'success'"
          :title="`共判断 ${batchSummary.total} 个任务：建议挖掘 ${batchSummary.high} / 可挖掘 ${batchSummary.mid} / 不建议 ${batchSummary.low}，预计总产出 ${batchSummary.estimated} 条（${batchSummary.judgedAt}）`"
        />
        <el-table :data="batchRows" size="small" stripe style="margin-top:12px">
          <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="kbName" label="知识库" min-width="160" show-overflow-tooltip />
          <el-table-column prop="ruleType" label="规则类型" width="100" align="center" />
          <el-table-column prop="chunks" label="分块" width="70" align="center" />
          <el-table-column prop="score" label="评分" width="70" align="center" />
          <el-table-column prop="estimated" label="预计产出" width="90" align="center" />
          <el-table-column label="判断" min-width="160">
            <template #default="{ row }">
              <el-tag size="small" :type="row.level === 'high' ? 'success' : row.level === 'mid' ? 'warning' : 'info'">
                {{ row.verdict }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer><el-button type="primary" @click="batchVisible = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="showDialog" title="新增数据挖掘任务" width="520px">
      <el-form label-width="90px">
        <el-form-item label="任务名称" required>
          <el-input v-model="formData.name" placeholder="如：高退款率商品挖掘" />
        </el-form-item>
        <el-form-item label="知识库ID">
          <el-input v-model="formData.kbId" placeholder="kb_sample" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="formData.ruleType" style="width: 160px">
            <el-option label="关键词" value="keyword" />
            <el-option label="频率统计" value="frequency" />
            <el-option label="聚类" value="cluster" />
            <el-option label="阈值" value="threshold" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则配置">
          <el-input v-model="formData.ruleConfig" type="textarea" :rows="4" placeholder='{"threshold": 0.15, "days": 30}' />
        </el-form-item>
      </el-form>

      <!-- 新增时智能判断 -->
      <div style="display:flex;gap:8px;margin-bottom:8px">
        <el-button size="small" type="warning" plain :loading="judgeLoading" @click="judgeForm">智能判断</el-button>
        <el-button v-if="formJudge" size="small" @click="applyJudgeSuggestion">采用建议（填充规则模板）</el-button>
      </div>
      <div v-if="formJudge">
        <el-alert
          :type="formJudge.level === 'high' ? 'success' : formJudge.level === 'mid' ? 'warning' : 'info'"
          :closable="false" show-icon :title="`${formJudge.verdict}（评分 ${formJudge.score}，样本 ${formJudge.chunks} 分块，预计产出 ${formJudge.estimated} 条）`"
        />
        <ul style="margin:6px 0 0 18px;font-size:12px;line-height:1.8;color:var(--el-text-color-secondary)">
          <li v-for="(s, i) in formJudge.suggestions" :key="i">{{ s }}</li>
        </ul>
      </div>

      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}
.section-title { font-size: 15px; font-weight: 600; }
</style>
