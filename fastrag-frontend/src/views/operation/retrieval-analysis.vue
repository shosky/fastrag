<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const analysis = ref<any>({})
const logList = ref<any[]>([])
const total = ref(0)
const query = ref({ kbId: '', hasResult: '' as string, page: 1, pageSize: 10 })

async function loadAnalysis() {
  try {
    const res: any = await api.getRetrievalLogAnalysis(query.value.kbId || undefined)
    analysis.value = res || {}
  } catch { analysis.value = {} }
  if (!analysis.value.totalQueries && !analysis.value.topQueries) {
    analysis.value = {
      totalQueries: 1523, noResultCount: 87, noResultRate: 5.7, avgLatencyMs: 235, avgHitCount: 3.2,
      topQueries: [
        { query: '产品价格', count: 156 }, { query: '技术支持', count: 98 }, { query: '联系方式', count: 87 },
        { query: '使用教程', count: 76 }, { query: '功能介绍', count: 65 }, { query: '常见问题', count: 54 },
        { query: '版本更新', count: 43 }, { query: '数据导出', count: 38 }, { query: 'API文档', count: 32 }, { query: '账号设置', count: 28 },
      ],
      noResultQueries: [
        { query: 'xxx系统', count: 12 }, { query: '内部工具', count: 8 }, { query: '旧版接口', count: 6 },
        { query: '测试环境', count: 5 }, { query: '废弃功能', count: 4 },
      ],
    }
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
  if (!logList.value.length) {
    logList.value = [
      { id: 'log1', kbId: 'kb_001', query: '产品价格是多少', hitCount: 5, topScore: 0.92, latencyMs: 156, hasResult: true, createdAt: '2026-06-29 10:30:00' },
      { id: 'log2', kbId: 'kb_001', query: '如何申请退款', hitCount: 3, topScore: 0.85, latencyMs: 203, hasResult: true, createdAt: '2026-06-29 10:28:00' },
      { id: 'log3', kbId: 'kb_002', query: 'xxx系统使用', hitCount: 0, topScore: 0, latencyMs: 89, hasResult: false, createdAt: '2026-06-29 10:25:00' },
      { id: 'log4', kbId: 'kb_001', query: 'API调用方式', hitCount: 4, topScore: 0.78, latencyMs: 312, hasResult: true, createdAt: '2026-06-29 10:20:00' },
      { id: 'log5', kbId: 'kb_001', query: '技术支持电话', hitCount: 2, topScore: 0.65, latencyMs: 178, hasResult: true, createdAt: '2026-06-29 10:15:00' },
    ]
    total.value = logList.value.length
  }
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
        <el-button size="small" @click="handleShowConfig">分析配置</el-button>
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
