<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const dataList = ref<any[]>([])
const efficiency = ref<any>({})
const coverageData = ref<any>(null)

const kbList = ref<any[]>([])
const selectedKbId = ref('')
async function loadKbList() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbList.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
    if (kbList.value.length > 0 && !selectedKbId.value) selectedKbId.value = kbList.value[0].id
  } catch { /* ignore */ }
}

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    const [reportRes, effRes, covRes] = await Promise.all([
      api.generatePublishReport(selectedKbId.value).catch(() => ({})),
      api.getPublishEfficiency(selectedKbId.value).catch(() => ({})),
      api.getReviewCoverage(selectedKbId.value).catch(() => null),
    ])
    const report: any = reportRes
    const eff: any = effRes
    const cov: any = covRes
    efficiency.value = { totalPublished: eff.totalPublishes || eff.totalPublished || 0, successRate: eff.successRate || 0, avgPublishTime: eff.avgPublishTime || '-', totalPublishes: eff.totalPublishes || 0, strategyEffect: eff.strategyEffect || {} }
    coverageData.value = cov
    dataList.value = report ? [{
      id: '1', name: '发布报告', period: new Date().toISOString().slice(0, 7),
      totalReviews: eff.totalPublishes || report.totalReviews || 0,
      approved: eff.successRate ? Math.round(eff.totalPublishes * eff.successRate / 100) : 0,
      rejected: eff.totalPublishes ? Math.round(eff.totalPublishes * (1 - (eff.successRate || 0) / 100)) : 0,
      timeout: 0, avgReviewTime: eff.avgPublishTime || 0,
      generatedAt: new Date().toISOString()
    }] : []
  } finally { loading.value = false }
}
onMounted(async () => { await loadKbList(); loadData() })

const summary = computed(() => {
  const total = dataList.value.reduce((s, r) => s + (r.totalReviews || 0), 0)
  const approved = dataList.value.reduce((s, r) => s + (r.approved || 0), 0)
  const rejected = dataList.value.reduce((s, r) => s + (r.rejected || 0), 0)
  const avgTime = dataList.value.length > 0 ? Math.round(dataList.value.reduce((s, r) => s + (r.avgReviewTime || 0), 0) / dataList.value.length) : 0
  return { total, approved, rejected, avgTime }
})

async function handleExport() {
  try {
    const blob = await api.exportPublishReport(selectedKbId.value) as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `publish_report_${selectedKbId.value}_${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('报告已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">审核报告</div>
        <div style="display:flex;gap:12px;align-items:center">
          <el-select v-model="selectedKbId" @change="loadData" placeholder="选择知识库" style="width:200px">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button @click="handleExport">导出报告</el-button>
        </div>
      </div>
      <div style="display: flex; gap: 16px; margin-bottom: 20px">
        <div class="stat-card">
          <div class="stat-card__value">{{ summary.total }}</div>
          <div class="stat-card__label">审核总数</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__value" style="color: #67C23A">{{ summary.approved }}</div>
          <div class="stat-card__label">通过数</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__value" style="color: #F56C6C">{{ summary.rejected }}</div>
          <div class="stat-card__label">驳回数</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__value" style="color: #E6A23C">{{ summary.avgTime }}h</div>
          <div class="stat-card__label">平均审核时长</div>
        </div>
      </div>
      <div v-if="coverageData" style="display:flex;gap:16px;margin-bottom:16px">
        <div class="stat-card" style="flex:1">
          <div class="stat-card__value" style="color:#409eff">{{ coverageData.coverageRate || 0 }}%</div>
          <div class="stat-card__label">审核覆盖率</div>
        </div>
        <div class="stat-card" style="flex:1">
          <div class="stat-card__value">{{ coverageData.totalKnowledge || 0 }}</div>
          <div class="stat-card__label">总知识数</div>
        </div>
        <div class="stat-card" style="flex:1">
          <div class="stat-card__value" style="color:#67C23A">{{ coverageData.reviewedCount || 0 }}</div>
          <div class="stat-card__label">已审核数</div>
        </div>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="name" label="报告名称" min-width="200" />
        <el-table-column prop="period" label="周期" width="100" />
        <el-table-column prop="totalReviews" label="审核总数" width="100" />
        <el-table-column prop="approved" label="通过" width="80">
          <template #default="{ row }"><span style="color: #67C23A">{{ row.approved }}</span></template>
        </el-table-column>
        <el-table-column prop="rejected" label="驳回" width="80">
          <template #default="{ row }"><span style="color: #F56C6C">{{ row.rejected }}</span></template>
        </el-table-column>
        <el-table-column prop="timeout" label="超时" width="80">
          <template #default="{ row }"><span style="color: #E6A23C">{{ row.timeout }}</span></template>
        </el-table-column>
        <el-table-column prop="avgReviewTime" label="平均时长(h)" width="120" />
        <el-table-column prop="generatedAt" label="生成时间" width="160" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.stat-card {
  flex: 1; background: $bg-white; border: 1px solid $border-lighter; border-radius: $radius-base;
  padding: 16px; text-align: center;
  &__value { font-size: 28px; font-weight: 600; color: $text-primary; }
  &__label { font-size: 13px; color: $text-secondary; margin-top: 4px; }
}
</style>
