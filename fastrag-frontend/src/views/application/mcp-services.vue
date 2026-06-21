<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMcpServices,
  deleteMcpService,
  toggleMcpEnabled,
  getStatusLabel,
} from '@/mock/mcp'
import type { McpService, McpStatus } from '@/mock/mcp'

const router = useRouter()

// --- 列表数据 ---
const services = ref<McpService[]>(getMcpServices())

const searchKeyword = ref('')
const selectedStatus = ref<McpStatus | ''>('')

const filteredServices = computed(() => {
  let list = services.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(
      (s) =>
        s.name.toLowerCase().includes(kw) ||
        s.mcpUrl.toLowerCase().includes(kw) ||
        s.toolsList.some((t) => t.name.toLowerCase().includes(kw)),
    )
  }
  if (selectedStatus.value) {
    list = list.filter((s) => s.status === selectedStatus.value)
  }
  return list
})

// --- 路由跳转 ---
function openCreate() {
  router.push('/application/mcp-management/create')
}

function openDetail(service: McpService) {
  router.push(`/application/mcp-management/${service.id}`)
}

function openEdit(service: McpService) {
  router.push(`/application/mcp-management/${service.id}/edit`)
}

async function handleDelete(service: McpService) {
  try {
    await ElMessageBox.confirm(`确定删除 MCP 服务「${service.name}」吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    deleteMcpService(service.id)
    services.value = getMcpServices()
    ElMessage.success('删除成功')
  } catch {}
}

function handleToggleEnabled(service: McpService) {
  toggleMcpEnabled(service.id)
  services.value = getMcpServices()
  ElMessage.success(service.enabled ? '已禁用' : '已启用')
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-title">
        <h2>MCP 管理</h2>
        <p>管理 MCP 服务，扩展智能体的工具调用能力</p>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索服务名称 / 地址 / 工具"
          clearable
          style="width: 320px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="selectedStatus" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="在线" value="online" />
          <el-option label="离线" value="offline" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>添加 MCP 服务
        </el-button>
      </div>
    </div>

    <!-- MCP 服务列表 -->
    <div class="mcp-grid" v-if="filteredServices.length">
      <div v-for="service in filteredServices" :key="service.id" class="mcp-card">
        <div class="mcp-card-body" @click="openDetail(service)">
          <div class="mcp-title-row">
            <div class="mcp-icon">
              <el-icon :size="16" color="#909399"><Connection /></el-icon>
            </div>
            <h4 :title="service.name">{{ service.name }}</h4>
            <span :class="['status-text', service.status === 'online' ? 'is-on' : 'is-off']">
              {{ getStatusLabel(service.status) }}
            </span>
          </div>
          <p class="mcp-url-text" :title="service.mcpUrl">{{ service.mcpUrl }}</p>
          <div class="mcp-tags">
            <span class="tag-text">{{ service.authType }}</span>
            <span class="tag-text">{{ service.toolsList.length }} 个工具</span>
          </div>
        </div>
        <div class="mcp-card-footer">
          <div class="usage">
            <el-icon><Clock /></el-icon>
            <span>{{ service.lastUsed }}</span>
          </div>
          <div class="actions">
            <el-switch :model-value="service.enabled" size="small" @change="handleToggleEnabled(service)" />
            <el-button link type="primary" size="small" @click="openEdit(service)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openDetail(service)">详情</el-button>
            <el-dropdown trigger="click">
              <el-icon class="more-icon"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openDetail(service)">查看详情</el-dropdown-item>
                  <el-dropdown-item divided @click="handleDelete(service)">
                    <span style="color: var(--el-color-danger)">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无 MCP 服务，点击右上角添加" />
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-header {
  margin-bottom: $spacing-lg;

  h2 {
    margin: 0 0 $spacing-xs;
    font-size: 20px;
    color: $text-primary;
  }

  p {
    margin: 0;
    font-size: 14px;
    color: $text-secondary;
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
}

.toolbar-left {
  display: flex;
  gap: $spacing-sm;
}

.toolbar-right {
  display: flex;
  gap: $spacing-sm;
}

.mcp-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: $spacing-base;
}

.mcp-card {
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  overflow: hidden;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;

  &:hover {
    box-shadow: $shadow-base;
    border-color: $border-base;
    transform: translateY(-2px);
  }
}

.mcp-card-body {
  padding: $spacing-base;
  flex: 1;
  cursor: pointer;
}

.mcp-title-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-xs;

  .mcp-icon {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  h4 {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    min-width: 0;
  }

  .status-text {
    flex-shrink: 0;
    font-size: 12px;

    &.is-on {
      color: $color-success;
    }

    &.is-off {
      color: $text-placeholder;
    }
  }
}

.mcp-url-text {
  margin: 0 0 $spacing-sm;
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.5;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-height: 18px;
}

.mcp-tags {
  display: flex;
  gap: $spacing-xs;
  flex-wrap: wrap;

  .tag-text {
    font-size: 12px;
    color: $text-secondary;
    background: $bg-hover;
    border-radius: $radius-sm;
    padding: 2px 8px;
  }
}

.mcp-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-sm $spacing-base;
  border-top: 1px solid $border-lighter;

  .usage {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: $text-secondary;
  }

  .actions {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
  }

  .more-icon {
    cursor: pointer;
    color: $text-secondary;
    padding: 2px;

    &:hover {
      color: $color-primary;
    }
  }
}
</style>
