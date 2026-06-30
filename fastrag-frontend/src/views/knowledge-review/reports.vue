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
  kbList.value = [{ id: 'kb_sample', name: '示例知识库' }, { id: 'kb_tech', name: '技术文档库' }, { id: 'kb_product', name: '产品知识库' }]
  selectedKbId.value = kbList.value[0].id
}

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  // 直接使用 mock 数据
  coverageData.value = { coverageRate: 78, totalKnowledge: 320, reviewedCount: 250 }
  efficiency.value = { totalPublished: 45, successRate: 92, avgPublishTime: 36, totalPublishes: 45, strategyEffect: {} }
  dataList.value = [
    { id: 'rp1', name: '6月发布报告', period: '2026-06', totalReviews: 45, approved: 38, rejected: 4, timeout: 3, avgReviewTime: 36, generatedAt: '2026-06-29 10:00' },
    { id: 'rp2', name: '5月发布报告', period: '2026-05', totalReviews: 52, approved: 45, rejected: 5, timeout: 2, avgReviewTime: 32, generatedAt: '2026-06-01 09:00' },
    { id: 'rp3', name: '4月发布报告', period: '2026-04', totalReviews: 38, approved: 32, rejected: 4, timeout: 2, avgReviewTime: 28, generatedAt: '2026-05-01 09:00' },
  ]
  loading.value = false
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

// 审核报告模板设置
const showTemplateDialog = ref(false)
const reportTemplate = ref({ title: '审核报告', includeCharts: true, includeDetails: true, includeSummary: true, format: 'pdf', period: 'monthly' })
function handleShowTemplate() {
  try {
    const saved = localStorage.getItem('report_template_config')
    if (saved) Object.assign(reportTemplate.value, JSON.parse(saved))
  } catch {}
  showTemplateDialog.value = true
}
function handleSaveTemplate() {
  localStorage.setItem('report_template_config', JSON.stringify(reportTemplate.value))
  ElMessage.success('报告模板已保存'); showTemplateDialog.value = false
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
          <el-button @click="handleShowTemplate">报告模板</el-button>
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

    <!-- 报告模板设置 -->
    <el-dialog v-model="showTemplateDialog" title="审核报告模板设置" width="480px">
      <el-form label-width="120px">
        <el-form-item label="报告标题"><el-input v-model="reportTemplate.title" /></el-form-item>
        <el-form-item label="包含图表"><el-switch v-model="reportTemplate.includeCharts" /></el-form-item>
        <el-form-item label="包含明细"><el-switch v-model="reportTemplate.includeDetails" /></el-form-item>
        <el-form-item label="包含摘要"><el-switch v-model="reportTemplate.includeSummary" /></el-form-item>
        <el-form-item label="导出格式"><el-select v-model="reportTemplate.format" style="width:160px"><el-option label="PDF" value="pdf" /><el-option label="Excel" value="excel" /><el-option label="CSV" value="csv" /></el-select></el-form-item>
        <el-form-item label="统计周期"><el-select v-model="reportTemplate.period" style="width:160px"><el-option label="月报" value="monthly" /><el-option label="季报" value="quarterly" /><el-option label="年报" value="yearly" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTemplateDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSaveTemplate">保存</el-button>
      </template>
    </el-dialog>
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
