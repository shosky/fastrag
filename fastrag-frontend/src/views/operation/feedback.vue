<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import * as api from '@/api'
import { usePagination } from '@/composables/usePagination'
import type {
  ChatSession,
  UserFeedback,
  FeedbackOverview,
  FeedbackMetrics,
  FeedbackStatistics,
} from '@/types/feedback'
import MetricCards from './components/MetricCards.vue'
import RankList from './components/RankList.vue'
import WordCloud from './components/WordCloud.vue'

// ========== 工具 ==========
function formatTime(ts?: string): string {
  if (!ts) return '-'
  return ts.replace('T', ' ').substring(0, 19)
}

// ========== 总览 Tab ==========
const activeTab = ref('overview')
const overviewData = ref<Partial<FeedbackOverview>>({})
const overviewLoading = ref(false)

async function loadOverview() {
  overviewLoading.value = true
  try {
    const res = await api.getFeedbackOverview(feedbackSearch.value.kbId || undefined)
    overviewData.value = res || {}
  } catch {
    overviewData.value = {}
  } finally {
    overviewLoading.value = false
  }
}

const metrics: { label: string; key: keyof FeedbackMetrics; suffix?: string }[] = [
  { label: '累计问答量', key: 'totalQaCount' },
  { label: '累计反馈量', key: 'totalFeedbackCount' },
  { label: '反馈满意度', key: 'satisfactionRate', suffix: '%' },
  { label: '反馈率', key: 'feedbackRate', suffix: '%' },
  { label: '未解决问题占比', key: 'unresolvedRate', suffix: '%' },
]

const metricItems = computed(() =>
  metrics.map((m) => ({
    label: m.label,
    value: overviewData.value?.metrics?.[m.key] ?? '—',
    suffix: m.suffix,
  }))
)

// ========== 问答明细 Tab ==========
const qaDetailSearch = ref({ question: '', user: '' })
const qaData = ref<ChatSession[]>([])
const qaLoading = ref(false)

const {
  currentPage: qaPage,
  pageSize: qaPageSize,
  total: qaTotal,
  handleCurrentChange: onQaPageChange,
  handleSizeChange: onQaSizeChange,
} = usePagination(10)

async function loadQaDetails() {
  qaLoading.value = true
  try {
    const res = await api.getChatSessions({
      keyword: qaDetailSearch.value.question || undefined,
      userId: qaDetailSearch.value.user || undefined,
      page: qaPage.value,
      pageSize: qaPageSize.value,
    })
    qaData.value = res?.list || []
    qaTotal.value = res?.total || 0
  } catch {
    qaData.value = []
    qaTotal.value = 0
  } finally {
    qaLoading.value = false
  }
}

/** 事件驱动：页码变化时重新拉取（同时更新 composable 状态） */
function handleQaPageChange(page: number) {
  onQaPageChange(page)
  loadQaDetails()
}

/** 事件驱动：每页条数变化时重置到第一页并拉取 */
function handleQaSizeChange(size: number) {
  onQaSizeChange(size)
  loadQaDetails()
}

function handleQaSearch() {
  qaPage.value = 1
  loadQaDetails()
}

function handleQaReset() {
  qaDetailSearch.value = { question: '', user: '' }
  qaPage.value = 1
  loadQaDetails()
}

const showDetailDialog = ref(false)
const currentDetail = ref<ChatSession | null>(null)

function handleViewDetail(detail: ChatSession) {
  currentDetail.value = detail
  showDetailDialog.value = true
}

// ========== 反馈明细 Tab ==========
const feedbackSearch = ref({ kbId: '', feedback: '', status: '' })
const feedbacks = ref<UserFeedback[]>([])
const feedbackLoading = ref(false)
const feedbackStats = ref<Partial<FeedbackStatistics>>({})
const kbOptions = ref<{ id: string; name: string }[]>([])

