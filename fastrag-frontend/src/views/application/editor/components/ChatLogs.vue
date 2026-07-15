<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// 对话记录 — 对接后端真实 API
// ===========================================================================

const chatLogs = ref<Array<{
  id: string
  sessionId: string
  userId: string
  userName: string
  title: string
  firstQuestion: string
  answerSummary: string
  rating: number
  tokenCount: number
  messageCount: number
  createdAt: string
}>>([])

const totalCount = ref(0)
const page = ref(1)
const pageSize = 15
const loading = ref(false)

const keyword = ref('')
const dateRange = ref<[string, string] | null>(null)
const ratingFilter = ref<number | ''>('')
const showDetail = ref(false)
const selectedLog = ref<any>(null)
const detailMessages = ref<any[]>([])
const detailLoading = ref(false)

const filteredLogs = computed(() => chatLogs.value)

async function loadData() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: page.value, size: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    if (ratingFilter.value !== '') params.rating = ratingFilter.value
    if (dateRange.value) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res: any = await api.getAppConversations(appId(), params)
    if (res) {
      // 后端返回 { total, items, page, size }
      const data = res.data || res
      chatLogs.value = Array.isArray(data) ? data : (data.items || [])
      totalCount.value = data.total ?? chatLogs.value.length
    } else {
      chatLogs.value = []
      totalCount.value = 0
    }
  } catch (e) {
    chatLogs.value = []
    totalCount.value = 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  page.value = 1
  loadData()
}

function handleReset() {
  keyword.value = ''
  dateRange.value = null
  ratingFilter.value = ''
  page.value = 1
  loadData()
}

async function handleViewDetail(row: any) {
  detailLoading.value = true
  showDetail.value = true
  selectedLog.value = row
  try {
    const res: any = await api.getAppConversationDetail(appId(), row.id)
    const data = res?.data || res
    detailMessages.value = data?.messages || []
  } catch (e) {
    detailMessages.value = []
  } finally {
    detailLoading.value = false
  }
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('确认删除此对话记录？', '删除确认', { type: 'warning' })
    await api.deleteAppConversation(appId(), id)
    await loadData()
    ElMessage.success('对话记录已删除')
  } catch (e) {
    // cancelled
  }
}

async function handleExport() {
  try {
    const blob = await api.exportAppConversations(appId()) as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `conversations_${appId()}_${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('对话记录已导出')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

function handlePageChange(newPage: number) {
  page.value = newPage
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="config-section">
    <div class="section-group">
      <div class="section-title">对话记录</div>
      <p class="desc">查看和搜索用户与应用的对话历史记录</p>
      <!-- 搜索筛选栏 -->
      <div style="display:flex;gap:12px;margin-bottom:16px;flex-wrap:wrap;align-items:center">
        <el-input v-model="keyword" placeholder="搜索对话内容..." prefix-icon="Search" style="width:260px" size="small" clearable @keyup.enter="handleQuery" />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="small"
          style="width:260px"
          value-format="YYYY-MM-DD"
        />
        <el-select v-model="ratingFilter" placeholder="评分过滤" size="small" style="width:130px" clearable @change="handleQuery">
          <el-option label="全部" :value="''" />
          <el-option label="好评 (5分)" :value="5" />
          <el-option label="中评 (3分)" :value="3" />
          <el-option label="差评 (1分)" :value="1" />
        </el-select>
        <el-button size="small" type="primary" @click="handleQuery" :loading="loading">查询</el-button>
        <el-button size="small" @click="handleReset">重置</el-button>
        <div style="flex:1" />
        <el-button size="small" @click="handleExport">
          <el-icon><Download /></el-icon>导出记录
        </el-button>
      </div>
      <!-- 对话列表 -->
      <el-table :data="filteredLogs" v-loading="loading" border stripe size="small" style="width:100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="userName" label="用户" width="120" />
        <el-table-column prop="sessionId" label="会话ID" width="150" />
        <el-table-column label="消息摘要" min-width="300">
          <template #default="{ row }">
            <div style="display:flex;flex-direction:column;gap:2px">
              <div style="font-size:12px;color:#666">
                <el-tag size="small" type="info" round>Q</el-tag>
                <span style="margin-left:4px">{{ row.firstQuestion || row.title || '-' }}</span>
              </div>
              <div v-if="row.answerSummary" style="font-size:12px;color:#333">
                <el-tag size="small" type="primary" round>A</el-tag>
                <span style="margin-left:4px">{{ row.answerSummary }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="rating" label="评分" width="80" align="center">
          <template #default="{ row }">
            <el-rate v-model="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="messageCount" label="消息数" width="80" align="center" />
        <el-table-column prop="tokenCount" label="Token" width="90" />
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="110" fixed="right">
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
      <el-empty v-if="!filteredLogs.length && !loading" description="暂无对话记录" :image-size="60" />
      <div style="display:flex;justify-content:space-between;align-items:center;margin-top:16px">
        <span style="font-size:13px;color:#999">共 {{ totalCount }} 条记录</span>
        <el-pagination
          background
          layout="prev, pager, next"
          :total="totalCount"
          :page-size="pageSize"
          :current-page="page"
          size="small"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 对话详情抽屉 -->
    <el-drawer v-model="showDetail" title="对话详情" size="500px">
      <div v-if="selectedLog" v-loading="detailLoading">
        <div style="margin-bottom:16px">
          <div style="font-size:13px;color:#999;margin-bottom:8px">
            用户：{{ selectedLog.userName || '匿名' }}
            <span v-if="selectedLog.sessionId"> | 会话ID：{{ selectedLog.sessionId }}</span>
            <span v-if="selectedLog.createdAt"> | 时间：{{ selectedLog.createdAt }}</span>
          </div>
        </div>
        <div v-if="detailMessages.length">
          <div v-for="(msg, i) in detailMessages" :key="i" :class="['detail-msg', msg.role]">
            <div class="detail-msg-label">
              <el-tag :type="msg.role === 'user' ? 'info' : 'primary'" size="small" round>
                {{ msg.role === 'user' ? '用户' : 'AI' }}
              </el-tag>
            </div>
            <div class="detail-msg-content">{{ msg.content }}</div>
            <div class="detail-msg-meta" v-if="msg.tokens">
              Token: {{ msg.tokens }}<span v-if="msg.latencyMs"> | 耗时: {{ (msg.latencyMs / 1000).toFixed(1) }}s</span>
            </div>
          </div>
        </div>
        <el-empty v-else-if="!detailLoading" description="暂无消息详情" />
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
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-msg-meta {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}
</style>
