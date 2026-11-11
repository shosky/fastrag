<script setup lang="ts">
import { computed } from 'vue'
import { propString, type WidgetComponentProps } from '../registry'

// Checkbox：复选框（布尔值；props.name 归集表单收集值，text 为勾选标签）
const props = defineProps<WidgetComponentProps>()

const name = propString(props.node, 'name')
const text = propString(props.node, 'text')
const defaultValue = props.node?.props?.defaultValue === true

const value = computed({
  get: () => props.ctx.getFormValue(name, defaultValue) === true,
  set: (v: unknown) => props.ctx.setFormValue(name, v === true),
})
</script>

<template>
  <div class="widget-w-field">
    <el-checkbox v-model="value" class="widget-w-field__control">{{ text }}</el-checkbox>
  </div>
</template>

<style scoped>
.widget-w-field {
  display: flex;
  flex-direction: column;
}
</style>
