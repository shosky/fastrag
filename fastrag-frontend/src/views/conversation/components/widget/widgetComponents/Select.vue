<script setup lang="ts">
import { computed } from 'vue'
import { propArray, propString, type WidgetComponentProps } from '../registry'

// Select：下拉选择（options [{label,value}]；props.name 归集表单收集值）
interface WidgetOption {
  label?: string
  value?: unknown
}

const props = defineProps<WidgetComponentProps>()

const name = propString(props.node, 'name')
const label = propString(props.node, 'label')
const placeholder = propString(props.node, 'placeholder', '请选择')
const options = propArray<WidgetOption>(props.node, 'options')

const value = computed({
  get: () => props.ctx.getFormValue(name, props.node?.props?.defaultValue),
  set: (v: unknown) => props.ctx.setFormValue(name, v),
})
</script>

<template>
  <div class="widget-w-field">
    <label v-if="label" class="widget-w-field__label">{{ label }}</label>
    <el-select
      :model-value="value"
      class="widget-w-field__control"
      :placeholder="placeholder"
      size="small"
      @update:model-value="value = $event"
    >
      <el-option
        v-for="(opt, i) in options"
        :key="i"
        :label="typeof opt.label === 'string' ? opt.label : String(opt.value ?? '')"
        :value="opt.value"
      />
    </el-select>
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
