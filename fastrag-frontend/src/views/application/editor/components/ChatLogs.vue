<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// 对话记录
// ===========================================================================

const chatLogs = ref<Array<{
  id: string
  user: string
  sessionId: string
  question: string
  answer: string
  rating: number
  tokens: number
  time: string
  messages: Array<{ role: 'user' | 'assistant'; content: string; tokens?: number; latency?: string }>
}>>([])

const keyword = ref('')
const dateRange = ref<[Date, Date] | null>(null)
const ratingFilter = ref('')
const showDetail = ref(false)
const selectedLog = ref<any>(null)

// 临时 mock 数据（后端 API 待实现）
const mockLogs = [
  {
    id: '1',
    user: '匿名用户A',
    sessionId: 'sess_abc123',
    question: '你们的服务有哪些功能？',
    answer: '我们提供AI知识库问答、智能客服、文档助手等功能，支持多种模型和知识库配置。',
    rating: 5,
    tokens: 256,
    time: '2026-06-27 14:32:10',
    messages: [
      { role: 'user', content: '你们的服务有哪些功能？', tokens: 12, latency: '0.1s' },
      { role: 'assistant', content: '我们提供AI知识库问答、智能客服、文档助手等功能，支持多种模型和知识库配置。', tokens: 244, latency: '1.2s' },
    ],
  },
  {
    id: '2',
    user: '匿名用户B',
    sessionId: 'sess_def456',
    question: '如何配置知识库？',
    answer: '在知识库配置页面，您可以绑定已有知识库或创建新的知识库。',
    rating: 4,
    tokens: 189,
    time: '2026-06-27 15:10:45',
    messages: [
      { role: 'user', content: '如何配置知识库？', tokens: 10, latency: '0.1s' },
      { role: 'assistant', content: '在知识库配置页面，您可以绑定已有知识库或创建新的知识库。', tokens: 179, latency: '0.9s' },
    ],
  },
  {
    id: '3',
    user: '匿名用户C',
    sessionId: 'sess_ghi789',
    question: '支持哪些模型？',
    answer: '我们支持多种大语言模型，包括 GPT-4、通义千问、文心一言等。',
    rating: 3,
    tokens: 312,
    time: '2026-06-27 16:20:30',
    messages: [
      { role: 'user', content: '支持哪些模型？', tokens: 8, latency: '0.1s' },
      { role: 'assistant', content: '我们支持多种大语言模型，包括 GPT-4、通义千问、文心一言等。', tokens: 304, latency: '1.5s' },
    ],
  },
  {
    id: '4',
    user: '匿名用户D',
    sessionId: 'sess_jkl012',
    question: '应用发布后如何更新？',
    answer: '在编辑器中修改配置后，点击发布按钮即可更新已发布的应用。',
    rating: 5,
    tokens: 178,
    time: '2026-06-28 09:05:12',
    messages: [
      { role: 'user', content: '应用发布后如何更新？', tokens: 11, latency: '0.1s' },
      { role: 'assistant', content: '在编辑器中修改配置后，点击发布按钮即可更新已发布的应用。', tokens: 167, latency: '0.8s' },
    ],
  },
]

const filteredLogs = computed(() => {
  let list = chatLogs.value
  if (keyword.value) {
    list = list.filter(l => l.question.includes(keyword.value) || l.answer.includes(keyword.value))
  }
  if (ratingFilter.value) {
    list = list.filter(l => l.rating === Number(ratingFilter.value))
  }
  return list
})

function handleQuery() {
  ElMessage.success('查询完成')
}

function handleReset() {
  keyword.value = ''
  dateRange.value = null
  ratingFilter.value = ''
}

function handleViewDetail(row: any) {
  selectedLog.value = row
  showDetail.value = true
}

function handleDelete(id: string) {
  chatLogs.value = chatLogs.value.filter(l => l.id !== id)
  ElMessage.success('对话记录已删除')
}

function handleExport() {
  ElMessage.success('对话记录导出中，请稍候...')
}

onMounted(() => {
  // 使用 mock 数据（后端 API 待实现）
  chatLogs.value = mockLogs
})
</script>

