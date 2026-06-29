<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const metrics = ref([
  { label: '累计问答量', value: '12,345' },
  { label: '累计反馈量', value: '3,456' },
  { label: '反馈满意度', value: '85.6%' },
  { label: '反馈率', value: '28.0%' },
  { label: '未解决问题占比', value: '12.3%' },
])

const questionCategories = ref([
  { name: '产品功能', count: 1230, percentage: 35 },
  { name: '技术支持', count: 890, percentage: 25 },
  { name: '使用咨询', count: 670, percentage: 19 },
  { name: '故障报修', count: 450, percentage: 13 },
  { name: '其他', count: 216, percentage: 8 },
])

const hotQuestions = ref([
  { word: '登录', count: 234 },
  { word: '知识库', count: 189 },
  { word: '文档', count: 156 },
  { word: 'API', count: 134 },
  { word: '权限', count: 112 },
  { word: '导入', count: 98 },
  { word: '导出', count: 87 },
  { word: '模型', count: 76 },
])

const appSatisfaction = ref([
  { rank: 1, name: '智能问答助手', satisfaction: 92.3, feedbackCount: 1230 },
  { rank: 2, name: '客服机器人', satisfaction: 88.7, feedbackCount: 890 },
  { rank: 3, name: '文档写作助手', satisfaction: 85.2, feedbackCount: 670 },
  { rank: 4, name: '手册演示_ChatBot', satisfaction: 82.1, feedbackCount: 456 },
  { rank: 5, name: '测试应用', satisfaction: 78.5, feedbackCount: 234 },
])

function handleGenerateAISuggestion() {
  ElMessage.info('AI 优化建议生成中...')
}

// 问答明细
const qaDetailSearch = ref({ question: '', user: '' })
const qaDetails = ref([
  { id: '1', question: '普陀山怎么去？', user: '谢晓琴', time: '2026-06-04 10:30', answer: '根据知识库内容...' },
  { id: '2', question: '企业制度里关于请假审批有什么要求？', user: '赵高峰', time: '2026-06-04 09:15', answer: '企业制度规定...' },
  { id: '3', question: '今天是几号？', user: '张三', time: '2026-06-03 16:00', answer: '抱歉，AI服务暂时不可用...' },
])

// 反馈明细（接入真实API）
const feedbackSearch = ref({ kbId: '', feedback: '', status: '', page: 1, pageSize: 10 })
const feedbacks = ref<any[]>([])
const feedbackTotal = ref(0)
const feedbackLoading = ref(false)
const feedbackStats = ref<any>({})

async function loadFeedbacks() {
  feedbackLoading.value = true
  try {
    const res: any = await api.getFeedbackPage({
      kbId: feedbackSearch.value.kbId || undefined,
      feedback: feedbackSearch.value.feedback || undefined,
      status: feedbackSearch.value.status || undefined,
      page: feedbackSearch.value.page,
      pageSize: feedbackSearch.value.pageSize,
    })
    feedbacks.value = res?.list || []
    feedbackTotal.value = res?.total || 0
  } finally {
    feedbackLoading.value = false
  }
}

async function loadFeedbackStats() {
  const res: any = await api.getFeedbackStatistics(feedbackSearch.value.kbId || undefined)
  feedbackStats.value = res || {}
}

function resetFeedbackSearch() {
  feedbackSearch.value = { kbId: '', feedback: '', status: '', page: 1, pageSize: 10 }
  loadFeedbacks()
}

function handleFeedbackPageChange(p: number) {
  feedbackSearch.value.page = p
  loadFeedbacks()
}

// 回复反馈
const showReplyDialog = ref(false)
const replyForm = ref({ id: 0, reply: '' })

function handleProcessFeedback(row: any) {
  replyForm.value = { id: row.id, reply: row.reply || '' }
  showReplyDialog.value = true
}

async function handleReplySubmit() {
  if (!replyForm.value.reply) {
    ElMessage.warning('请输入回复内容')
    return
  }
  await api.replyFeedback(replyForm.value.id, { reply: replyForm.value.reply, operator: 'admin' })
  showReplyDialog.value = false
  ElMessage.success('回复成功')
  await loadFeedbacks()
  await loadFeedbackStats()
}

