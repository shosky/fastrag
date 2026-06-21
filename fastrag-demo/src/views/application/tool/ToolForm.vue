<script setup lang="ts">
import { reactive, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  TOOL_CATEGORIES,
  HTTP_METHOD_OPTIONS,
  AUTH_TYPE_OPTIONS,
  BODY_TYPE_OPTIONS,
  defaultHttpConfig,
  defaultInputs,
} from '@/mock/tools'
import type { Tool, HttpToolConfig } from '@/mock/tools'

const props = withDefaults(
  defineProps<{
    mode: 'create' | 'edit'
    initialData?: Tool | null
    /** 限制为 HTTP 工具（创建页用） */
    httpOnly?: boolean
  }>(),
  { initialData: null, httpOnly: false },
)

const emit = defineEmits<{
  (e: 'submit', data: Tool): void
  (e: 'cancel'): void
}>()

// --- 表单数据 ---
function defaultForm(): Tool {
  return {
    id: '',
    name: '',
    identifier: '',
    description: '',
    type: props.httpOnly ? 'http' : 'http',
    tags: ['HTTP工具'],
    icon: '#409eff',
    httpConfig: defaultHttpConfig(),
    inputs: defaultInputs(),
    enabled: true,
    createdAt: '',
  }
}

const form = reactive<Tool>(defaultForm())

function syncFromProps() {
  Object.assign(form, defaultForm())
  if (props.initialData) {
    form.id = props.initialData.id
    form.name = props.initialData.name
    form.identifier = props.initialData.identifier
    form.description = props.initialData.description
    form.type = props.initialData.type
    form.tags = [...props.initialData.tags]
    form.icon = props.initialData.icon
    form.enabled = props.initialData.enabled
    form.inputs = props.initialData.inputs.map((i) => ({ ...i }))
    if (props.initialData.httpConfig) {
      const h = props.initialData.httpConfig
      form.httpConfig = {
        method: h.method,
        url: h.url,
        authType: h.authType,
        params: h.params.map((p) => ({ ...p })),
        bodyType: h.bodyType,
        body: h.body,
        headers: h.headers.map((hd) => ({ ...hd })),
      }
    } else {
      form.httpConfig = defaultHttpConfig()
    }
  }
}

watch(
  () => props.initialData,
  () => syncFromProps(),
  { immediate: true },
)

/** 是否展示 HTTP 配置区 */
const showHttpConfig = computed(() => form.type === 'http')

// --- 输入参数管理 ---
function addInputParam() {
  form.inputs.push({ name: '', description: '', type: 'string', isToolParam: true })
}

function removeInputParam(index: number) {
  form.inputs.splice(index, 1)
}

// --- Params 管理 ---
function addParam() {
  form.httpConfig!.params.push({ key: '', value: '' })
}

function removeParam(index: number) {
  form.httpConfig!.params.splice(index, 1)
}

// --- Header 管理 ---
function addHeader() {
  form.httpConfig!.headers.push({ key: '', value: '' })
}

function removeHeader(index: number) {
  form.httpConfig!.headers.splice(index, 1)
}

// --- 提交 ---
function validate(): boolean {
  if (!form.name.trim()) {
    ElMessage.warning('请输入工具名称')
    return false
  }
  if (showHttpConfig.value && !form.httpConfig?.url.trim()) {
    ElMessage.warning('请输入请求地址')
    return false
  }
  return true
}

function handleSubmit() {
  if (!validate()) return
  // 自动生成 identifier（若为空）
  if (!form.identifier.trim()) {
    form.identifier = form.name.toLowerCase().replace(/\s+/g, '_')
  }
  const payload: Tool = {
    ...form,
    tags: [...form.tags],
    inputs: form.inputs.map((i) => ({ ...i })),
    httpConfig: showHttpConfig.value
      ? {
          ...form.httpConfig!,
          params: form.httpConfig!.params.map((p) => ({ ...p })),
          headers: form.httpConfig!.headers.map((h) => ({ ...h })),
        }
      : undefined,
  }
  emit('submit', payload)
}

/** cURL 导入占位 */
function handleCurlImport() {
  ElMessage.info('cURL 导入功能开发中')
}
</script>

