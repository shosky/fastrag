<script setup lang="ts">
/**
 * 高频词云（运营模块共享）
 *
 * 按 count 计算字号（12px ~ 40px），样式与 feedback 页原实现一致。
 */
interface WordItem {
  word: string
  count: number
}

defineProps<{ words: WordItem[] }>()
</script>

<template>
  <div class="word-cloud">
    <span
      v-for="q in words"
      :key="q.word"
      class="word"
      :style="{ fontSize: 12 + Math.min(q.count / 5, 28) + 'px' }"
    >
      {{ q.word }}
    </span>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.word-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  align-items: center;
  justify-content: center;
  padding: $spacing-lg;
  .word { color: $color-primary; cursor: pointer; &:hover { color: $color-success; } }
}
</style>
