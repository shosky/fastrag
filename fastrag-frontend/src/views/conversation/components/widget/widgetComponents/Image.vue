<script setup lang="ts">
import { computed, ref } from 'vue'
import { propString, type WidgetComponentProps } from '../registry'

// Image：图片（src 仅 http(s) 协议白名单；javascript:/data: 等渲染占位；
// 加载失败 → 占位单次切换，无重试风暴）
const props = defineProps<WidgetComponentProps>()

const src = propString(props.node, 'src')
const alt = propString(props.node, 'alt', '')
const width = propString(props.node, 'width', '100%')

const safe = computed(() => /^https?:\/\//i.test(src))
const failed = ref(false)
</script>

<template>
  <img
    v-if="safe && !failed"
    class="widget-w-image"
    :src="src"
    :alt="alt"
    :style="{ width, maxWidth: '100%' }"
    @error="failed = true"
  />
  <div v-else class="widget-w-image__placeholder">图片不可用</div>
</template>

<style scoped>
.widget-w-image {
  display: block;
  border-radius: var(--el-border-radius-base, 4px);
  object-fit: cover;
}
.widget-w-image__placeholder {
  border: 1px dashed var(--el-border-color-darker, #dcdfe6);
  border-radius: var(--el-border-radius-base, 4px);
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  padding: 16px 10px;
  text-align: center;
}
</style>