<template>
  <div class="tool-form-wrap">
    <div class="form-columns">
      <!-- 左列：工具信息 + 输入参数 -->
      <div class="form-left">
        <div class="form-section">
          <div class="section-header"><span>工具信息</span></div>
          <el-form label-position="top">
            <el-form-item label="工具名称" required>
              <el-input v-model="form.name" placeholder="工具名称" />
            </el-form-item>
            <el-form-item label="工具标识">
              <el-input v-model="form.identifier" placeholder="留空则按名称自动生成" />
            </el-form-item>
            <el-form-item label="工具类型">
              <el-radio-group v-model="form.type" :disabled="httpOnly">
                <el-radio-button v-for="t in TOOL_CATEGORIES" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="工具描述">
              <el-input v-model="form.description" type="textarea" :rows="3" placeholder="工具描述" />
            </el-form-item>
            <el-form-item label="是否启用">
              <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
            </el-form-item>
          </el-form>
        </div>

        <div class="form-section">
          <div class="section-header">
            <span>输入参数</span>
            <el-button size="small" @click="addInputParam">
              <el-icon><Plus /></el-icon>新增
            </el-button>
          </div>
          <el-table :data="form.inputs" size="small" border>
            <el-table-column prop="name" label="参数名" min-width="100">
              <template #default="{ row }">
                <el-input v-model="row.name" size="small" placeholder="参数名" />
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.description" size="small" placeholder="描述" />
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-select v-model="row.type" size="small" style="width: 100%">
                  <el-option label="string" value="string" />
                  <el-option label="number" value="number" />
                  <el-option label="boolean" value="boolean" />
                  <el-option label="array" value="array" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="isToolParam" label="工具参数" width="80" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.isToolParam" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="removeInputParam($index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 右列：HTTP 请求配置（仅 HTTP 工具） -->
      <div class="form-right" v-if="showHttpConfig">
        <div class="form-section">
          <div class="section-header">
            <span>请求配置</span>
            <el-button size="small" type="primary" plain @click="handleCurlImport">
              cURL 导入
            </el-button>
          </div>
          <div class="request-config">
            <el-select v-model="form.httpConfig!.method" style="width: 110px">
              <el-option v-for="m in HTTP_METHOD_OPTIONS" :key="m" :label="m" :value="m" />
            </el-select>
            <el-input v-model="form.httpConfig!.url" placeholder="请求地址" style="flex: 1" />
          </div>
        </div>

        <div class="form-section">
          <div class="section-header"><span>鉴权配置</span></div>
          <el-form label-width="80px" label-position="left">
            <el-form-item label="鉴权类型">
              <el-select v-model="form.httpConfig!.authType" style="width: 100%">
                <el-option v-for="a in AUTH_TYPE_OPTIONS" :key="a.value" :label="a.label" :value="a.value" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <div class="form-section">
          <div class="section-header"><span>Params</span></div>
          <el-table :data="form.httpConfig!.params" size="small" border>
            <el-table-column label="参数名" min-width="100">
              <template #default="{ row }">
                <el-input v-model="row.key" size="small" placeholder="key" />
              </template>
            </el-table-column>
            <el-table-column label="参数值" min-width="100">
              <template #default="{ row }">
                <el-input v-model="row.value" size="small" placeholder="value" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="removeParam($index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" link @click="addParam">
            <el-icon><Plus /></el-icon>添加参数
          </el-button>
        </div>

        <div class="form-section">
          <div class="section-header"><span>Body</span></div>
          <div class="body-type-tabs">
            <el-radio-group v-model="form.httpConfig!.bodyType" size="small">
              <el-radio-button v-for="b in BODY_TYPE_OPTIONS" :key="b.value" :value="b.value">{{ b.label }}</el-radio-button>
            </el-radio-group>
          </div>
          <el-input
            v-model="form.httpConfig!.body"
            type="textarea"
            :rows="4"
            :disabled="form.httpConfig!.bodyType === 'none'"
            placeholder="请求体内容"
          />
        </div>

        <div class="form-section">
          <div class="section-header"><span>Headers</span></div>
          <el-table :data="form.httpConfig!.headers" size="small" border>
            <el-table-column label="参数名" min-width="100">
              <template #default="{ row }">
                <el-input v-model="row.key" size="small" placeholder="key" />
              </template>
            </el-table-column>
            <el-table-column label="参数值" min-width="100">
              <template #default="{ row }">
                <el-input v-model="row.value" size="small" placeholder="value" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="removeHeader($index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" link @click="addHeader">
            <el-icon><Plus /></el-icon>添加 Header
          </el-button>
        </div>
      </div>

      <!-- 非 HTTP 工具的提示 -->
      <div class="form-right form-placeholder" v-else>
        <el-empty description="该工具类型无需 HTTP 请求配置，仅维护基础信息与输入参数即可" :image-size="80" />
      </div>
    </div>

    <div class="form-footer">
      <el-button @click="emit('cancel')">取消</el-button>
      <el-button type="primary" @click="handleSubmit">
        {{ mode === 'create' ? '创建' : '保存' }}
      </el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.tool-form-wrap {
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  padding: $spacing-lg $spacing-xl;
}

.form-columns {
  display: flex;
  gap: $spacing-xl;
}

.form-left,
.form-right {
  flex: 1;
  min-width: 0;
}

.form-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.form-section {
  margin-bottom: $spacing-lg;

  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-sm;
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }
}

.request-config {
  display: flex;
  gap: $spacing-sm;
}

.body-type-tabs {
  margin-bottom: $spacing-sm;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
  border-top: 1px solid $border-lighter;
}
</style>
