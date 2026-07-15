<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh, Edit, Delete, CaretRight } from '@element-plus/icons-vue'
import { getStatusLabel } from '@/mock/mcp'
import type { McpService } from '@/mock/mcp'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()

const service = ref<McpService | null>(null)
const loading = ref(false)
const refreshing = ref(false)
const activeTab = ref('tools')

const id = route.params.id as string

/** 是否为内置服务（内置服务不允许删除） */
const isBuiltin = computed(() => (service.value as any)?.isBuiltin === 1)

// ========== 工具测试对话框 ==========
const testDialog = reactive({
  visible: false,
  loading: false,
  tool: null as any,
  form: {} as Record<string, any>,
  result: null as string | null,
  resultSuccess: false,
  duration: 0,
})

function openTestDialog(tool: any) {
  testDialog.tool = tool
  testDialog.form = {}
  testDialog.result = null
  testDialog.resultSuccess = false
  testDialog.duration = 0
  // 初始化表单字段
  if (tool.params?.length) {
    tool.params.forEach((p: any) => {
      testDialog.form[p.name] = ''
    })
  }
  testDialog.visible = true
}

async function handleTestTool() {
  if (!testDialog.tool) return
  testDialog.loading = true
  testDialog.result = null
  try {
    const toolId = testDialog.tool.id || testDialog.tool.toolId
    const res: any = await api.testMcpTool(toolId, testDialog.form)
    const data = res?.data || res
    testDialog.resultSuccess = data?.success !== false
    testDialog.result = data?.output || JSON.stringify(data, null, 2)
    testDialog.duration = data?.durationMs || 0
  } catch (e: any) {
    testDialog.resultSuccess = false
    testDialog.result = e?.message || '调用失败'
    testDialog.duration = 0
  } finally {
    testDialog.loading = false
  }
}

async function loadService() {
  loading.value = true
  try {
    const data = (await api.getMcpServiceDetail(id)) as any
    if (!data) {
      ElMessage.error('MCP 服务不存在')
      router.push('/application/mcp-management')
      return
    }
    service.value = normalizeService(data)
  } finally {
    loading.value = false
  }
}

/** 将后端返回的 Integer 字段转为前端需要的 boolean 等类型 */
function normalizeService(s: any): any {
  const toolsList = (s.toolsList || []).map((t: any) => ({
    ...t,
    params: normalizeToolParams(t.params),
  }))
  return {
    ...s,
    enabled: s.enabled === 1 || s.enabled === true,
    toolsList,
    callLogs: s.callLogs || [],
  }
}

/** 将 JSON Schema Map 格式的工具参数转为前端数组格式 */
function normalizeToolParams(params: any): any[] {
  if (!params) return []
  if (Array.isArray(params)) return params.map((p: any) => ({ ...p }))
  if (typeof params === 'object' && params.properties) {
    return Object.entries(params.properties).map(([name, prop]: [string, any]) => ({
      name,
      type: prop.type || 'string',
      description: prop.description || '',
      required: (params.required || []).includes(name),
    }))
  }
  return []
}

/** 刷新服务：重新拉取工具列表 + 检测连通性 */
async function handleRefresh() {
  if (!service.value) return
  refreshing.value = true
  try {
    // 先调后端 refresh 端点：连接 MCP 服务器、发现工具、更新状态
    await api.refreshMcpService(id)
    // 再重新加载服务详情
    const refreshed = (await api.getMcpServiceDetail(id)) as any
    if (refreshed) {
      service.value = normalizeService(refreshed)
      ElMessage.success(`已刷新，当前状态：${getStatusLabel(refreshed.status)}，工具 ${(refreshed.toolsList || []).length} 个`)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

/** 切换启用状态 */
async function handleToggleEnabled() {
  if (!service.value) return
  const wasEnabled = service.value.enabled
  await api.toggleMcpService(id)
  service.value = (await api.getMcpServiceDetail(id)) as any
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
    await api.deleteMcpService(id)
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
        <el-button v-if="!isBuiltin" type="danger" plain @click="handleDelete">
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
            <span class="title-meta">{{ (service.toolsList || []).length }} 个工具</span>
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
          <el-tab-pane :label="`工具列表 (${(service.toolsList || []).length})`" name="tools">
            <el-empty v-if="!(service.toolsList || []).length" description="暂无工具，可点击右上角「刷新」重新拉取" :image-size="80" />
            <template v-else>
              <el-table :data="service.toolsList || []" border>
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
                <el-table-column label="操作" width="100" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button type="primary" link size="small" @click="openTestDialog(row)">
                      <el-icon><CaretRight /></el-icon>测试
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>

              <!-- 工具参数详情（展开） -->
              <div class="tool-params-list">
                <div v-for="tool in (service.toolsList || [])" :key="tool.name" class="tool-param-block">
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

            <!-- 工具测试对话框 -->
            <el-dialog
              v-model="testDialog.visible"
              :title="`测试工具：${testDialog.tool?.name || ''}`"
              width="700px"
              destroy-on-close
            >
              <div v-if="testDialog.tool" class="test-dialog-body">
                <p class="test-dialog-desc">{{ testDialog.tool.description }}</p>

                <el-form :model="testDialog.form" label-width="120px" v-if="testDialog.tool.params?.length">
                  <el-form-item
                    v-for="param in testDialog.tool.params"
                    :key="param.name"
                    :label="param.name"
                    :required="param.required"
                  >
                    <el-input
                      v-model="testDialog.form[param.name]"
                      :placeholder="param.description || `请输入${param.name}`"
                      :type="param.type === 'number' ? 'number' : 'text'"
                    />
                  </el-form-item>
                </el-form>
                <el-empty v-else description="该工具无参数" :image-size="40" />

                <div class="test-result" v-if="testDialog.result !== null">
                  <div class="test-result-header">
                    <span :class="['test-status', testDialog.resultSuccess ? 'success' : 'error']">
                      {{ testDialog.resultSuccess ? '✅ 调用成功' : '❌ 调用失败' }}
                    </span>
                    <span class="test-duration">{{ testDialog.duration }}ms</span>
                  </div>
                  <el-input
                    type="textarea"
                    :rows="8"
                    :model-value="testDialog.result"
                    readonly
                    class="test-result-output"
                  />
                </div>
              </div>

              <template #footer>
                <el-button @click="testDialog.visible = false">关闭</el-button>
                <el-button
                  type="primary"
                  :loading="testDialog.loading"
                  :disabled="testDialog.loading"
                  @click="handleTestTool"
                >
                  {{ testDialog.loading ? '调用中...' : '调用' }}
                </el-button>
              </template>
            </el-dialog>
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
              <el-descriptions-item label="工具数量">{{ (service.toolsList || []).length }}</el-descriptions-item>
              <el-descriptions-item label="创建时间" :span="2">{{ service.createdAt }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <!-- 调用日志 -->
          <el-tab-pane :label="`调用日志 (${(service.callLogs || []).length})`" name="logs">
            <el-empty v-if="!(service.callLogs || []).length" description="暂无调用记录" :image-size="80" />
            <el-table v-else :data="service.callLogs || []" border>
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
