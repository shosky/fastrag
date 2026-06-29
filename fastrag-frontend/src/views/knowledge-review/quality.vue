<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
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
  } catch { /* ignore */ }
}

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    const res: any = await api.getQualityRules(selectedKbId.value)
    ruleList.value = Array.isArray(res) ? res : []
  } finally { loading.value = false }
}
onMounted(async () => { await loadKbList(); loadData() })

function handleShowRules() { loadData(); showRules.value = true }
async function handleSaveRules() {
  try {
    for (const r of ruleList.value) {
      await api.createQualityRule(selectedKbId.value, {
        ruleName: r.ruleName || r.name, metric: r.metric, weight: r.weight,
        threshold: r.threshold ?? 0.8, enabled: r.enabled ? 1 : 0
      })
    }
    ElMessage.success('规则已保存'); showRules.value = false
  } catch { ElMessage.error('保存失败') }
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
          <el-button @click="handleShowRules">评估规则配置</el-button>
        </div>
      </div>
      <div class="filter-bar">
        <el-select v-model="filterLevel" placeholder="等级筛选" clearable style="width: 120px">
          <el-option v-for="(label, key) in LEVEL_LABELS" :key="key" :label="label" :value="key" />
        </el-select>
      </div>
      <el-table :data="ruleList" stripe>
        <el-table-column label="规则名称" min-width="200">
          <template #default="{ row }">{{ row.ruleName || row.name }}</template>
        </el-table-column>
        <el-table-column label="评估维度" width="120">
          <template #default="{ row }">{{ dimensionLabels[row.metric] || row.metric }}</template>
        </el-table-column>
        <el-table-column label="权重" width="100">
          <template #default="{ row }">{{ row.weight }}</template>
        </el-table-column>
        <el-table-column label="阈值" width="100">
          <template #default="{ row }">{{ row.threshold }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="showRules" title="评估规则配置" width="600px" :close-on-click-modal="false">
      <el-table :data="ruleList" stripe size="small">
        <el-table-column label="维度" min-width="120">
          <template #default="{ row }">{{ row.ruleName || row.name }}</template>
        </el-table-column>
        <el-table-column label="评估维度" width="100">
          <template #default="{ row }">{{ dimensionLabels[row.metric] || row.metric }}</template>
        </el-table-column>
        <el-table-column label="权重" width="150">
          <template #default="{ row }"><el-input-number v-model="row.weight" :min="0" :max="1" :step="0.05" :precision="2" size="small" style="width: 120px" /></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showRules = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRules">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
