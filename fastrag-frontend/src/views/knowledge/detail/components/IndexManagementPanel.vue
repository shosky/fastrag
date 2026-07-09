<script setup lang="ts">
import { Refresh, Close, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { buildGraphIndex, retryGraphBuild } from '@/api'
import type { GraphBuildStatus } from '@/types/evaluation'

// --- Props & Emits ---
const props = defineProps<{
  visible: boolean
  kbId?: string
  buildStatus: GraphBuildStatus
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'open-settings'): void
  (e: 'refresh'): void
}>()

// --- Popup visibility ---
const popupVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// --- Derived state ---
const isBuilding = computed(() => props.buildStatus.status === 'building')
const isCompleted = computed(() => props.buildStatus.status === 'completed')
const isFailed = computed(() => props.buildStatus.status === 'failed')
const isIdle = computed(() => props.buildStatus.status === 'idle')
const hasPending = computed(() => {
  const t = props.buildStatus.totalChunks || 0
  const b = props.buildStatus.builtChunks || 0
  return t > b
})

// --- Status tag ---
const statusTag = computed(() => {
  if (isBuilding.value) return { type: 'warning' as const, text: '构建中' }
  if (isCompleted.value) return { type: 'success' as const, text: '已完成' }
  if (isFailed.value) return { type: 'danger' as const, text: '构建失败' }
  return { type: 'info' as const, text: '待构建' }
})

// --- Actions ---
const actionLoading = ref(false)

async function handleStartIndex() {
  if (!props.kbId) return
  actionLoading.value = true
  try {
    await buildGraphIndex(props.kbId)
    ElMessage.success('开始索引')
    emit('refresh')
  } catch (e: any) {
    ElMessage.error('启动索引失败: ' + (e.message || e))
  } finally {
    actionLoading.value = false
  }
}

async function handleRetry() {
  if (!props.kbId) return
  actionLoading.value = true
  try {
    await retryGraphBuild(props.kbId)
    ElMessage.success('已重试索引')
    emit('refresh')
  } catch (e: any) {
    ElMessage.error('重试失败: ' + (e.message || e))
  } finally {
    actionLoading.value = false
  }
}

function handleRefresh() {
  emit('refresh')
  ElMessage.success('状态已刷新')
}

function handleReset() {
  ElMessage.warning('请先清空图谱数据后再重新索引')
  // 目前重置通过 retry 完成，retry 会先 clearGraph 再 full 模式构建
  handleRetry()
}
</script>