const {
  currentPage: fbPage,
  pageSize: fbPageSize,
  total: fbTotal,
  handleCurrentChange: onFbPageChange,
  handleSizeChange: onFbSizeChange,
} = usePagination(10)

async function loadKbOptions() {
  try {
    const res = await api.getKnowledgeBases({ page: 1, pageSize: 999 })
    kbOptions.value = (res?.list || []).map((kb) => ({
      id: String(kb.id),
      name: kb.name || kb.id,
    }))
  } catch {
    // ignore：选择器无选项时保持兜底
  }
}

async function loadFeedbacks() {
  feedbackLoading.value = true
  try {
    const res = await api.getFeedbackPage({
      kbId: feedbackSearch.value.kbId || undefined,
      feedback: feedbackSearch.value.feedback || undefined,
      status: feedbackSearch.value.status || undefined,
      page: fbPage.value,
      pageSize: fbPageSize.value,
    })
    feedbacks.value = res?.list || []
    fbTotal.value = res?.total || 0
  } catch {
    feedbacks.value = []
    fbTotal.value = 0
  } finally {
    feedbackLoading.value = false
  }
}

async function loadFeedbackStats() {
  try {
    const res = await api.getFeedbackStatistics(feedbackSearch.value.kbId || undefined)
    feedbackStats.value = res || {}
  } catch {
    feedbackStats.value = {}
  }
}

function resetFeedbackSearch() {
  feedbackSearch.value = { kbId: '', feedback: '', status: '' }
  fbPage.value = 1
  loadFeedbacks()
  loadFeedbackStats()
  loadOverview()
}

function handleFbSearch() {
  fbPage.value = 1
  loadFeedbacks()
  loadFeedbackStats()
  loadOverview()
}

/** 事件驱动：页码变化时重新拉取（同时更新 composable 状态） */
function handleFbPageChange(page: number) {
  onFbPageChange(page)
  loadFeedbacks()
}

/** 事件驱动：每页条数变化时重置到第一页并拉取 */
function handleFbSizeChange(size: number) {
  onFbSizeChange(size)
  loadFeedbacks()
}

// 回复反馈
const showReplyDialog = ref(false)
const replyForm = ref({ id: 0, reply: '' })

function handleProcessFeedback(row: UserFeedback) {
  replyForm.value = { id: row.id, reply: row.reply || '' }
  showReplyDialog.value = true
}

async function handleReplySubmit() {
  if (!replyForm.value.reply) {
    ElMessage.warning('请输入回复内容')
    return
  }
  try {
    await api.replyFeedback(replyForm.value.id, { reply: replyForm.value.reply, operator: 'admin' })
    showReplyDialog.value = false
    ElMessage.success('回复成功')
    await loadFeedbacks()
    await loadFeedbackStats()
  } catch {
    ElMessage.error('回复失败，请重试')
  }
}

// 新增/编辑反馈
const showFeedbackFormDialog = ref(false)
const feedbackForm = ref<{ id: number | string; kbId: string; query: string; feedback: string; comment: string; score: number }>({
  id: '', kbId: '', query: '', feedback: 'like', comment: '', score: 5,
})
const isEditingFeedback = ref(false)

function handleAddFeedback() {
  isEditingFeedback.value = false
  feedbackForm.value = { id: '', kbId: '', query: '', feedback: 'like', comment: '', score: 5 }
  showFeedbackFormDialog.value = true
}

function handleEditFeedback(row: UserFeedback) {
  isEditingFeedback.value = true
  feedbackForm.value = {
    id: row.id, kbId: row.kbId || '', query: row.query || '',
    feedback: row.feedback || 'like', comment: row.comment || '', score: row.score || 5,
  }
  showFeedbackFormDialog.value = true
}

