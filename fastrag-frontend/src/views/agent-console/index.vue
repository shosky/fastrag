<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Promotion, Connection } from '@element-plus/icons-vue'
import * as api from '@/api'
import { listSimpleFlows } from '@/api/bpm'

/**
 * 座席工作台（座席端管理）：
 * - 知识推荐：识别知识推荐（输入问题自动识别并推荐知识/关键词）+ 推荐知识列表（真实检索）
 * - 知识流程搜索：按名称检索业务流程（BPM），辅助座席处理流程类咨询
 */
const loading = ref(false)
const kbs = ref<any[]>([])
const kbId = ref('')

// 知识推荐
const query = ref('')
const identifiedKeywords = ref<any[]>([])
const recommendResults = ref<any[]>([])

// 流程搜索
const flowKeyword = ref('')
const flows = ref<any[]>([])
const flowLoading = ref(false)

onMounted(async () => {
  try {
    const res: any = await api.getKnowledgeBases()
    kbs.value = res?.list || res || []
    if (kbs.value.length) kbId.value = kbs.value[0].id
  } catch { kbs.value = [] }
})

// 识别知识推荐：关键词判断 + 检索推荐一次完成
async function handleRecommend() {
  if (!query.value.trim()) { ElMessage.warning('请输入客户问题'); return }
  if (!kbId.value) { ElMessage.warning('请选择知识库'); return }
  loading.value = true
  identifiedKeywords.value = []
  recommendResults.value = []
  try {
    try {
      const judge: any = await api.judgeKeywords(kbId.value, query.value)
      identifiedKeywords.value = judge?.keywords || []
    } catch { /* 判断失败不阻塞推荐 */ }
    recommendResults.value = await api.searchRetrieval({
      knowledgeId: kbId.value,
      query: query.value,
      config: { topK: 8, similarityThreshold: 0 },
    } as any)
  } catch { ElMessage.error('推荐失败，请检查后端服务') } finally { loading.value = false }
}

// 知识流程搜索
async function handleFlowSearch() {
  flowLoading.value = true
  try {
    const res: any = await listSimpleFlows({ keyword: flowKeyword.value || undefined })
    flows.value = Array.isArray(res) ? res : (res?.list || [])
  } catch { flows.value = [] } finally { flowLoading.value = false }
}
onMounted(handleFlowSearch)
</script>

<template>
  <div class="agent-console">
    <div class="agent-console__header">
      <div>
        <h2 class="agent-console__title">座席工作台</h2>
        <p class="agent-console__sub">面向座席的知识推荐与流程搜索辅助</p>
      </div>
      <el-select v-model="kbId" placeholder="选择知识库" style="width: 240px">
        <el-option v-for="k in kbs" :key="k.id" :label="k.name" :value="k.id" />
      </el-select>
    </div>

    <!-- 知识推荐 -->
    <el-card shadow="never" class="agent-card">
      <template #header>
        <div class="agent-card__header">
          <el-icon><Promotion /></el-icon>
          <span>知识推荐</span>
        </div>
      </template>
      <div style="display: flex; gap: 8px; margin-bottom: 16px">
        <el-input v-model="query" size="large" placeholder="输入客户问题，系统自动识别并推荐知识..."
          @keyup.enter="handleRecommend" clearable />
        <el-button type="primary" size="large" :icon="Search" :loading="loading" @click="handleRecommend">
          识别并推荐
        </el-button>
      </div>

      <div v-if="identifiedKeywords.length" style="margin-bottom: 12px">
        <span style="font-size: 13px; color: #909399; margin-right: 8px">识别到关键词：</span>
        <el-tag v-for="(k, i) in identifiedKeywords" :key="i" type="warning" size="small" style="margin-right: 6px">
          {{ k.text }}
        </el-tag>
      </div>

      <el-table v-if="recommendResults.length" :data="recommendResults" stripe size="small">
        <el-table-column type="index" width="46" />
        <el-table-column label="推荐知识" min-width="380">
          <template #default="{ row }">
            <div class="result-content" v-html="row.previewSnippet || row.content" />
          </template>
        </el-table-column>
        <el-table-column label="相似度" width="100">
          <template #default="{ row }">
            <el-tag :type="row.fallback ? 'info' : 'success'" size="small">{{ row.similarity.toFixed(2) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileId" label="来源文档" width="140" show-overflow-tooltip />
      </el-table>
      <el-empty v-else-if="!loading" description="输入问题后点击「识别并推荐」获取知识推荐" :image-size="80" />
    </el-card>

    <!-- 知识流程搜索 -->
    <el-card shadow="never" class="agent-card">
      <template #header>
        <div class="agent-card__header">
          <el-icon><Connection /></el-icon>
          <span>知识流程搜索</span>
        </div>
      </template>
      <div style="display: flex; gap: 8px; margin-bottom: 12px">
        <el-input v-model="flowKeyword" placeholder="按流程名称搜索业务流程..." clearable @keyup.enter="handleFlowSearch" />
        <el-button type="primary" :loading="flowLoading" @click="handleFlowSearch">搜索流程</el-button>
      </div>
      <el-table v-if="flows.length" :data="flows" stripe size="small">
        <el-table-column prop="name" label="流程名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column prop="triggerType" label="触发方式" width="100" />
        <el-table-column prop="updatedAt" label="更新时间" width="160" show-overflow-tooltip />
      </el-table>
      <el-empty v-else-if="!flowLoading" description="无匹配流程" :image-size="60" />
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.agent-console { padding: 16px 20px; max-width: 1100px; margin: 0 auto; }
.agent-console__header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;
}
.agent-console__title { font-size: 20px; font-weight: 600; margin: 0; }
.agent-console__sub { font-size: 13px; color: #909399; margin: 4px 0 0; }
.agent-card { margin-bottom: 16px; }
.agent-card__header { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.result-content {
  font-size: 13px; color: #606266; line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
  :deep(mark) { background: #fdf6ec; color: #e6a23c; padding: 0 2px; }
}
</style>
