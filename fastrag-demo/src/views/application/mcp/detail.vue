<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import {
  getMcpService,
  deleteMcpService,
  toggleMcpEnabled,
  refreshMcpService,
  getStatusLabel,
} from '@/mock/mcp'
import type { McpService } from '@/mock/mcp'

const route = useRoute()
const router = useRouter()

const service = ref<McpService | null>(null)
const loading = ref(false)
const refreshing = ref(false)
const activeTab = ref('tools')

const id = route.params.id as string

async function loadService() {
  loading.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 200))
    const data = getMcpService(id)
    if (!data) {
      ElMessage.error('MCP 服务不存在')
      router.push('/application/mcp-management')
      return
    }
    service.value = data
  } finally {
    loading.value = false
  }
}

/** 刷新服务：重新拉取工具列表 + 检测连通性 */
async function handleRefresh() {
  if (!service.value) return
  refreshing.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 600))
    const refreshed = refreshMcpService(id)
    if (refreshed) {
      service.value = refreshed
      ElMessage.success(`已刷新，当前状态：${getStatusLabel(refreshed.status)}，工具 ${refreshed.toolsList.length} 个`)
    }
  } finally {
    refreshing.value = false
  }
}

/** 切换启用状态 */
function handleToggleEnabled() {
  if (!service.value) return
  const wasEnabled = service.value.enabled
  toggleMcpEnabled(id)
  service.value = getMcpService(id)
  ElMessage.success(wasEnabled ? '已禁用' : '已启用')
}

function goEdit() {
  router.push(`/application/mcp-management/${id}/edit`)
}

function goBack() {
  router.push('/application/mcp-management')
}

