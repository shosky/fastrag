<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const analysis = ref<any>({})
const logList = ref<any[]>([])
const total = ref(0)
const query = ref({ kbId: '', hasResult: '' as string, page: 1, pageSize: 10 })

async function loadAnalysis() {
  loading.value = true
  try {
    const res: any = await api.getRetrievalLogAnalysis(query.value.kbId || undefined)
    analysis.value = res || {}
  } catch {
    analysis.value = {}
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const res: any = await api.getRetrievalLogs({
      kbId: query.value.kbId || undefined,
      hasResult: query.value.hasResult === '' ? undefined : query.value.hasResult === 'true',
      page: query.value.page,
      pageSize: query.value.pageSize,
    })
    logList.value = res?.list || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.value.page = 1
  loadAnalysis()
  loadLogs()
}

function handleReset() {
  query.value = { kbId: '', hasResult: '', page: 1, pageSize: 10 }
  handleSearch()
}

function handlePageChange(p: number) {
  query.value.page = p
  loadLogs()
}

// 检索日志修改功能 (#2703)
const showEditDialog = ref(false)
const editForm = ref<{
  id: number | string
  kbId: string
  query: string
  hitCount: number
  topScore: number
  latencyMs: number
  hasResult: boolean
}>({
  id: '',
  kbId: '',
  query: '',
  hitCount: 0,
  topScore: 0,
  latencyMs: 0,
  hasResult: true,
})

function handleEditLog(row: any) {
  editForm.value = {
    id: row.id,
    kbId: row.kbId || '',
    query: row.query || '',
    hitCount: row.hitCount ?? 0,
    topScore: row.topScore ?? 0,
    latencyMs: row.latencyMs ?? 0,
    hasResult: row.hasResult ?? true,
  }
  showEditDialog.value = true
}

async function handleSaveLogEdit() {
  if (!editForm.value.query) {
    ElMessage.warning('请输入查询内容')
    return
  }
  try {
    await api.updateRetrievalLog(editForm.value.id, {
      kbId: editForm.value.kbId,
      query: editForm.value.query,
      hitCount: editForm.value.hitCount,
      topScore: editForm.value.topScore,
      latencyMs: editForm.value.latencyMs,
      hasResult: editForm.value.hasResult,
    })
    ElMessage.success('修改成功')
    showEditDialog.value = false
    loadLogs()
    loadAnalysis()
  } catch {
    ElMessage.error('修改失败')
  }
}

// 检索日志新增/删除功能
const showAddDialog = ref(false)
const addForm = ref<{
  kbId: string
  query: string
  hitCount: number
  topScore: number
  latencyMs: number
  hasResult: boolean
}>({
  kbId: '',
  query: '',
  hitCount: 0,
  topScore: 0,
  latencyMs: 0,
  hasResult: true,
})

function handleAddLog() {
  addForm.value = { kbId: query.value.kbId || '', query: '', hitCount: 0, topScore: 0, latencyMs: 0, hasResult: true }
  showAddDialog.value = true
}

async function handleSaveLogAdd() {
  if (!addForm.value.kbId || !addForm.value.query) {
    ElMessage.warning('请填写知识库ID和查询内容')
    return
  }
  try {
    await api.createRetrievalLog({
      kbId: addForm.value.kbId,
      query: addForm.value.query,
      hitCount: addForm.value.hitCount,
      topScore: addForm.value.topScore,
      latencyMs: addForm.value.latencyMs,
      hasResult: addForm.value.hasResult,
    })
    ElMessage.success('新增成功')
    showAddDialog.value = false
    loadLogs()
    loadAnalysis()
  } catch {
    ElMessage.error('新增失败')
  }
}

async function handleDeleteLog(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除该条检索日志？`, '删除确认', { type: 'warning' })
    await api.deleteRetrievalLog(row.id)
    ElMessage.success('已删除')
    loadLogs()
    loadAnalysis()
  } catch {
    /* 取消或失败 */
  }
}

onMounted(() => {
  loadAnalysis()
  loadLogs()
})

// 检索日志分析配置 (#2703)
const showConfigDialog = ref(false)
const analysisConfig = ref({
  timeRange: 7,
  samplingRate: 100,
  alertThreshold: 50,
  excludedQueries: '',
  scheduledReport: false,
})
async function handleShowConfig() {
  try {
    const saved = localStorage.getItem('retrieval_analysis_config')
    if (saved) Object.assign(analysisConfig.value, JSON.parse(saved))
  } catch {}
  showConfigDialog.value = true
}
function handleSaveConfig() {
  localStorage.setItem('retrieval_analysis_config', JSON.stringify(analysisConfig.value))
  ElMessage.success('分析配置已保存')
  showConfigDialog.value = false
  loadAnalysis()
  loadLogs()
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 统计卡片 -->
    <div class="metric-cards">
      <div class="metric-card">
        <div class="metric-label">总查询次数</div>
        <div class="metric-value">{{ analysis.totalQueries ?? 0 }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">无结果数</div>
        <div class="metric-value">{{ analysis.noResultCount ?? 0 }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">无结果率</div>
        <div class="metric-value">{{ analysis.noResultRate ?? 0 }}%</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">平均耗时(ms)</div>
        <div class="metric-value">{{ analysis.avgLatencyMs ?? 0 }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">平均命中数</div>
        <div class="metric-value">{{ analysis.avgHitCount ?? 0 }}</div>
      </div>
    </div>

    <!-- 热门/无结果查询 -->
    <div class="analysis-grid">
      <div class="card-panel">
        <div class="section-title">热门查询 Top10</div>
        <el-table :data="analysis.topQueries || []" size="small">
          <el-table-column prop="query" label="查询词" show-overflow-tooltip />
          <el-table-column prop="count" label="次数" width="80" align="center" />
        </el-table>
        <el-empty v-if="!(analysis.topQueries && analysis.topQueries.length)" description="暂无数据" :image-size="60" />
      </div>
      <div class="card-panel">
        <div class="section-title">无结果查询 Top10</div>
        <el-table :data="analysis.noResultQueries || []" size="small">
          <el-table-column prop="query" label="查询词" show-overflow-tooltip />
          <el-table-column prop="count" label="次数" width="80" align="center" />
        </el-table>
        <el-empty v-if="!(analysis.noResultQueries && analysis.noResultQueries.length)" description="暂无数据" :image-size="60" />
      </div>
    </div>

    <!-- 检索日志明细 -->
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">检索日志明细</div>
        <div>
          <el-button size="small" type="primary" @click="handleAddLog">新增日志</el-button>
          <el-button size="small" @click="handleShowConfig">分析配置</el-button>
        </div>
      </div>
      <div class="filter-bar">
        <el-input v-model="query.kbId" placeholder="知识库ID" clearable style="width: 180px" />
        <el-select v-model="query.hasResult" placeholder="结果状态" clearable style="width: 140px">
          <el-option label="有结果" value="true" />
          <el-option label="无结果" value="false" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <el-table :data="logList" stripe size="small">
        <el-table-column prop="kbId" label="知识库" width="140" show-overflow-tooltip />
        <el-table-column prop="query" label="查询内容" show-overflow-tooltip />
        <el-table-column prop="hitCount" label="命中数" width="80" align="center" />
        <el-table-column prop="topScore" label="最高分" width="80" align="center" />
        <el-table-column prop="latencyMs" label="耗时(ms)" width="90" align="center" />
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hasResult ? 'success' : 'danger'" size="small">{{ row.hasResult ? '有' : '无' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEditLog(row)">修改</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteLog(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 0"
        class="table-footer"
        background
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.page"
        :page-size="query.pageSize"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 检索日志分析配置 -->
    <el-dialog v-model="showConfigDialog" title="检索日志分析配置" width="500px">
      <el-form label-width="140px">
        <el-form-item label="分析时间范围(天)"><el-input-number v-model="analysisConfig.timeRange" :min="1" :max="365" style="width:160px" /></el-form-item>
        <el-form-item label="采样率(%)"><el-input-number v-model="analysisConfig.samplingRate" :min="1" :max="100" style="width:160px" /></el-form-item>
        <el-form-item label="无结果告警阈值"><el-input-number v-model="analysisConfig.alertThreshold" :min="0" :max="100" style="width:160px" /><div style="font-size:12px;color:#909399;margin-top:4px">无结果率超过此值时触发告警</div></el-form-item>
        <el-form-item label="排除查询词"><el-input v-model="analysisConfig.excludedQueries" type="textarea" :rows="3" placeholder="每行一个，匹配的查询将不纳入分析" /></el-form-item>
        <el-form-item label="定时报告"><el-switch v-model="analysisConfig.scheduledReport" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showConfigDialog=false">取消</el-button><el-button type="primary" @click="handleSaveConfig">保存配置</el-button></template>
    </el-dialog>

    <!-- 新增检索日志 -->
    <el-dialog v-model="showAddDialog" title="新增检索日志" width="500px">
      <el-form label-width="100px">
        <el-form-item label="知识库ID" required><el-input v-model="addForm.kbId" /></el-form-item>
        <el-form-item label="查询内容" required><el-input v-model="addForm.query" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="命中数"><el-input-number v-model="addForm.hitCount" :min="0" style="width:160px" /></el-form-item>
        <el-form-item label="最高分"><el-input-number v-model="addForm.topScore" :min="0" :max="1" :step="0.01" style="width:160px" /></el-form-item>
        <el-form-item label="耗时(ms)"><el-input-number v-model="addForm.latencyMs" :min="0" style="width:160px" /></el-form-item>
        <el-form-item label="是否有结果"><el-switch v-model="addForm.hasResult" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveLogAdd">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改检索日志 -->
    <el-dialog v-model="showEditDialog" title="修改检索日志" width="500px">
      <el-form label-width="100px">
        <el-form-item label="知识库ID"><el-input v-model="editForm.kbId" /></el-form-item>
        <el-form-item label="查询内容" required><el-input v-model="editForm.query" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="命中数"><el-input-number v-model="editForm.hitCount" :min="0" style="width:160px" /></el-form-item>
        <el-form-item label="最高分"><el-input-number v-model="editForm.topScore" :min="0" :max="1" :step="0.01" style="width:160px" /></el-form-item>
        <el-form-item label="耗时(ms)"><el-input-number v-model="editForm.latencyMs" :min="0" style="width:160px" /></el-form-item>
        <el-form-item label="是否有结果"><el-switch v-model="editForm.hasResult" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveLogEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.metric-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: $spacing-base;
  margin-bottom: $spacing-base;
}

.metric-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  .metric-label { font-size: 13px; color: $text-secondary; margin-bottom: $spacing-sm; }
  .metric-value { font-size: 24px; font-weight: 700; }
}

.analysis-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-base;
  margin-bottom: $spacing-base;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.section-title { font-size: 15px; font-weight: 600; }

.table-footer { margin-top: $spacing-base; display: flex; justify-content: flex-end; }
</style>
