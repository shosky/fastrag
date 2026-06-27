<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getQualityScoreList, getQualityRuleList, updateQualityRule, QUALITY_LEVEL_LABELS, QUALITY_LEVEL_COLORS } from '@/mock/knowledge-review'

const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const filterLevel = ref('')
const showRules = ref(false)
const rules = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const res = getQualityScoreList({ page: currentPage.value, pageSize: pageSize.value, level: filterLevel.value || undefined })
    dataList.value = res.list; total.value = res.total
  } finally { loading.value = false }
}
onMounted(loadData)

function handleFilter() { currentPage.value = 1; loadData() }
function handlePageChange(p: number) { currentPage.value = p; loadData() }
function handleSizeChange(s: number) { pageSize.value = s; currentPage.value = 1; loadData() }

function handleShowRules() {
  rules.value = getQualityRuleList().map((r: any) => ({ ...r }))
  showRules.value = true
}
function handleSaveRules() {
  rules.value.forEach(r => updateQualityRule(r.id, { weight: r.weight, enabled: r.enabled }))
  ElMessage.success('规则已保存'); showRules.value = false
}

const dimensionLabels: Record<string, string> = { completeness: '完整性', accuracy: '准确性', freshness: '时效性', relevance: '相关性' }
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">质量评估</div>
        <el-button @click="handleShowRules">评估规则配置</el-button>
      </div>
      <div class="filter-bar">
        <el-select v-model="filterLevel" placeholder="等级筛选" clearable style="width: 120px" @change="handleFilter">
          <el-option v-for="(label, key) in QUALITY_LEVEL_LABELS" :key="key" :label="label" :value="key" />
        </el-select>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 12px 20px; display: flex; gap: 24px">
              <div v-for="(score, dim) in (row.scores || {})" :key="dim" style="text-align: center">
                <div style="font-size: 20px; font-weight: 600; color: #303133">{{ score }}</div>
                <div style="font-size: 12px; color: #909399">{{ dimensionLabels[dim] || dim }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="knowledgeTitle" label="知识标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="totalScore" label="总分" width="80">
          <template #default="{ row }"><span style="font-weight: 600">{{ row.totalScore }}</span></template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="90">
          <template #default="{ row }"><el-tag :type="QUALITY_LEVEL_COLORS[row.level] as any" size="small">{{ QUALITY_LEVEL_LABELS[row.level] || row.level }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="evaluatedAt" label="评估时间" width="160" show-overflow-tooltip />
      </el-table>
      <div class="table-footer" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="handlePageChange" @size-change="handleSizeChange" />
      </div>
    </div>
    <el-dialog v-model="showRules" title="评估规则配置" width="600px" :close-on-click-modal="false">
      <el-table :data="rules" stripe size="small">
        <el-table-column prop="name" label="维度" min-width="120" />
        <el-table-column prop="dimension" label="评估维度" width="100">
          <template #default="{ row }">{{ dimensionLabels[row.dimension] || row.dimension }}</template>
        </el-table-column>
        <el-table-column label="权重" width="150">
          <template #default="{ row }"><el-input-number v-model="row.weight" :min="0" :max="1" :step="0.05" :precision="2" size="small" style="width: 120px" /></template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }"><el-switch v-model="row.enabled" size="small" /></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showRules = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRules">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