<template>
  <Transition name="popup-slide">
    <div v-if="popupVisible" class="index-popup">
      <!-- Header -->
      <div class="index-popup__header">
        <div class="index-popup__title-row">
          <h3 class="index-popup__title">索引管理</h3>
          <el-button :icon="Refresh" link @click="handleRefresh" />
        </div>
      </div>

      <!-- Body -->
      <div class="index-popup__body">
        <!-- Status -->
        <div class="index-popup__status">
          <span class="index-popup__status-label">状态</span>
          <el-tag :type="statusTag.type" size="small">{{ statusTag.text }}</el-tag>
        </div>

        <!-- Progress bar (仅构建中) -->
        <div v-if="isBuilding" class="index-popup__progress">
          <el-progress
            :percentage="buildStatus.progress"
            :stroke-color="{ '0%': '#409EFF', '100%': '#67C23A' }"
            :format="() => buildStatus.progress + '%'"
          />
        </div>

        <!-- Chunk stats -->
        <div class="index-popup__chunks">
          <div class="index-popup__chunk-item">
            <span class="index-popup__chunk-value">{{ buildStatus.totalChunks }}</span>
            <span class="index-popup__chunk-label">总 Chunk</span>
          </div>
          <div class="index-popup__chunk-item">
            <span class="index-popup__chunk-value index-popup__chunk-value--warning">
              {{ Math.max(0, (buildStatus.totalChunks || 0) - (buildStatus.builtChunks || 0)) }}
            </span>
            <span class="index-popup__chunk-label">待构建</span>
          </div>
          <div class="index-popup__chunk-item">
            <span class="index-popup__chunk-value index-popup__chunk-value--success">
              {{ buildStatus.builtChunks }}
            </span>
            <span class="index-popup__chunk-label">已构建</span>
          </div>
          <div v-if="buildStatus.failedChunks > 0" class="index-popup__chunk-item">
            <span class="index-popup__chunk-value index-popup__chunk-value--danger">
              {{ buildStatus.failedChunks }}
            </span>
            <span class="index-popup__chunk-label">失败</span>
          </div>
        </div>

        <!-- Entity & Relation stats -->
        <div class="index-popup__entities">
          <div class="index-popup__entity-item">
            <span class="index-popup__entity-value">{{ buildStatus.entityCount }}</span>
            <span class="index-popup__entity-label">实体</span>
          </div>
          <div class="index-popup__entity-item">
            <span class="index-popup__entity-value">{{ buildStatus.relationCount }}</span>
            <span class="index-popup__entity-label">关系</span>
          </div>
        </div>

        <!-- Build error (仅失败时) -->
        <div v-if="isFailed && buildStatus.buildError" class="index-popup__error">
          <el-alert
            :title="buildStatus.buildError"
            type="error"
            :closable="false"
            show-icon
          />
        </div>

        <!-- Action buttons -->
        <el-button
          v-if="isBuilding"
          type="primary"
          class="index-popup__action-btn"
          :icon="Loading"
          disabled
        >
          构建中 {{ buildStatus.progress }}%
        </el-button>

        <el-button
          v-else-if="isFailed"
          type="primary"
          class="index-popup__action-btn"
          :loading="actionLoading"
          @click="handleRetry"
        >
          重试索引
        </el-button>

        <el-button
          v-else
          type="primary"
          class="index-popup__action-btn"
          :loading="actionLoading"
          :disabled="!hasPending"
          @click="handleStartIndex"
        >
          {{ hasPending ? '开始索引' : '已全部构建' }}
        </el-button>

        <!-- Config actions -->
        <div class="index-popup__actions">
          <el-button
            v-if="!isBuilding"
            link type="primary"
            @click="emit('open-settings')"
          >
            修改配置
          </el-button>
          <el-button
            v-if="!isBuilding"
            link type="danger"
            @click="handleReset"
          >
            重置
          </el-button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.index-popup {
  position: absolute;
  top: 60px;
  right: $spacing-base;
  z-index: 300;
  width: 300px;
  background: $bg-white;
  border-radius: $radius-base;
  box-shadow: $shadow-lg;
  overflow: hidden;

  &__header {
    padding: $spacing-base $spacing-lg;
    border-bottom: 1px solid $border-lighter;
  }

  &__title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  &__body {
    padding: $spacing-base $spacing-lg;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__status {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__status-label {
    font-size: 14px;
    color: $text-regular;
  }

  &__progress {
    margin: -4px 0;
  }

  &__chunks {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: $spacing-sm;
    padding: $spacing-base;
    background: $bg-hover;
    border-radius: $radius-base;
  }

  &__chunk-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
  }

  &__chunk-value {
    font-size: 20px;
    font-weight: 600;
    color: $text-primary;

    &--warning { color: $color-warning; }
    &--success { color: $color-success; }
    &--danger { color: $color-danger; }
  }

  &__chunk-label {
    font-size: 12px;
    color: $text-secondary;
  }

  &__entities {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: $spacing-sm;
    padding: $spacing-base;
    background: $bg-hover;
    border-radius: $radius-base;
  }

  &__entity-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
  }

  &__entity-value {
    font-size: 20px;
    font-weight: 600;
    color: $text-primary;
  }

  &__entity-label {
    font-size: 12px;
    color: $text-secondary;
  }

  &__error {
    margin: -4px 0;
  }

  &__action-btn {
    width: 100%;
    height: 40px;
    font-size: 15px;
  }

  &__actions {
    display: flex;
    justify-content: space-between;
    padding-top: $spacing-sm;
    border-top: 1px solid $border-lighter;
  }
}

.popup-slide-enter-active,
.popup-slide-leave-active {
  transition: all 0.2s ease;
}

.popup-slide-enter-from,
.popup-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
