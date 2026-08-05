<script setup lang="ts">
import { CopyDocument, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { ApiEndpoint } from '@/data/api-docs/types'
import ApiCodeBlock from './ApiCodeBlock.vue'

const props = defineProps<{
  endpoint: ApiEndpoint
  baseUrl: string
  kbId: string
  token?: string
}>()

const expanded = ref(false)

const methodColorMap: Record<string, string> = {
  GET: '#2563EB',
  POST: '#10B981',
  PUT: '#F59E0B',
  DELETE: '#EF4444',
}

const methodBgMap: Record<string, string> = {
  GET: '#EFF6FF',
  POST: '#ECFDF5',
  PUT: '#FFFBEB',
  DELETE: '#FEF2F2',
}

async function copyPath() {
  try {
    await navigator.clipboard.writeText(props.baseUrl + props.endpoint.path)
    ElMessage.success('路径已复制')
  } catch {
    ElMessage.info(`路径: ${props.baseUrl}${props.endpoint.path}`)
  }
}

function resolvePath(template: string): string {
  return template
    .replace('{kbId}', props.kbId)
    .replace('{id}', props.kbId)
}

function hasParams(): boolean {
  return !!(
    props.endpoint.pathParams?.length ||
    props.endpoint.queryParams?.length ||
    props.endpoint.body?.length
  )
}

function tryFormatJson(str?: string): string {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}
</script>

<template>
  <div class="api-endpoint-card">
    <!-- Header row: click to expand -->
    <div class="api-endpoint-card__header" @click="expanded = !expanded">
      <span
        class="api-endpoint-card__method"
        :style="{
          color: methodColorMap[endpoint.method] || '#333',
          background: methodBgMap[endpoint.method] || '#f5f5f5',
        }"
      >
        {{ endpoint.method }}
      </span>
      <code class="api-endpoint-card__path" @click.stop="copyPath">
        {{ resolvePath(endpoint.path) }}
      </code>
      <span class="api-endpoint-card__summary">{{ endpoint.summary }}</span>
      <span class="api-endpoint-card__arrow">
        <el-icon :size="14">
          <ArrowUp v-if="expanded" />
          <ArrowDown v-else />
        </el-icon>
      </span>
    </div>

    <!-- Expanded content -->
    <transition name="el-zoom-in-top">
      <div v-if="expanded" class="api-endpoint-card__body">
        <!-- Description -->
        <p class="api-endpoint-card__desc">{{ endpoint.description }}</p>

        <!-- Parameters table -->
        <template v-if="hasParams()">
          <div v-if="endpoint.pathParams?.length" class="api-endpoint-card__section">
            <h4 class="api-endpoint-card__section-title">路径参数</h4>
            <el-table :data="endpoint.pathParams" size="small" stripe border>
              <el-table-column prop="name" label="参数名" width="120" />
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column prop="required" label="必填" width="70" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                    {{ row.required ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="说明" />
              <el-table-column prop="example" label="示例" width="150">
                <template #default="{ row }">
                  <code v-if="row.example">{{ row.example }}</code>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div v-if="endpoint.queryParams?.length" class="api-endpoint-card__section">
            <h4 class="api-endpoint-card__section-title">Query 参数</h4>
            <el-table :data="endpoint.queryParams" size="small" stripe border>
              <el-table-column prop="name" label="参数名" width="120" />
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column prop="required" label="必填" width="70" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                    {{ row.required ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="说明" />
              <el-table-column prop="example" label="示例" width="150">
                <template #default="{ row }">
                  <code v-if="row.example">{{ row.example }}</code>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div v-if="endpoint.body?.length" class="api-endpoint-card__section">
            <h4 class="api-endpoint-card__section-title">请求体</h4>
            <el-table :data="endpoint.body" size="small" stripe border>
              <el-table-column prop="name" label="字段名" width="140" />
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column prop="required" label="必填" width="70" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                    {{ row.required ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="说明" />
              <el-table-column prop="example" label="示例" width="150">
                <template #default="{ row }">
                  <code v-if="row.example">{{ row.example }}</code>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
            </el-table>

            <!-- Request body example -->
            <div v-if="endpoint.requestExample" class="api-endpoint-card__example">
              <div class="api-endpoint-card__example-header">
                <span>请求示例</span>
                <el-button :icon="CopyDocument" size="small" link
                  @click="endpoint.requestExample && navigator.clipboard.writeText(tryFormatJson(endpoint.requestExample)).then(() => ElMessage.success('已复制'))">
                  复制
                </el-button>
              </div>
              <pre class="api-endpoint-card__code">{{ tryFormatJson(endpoint.requestExample) }}</pre>
            </div>
          </div>
        </template>

        <!-- Response example -->
        <div v-if="endpoint.responseExample" class="api-endpoint-card__section">
          <h4 class="api-endpoint-card__section-title">响应示例</h4>
          <div class="api-endpoint-card__example">
            <div class="api-endpoint-card__example-header">
              <span>200 OK</span>
              <el-button :icon="CopyDocument" size="small" link
                @click="navigator.clipboard.writeText(tryFormatJson(endpoint.responseExample)).then(() => ElMessage.success('已复制'))">
                复制
              </el-button>
            </div>
            <pre class="api-endpoint-card__code">{{ tryFormatJson(endpoint.responseExample) }}</pre>
          </div>
        </div>

        <!-- Code block -->
        <div class="api-endpoint-card__section" v-if="endpoint.debuggable !== false">
          <h4 class="api-endpoint-card__section-title">代码示例</h4>
          <ApiCodeBlock
            :endpoint="endpoint"
            :base-url="baseUrl"
            :kb-id="kbId"
            :token="token"
          />
        </div>
      </div>
    </transition>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.api-endpoint-card {
  border: 1px solid $border-base;
  border-radius: $radius-base;
  overflow: hidden;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: $shadow-sm;
  }

  &__header {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    background: $bg-white;
    cursor: pointer;
    user-select: none;
    transition: background 0.15s;

    &:hover {
      background: $bg-hover;
    }
  }

  &__method {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 56px;
    padding: 2px 8px;
    border-radius: $radius-sm;
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.5px;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
  }

  &__path {
    flex: 1;
    font-size: 13px;
    font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
    color: $text-primary;
    cursor: pointer;

    &:hover {
      color: $color-primary;
    }
  }

  &__summary {
    font-size: 13px;
    color: $text-secondary;
    white-space: nowrap;
  }

  &__arrow {
    color: $text-placeholder;
    flex-shrink: 0;
  }

  &__body {
    padding: $spacing-base;
    background: $bg-white;
    border-top: 1px solid $border-light;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__desc {
    margin: 0;
    font-size: 13px;
    color: $text-regular;
    line-height: 1.6;
  }

  &__section {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__section-title {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
  }

  &__example {
    border: 1px solid $border-light;
    border-radius: $radius-sm;
    overflow: hidden;
  }

  &__example-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $spacing-xs $spacing-sm;
    background: #F8FAFC;
    border-bottom: 1px solid $border-light;
    font-size: 12px;
    color: $text-secondary;
  }

  &__code {
    margin: 0;
    padding: $spacing-sm $spacing-base;
    background: #1E293B;
    color: #E2E8F0;
    font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
    font-size: 12px;
    line-height: 1.6;
    overflow-x: auto;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

.text-muted {
  color: $text-placeholder;
  font-size: 12px;
}
</style>