async function handleSaveFeedback() {
  if (!feedbackForm.value.query) { ElMessage.warning('请输入问题'); return }
  try {
    if (isEditingFeedback.value) {
      await api.updateFeedback(feedbackForm.value.id, {
        query: feedbackForm.value.query, feedback: feedbackForm.value.feedback,
        comment: feedbackForm.value.comment, score: feedbackForm.value.score,
      })
      ElMessage.success('修改成功')
    } else {
      await api.submitFeedback({
        kbId: feedbackForm.value.kbId || undefined, query: feedbackForm.value.query,
        feedback: feedbackForm.value.feedback, comment: feedbackForm.value.comment, score: feedbackForm.value.score,
      })
      ElMessage.success('新增成功')
    }
    showFeedbackFormDialog.value = false
    await loadFeedbacks()
    await loadFeedbackStats()
    await loadOverview()
  } catch { ElMessage.error('操作失败') }
}

async function handleDeleteFeedback(row: UserFeedback) {
  try {
    await ElMessageBox.confirm('确定删除该反馈记录？', '删除确认', { type: 'warning' })
    await api.deleteFeedback(row.id)
    await loadFeedbacks()
    await loadFeedbackStats()
    ElMessage.success('删除成功')
  } catch {}
}

// ========== 优化建议（基于统计指标的规则建议，非 AI 生成） ==========
function handleGenerateSuggestion() {
  const m = overviewData.value?.metrics
  if (!m) { ElMessage.info('请先加载数据'); return }
  const suggestions: string[] = []
  if (m.satisfactionRate < 80) suggestions.push('反馈满意度偏低（<80%），建议检查回答质量，优化知识库覆盖')
  if (m.feedbackRate < 20) suggestions.push('反馈率偏低（<20%），建议在对话界面增加反馈引导，鼓励用户评价')
  if (m.unresolvedRate > 20) suggestions.push('未解决问题占比偏高（>20%），建议梳理常见未解决问题，补充相关知识')
  if (!suggestions.length) suggestions.push('当前运营状态良好，暂无优化建议')
  ElMessageBox.alert(
    suggestions.map(s => `• ${s}`).join('<br>'),
    '规则优化建议',
    { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' }
  )
}

onMounted(() => {
  loadKbOptions()
  loadOverview()
  loadFeedbacks()
  loadFeedbackStats()
})

// 问答明细 Tab 懒加载：进入 Tab 时再拉取（避免页面初始化时多余请求）
watch(activeTab, (tab) => {
  if (tab === 'qa') loadQaDetails()
})
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <!-- ========== 总览 Tab ========== -->
      <el-tab-pane label="总览" name="overview">
        <div v-loading="overviewLoading">
          <MetricCards :items="metricItems" :columns="5" />

          <div class="overview-grid">
            <div class="card-panel">
              <div class="section-title">问题分类分析</div>
              <div v-if="overviewData?.questionCategories?.length">
                <div v-for="cat in overviewData.questionCategories" :key="cat.name" class="category-item">
                  <div class="cat-header">
                    <span>{{ cat.name }}</span>
                    <span>{{ cat.count }} ({{ cat.percentage }}%)</span>
                  </div>
                  <el-progress :percentage="cat.percentage" :show-text="false" />
                </div>
              </div>
              <el-empty v-else description="暂无分类数据" :image-size="50" />
            </div>

            <div class="card-panel">
              <div class="section-title">高频问题词云</div>
              <WordCloud v-if="overviewData?.hotKeywords?.length" :words="overviewData.hotKeywords" />
              <el-empty v-else description="暂无词云数据" :image-size="50" />
            </div>

            <div class="card-panel">
              <div class="section-title">应用满意度排行</div>
              <RankList v-if="overviewData?.appSatisfactionRanking?.length" :items="overviewData.appSatisfactionRanking">
                <template #default="{ item }">
                  <span class="satisfaction">{{ item.satisfaction }}%</span>
                  <span class="count">{{ item.feedbackCount }} 条</span>
                </template>
              </RankList>
              <el-empty v-else description="暂无排行数据" :image-size="50" />
            </div>

            <div class="card-panel">
              <div class="section-title">规则优化建议</div>
              <el-button type="primary" @click="handleGenerateSuggestion">生成优化建议</el-button>
              <p class="ai-tip">基于满意度、反馈率、未解决率等统计指标按预设规则生成，非 AI 生成</p>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ========== 问答明细 Tab ========== -->
      <el-tab-pane label="问答明细" name="qa">
        <div class="card-panel">
          <div class="filter-bar">
            <el-input v-model="qaDetailSearch.question" placeholder="问题描述" clearable style="width: 200px" />
            <el-input v-model="qaDetailSearch.user" placeholder="用户ID" clearable style="width: 200px" />
            <el-button type="primary" @click="handleQaSearch">查询</el-button>
            <el-button @click="handleQaReset">重置</el-button>
          </div>
          <el-table :data="qaData" stripe v-loading="qaLoading">
            <el-table-column prop="query" label="问题描述" show-overflow-tooltip />
            <el-table-column prop="userId" label="用户" width="120" />
            <el-table-column label="时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleViewDetail(row as ChatSession)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!qaLoading && qaData.length === 0" description="暂无数据" :image-size="60" />
          <div class="feedback__qa-pagination">
            <el-pagination
              v-model:current-page="qaPage"
              v-model:page-size="qaPageSize"
              :total="qaTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleQaPageChange"
              @size-change="handleQaSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- ========== 反馈明细 Tab ========== -->
      <el-tab-pane label="反馈明细" name="feedback">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">反馈明细</div>
            <el-button type="primary" @click="handleAddFeedback">
              <el-icon><Plus /></el-icon>新增反馈
            </el-button>
          </div>
          <div class="stats-row" v-if="feedbackStats.total !== undefined">
            <div class="stat-item"><span class="stat-label">反馈总数</span><span class="stat-val">{{ feedbackStats.total }}</span></div>
            <div class="stat-item"><span class="stat-label">满意度</span><span class="stat-val">{{ feedbackStats.satisfactionRate }}%</span></div>
            <div class="stat-item"><span class="stat-label">已解决率</span><span class="stat-val">{{ feedbackStats.resolvedRate }}%</span></div>
            <div class="stat-item"><span class="stat-label">待处理</span><span class="stat-val">{{ (feedbackStats.byStatus && feedbackStats.byStatus.pending) || 0 }}</span></div>
          </div>
          <div class="filter-bar">
            <el-select v-model="feedbackSearch.kbId" placeholder="知识库" clearable filterable style="width: 180px">
              <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
            </el-select>
            <el-select v-model="feedbackSearch.feedback" placeholder="反馈类型" clearable style="width: 120px">
              <el-option label="赞" value="like" />
              <el-option label="踩" value="dislike" />
              <el-option label="报错" value="report" />
            </el-select>
            <el-select v-model="feedbackSearch.status" placeholder="处理状态" clearable style="width: 120px">
              <el-option label="待处理" value="pending" />
              <el-option label="已解决" value="resolved" />
              <el-option label="已忽略" value="ignored" />
            </el-select>
            <el-button type="primary" @click="handleFbSearch">查询</el-button>
            <el-button @click="resetFeedbackSearch">重置</el-button>
          </div>
          <el-table :data="feedbacks" stripe v-loading="feedbackLoading">
            <el-table-column prop="query" label="提问问题" show-overflow-tooltip />
            <el-table-column prop="feedback" label="反馈类型" width="90">
              <template #default="{ row }">
                <el-tag
                  :type="row.feedback === 'like' ? 'success' : (row.feedback === 'report' ? 'danger' : 'warning')"
                  size="small"
                >
                  {{ row.feedback === 'like' ? '赞' : (row.feedback === 'dislike' ? '踩' : (row.feedback === 'report' ? '报错' : row.feedback)) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="comment" label="反馈内容" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag
                  :type="row.status === 'resolved' ? 'success' : (row.status === 'ignored' ? 'info' : 'warning')"
                  size="small"
                >
                  {{ row.status === 'resolved' ? '已解决' : (row.status === 'ignored' ? '已忽略' : '待处理') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button link size="small" @click="handleEditFeedback(row as UserFeedback)">编辑</el-button>
                <el-button link type="primary" size="small" @click="handleProcessFeedback(row as UserFeedback)">回复</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteFeedback(row as UserFeedback)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="feedback__list-pagination">
            <el-pagination
              v-model:current-page="fbPage"
              v-model:page-size="fbPageSize"
              :total="fbTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleFbPageChange"
              @size-change="handleFbSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 问答详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="问答详情" width="600px">
      <div v-if="currentDetail">
        <p><strong>问题：</strong>{{ currentDetail.query }}</p>
        <p><strong>回答：</strong>{{ currentDetail.answer }}</p>
        <p v-if="currentDetail.model"><strong>模型：</strong>{{ currentDetail.model }}</p>
        <p v-if="currentDetail.duration"><strong>耗时：</strong>{{ currentDetail.duration }}ms</p>
        <p v-if="currentDetail.tokens"><strong>Token 消耗：</strong>{{ currentDetail.tokens }}</p>
        <p><strong>时间：</strong>{{ formatTime(currentDetail.createdAt) }}</p>
      </div>
    </el-dialog>

    <!-- 回复反馈对话框 -->
    <el-dialog v-model="showReplyDialog" title="回复反馈" width="500px">
      <el-form label-width="80px">
        <el-form-item label="回复内容" required>
          <el-input v-model="replyForm.reply" type="textarea" :rows="4" placeholder="请输入回复内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReplyDialog = false">取消</el-button>
        <el-button type="primary" @click="handleReplySubmit">提交回复</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑反馈对话框 -->
    <el-dialog v-model="showFeedbackFormDialog" :title="isEditingFeedback ? '编辑反馈' : '新增反馈'" width="550px">
      <el-form label-width="100px">
        <el-form-item label="问题" required><el-input v-model="feedbackForm.query" placeholder="请输入用户问题" /></el-form-item>
        <el-form-item label="反馈内容"><el-input v-model="feedbackForm.comment" type="textarea" :rows="3" placeholder="请输入反馈内容" /></el-form-item>
        <el-form-item label="反馈类型">
          <el-select v-model="feedbackForm.feedback" style="width:200px">
            <el-option label="赞" value="like" /><el-option label="踩" value="dislike" /><el-option label="报错" value="report" />
          </el-select>
        </el-form-item>
        <el-form-item label="评分">
          <el-rate v-model="feedbackForm.score" :max="5" />
        </el-form-item>
        <el-form-item label="知识库" v-if="!isEditingFeedback">
          <el-select v-model="feedbackForm.kbId" placeholder="选填" clearable filterable style="width: 200px">
            <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFeedbackFormDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveFeedback">{{ isEditingFeedback ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-base;
}

.category-item {
  margin-bottom: $spacing-base;
  .cat-header { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: $spacing-xs; }
}

.ai-tip { font-size: 12px; color: $text-secondary; margin-top: $spacing-sm; }

.stats-row {
  display: flex; gap: $spacing-lg; margin-bottom: $spacing-base; padding: $spacing-base; background: $bg-white; border-radius: $radius-base;
  .stat-item { display: flex; flex-direction: column; gap: 4px; }
  .stat-label { font-size: 12px; color: $text-secondary; }
  .stat-val { font-size: 20px; font-weight: 700; }
}

// RankList 插槽附加字段（排行行内样式）
.satisfaction { font-weight: 600; color: $color-success; }
.count { font-size: 12px; color: $text-secondary; }

// 分页 BEM 风格 — 遵循 AGENTS.md 规范（同页两个分页块共享样式）
.feedback__qa-pagination,
.feedback__list-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
