<script setup lang="ts">
import { Refresh, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useKnowledgeGraph } from '@/composables/useKnowledgeGraph'

// --- Props & Emits ---
const props = defineProps<{
  visible: boolean
  kbId?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'open-settings'): void
}>()

// 复用父级图谱 composable 的数据来源，保证统计数字一致
const { entityCount, relationCount, chunkCount, load } = useKnowledgeGraph(props.kbId || 'default')

// --- Popup visibility ---
const popupVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

// --- Index stats (派生自真实数据) ---
const indexStats = computed(() => ({
  totalChunks: chunkCount.value,
  pendingBuild: 0,
  built: chunkCount.value,
  entities: entityCount.value,
  relations: relationCount.value,
}))

// --- Building state ---
const isBuilding = ref(false)

async function handleStartIndex() {
  isBuilding.value = true
  ElMessage.info('开始索引...')
  // 模拟索引：重新拉取数据
  await new Promise((r) => setTimeout(r, 1500))
  await load()
  isBuilding.value = false
  ElMessage.success('索引完成')
}

function handleReset() {
  ElMessage.warning('配置已重置')
}

function handleRefresh() {
  load()
  ElMessage.success('状态已刷新')
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
          <el-tag type="success" size="small">已就绪</el-tag>
        </div>

        <!-- Chunk stats -->
        <div class="index-popup__chunks">
          <div class="index-popup__chunk-item">
            <span class="index-popup__chunk-value">{{ indexStats.totalChunks }}</span>
            <span class="index-popup__chunk-label">总 Chunk</span>
          </div>
          <div class="index-popup__chunk-item">
            <span class="index-popup__chunk-value index-popup__chunk-value--warning">
              {{ indexStats.pendingBuild }}
            </span>
            <span class="index-popup__chunk-label">待构建</span>
          </div>
          <div class="index-popup__chunk-item">
            <span class="index-popup__chunk-value index-popup__chunk-value--success">
              {{ indexStats.built }}
            </span>
            <span class="index-popup__chunk-label">已构建</span>
          </div>
        </div>

        <!-- Entity & Relation stats -->
        <div class="index-popup__entities">
          <div class="index-popup__entity-item">
            <span class="index-popup__entity-value">{{ indexStats.entities }}</span>
            <span class="index-popup__entity-label">实体</span>
          </div>
          <div class="index-popup__entity-item">
            <span class="index-popup__entity-value">{{ indexStats.relations }}</span>
            <span class="index-popup__entity-label">关系</span>
          </div>
        </div>

        <!-- Start index button -->
        <el-button
          type="primary"
          class="index-popup__start-btn"
          :loading="isBuilding"
          @click="handleStartIndex"
        >
          开始索引
        </el-button>

        <!-- Config actions -->
        <div class="index-popup__actions">
          <el-button link type="primary" @click="emit('open-settings')">
            修改配置
          </el-button>
          <el-button link type="danger" @click="handleReset">
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
  width: 280px;
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

    &--warning {
      color: $color-warning;
    }

    &--success {
      color: $color-success;
    }
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

  &__start-btn {
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
