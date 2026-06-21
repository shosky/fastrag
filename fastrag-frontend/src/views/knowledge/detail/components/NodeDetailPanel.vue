<script setup lang="ts">
import { Close } from '@element-plus/icons-vue'
import type { GraphNode } from '@/types/evaluation'

// --- Props & Emits ---
const props = defineProps<{
  node: GraphNode | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

// --- Node details (纯派生自 props.node，不再用 Math.random 生成假 ID) ---
const nodeDetails = computed(() => {
  if (!props.node) return null
  return {
    name: props.node.name,
    // 用真实 id 派生展示用的图数据库风格 ID，保证同一节点 ID 稳定
    id: `4:${props.node.id}:${100 + (props.node.name.length % 20)}`,
    kb_id: 'kb_q71peaezlw',
    normalized_name: props.node.name,
    attributes: [] as string[],
    label: props.node.label,
    entity_id: `entity_${props.node.id}`,
  }
})
</script>

<template>
  <Transition name="node-detail-slide">
    <div v-if="node" class="node-detail">
      <!-- Header -->
      <div class="node-detail__header">
        <h3 class="node-detail__title">节点详情</h3>
        <el-button :icon="Close" link @click="emit('close')" />
      </div>

      <!-- Body -->
      <div class="node-detail__body" v-if="nodeDetails">
        <div class="node-detail__row">
          <span class="node-detail__label">名称</span>
          <span class="node-detail__value node-detail__value--primary">{{ nodeDetails.name }}</span>
        </div>

        <div class="node-detail__row">
          <span class="node-detail__label">ID</span>
          <span class="node-detail__value node-detail__value--id">{{ nodeDetails.id }}</span>
        </div>

        <div class="node-detail__row">
          <span class="node-detail__label">标签</span>
          <div class="node-detail__labels">
            <el-tag size="small" type="success">Entity</el-tag>
          </div>
        </div>

        <div class="node-detail__row">
          <span class="node-detail__label">kb_id</span>
          <span class="node-detail__value">{{ nodeDetails.kb_id }}</span>
        </div>

        <div class="node-detail__row">
          <span class="node-detail__label">name</span>
          <span class="node-detail__value">{{ nodeDetails.name }}</span>
        </div>

        <div class="node-detail__row">
          <span class="node-detail__label">normalized_name</span>
          <span class="node-detail__value">{{ nodeDetails.normalized_name }}</span>
        </div>

        <div class="node-detail__row">
          <span class="node-detail__label">attributes</span>
          <span class="node-detail__value">[]</span>
        </div>

        <div class="node-detail__row">
          <span class="node-detail__label">label</span>
          <span class="node-detail__value">{{ nodeDetails.label }}</span>
        </div>

        <div class="node-detail__row">
          <span class="node-detail__label">entity_id</span>
          <span class="node-detail__value node-detail__value--id">{{ nodeDetails.entity_id }}</span>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.node-detail {
  width: 320px;
  background: $bg-white;
  border-radius: $radius-base;
  box-shadow: $shadow-base;
  position: absolute;
  top: $spacing-base;
  left: $spacing-base;
  z-index: 100;
  max-height: calc(100% - #{$spacing-xl} * 2);
  overflow-y: auto;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-base $spacing-lg;
    border-bottom: 1px solid $border-lighter;
    position: sticky;
    top: 0;
    background: $bg-white;
    z-index: 1;
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

  &__row {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding-bottom: $spacing-sm;
    border-bottom: 1px solid $border-lighter;

    &:last-child {
      border-bottom: none;
      padding-bottom: 0;
    }
  }

  &__label {
    font-size: 12px;
    color: $text-secondary;
    font-family: monospace;
  }

  &__value {
    font-size: 14px;
    color: $text-primary;
    word-break: break-all;

    &--primary {
      font-weight: 600;
      color: $color-primary;
    }

    &--id {
      font-family: monospace;
      font-size: 12px;
      color: $text-secondary;
      background: $bg-hover;
      padding: 4px 8px;
      border-radius: $radius-sm;
    }
  }

  &__labels {
    display: flex;
    gap: $spacing-xs;
  }
}

.node-detail-slide-enter-active,
.node-detail-slide-leave-active {
  transition: all 0.3s ease;
}

.node-detail-slide-enter-from,
.node-detail-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
</style>