// 新增/编辑反馈
const showFeedbackFormDialog = ref(false)
const feedbackForm = ref({ id: '', kbId: '', query: '', feedback: 'like', comment: '', score: 5 })
const isEditingFeedback = ref(false)

function handleAddFeedback() {
  isEditingFeedback.value = false
  feedbackForm.value = { id: '', kbId: '', query: '', feedback: 'like', comment: '', score: 5 }
  showFeedbackFormDialog.value = true
}
function handleEditFeedback(row: any) {
  isEditingFeedback.value = true
  feedbackForm.value = { id: row.id, kbId: row.kbId || '', query: row.query || '', feedback: row.feedback || 'like', comment: row.comment || '', score: row.score || 5 }
  showFeedbackFormDialog.value = true
}
async function handleSaveFeedback() {
  if (!feedbackForm.value.query) { ElMessage.warning('请输入问题'); return }
  try {
    if (isEditingFeedback.value) {
      await api.updateFeedback(feedbackForm.value.id, { query: feedbackForm.value.query, feedback: feedbackForm.value.feedback, comment: feedbackForm.value.comment, score: feedbackForm.value.score })
      ElMessage.success('修改成功')
    } else {
      await api.submitFeedback({ kbId: feedbackForm.value.kbId || undefined, query: feedbackForm.value.query, feedback: feedbackForm.value.feedback, comment: feedbackForm.value.comment, score: feedbackForm.value.score })
      ElMessage.success('新增成功')
    }
    showFeedbackFormDialog.value = false
    await loadFeedbacks()
    await loadFeedbackStats()
  } catch { ElMessage.error('操作失败') }
}

async function handleDeleteFeedback(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该反馈记录？', '删除确认', { type: 'warning' })
    await api.deleteFeedback(row.id)
    await loadFeedbacks()
    ElMessage.success('删除成功')
  } catch {}
}

onMounted(() => {
  loadFeedbacks()
  loadFeedbackStats()
})

const activeTab = ref('overview')
const showDetailDialog = ref(false)
const currentDetail = ref<any>(null)