<template>
  <div class="config-section">
    <div class="section-group">
      <div class="section-title">对话记录</div>
      <p class="desc">查看和搜索用户与应用的对话历史记录</p>
      <!-- 搜索筛选栏 -->
      <div style="display:flex;gap:12px;margin-bottom:16px;flex-wrap:wrap;align-items:center">
        <el-input v-model="keyword" placeholder="搜索对话内容..." prefix-icon="Search" style="width:260px" size="small" clearable />
        <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" size="small" style="width:260px" />
        <el-select v-model="ratingFilter" placeholder="评分过滤" size="small" style="width:130px" clearable>
          <el-option label="全部" value="" />
          <el-option label="好评" value="5" />
          <el-option label="中评" value="3" />
          <el-option label="差评" value="1" />
        </el-select>
        <el-button size="small" @click="handleQuery">查询</el-button>
        <el-button size="small" @click="handleReset">重置</el-button>
        <div style="flex:1" />
        <el-button size="small" @click="handleExport">
          <el-icon><Download /></el-icon>导出记录
        </el-button>
      </div>
      <!-- 对话列表 -->
      <el-table :data="filteredLogs" border stripe size="small" style="width:100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="user" label="用户" width="120" />
        <el-table-column prop="sessionId" label="会话ID" width="160" />
        <el-table-column label="消息摘要" min-width="300">
          <template #default="{ row }">
            <div style="display:flex;flex-direction:column;gap:2px">
              <div style="font-size:12px;color:#666">
                <el-tag size="small" type="info" round>Q</el-tag>
                <span style="margin-left:4px">{{ row.question }}</span>
              </div>
              <div style="font-size:12px;color:#333">
                <el-tag size="small" type="primary" round>A</el-tag>
                <span style="margin-left:4px">{{ row.answer }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="rating" label="评分" width="80" align="center">
          <template #default="{ row }">
            <el-rate v-model="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="tokens" label="Token消耗" width="110" />
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link size="small" type="primary" @click="handleViewDetail(row)">详情</el-button>
            <el-popconfirm title="确定删除此对话?" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:space-between;align-items:center;margin-top:16px">
        <span style="font-size:13px;color:#999">共 {{ filteredLogs.length }} 条记录</span>
        <el-pagination background layout="prev, pager, next" :total="filteredLogs.length" :page-size="15" size="small" />
      </div>
    </div>

    <!-- 对话详情抽屉 -->
    <el-drawer v-model="showDetail" title="对话详情" size="500px">
      <div v-if="selectedLog">
        <div style="margin-bottom:16px">
          <div style="font-size:13px;color:#999;margin-bottom:8px">用户：{{ selectedLog.user }} | 会话ID：{{ selectedLog.sessionId }} | 时间：{{ selectedLog.time }}</div>
        </div>
        <div v-for="(msg, i) in selectedLog.messages" :key="i" :class="['detail-msg', msg.role]">
          <div class="detail-msg-label">
            <el-tag :type="msg.role === 'user' ? 'info' : 'primary'" size="small" round>{{ msg.role === 'user' ? '用户' : 'AI' }}</el-tag>
          </div>
          <div class="detail-msg-content">{{ msg.content }}</div>
          <div class="detail-msg-meta" v-if="msg.tokens">Token: {{ msg.tokens }} | 耗时: {{ msg.latency }}</div>
        </div>
      </div>
      <el-empty v-else description="暂无数据" />
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section {
  .section-group {
    margin-bottom: $spacing-xl;
  }

  .section-title {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: $spacing-lg;
  }

  .desc {
    color: $text-secondary;
    margin-bottom: $spacing-base;
  }
}

.detail-msg {
  padding: $spacing-base;
  margin-bottom: $spacing-base;
  border-radius: $radius-base;

  &.user {
    background: #ecf5ff;
    margin-left: 40px;
  }

  &.assistant {
    background: $bg-hover;
    margin-right: 40px;
  }
}

.detail-msg-label {
  margin-bottom: $spacing-sm;
}

.detail-msg-content {
  font-size: 14px;
  line-height: 1.6;
  color: $text-primary;
}

.detail-msg-meta {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}
</style>
