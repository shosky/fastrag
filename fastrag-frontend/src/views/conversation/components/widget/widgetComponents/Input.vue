<script setup lang="ts">
import { computed } from 'vue'
import { propString, type WidgetComponentProps } from '../registry'

// Input：单行文本（真实 el-input；props.name 归集进卡片表单收集值，defaultValue 首值）
const props = defineProps<WidgetComponentProps>()

const name = propString(props.node, 'name')
const label = propString(props.node, 'label')
const placeholder = propString(props.node, 'placeholder', '请输入')

const value = computed({
  get: () => props.ctx.getFormValue(name, propString(props.node, 'defaultValue')),
  set: (v: unknown) => props.ctx.setFormValue(name, v),
})
</script>

<template>
  <div class="widget-w-field">
    <label v-if="label" class="widget-w-field__label">{{ label }}</label>
    <el-input
      v-model="value"
      class="widget-w-field__control"
      :placeholder="placeholder"
      :name="name"
      size="small"
    />
  </div>
</template>

<style scoped>
.widget-w-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.widget-w-field__label {
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
}
</style>
