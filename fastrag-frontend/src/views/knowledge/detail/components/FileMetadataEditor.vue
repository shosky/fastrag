<script setup lang="ts">
import type { AttrDef, FileMetadata } from '@/types/knowledge'
import * as api from '@/api'
import { ElMessage } from 'element-plus'

/**
 * 文件元数据编辑器（分册四 · schema 驱动）。
 *
 * 无弹窗外壳的可复用组件（不含状态徽标与标签，仅字段编辑 + 保存）：
 * - chunks 页「元数据信息」标题右侧的编辑弹窗；
 * - 文件列表的「元数据」弹窗（FileMetadataDialog 包一层 el-dialog）。
 * 字段完全由 KB schema (attrSchema) 决定，取值统一存 values（key=AttrDef.name）。
 */
const props = defineProps<{
  kbId: string
  fileId: string
}>()

const emit = defineEmits<{
  (e: 'saved', fileId: string): void
}>()

const loading = ref(false)
const saving = ref(false)
const attrSchema = ref<AttrDef[]>([])
const formValues = ref<Record<string, any>>({})

async function load() {
  if (!props.kbId || !props.fileId) return
  loading.value = true
  try {
    const resMeta: any = await api.getFileMetadata(props.kbId, props.fileId)
    const meta = (resMeta || {}) as FileMetadata
    attrSchema.value = meta.attrSchema || []
    formValues.value = { ...(meta.values || {}) }
  } catch (e) {
    ElMessage.error('加载元数据失败')
  } finally {
    loading.value = false
  }
}

// 文件切换时重新加载
watch(() => props.fileId, () => { if (props.fileId) load() }, { immediate: true })

// region 类型的取值：后端为 JSON 数组串/逗号串，编辑时以多选数组呈现
function regionArr(def: AttrDef): string[] {
  const v = formValues.value[def.name]
  if (!v) return []
  if (Array.isArray(v)) return v as string[]
  const t = String(v).trim()
  if (t.startsWith('[')) {
    try { return (JSON.parse(t) as string[]).filter(Boolean) } catch { return [t] }
  }
  return t.split(/[,，、\s]+/).filter(Boolean)
}

function setRegion(def: AttrDef, arr: string[]) {
  formValues.value[def.name] = arr.slice()
}

function isPresent(def: AttrDef): boolean {
  const v = formValues.value[def.name]
  if (v === undefined || v === null) return false
  if (Array.isArray(v)) return v.length > 0
  return String(v).trim() !== ''
}

function toPayloadValue(def: AttrDef): unknown {
  const raw = formValues.value[def.name]
  if (raw === undefined || raw === null) return ''
  if (def.type === 'region') {
    const arr = Array.isArray(raw) ? raw : regionArr(def)
    return JSON.stringify(arr)
  }
  if (def.type === 'number') {
    if (raw === '') return ''
    const n = Number(raw)
    return Number.isNaN(n) ? '' : n
  }
  if (def.type === 'boolean') return Boolean(raw)
  return raw
}

async function handleSave() {
  if (!props.kbId || !props.fileId) return
  const missing = attrSchema.value.filter((d) => d.required && !isPresent(d))
  if (missing.length) {
    ElMessage.warning(`请填写必填字段：${missing.map((d) => d.label || d.name).join('、')}`)
    return
  }
  saving.value = true
  try {
    const values: Record<string, unknown> = {}
    for (const def of attrSchema.value) {
      values[def.name] = toPayloadValue(def)
    }
    await api.updateFileMetadata(props.kbId, props.fileId, { values })
    ElMessage.success('文件元数据已保存')
    emit('saved', props.fileId)
  } catch (e: any) {
    ElMessage.error(e?.message || '保存元数据失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="file-metadata-editor" v-loading="loading">
    <template v-if="attrSchema.length">
      <el-form label-width="88px">
        <el-form-item v-for="def in attrSchema" :key="def.name" :label="`${def.label || def.name}`" :required="def.required">
          <el-select
            v-if="def.type === 'region'"
            :model-value="regionArr(def)"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入地域，回车添加多个"
            style="width: 100%"
            @update:model-value="(v: string[]) => setRegion(def, v)"
          />
          <el-select
            v-else-if="def.type === 'select'"
            v-model="formValues[def.name]"
            multiple
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="选择或输入（可多选）"
            style="width: 100%"
          >
            <el-option v-for="opt in def.options || []" :key="opt" :label="opt" :value="opt" />
          </el-select>
          <el-input-number
            v-else-if="def.type === 'number'"
            v-model="formValues[def.name]"
            :controls="false"
            style="width: 100%"
          />
          <el-date-picker
            v-else-if="def.type === 'date'"
            v-model="formValues[def.name]"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
          <el-switch
            v-else-if="def.type === 'boolean'"
            v-model="formValues[def.name]"
            :active-value="true"
            :inactive-value="false"
          />
          <el-input v-else v-model="formValues[def.name]" placeholder="请输入" />
          <div v-if="def.description" class="file-metadata-editor__attr-desc">{{ def.description }}</div>
        </el-form-item>
      </el-form>
    </template>
    <el-empty v-else description="暂无元数据字段，可在知识库表单「元数据字段」中配置" :image-size="56" />

    <div class="file-metadata-editor__footer">
      <el-button type="primary" :loading="saving" @click="handleSave">保存元数据</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.file-metadata-editor {
  &__attr-desc {
    font-size: 12px;
    color: $text-secondary;
    line-height: 1.4;
    margin-top: 4px;
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }
}
</style>
