<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

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

// 反馈明细
const feedbackSearch = ref({ source: '', question: '', type: '', status: '' })
const feedbacks = ref([
  { id: '1', source: '官网Bot', question: '今天是几号？', type: '不满意', status: '待处理', reporter: '张三', problemType: '无效回答' },
  { id: '2', source: '企业Bot', question: '请假流程', type: '满意', status: '已处理', reporter: '李四', problemType: '' },
])

const activeTab = ref('overview')
const showDetailDialog = ref(false)
const currentDetail = ref<any>(null)

function handleViewDetail(detail: any) {
  currentDetail.value = detail
  showDetailDialog.value = true
}

function handleProcessFeedback(feedback: any) {
  ElMessage.info('处理反馈：' + feedback.question)
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
          <div class="filter-bar">
            <el-input v-model="feedbackSearch.source" placeholder="反馈来源" clearable style="width: 150px" />
            <el-input v-model="feedbackSearch.question" placeholder="提问问题" clearable style="width: 150px" />
            <el-select v-model="feedbackSearch.type" placeholder="反馈类型" clearable style="width: 120px">
              <el-option label="满意" value="满意" />
              <el-option label="不满意" value="不满意" />
            </el-select>
            <el-select v-model="feedbackSearch.status" placeholder="处理状态" clearable style="width: 120px">
              <el-option label="待处理" value="待处理" />
              <el-option label="已处理" value="已处理" />
            </el-select>
            <el-button type="primary">查询</el-button>
            <el-button>重置</el-button>
          </div>
          <el-table :data="feedbacks" stripe>
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-info">
                  <p>反馈人：{{ row.reporter }}</p>
                  <p>问题类型：{{ row.problemType || '-' }}</p>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="反馈来源" width="120" />
            <el-table-column prop="question" label="提问问题" show-overflow-tooltip />
            <el-table-column prop="type" label="反馈类型" width="100">
              <template #default="{ row }">
                <el-tag :type="row.type === '满意' ? 'success' : 'danger'" size="small">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="处理状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === '已处理' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleProcessFeedback(row)">处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showDetailDialog" title="问答详情" width="600px">
      <div v-if="currentDetail">
        <h4>问题：{{ currentDetail.question }}</h4>
        <p>{{ currentDetail.answer }}</p>
      </div>
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
</style>