function handleViewDetail(detail: any) {
  currentDetail.value = detail
  showDetailDialog.value = true
}
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="总览" name="overview">
        <div class="metric-cards">
          <div v-for="m in metrics" :key="m.label" class="metric-card">
            <div class="metric-label">{{ m.label }}</div>
            <div class="metric-value">{{ m.value }}</div>
          </div>
        </div>

        <div class="overview-grid">
          <div class="card-panel">
            <div class="section-title">问题分类分析</div>
            <div v-for="cat in questionCategories" :key="cat.name" class="category-item">
              <div class="cat-header">
                <span>{{ cat.name }}</span>
                <span>{{ cat.count }} ({{ cat.percentage }}%)</span>
              </div>
              <el-progress :percentage="cat.percentage" :show-text="false" />
            </div>
          </div>

          <div class="card-panel">
            <div class="section-title">高频问题词云</div>
            <div class="word-cloud">
              <span v-for="q in hotQuestions" :key="q.word" class="word" :style="{ fontSize: 12 + q.count / 20 + 'px' }">
                {{ q.word }}
              </span>
            </div>
          </div>

          <div class="card-panel">
            <div class="section-title">应用满意度排行</div>
            <div v-for="app in appSatisfaction" :key="app.rank" class="rank-item">
              <span class="rank" :class="{ 'top-3': app.rank <= 3 }">{{ app.rank }}</span>
              <span class="name">{{ app.name }}</span>
              <span class="satisfaction">{{ app.satisfaction }}%</span>
              <span class="count">{{ app.feedbackCount }} 条</span>
            </div>
          </div>

          <div class="card-panel">
            <div class="section-title">AI 优化建议</div>
            <el-button type="primary" @click="handleGenerateAISuggestion">生成AI优化建议</el-button>
            <p class="ai-tip">点击按钮根据当前统计数据生成优化方向和运营建议</p>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="问答明细" name="qa">
        <div class="card-panel">
          <div class="filter-bar">
            <el-input v-model="qaDetailSearch.question" placeholder="问题描述" clearable style="width: 200px" />
            <el-input v-model="qaDetailSearch.user" placeholder="用户名称" clearable style="width: 200px" />
            <el-button type="primary">查询</el-button>
            <el-button>重置</el-button>
          </div>
          <el-table :data="qaDetails" stripe>
            <el-table-column prop="question" label="问题描述" show-overflow-tooltip />
            <el-table-column prop="user" label="用户" width="120" />
            <el-table-column prop="time" label="时间" width="180" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleViewDetail(row)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

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
            <el-input v-model="feedbackSearch.kbId" placeholder="知识库ID" clearable style="width: 150px" />
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
            <el-button type="primary" @click="feedbackSearch.page = 1; loadFeedbacks()">查询</el-button>
            <el-button @click="resetFeedbackSearch">重置</el-button>
          </div>
          <el-table :data="feedbacks" stripe v-loading="feedbackLoading">
            <el-table-column prop="query" label="提问问题" show-overflow-tooltip />
            <el-table-column prop="feedback" label="反馈类型" width="90">
              <template #default="{ row }">
                <el-tag :type="row.feedback === 'like' ? 'success' : (row.feedback === 'report' ? 'danger' : 'warning')" size="small">
                  {{ row.feedback === 'like' ? '赞' : (row.feedback === 'dislike' ? '踩' : (row.feedback === 'report' ? '报错' : row.feedback)) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="comment" label="反馈内容" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'resolved' ? 'success' : (row.status === 'ignored' ? 'info' : 'warning')" size="small">
                  {{ row.status === 'resolved' ? '已解决' : (row.status === 'ignored' ? '已忽略' : '待处理') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="160" />
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button link size="small" @click="handleEditFeedback(row)">编辑</el-button>
                <el-button link type="primary" size="small" @click="handleProcessFeedback(row)">回复</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteFeedback(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="feedbackTotal > 0"
            class="table-footer"
            background
            layout="total, prev, pager, next"
            :total="feedbackTotal"
            :current-page="feedbackSearch.page"
            :page-size="feedbackSearch.pageSize"
            @current-change="handleFeedbackPageChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showDetailDialog" title="问答详情" width="600px">
      <div v-if="currentDetail">
        <h4>问题：{{ currentDetail.question }}</h4>
        <p>{{ currentDetail.answer }}</p>
      </div>
    </el-dialog>

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

    <!-- 新增/编辑反馈 -->
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
        <el-form-item label="知识库ID" v-if="!isEditingFeedback">
          <el-input v-model="feedbackForm.kbId" placeholder="选填" />
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

.overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-base;
}

.category-item {
  margin-bottom: $spacing-base;
  .cat-header { display: flex; justify-content: space-between; font-size: 13px; margin-bottom: $spacing-xs; }
}

.word-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  align-items: center;
  justify-content: center;
  padding: $spacing-lg;
  .word { color: $color-primary; cursor: pointer; &:hover { color: $color-success; } }
}

.rank-item {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  padding: $spacing-sm 0;
  border-bottom: 1px solid $border-extra-light;
  .rank {
    width: 24px; height: 24px; border-radius: 50%; background: $border-lighter;
    display: flex; align-items: center; justify-content: center;
    font-size: 12px; font-weight: 600; color: $text-secondary;
    &.top-3 { background: $color-primary; color: #fff; }
  }
  .name { flex: 1; font-size: 13px; }
  .satisfaction { font-weight: 600; color: $color-success; }
  .count { font-size: 12px; color: $text-secondary; }
}

.ai-tip { font-size: 12px; color: $text-secondary; margin-top: $spacing-sm; }

.expand-info { padding: $spacing-base; p { margin: $spacing-xs 0; } }

.stats-row {
  display: flex; gap: $spacing-lg; margin-bottom: $spacing-base; padding: $spacing-base; background: $bg-white; border-radius: $radius-base;
  .stat-item { display: flex; flex-direction: column; gap: 4px; }
  .stat-label { font-size: 12px; color: $text-secondary; }
  .stat-val { font-size: 20px; font-weight: 700; }
}
.table-footer { margin-top: $spacing-base; display: flex; justify-content: flex-end; }
</style>
