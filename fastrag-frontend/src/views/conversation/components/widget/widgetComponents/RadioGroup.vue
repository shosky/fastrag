<script setup lang="ts">
import { computed } from 'vue'
import { propArray, propString, type WidgetComponentProps } from '../registry'

// RadioGroup：单选组（options [{label,value}]；props.name 归集表单收集值）
interface WidgetOption {
  label?: string
  value?: unknown
}

const props = defineProps<WidgetComponentProps>()

const name = propString(props.node, 'name')
const label = propString(props.node, 'label')
const options = propArray<WidgetOption>(props.node, 'options')

const value = computed({
  get: () => props.ctx.getFormValue(name, props.node?.props?.defaultValue),
  set: (v: unknown) => props.ctx.setFormValue(name, v),
})
</script>

<template>
  <div class="widget-w-field">
    <label v-if="label" class="widget-w-field__label">{{ label }}</label>
    <el-radio-group :model-value="value" class="widget-w-field__control" @update:model-value="value = $event">
      <el-radio v-for="(opt, i) in options" :key="i" :value="opt.value">
        {{ typeof opt.label === 'string' ? opt.label : String(opt.value ?? '') }}
      </el-radio>
    </el-radio-group>
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
