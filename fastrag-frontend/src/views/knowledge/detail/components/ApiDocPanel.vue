<script setup lang="ts">
import { Search, Connection, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { allApiGroups, searchEndpoints } from '@/data/api-docs'
import type { ApiGroup } from '@/data/api-docs/types'
import { useAuth } from '@/composables/useAuth'
import ApiEndpointCard from './ApiEndpointCard.vue'
import ApiTokenManager from './ApiTokenManager.vue'

const props = defineProps<{
  kbId: string
}>()

// API Token 管理仅 kb:manage 权限（kb_admin 及以上）可见；无权限不挂载组件（避免无效请求）
const { hasPermission } = useAuth()
const canManageToken = computed(() => hasPermission('kb:manage'))

// --- State ---
const activeGroup = ref<string>('auth')
const searchKeyword = ref('')
const currentToken = ref('')

// --- Base URL (auto detect from current origin) ---
const baseUrl = computed(() => {
  // In production, use current origin + /api
  // For display: show the full URL
  if (typeof window !== 'undefined') {
    return `${window.location.origin}/api`
  }
  return '/api'
})

// --- Filtered groups based on search ---
const displayGroups = computed<ApiGroup[]>(() => {
  if (!searchKeyword.value.trim()) return allApiGroups
  return searchEndpoints(searchKeyword.value.trim())
})

const currentGroup = computed(() => {
  return displayGroups.value.find(g => g.key === activeGroup.value) || displayGroups.value[0]
})

// --- When search changes, reset group to first match ---
watch(searchKeyword, (val) => {
  if (val.trim() && displayGroups.value.length > 0) {
    activeGroup.value = displayGroups.value[0].key
  } else if (!val.trim() && allApiGroups.length > 0) {
    activeGroup.value = allApiGroups[0].key
  }
})

// --- Copy base URL ---
async function copyBaseUrl() {
  try {
    await navigator.clipboard.writeText(baseUrl.value)
    ElMessage.success('Base URL 已复制')
  } catch {
    ElMessage.info(`Base URL: ${baseUrl.value}`)
  }
}

// --- Endpoints count ---
function endpointCount(): number {
  return allApiGroups.reduce((sum, g) => sum + g.endpoints.length, 0)
}

// --- Icon map ---
const iconMap: Record<string, any> = {
  Lock: Connection,
  Notebook: Connection,
  Document: Connection,
  Files: Connection,
  Search: Connection,
}
</script>

<template>
  <div class="api-doc-panel">
    <!-- Top section: Base config + search -->
    <div class="api-doc-panel__top">
      <div class="api-doc-panel__config">
        <!-- Base URL -->
        <div class="api-doc-panel__config-item">
          <span class="api-doc-panel__config-label">Base URL</span>
          <div class="api-doc-panel__config-value">
            <code>{{ baseUrl }}</code>
            <el-button :icon="CopyDocument" size="small" link @click="copyBaseUrl" />
          </div>
        </div>
        <!-- Stats -->
        <div class="api-doc-panel__config-item">
          <span class="api-doc-panel__config-label">接口总数</span>
          <span class="api-doc-panel__config-value">{{ endpointCount() }} 个接口，{{ allApiGroups.length }} 个分组</span>
        </div>
      </div>

      <!-- Search -->
      <div class="api-doc-panel__search">
        <el-input
          v-model="searchKeyword"
          :prefix-icon="Search"
          placeholder="搜索接口名称、路径或描述..."
          clearable
          size="default"
        />
      </div>
    </div>

    <!-- Token Manager（仅 kb:manage 权限可见） -->
    <div v-if="canManageToken" class="api-doc-panel__section">
      <ApiTokenManager @token-update="currentToken = $event" />
    </div>

    <!-- Main content: Group nav + endpoint list -->
    <div class="api-doc-panel__main">
      <!-- Left: Group navigation (vertical) -->
      <div class="api-doc-panel__nav">
        <div
          v-for="group in displayGroups"
          :key="group.key"
          class="api-doc-panel__nav-item"
          :class="{ 'api-doc-panel__nav-item--active': activeGroup === group.key }"
          @click="activeGroup = group.key"
        >
          <el-icon :size="16">
            <component :is="iconMap[group.icon] || Connection" />
          </el-icon>
          <span class="api-doc-panel__nav-text">{{ group.name }}</span>
          <el-badge
            :value="group.endpoints.length"
            :max="99"
            type="info"
            class="api-doc-panel__nav-badge"
          />
        </div>
      </div>

      <!-- Right: Endpoint list -->
      <div class="api-doc-panel__content">
        <div v-if="currentGroup" class="api-doc-panel__endpoints">
          <div class="api-doc-panel__group-header">
            <h3 class="api-doc-panel__group-title">{{ currentGroup.name }}</h3>
            <span class="api-doc-panel__group-count">{{ currentGroup.endpoints.length }} 个接口</span>
          </div>

          <div class="api-doc-panel__endpoint-list">
            <ApiEndpointCard
              v-for="ep in currentGroup.endpoints"
              :key="`${ep.method}-${ep.path}`"
              :endpoint="ep"
              :base-url="baseUrl"
              :kb-id="kbId"
              :token="currentToken || undefined"
            />
          </div>
        </div>

        <el-empty v-else description="没有找到匹配的接口" />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.api-doc-panel {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;

  // --- Top bar ---
  &__top {
    display: flex;
    align-items: flex-end;
    gap: $spacing-lg;
    flex-wrap: wrap;
  }

  &__config {
    display: flex;
    align-items: center;
    gap: $spacing-xl;
    flex: 1;
    min-width: 0;
  }

  &__config-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__config-label {
    font-size: 12px;
    color: $text-secondary;
    font-weight: 500;
  }

  &__config-value {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    font-size: 14px;
    color: $text-primary;

    code {
      padding: 4px 10px;
      background: #F1F5F9;
      border: 1px solid $border-base;
      border-radius: $radius-sm;
      font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
      font-size: 13px;
    }
  }

  &__search {
    width: 320px;
    flex-shrink: 0;
  }

  // --- Section ---
  &__section {
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  // --- Main content area ---
  &__main {
    display: flex;
    gap: $spacing-base;
    min-height: 400px;
  }

  // --- Left navigation ---
  &__nav {
    width: 180px;
    flex-shrink: 0;
    background: $bg-white;
    border: 1px solid $border-base;
    border-radius: $radius-base;
    padding: $spacing-sm;
    display: flex;
    flex-direction: column;
    gap: 2px;
    align-self: flex-start;
    position: sticky;
    top: $spacing-lg;
  }

  &__nav-item {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    border-radius: $radius-sm;
    cursor: pointer;
    transition: all 0.15s;
    font-size: 14px;
    color: $text-regular;
    position: relative;

    &:hover {
      background: $bg-hover;
      color: $text-primary;
    }

    &--active {
      background: $color-primary-light;
      color: $color-primary;
      font-weight: 600;
    }
  }

  &__nav-text {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__nav-badge {
    flex-shrink: 0;
  }

  // --- Right content ---
  &__content {
    flex: 1;
    min-width: 0;
  }

  &__group-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-base;
  }

  &__group-title {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
  }

  &__group-count {
    font-size: 13px;
    color: $text-secondary;
  }

  &__endpoint-list {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }
}
</style>