async function handleDelete() {
  if (!service.value) return
  try {
    await ElMessageBox.confirm(
      `确定要删除 MCP 服务「${service.value.name}」吗？`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    deleteMcpService(id)
    ElMessage.success('删除成功')
    router.push('/application/mcp-management')
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadService()
})
</script>

<template>
  <div class="page-container">
    <!-- 顶部操作栏 -->
    <div class="page-header">
      <el-button @click="goBack">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h3>MCP 服务详情</h3>
      <div class="header-actions">
        <el-button :loading="refreshing" @click="handleRefresh">
          <el-icon v-if="!refreshing"><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="primary" plain @click="goEdit">
          <el-icon><Edit /></el-icon>编辑
        </el-button>
        <el-button type="danger" plain @click="handleDelete">
          <el-icon><Delete /></el-icon>删除
        </el-button>
      </div>
    </div>

    <div v-if="loading" v-loading="true" style="min-height: 400px" />

    <template v-else-if="service">
      <!-- 元信息卡 -->
      <div class="detail-meta-card">
        <div class="meta-icon">
          <el-icon :size="24" color="#909399"><Connection /></el-icon>
        </div>
        <div class="meta-main">
          <div class="meta-title">
            <span class="title-name">{{ service.name }}</span>
            <span :class="['status-text', service.status === 'online' ? 'is-on' : 'is-off']">
              {{ getStatusLabel(service.status) }}
            </span>
            <span class="title-meta">{{ service.toolsList.length }} 个工具</span>
          </div>
          <p class="meta-url" :title="service.mcpUrl">{{ service.mcpUrl }}</p>
          <div class="meta-info">
            <span><el-icon><Key /></el-icon>鉴权：{{ service.authType }}</span>
            <span><el-icon><Clock /></el-icon>最近使用：{{ service.lastUsed }}</span>
            <span><el-icon><Calendar /></el-icon>创建：{{ service.createdAt }}</span>
          </div>
        </div>
        <div class="meta-switch">
          <span>启用</span>
          <el-switch :model-value="service.enabled" @change="handleToggleEnabled" />
        </div>
      </div>

      <!-- Tab 分区 -->
      <div class="detail-tabs-card">
        <el-tabs v-model="activeTab">
          <!-- 工具列表 -->
          <el-tab-pane :label="`工具列表 (${service.toolsList.length})`" name="tools">
            <el-empty v-if="!service.toolsList.length" description="暂无工具，可点击右上角「刷新」重新拉取" :image-size="80" />
            <template v-else>
              <el-table :data="service.toolsList" border>
                <el-table-column prop="name" label="工具名称" min-width="160">
                  <template #default="{ row }">
                    <span class="tool-name">{{ row.name }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="描述" min-width="280" show-overflow-tooltip />
                <el-table-column label="参数" width="80" align="center">
                  <template #default="{ row }">
                    {{ row.params?.length || 0 }}
                  </template>
                </el-table-column>
              </el-table>

              <!-- 工具参数详情（展开） -->
              <div class="tool-params-list">
                <div v-for="tool in service.toolsList" :key="tool.name" class="tool-param-block">
                  <div class="tool-param-title">
                    <el-icon><Tools /></el-icon>
                    <span>{{ tool.name }}</span>
                    <span class="param-count">{{ tool.params?.length || 0 }} 个参数</span>
                  </div>
                  <el-table v-if="tool.params?.length" :data="tool.params" size="small" border>
                    <el-table-column prop="name" label="参数名" min-width="120" />
                    <el-table-column prop="type" label="类型" width="100" />
                    <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
                    <el-table-column label="必填" width="80" align="center">
                      <template #default="{ row }">
                        <span :class="['status-text', row.required ? 'is-on' : 'is-off']">
                          {{ row.required ? '是' : '否' }}
                        </span>
                      </template>
                    </el-table-column>
                  </el-table>
                  <el-empty v-else description="该工具无参数" :image-size="40" />
                </div>
              </div>
            </template>
          </el-tab-pane>

          <!-- 基础信息 -->
          <el-tab-pane label="基础信息" name="info">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="服务名称">{{ service.name }}</el-descriptions-item>
              <el-descriptions-item label="服务状态">
                <span :class="['status-text', service.status === 'online' ? 'is-on' : 'is-off']">
                  {{ getStatusLabel(service.status) }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="MCP 地址" :span="2">{{ service.mcpUrl }}</el-descriptions-item>
              <el-descriptions-item label="鉴权类型">{{ service.authType }}</el-descriptions-item>
              <el-descriptions-item label="鉴权值">{{ service.authValue || '-' }}</el-descriptions-item>
              <el-descriptions-item label="是否启用">
                <span :class="['status-text', service.enabled ? 'is-on' : 'is-off']">
                  {{ service.enabled ? '已启用' : '已禁用' }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="工具数量">{{ service.toolsList.length }}</el-descriptions-item>
              <el-descriptions-item label="创建时间" :span="2">{{ service.createdAt }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <!-- 调用日志 -->
          <el-tab-pane :label="`调用日志 (${service.callLogs.length})`" name="logs">
            <el-empty v-if="!service.callLogs.length" description="暂无调用记录" :image-size="80" />
            <el-table v-else :data="service.callLogs" border>
              <el-table-column prop="caller" label="调用方" min-width="140" />
              <el-table-column prop="tool" label="调用工具" min-width="140" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <span :class="['status-text', row.status === 'success' ? 'is-on' : 'is-error']">
                    {{ row.status === 'success' ? '成功' : '失败' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="duration" label="耗时" width="100" align="center">
                <template #default="{ row }">
                  {{ row.duration }} ms
                </template>
              </el-table-column>
              <el-table-column prop="timestamp" label="时间" min-width="180" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.header-actions {
  margin-left: auto;
  display: flex;
  gap: $spacing-sm;
}

// 元信息卡
.detail-meta-card {
  display: flex;
  gap: $spacing-lg;
  padding: $spacing-lg $spacing-xl;
  background: $bg-white;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  margin-bottom: $spacing-base;
}

.meta-icon {
  width: 56px;
  height: 56px;
  border-radius: $radius-base;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: $bg-hover;
}

.meta-main {
  flex: 1;
  min-width: 0;
}

.meta-title {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-xs;

  .title-name {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  .title-meta {
    font-size: 12px;
    color: $text-secondary;
  }

  .status-text {
    font-size: 12px;

    &.is-on {
      color: $color-success;
    }

    &.is-off {
      color: $text-placeholder;
    }
  }
}

.meta-url {
  margin: 0 0 $spacing-xs;
  font-size: 13px;
  color: $text-secondary;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-info {
  display: flex;
  gap: $spacing-lg;
  font-size: 12px;
  color: $text-secondary;
  flex-wrap: wrap;

  span {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.meta-switch {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  font-size: 12px;
  color: $text-secondary;
  flex-shrink: 0;
}

// Tab 卡
.detail-tabs-card {
  background: $bg-white;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  padding: $spacing-lg $spacing-xl;
}

.tool-name {
  font-family: 'Consolas', 'Monaco', monospace;
  font-weight: 500;
  color: $text-primary;
}

.tool-params-list {
  margin-top: $spacing-lg;
}

.tool-param-block {
  margin-bottom: $spacing-lg;

  &:last-child {
    margin-bottom: 0;
  }

  .tool-param-title {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-sm;
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;

    .param-count {
      font-size: 12px;
      font-weight: 400;
      color: $text-secondary;
    }
  }
}

// 通用状态文本
.status-text {
  font-size: 12px;

  &.is-on {
    color: $color-success;
  }

  &.is-off {
    color: $text-placeholder;
  }

  &.is-error {
    color: $color-danger;
  }
}
</style>
