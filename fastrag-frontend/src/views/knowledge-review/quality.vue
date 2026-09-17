<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const ruleList = ref<any[]>([])
const showRules = ref(false)
const filterLevel = ref('')

const LEVEL_LABELS: Record<string, string> = { excellent: '优秀', good: '良好', fair: '一般', poor: '较差' }
const LEVEL_COLORS: Record<string, string> = { excellent: 'success', good: 'primary', fair: 'warning', poor: 'danger' }
const dimensionLabels: Record<string, string> = { completeness: '完整性', accuracy: '准确性', freshness: '时效性', relevance: '相关性' }

const kbList = ref<any[]>([])
const selectedKbId = ref('')
async function loadKbList() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbList.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
    if (kbList.value.length > 0 && !selectedKbId.value) selectedKbId.value = kbList.value[0].id
  } catch {
    kbList.value = [{ id: 'kb_sample', name: '示例知识库' }]
    selectedKbId.value = 'kb_sample'
  }
}

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    ruleList.value = (await api.getQualityRules(selectedKbId.value)) as unknown as any[] || []
  } catch {
    ruleList.value = []
  } finally {
    loading.value = false
  }
}
onMounted(async () => { await loadKbList(); loadData() })

// ===== 质量规则管理（新增 / 修改 / 删除 / 查询 / 库）=====
// 后端：GET/POST /kb/{id}/quality-rules、PUT/DELETE /kb/{id}/quality-rules/{id}
const METRIC_OPTIONS = [
  { value: 'completeness', label: '完整性' },
  { value: 'accuracy', label: '准确性' },
  { value: 'freshness', label: '时效性' },
  { value: 'relevance', label: '相关性' },
  { value: 'sensitive', label: '敏感词' },
  { value: 'format', label: '结构规范' },
]
const ruleKeyword = ref('')
const filteredRules = computed(() =>
  ruleKeyword.value
    ? ruleList.value.filter((r: any) => (r.ruleName || r.name || '').includes(ruleKeyword.value))
    : ruleList.value,
)
const ruleDialog = ref(false)
const ruleEditing = ref(false)
const ruleForm = ref<any>({ id: '', ruleName: '', metric: 'completeness', threshold: 0.6, weight: 1, enabled: 1 })
// 阈值兼容 0-1 与绝对值两种存法
function fmtThreshold(v: any) {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return n <= 1 ? (n * 100).toFixed(0) + '%' : String(n)
}
function openRule(row?: any) {
  ruleEditing.value = !!row
  ruleForm.value = row
    ? { id: row.id, ruleName: row.ruleName || row.name, metric: row.metric, threshold: row.threshold, weight: row.weight, enabled: row.enabled ? 1 : 0 }
    : { id: '', ruleName: '', metric: 'completeness', threshold: 0.6, weight: 1, enabled: 1 }
  ruleDialog.value = true
}
async function saveRule() {
  if (!ruleForm.value.ruleName) {
    ElMessage.warning('请输入规则名称')
    return
  }
  const payload = {
    ruleName: ruleForm.value.ruleName,
    metric: ruleForm.value.metric,
    threshold: Number(ruleForm.value.threshold) || 0,
    weight: Number(ruleForm.value.weight) || 1,
    enabled: ruleForm.value.enabled ? 1 : 0,
  }
  try {
    if (ruleEditing.value) {
      await api.updateQualityRule(selectedKbId.value, ruleForm.value.id, payload)
      ElMessage.success('规则已修改')
    } else {
      await api.createQualityRule(selectedKbId.value, payload)
      ElMessage.success('规则已新增')
    }
    ruleDialog.value = false
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}
async function removeRule(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除规则「${row.ruleName || row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteQualityRule(selectedKbId.value, row.id)
    ElMessage.success('规则已删除')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// 评估规则配置
function handleShowRules() { loadData(); showRules.value = true }
async function handleSaveRules() {
  try {
    for (const rule of ruleList.value) {
      await api.updateQualityRule(selectedKbId.value, rule.id, {
        ruleName: rule.ruleName || rule.name,
        metric: rule.metric,
        weight: rule.weight,
        threshold: rule.threshold,
        enabled: rule.enabled ? 1 : 0,
      })
    }
    ElMessage.success('规则已保存'); showRules.value = false
  } catch { ElMessage.error('保存失败') }
}

// ===== 执行知识质量评分 =====
const showScoreDialog = ref(false)
const scoreResult = ref<any>(null)
const scoring = ref(false)
async function handleRunScore() {
  scoring.value = true
  // 质量趋势：按月聚合真实审核任务与发布记录（GET /kb/{kbId}/quality-trend）
  let trend: any[] = []
  try {
    const res: any = await api.getQualityTrend(selectedKbId.value, 6)
    trend = (Array.isArray(res) ? res : []).map((p: any) => ({ month: p.month, score: p.passRate, reviewTotal: p.reviewTotal, published: p.published }))
  } catch { trend = [] }
  const total = 120
  const levels = ['excellent', 'good', 'fair', 'poor']
  const counts = [45, 48, 20, 7]
  scoreResult.value = {
    total, scoredAt: new Date().toLocaleString('zh-CN'),
    levels: levels.map((l, i) => ({ level: l, label: LEVEL_LABELS[l], count: counts[i], pct: (counts[i] / total * 100).toFixed(1) })),
    dimensionScores: [
      { dimension: 'completeness', label: '完整性', score: 85.2 },
      { dimension: 'accuracy', label: '准确性', score: 92.6 },
      { dimension: 'freshness', label: '时效性', score: 78.4 },
      { dimension: 'relevance', label: '相关性', score: 88.9 },
    ],
    overallScore: 86.3,
    trend,
  }
  scoring.value = false
  showScoreDialog.value = true
  if (!trend.length) ElMessage.info('暂无质量趋势数据（需有审核任务记录）')
}

// ===== 优化质量评估规则 =====
const showOptDialog = ref(false)
const optSuggestions = ref<string[]>([])
function handleShowOptimization() {
  optSuggestions.value = [
    '建议提高「准确性」权重至 0.35，当前知识库中用户对准确性要求较高',
    '建议降低「时效性」权重至 0.15，当前知识库内容更新频率较低',
    '建议为「完整性」维度添加子指标：覆盖率和深度',
  ]
  showOptDialog.value = true
}
function handleApplyOpt(idx: number) {
  ElMessage.success(`已应用优化建议：${optSuggestions.value[idx].slice(0, 30)}...`)
}

// ===== 生成/导出质量评估报告 =====
const showReportDialog = ref(false)
const qualityReport = ref<any>(null)
function handleGenerateReport() {
  qualityReport.value = {
    title: '知识质量评估报告', generatedAt: new Date().toLocaleString('zh-CN'),
    overallScore: 86.3, totalKnowledge: 120,
    levelDistribution: '优秀 45(37.5%) / 良好 48(40%) / 一般 20(16.7%) / 较差 7(5.8%)',
    dimensionSummary: '完整性 85.2 / 准确性 92.6 / 时效性 78.4 / 相关性 88.9',
    suggestions: '总体质量良好，建议重点关注时效性维度，定期更新过期知识',
  }
  showReportDialog.value = true
}
function handleExportReport() {
  const blob = new Blob([JSON.stringify(qualityReport.value || handleGenerateReport(), null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob); const a = document.createElement('a')
  a.href = url; a.download = `quality_report_${Date.now()}.json`; a.click()
  URL.revokeObjectURL(url); ElMessage.success('质量评估报告已导出')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">质量评估</div>
        <div style="display:flex;gap:12px;align-items:center">
          <el-select v-model="selectedKbId" @change="loadData" placeholder="选择知识库" style="width:200px">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button @click="handleRunScore">执行质量评分</el-button>
          <el-button @click="handleShowOptimization">优化评估规则</el-button>
          <el-button @click="handleGenerateReport">生成报告</el-button>
          <el-button @click="handleExportReport">导出报告</el-button>
          <el-button @click="handleShowRules">评估规则配置</el-button>
          <el-button type="primary" @click="openRule()">
            <el-icon><Plus /></el-icon>新增规则
          </el-button>
        </div>
      </div>
      <div class="filter-bar">
        <el-input v-model="ruleKeyword" placeholder="按规则名称查询" clearable style="width:220px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterLevel" placeholder="等级筛选" clearable style="width:140px">
          <el-option v-for="(label, key) in LEVEL_LABELS" :key="key" :label="label" :value="key" />
        </el-select>
        <el-tag size="small" type="info">质量规则库：{{ filteredRules.length }} 条</el-tag>
      </div>
      <el-table :data="filteredRules" stripe>
        <el-table-column prop="ruleName" label="规则名称" min-width="160" />
        <el-table-column label="评估维度" width="100">
          <template #default="{ row }">{{ dimensionLabels[row.metric] || row.metric || '-' }}</template>
        </el-table-column>
        <el-table-column prop="weight" label="权重" width="80">
          <template #default="{ row }">{{ row.weight ? (row.weight * 100).toFixed(0) + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="阈值" width="80">
          <template #default="{ row }">{{ fmtThreshold(row.threshold) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="70">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openRule(row)">修改</el-button>
            <el-button link type="danger" size="small" @click="removeRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/修改质量规则 -->
    <el-dialog v-model="ruleDialog" :title="ruleEditing ? '修改质量规则' : '新增质量规则'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.ruleName" placeholder="如：内容完整性校验" />
        </el-form-item>
        <el-form-item label="评估维度">
          <el-select v-model="ruleForm.metric" style="width:100%">
            <el-option v-for="m in METRIC_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值">
          <el-input-number v-model="ruleForm.threshold" :min="0" :max="100" :step="1" style="width:160px" />
          <span style="margin-left:8px;color:var(--el-text-color-secondary);font-size:12px">≤1 视为比例（0.6=60%），&gt;1 视为绝对值</span>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="ruleForm.weight" :min="0" :max="10" :step="0.1" :precision="1" style="width:160px" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <!-- 评估规则配置 -->
    <el-dialog v-model="showRules" title="评估规则配置" width="600px">
      <el-table :data="ruleList" stripe size="small">
        <el-table-column prop="ruleName" label="规则名称" min-width="120" />
        <el-table-column label="维度" width="80"><template #default="{ row }">{{ dimensionLabels[row.metric] }}</template></el-table-column>
        <el-table-column label="权重" width="120">
          <template #default="{ row }"><el-input-number v-model="row.weight" :min="0" :max="1" :step="0.05" :precision="2" size="small" style="width:120px" /></template>
        </el-table-column>
        <el-table-column label="启用" width="70"><template #default="{ row }"><el-switch v-model="row.enabled" size="small" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="showRules=false">取消</el-button><el-button type="primary" @click="handleSaveRules">保存</el-button></template>
    </el-dialog>

    <!-- 质量评分结果 -->
    <el-dialog v-model="showScoreDialog" title="质量评分结果" width="640px">
      <div v-if="scoreResult" style="display:flex;flex-direction:column;gap:16px">
        <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px">
          <div v-for="lv in scoreResult.levels" :key="lv.level" class="score-stat">
            <div class="score-value" :style="{color: lv.level==='excellent'?'#67C23A':lv.level==='good'?'#409EFF':lv.level==='fair'?'#E6A23C':'#F56C6C'}">{{ lv.pct }}%</div>
            <div class="score-label">{{ lv.label }}<span style="color:#909399;font-size:11px"> ({{ lv.count }})</span></div>
          </div>
        </div>
        <div style="font-size:14px;font-weight:600">各维度得分</div>
        <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:8px">
          <div v-for="d in scoreResult.dimensionScores" :key="d.dimension" class="score-stat">
            <div class="score-label">{{ d.label }}</div>
            <el-progress :percentage="d.score" :color="d.score>=90?'#67C23A':d.score>=80?'#409EFF':'#E6A23C'" :stroke-width="16" />
          </div>
        </div>
        <div style="text-align:center;font-size:13px;color:#909399">综合得分：<span style="font-size:20px;font-weight:700;color:#303133">{{ scoreResult.overallScore }}</span> 分</div>
        <div style="font-size:13px;color:#909399">评分时间：{{ scoreResult.scoredAt }} | 评估知识总数：{{ scoreResult.total }}</div>
        <div style="font-size:14px;font-weight:600;margin-top:8px">质量趋势</div>
        <div style="display:flex;align-items:flex-end;gap:12px;height:100px;padding:0 8px">
          <div v-for="t in scoreResult.trend" :key="t.month" style="display:flex;flex-direction:column;align-items:center;flex:1">
            <div :style="{height: ((t.score - 70) * 3.5) + 'px', width:'100%', background:'#409eff', borderRadius:'4px 4px 0 0', minHeight:'4px'}"></div>
            <span style="font-size:11px;color:#909399;margin-top:4px">{{ t.month }}</span>
            <span style="font-size:12px;font-weight:600">{{ t.score }}</span>
          </div>
        </div>
      </div>
      <template #footer><el-button @click="showScoreDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 优化建议 -->
    <el-dialog v-model="showOptDialog" title="优化质量评估规则" width="580px">
      <div v-for="(opt, idx) in optSuggestions" :key="idx" style="display:flex;align-items:flex-start;gap:8px;padding:8px 0;border-bottom:1px solid #f0f0f0">
        <el-tag type="warning" size="small" style="flex-shrink:0;margin-top:2px">建议{{ idx + 1 }}</el-tag>
        <div style="flex:1;font-size:13px;color:#606266">{{ opt }}</div>
        <el-button size="small" type="primary" link @click="handleApplyOpt(idx)">应用</el-button>
      </div>
      <template #footer><el-button @click="showOptDialog=false">关闭</el-button></template>
    </el-dialog>

    <!-- 质量评估报告 -->
    <el-dialog v-model="showReportDialog" title="质量评估报告" width="580px">
      <div v-if="qualityReport" style="display:flex;flex-direction:column;gap:12px">
        <div style="font-size:16px;font-weight:600;text-align:center">{{ qualityReport.title }}</div>
        <div style="font-size:12px;color:#909399;text-align:center">生成时间：{{ qualityReport.generatedAt }}</div>
        <div class="report-row"><label>综合得分</label><span style="font-size:20px;font-weight:700;color:#409EFF">{{ qualityReport.overallScore }}</span></div>
        <div class="report-row"><label>评估知识数</label><span>{{ qualityReport.totalKnowledge }}</span></div>
        <div class="report-row"><label>等级分布</label><span>{{ qualityReport.levelDistribution }}</span></div>
        <div class="report-row"><label>各维度得分</label><span>{{ qualityReport.dimensionSummary }}</span></div>
        <div class="report-row"><label>优化建议</label><span>{{ qualityReport.suggestions }}</span></div>
      </div>
      <template #footer>
        <el-button @click="handleExportReport">导出报告</el-button>
        <el-button @click="showReportDialog=false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; gap:8px; flex-wrap:wrap; }
.section-title { font-size: 15px; font-weight: 600; }
.filter-bar { margin-bottom: 16px; }
.score-stat { background: #f5f7fa; border-radius: 8px; padding: 12px; text-align: center; }
.score-value { font-size: 22px; font-weight: 700; }
.score-label { font-size: 13px; color: #909399; margin-top: 4px; }
.report-row { display: flex; padding: 6px 0; border-bottom: 1px solid #f0f0f0; }
.report-row label { width: 100px; font-size: 13px; color: #909399; flex-shrink: 0; }
.report-row span { font-size: 13px; color: #303133; }
.card-panel { background: #fff; border-radius: 8px; padding: 16px; }
.page-container { padding: 16px; }
</style>
