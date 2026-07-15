<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { HttpToolConfig, ToolInputParam, JsonSchema } from '@/mock/tools'
import * as api from '@/api'
import {
  Document, CopyDocument, Download, VideoPlay, RefreshRight,
  ArrowDown, StarFilled,
} from '@element-plus/icons-vue'

// ==================== Props ====================

const props = defineProps<{
  httpConfig: HttpToolConfig | null
  inputs: ToolInputParam[]
  inputSchema?: JsonSchema | null
  outputSchema?: JsonSchema | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const visible = defineModel<boolean>('visible', { required: true })

// ==================== 类型定义 ====================

interface TestParam {
  name: string
  type: string
  value: any
  required: boolean
  description: string
  enum?: string[]
}

interface TestRecord {
  id: number
  timestamp: string
  params: TestParam[]
  request: { method: string; url: string; headers: Record<string, string>; body: string | null }
  response: { status: number; statusText: string; headers: Record<string, string>; body: string }
  duration: number
  error?: string
}

interface Scenario {
  name: string
  params: { name: string; value: any }[]
  createdAt: string
}

// ==================== 状态 ====================

const loading = ref(false)
const timeoutMs = ref(30000)
const activeTab = ref('params')

// 测试参数
const testParams = ref<TestParam[]>([])

// 测试结果
const result = ref<TestRecord | null>(null)
const resultBodyFormatted = ref('')
const responseHeaders = ref<Record<string, string>>({})
const responseSize = ref(0)

// 历史记录
const history = ref<TestRecord[]>([])
const scenarioName = ref('')
const showSaveScenario = ref(false)
const scenarios = ref<Scenario[]>([])

// ==================== 初始化 ====================

function initTestParams() {
  const params: TestParam[] = []

  // 从 inputSchema 读取（优先）
  if (props.inputSchema?.properties) {
    const schema = props.inputSchema
    const required = schema.required || []
    for (const [name, prop] of Object.entries(schema.properties)) {
      params.push({
        name,
        type: prop.type || 'string',
        value: getDefaultValue(prop.type),
        required: required.includes(name),
        description: prop.description || '',
        enum: prop.enum,
      })
    }
  } else if (props.inputs?.length) {
    // 从 inputs 数组读取
    for (const p of props.inputs) {
      if (!p.name) continue
      params.push({
        name: p.name,
        type: p.type || 'string',
        value: getDefaultValue(p.type),
        required: p.required || false,
        description: p.description || '',
        enum: p.enum,
      })
    }
  }

  // 从 Body 模板中提取 ${xxx} 补充
  const body = props.httpConfig?.body || ''
  const matches = body.matchAll(/\$\{([^}]+)\}/g)
  const existingNames = new Set(params.map((p) => p.name))
  for (const m of matches) {
    if (!existingNames.has(m[1])) {
      params.push({
        name: m[1],
        type: 'string',
        value: '',
        required: false,
        description: '',
      })
    }
  }

  testParams.value = params
}

function getDefaultValue(type: string): any {
  switch (type) {
    case 'boolean': return false
    case 'number': case 'integer': return 0
    default: return ''
  }
}

// 监听 inputs/inputSchema 变化
watch(
  () => [props.inputs, props.inputSchema, props.httpConfig?.body],
  () => initTestParams(),
  { immediate: true },
)

// ==================== 场景管理 ====================

function loadScenarios() {
  try {
    const toolId = (props.httpConfig as any)?.url || 'default'
    const key = `tool_test_scenarios_${toolId}`
    const raw = localStorage.getItem(key)
    scenarios.value = raw ? JSON.parse(raw) : []
  } catch { scenarios.value = [] }
}

function saveScenario() {
  if (!scenarioName.value.trim()) {
    ElMessage.warning('请输入场景名称')
    return
  }
  const sc: Scenario = {
    name: scenarioName.value.trim(),
    params: testParams.value.map((p) => ({ name: p.name, value: p.value })),
    createdAt: new Date().toLocaleString(),
  }
  scenarios.value.push(sc)
  const toolId = (props.httpConfig as any)?.url || 'default'
  localStorage.setItem(`tool_test_scenarios_${toolId}`, JSON.stringify(scenarios.value))
  scenarioName.value = ''
  showSaveScenario.value = false
  ElMessage.success('场景已保存')
}

function loadScenario(sc: Scenario) {
  for (const sp of sc.params) {
    const found = testParams.value.find((p) => p.name === sp.name)
    if (found) found.value = sp.value
  }
  ElMessage.success(`已加载场景「${sc.name}」`)
}

function deleteScenario(idx: number) {
  scenarios.value.splice(idx, 1)
  const toolId = (props.httpConfig as any)?.url || 'default'
  localStorage.setItem(`tool_test_scenarios_${toolId}`, JSON.stringify(scenarios.value))
}

// ==================== 模板替换（类型安全） ====================

function serializeValue(value: any, type: string): string {
  if (value === null || value === undefined) return ''
  // 不额外加引号——模板中 ${xxx} 的位置决定是否要引号
  // "dataSourceIds":"${dataSourceIds}" → 模板已有引号，返回纯值
  // "count": ${count} → 模板无引号，返回纯值
  return String(value)
}

function buildRequestBody(template: string, params: TestParam[]): string {
  let body = template
  for (const p of params) {
    // 替换 ${paramName} 为类型正确的值
    const regex = new RegExp(`\\$\\{${escapeRegex(p.name)}\\}`, 'g')
    body = body.replace(regex, () => serializeValue(p.value, p.type))
  }
  // 尝试美化 JSON
  try { body = JSON.stringify(JSON.parse(body), null, 2) } catch { /* keep raw */ }
  return body
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function replaceTemplateVars(text: string, params: TestParam[]): string {
  let result = text
  for (const p of params) {
    const regex = new RegExp(`\\$\\{${escapeRegex(p.name)}\\}`, 'g')
    result = result.replace(regex, () => String(p.value))
  }
  return result
}

// ==================== 执行测试 ====================

function validate(): boolean {
  for (const p of testParams.value) {
    if (p.required && (p.value === '' || p.value === null || p.value === undefined)) {
      ElMessage.warning(`请填写必填参数「${p.name}」`)
      return false
    }
  }
  if (!props.httpConfig?.url.trim()) {
    ElMessage.warning('请先配置请求地址')
    return false
  }
  return true
}

async function runTest() {
  if (!validate()) return

  const config = props.httpConfig!
  loading.value = true
  result.value = null
  activeTab.value = 'result'

  try {
    // 构建参数变量
    const params = testParams.value

    // 构建 URL（替换路径/query中的模板变量）
    let url = config.url
    url = replaceTemplateVars(url, params)

    // 构建 query string
    const paramParts: string[] = []
    for (const qp of config.params) {
      if (!qp.key) continue
      const val = replaceTemplateVars(qp.value || '', params)
      paramParts.push(`${encodeURIComponent(qp.key)}=${encodeURIComponent(val)}`)
    }
    if (paramParts.length) {
      url += (url.includes('?') ? '&' : '?') + paramParts.join('&')
    }

    // 构建 body
    const body = buildRequestBody(config.body || '', params)

    // 构建 headers
    const reqHeaders: Record<string, string> = {
      ...Object.fromEntries(
        config.headers.filter((h) => h.key).map((h) => [h.key, replaceTemplateVars(h.value, params)]),
      ),
    }
    // 确保 Content-Type
    if (body && !reqHeaders['Content-Type']) {
      reqHeaders['Content-Type'] = 'application/json'
    }
    // 鉴权 header
    const authType = config.authType
    const authValue = config.authValue || ''
    if (authType === 'apiKey' && authValue) {
      reqHeaders['X-API-Key'] = authValue
    } else if ((authType === 'bearer' || authType === 'oauth2') && authValue) {
      reqHeaders['Authorization'] = `Bearer ${authValue}`
    }

    // 发送请求（通过后端代理解决 CORS）
    const startTime = Date.now()
    const proxyResp: any = await api.testProxy({
      method: config.method || 'GET',
      url,
      headers: reqHeaders,
      body: ['POST', 'PUT', 'PATCH'].includes(config.method) && body ? body : undefined,
      timeout: timeoutMs.value,
    })
    const duration = Date.now() - startTime

    // 解析后端返回的代理结果
    const respStatus = proxyResp.status || 0
    const respStatusText = proxyResp.statusText || ''
    const respBody = proxyResp.body || ''
    const respHeaders: Record<string, string> = proxyResp.headers || {}
    const proxyError = proxyResp.error

    const record: TestRecord = {
      id: Date.now(),
      timestamp: new Date().toLocaleTimeString(),
      params: JSON.parse(JSON.stringify(params)),
      request: { method: config.method, url, headers: reqHeaders, body: body || null },
      response: { status: respStatus, statusText: respStatusText, headers: respHeaders, body: respBody },
      duration,
      error: proxyError,
    }

    result.value = record
    resultBodyFormatted.value = formatBody(respBody)
    responseHeaders.value = respHeaders
    responseSize.value = new Blob([respBody]).size

    // 存入历史
    history.value.unshift(record)
    if (history.value.length > 50) history.value = history.value.slice(0, 50)

    // 失败时自动切换到结果 tab
    if (resp.status >= 400) {
      activeTab.value = 'result'
    }

  } catch (e: any) {
      const record: TestRecord = {
        id: Date.now(),
        timestamp: new Date().toLocaleTimeString(),
        params: JSON.parse(JSON.stringify(params)),
        request: { method: config.method, url: config.url, headers: {}, body: null },
        response: { status: 0, statusText: 'Error', headers: {}, body: '' },
        duration: 0,
        error: e.message || '请求失败',
      }
      result.value = record
      history.value.unshift(record)
  } finally {
    loading.value = false
  }
}

function retryTest() {
  runTest()
}

// ==================== 格式化 ====================

function formatBody(text: string): string {
  if (!text) return ''
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

function statusColor(status: number): string {
  if (status >= 200 && status < 300) return 'success'
  if (status >= 300 && status < 400) return 'warning'
  if (status >= 400) return 'danger'
  return 'info'
}

// ==================== 导出 ====================

function copyBody() {
  if (!result.value) return
  const text = result.value.response.body
  navigator.clipboard.writeText(text).then(
    () => ElMessage.success('已复制到剪贴板'),
    () => ElMessage.error('复制失败'),
  )
}

function copyFull() {
  if (!result.value) return
  const rec = result.value
  const json = JSON.stringify(rec.response, null, 2)
  navigator.clipboard.writeText(json).then(
    () => ElMessage.success('已复制到剪贴板'),
    () => ElMessage.error('复制失败'),
  )
}

function downloadJson() {
  if (!result.value) return
  const rec = result.value
  const blob = new Blob([JSON.stringify(rec, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `test-result-${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
}

function downloadMarkdown() {
  if (!result.value) return
  const rec = result.value
  let md = `# 测试报告\n\n`
  md += `**时间:** ${rec.timestamp}\n`
  md += `**请求:** \`${rec.request.method} ${rec.request.url}\`\n`
  md += `**状态:** ${rec.response.status} ${rec.response.statusText}\n`
  md += `**耗时:** ${rec.duration}ms\n\n`
  md += `## 请求头\n\`\`\`json\n${JSON.stringify(rec.request.headers, null, 2)}\n\`\`\`\n\n`
  if (rec.request.body) {
    md += `## 请求体\n\`\`\`json\n${rec.request.body}\n\`\`\`\n\n`
  }
  md += `## 响应体\n\`\`\`json\n${formatBody(rec.response.body)}\n\`\`\`\n`
  const blob = new Blob([md], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `test-report-${Date.now()}.md`
  a.click()
  URL.revokeObjectURL(url)
}

function viewHistory(record: TestRecord) {
  result.value = record
  resultBodyFormatted.value = formatBody(record.response.body)
  responseHeaders.value = record.response.headers
  responseSize.value = new Blob([record.response.body]).size
  activeTab.value = 'result'
}

// ==================== 生命周期 ====================

// 初始化场景
loadScenarios()
</script>

<template>
  <el-dialog
    v-model="visible"
    title="测试工具"
    width="860px"
    top="4vh"
    destroy-on-close
    class="test-dialog"
  >
    <el-tabs v-model="activeTab" class="test-tabs">
      <!-- ===== Tab 1: 参数 ===== -->
      <el-tab-pane label="参数设置" name="params">
        <div class="test-params-panel">
          <!-- 场景工具栏 -->
          <div class="scenario-toolbar">
            <el-dropdown v-if="scenarios.length" trigger="click">
              <el-button size="small">
                场景模板 <el-icon><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="(sc, i) in scenarios" :key="i" @click="loadScenario(sc)">
                    {{ sc.name }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button size="small" @click="showSaveScenario = !showSaveScenario">
              保存为场景
            </el-button>
            <div v-if="showSaveScenario" class="save-scenario-inline">
              <el-input v-model="scenarioName" size="small" placeholder="场景名称" style="width: 180px" />
              <el-button size="small" type="primary" @click="saveScenario">保存</el-button>
            </div>
          </div>

          <!-- 参数表格 -->
          <div class="params-table" v-if="testParams.length">
            <div class="params-header">
              <span class="col-name">参数名</span>
              <span class="col-type">类型</span>
              <span class="col-req">必填</span>
              <span class="col-value">测试值</span>
              <span class="col-desc">说明</span>
            </div>
            <div v-for="(p, idx) in testParams" :key="p.name" class="params-row">
              <span class="col-name">{{ p.name }}</span>
              <span class="col-type"><el-tag size="small">{{ p.type }}</el-tag></span>
              <span class="col-req">
                <el-icon v-if="p.required" color="#F56C6C"><StarFilled /></el-icon>
              </span>
              <span class="col-value">
                <el-input
                  v-if="p.type === 'string'"
                  v-model="p.value"
                  size="small"
                  placeholder="输入值，留空即空字符串"
                  clearable
                />
                <el-switch
                  v-else-if="p.type === 'boolean'"
                  v-model="p.value"
                  :active-value="true"
                  :inactive-value="false"
                  size="small"
                />
                <el-input-number
                  v-else-if="p.type === 'number' || p.type === 'integer'"
                  v-model="p.value"
                  size="small"
                  :controls="false"
                  style="width: 100%"
                />
                <el-select
                  v-else-if="p.enum && p.enum.length"
                  v-model="p.value"
                  size="small"
                  placeholder="选择值"
                  style="width: 100%"
                >
                  <el-option v-for="opt in p.enum" :key="opt" :label="opt" :value="opt" />
                </el-select>
                <el-input
                  v-else
                  v-model="p.value"
                  size="small"
                  placeholder="输入值"
                  clearable
                />
              </span>
              <span class="col-desc" :title="p.description">{{ p.description || '-' }}</span>
            </div>
          </div>
          <el-empty v-else :image-size="40" description="暂无入参定义，请在 Body 中使用 ${参数名} 定义" />

          <!-- 执行栏 -->
          <div class="execute-bar">
            <div class="execute-left">
              <el-button type="primary" :loading="loading" @click="runTest">
                <el-icon><VideoPlay /></el-icon>发送请求
              </el-button>
              <el-button v-if="result && !loading" @click="retryTest">
                <el-icon><RefreshRight /></el-icon>重试
              </el-button>
            </div>
            <div class="execute-right">
              <span class="timeout-label">超时:</span>
              <el-select v-model="timeoutMs" size="small" style="width: 100px">
                <el-option label="15s" :value="15000" />
                <el-option label="30s" :value="30000" />
                <el-option label="60s" :value="60000" />
                <el-option label="120s" :value="120000" />
              </el-select>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 2: 结果 ===== -->
      <el-tab-pane label="测试结果" name="result">
        <div v-if="!result" class="result-empty">
          <el-empty :image-size="60" description="尚未执行测试，请在「参数设置」中填写参数后发送请求" />
        </div>
        <div v-else class="result-panel">
          <!-- 状态概览 -->
          <div class="result-summary">
            <div class="summary-left">
              <el-tag v-if="result.error" type="danger" size="large">请求失败</el-tag>
              <el-tag v-else :type="statusColor(result.response.status)" size="large" effect="dark">
                {{ result.response.status }} {{ result.response.statusText }}
              </el-tag>
            </div>
            <div class="summary-right">
              <span class="summary-item">耗时: <strong>{{ result.duration }}ms</strong></span>
              <span class="summary-item">大小: <strong>{{ responseSize }}B</strong></span>
              <span class="summary-item">时间: {{ result.timestamp }}</span>
            </div>
          </div>

          <!-- 错误信息 -->
          <el-alert
            v-if="result.error"
            :title="result.error"
            type="error"
            show-icon
            closable
            class="result-error"
          />

          <!-- 请求详情 -->
          <el-collapse class="result-collapse">
            <el-collapse-item title="请求详情" name="request">
              <div class="detail-block">
                <div class="detail-row">
                  <span class="detail-label">URL</span>
                  <span class="detail-value mono">{{ result.request.method }} {{ result.request.url }}</span>
                </div>
                <div class="detail-row" v-if="Object.keys(result.request.headers).length">
                  <span class="detail-label">Headers</span>
                  <pre class="detail-pre">{{ JSON.stringify(result.request.headers, null, 2) }}</pre>
                </div>
                <div class="detail-row" v-if="result.request.body">
                  <span class="detail-label">Body</span>
                  <pre class="detail-pre">{{ result.request.body }}</pre>
                </div>
              </div>
            </el-collapse-item>

            <el-collapse-item title="响应头" name="headers">
              <pre class="detail-pre">{{ JSON.stringify(responseHeaders, null, 2) }}</pre>
            </el-collapse-item>
          </el-collapse>

          <!-- 响应体 -->
          <div class="result-body-section">
            <div class="result-body-header">
              <span>响应体</span>
              <div class="body-actions">
                <el-button size="small" link @click="copyBody">
                  <el-icon><CopyDocument /></el-icon>复制
                </el-button>
                <el-button size="small" link @click="downloadJson">
                  <el-icon><Download /></el-icon>JSON
                </el-button>
                <el-button size="small" link @click="downloadMarkdown">
                  <el-icon><Document /></el-icon>报告
                </el-button>
                <el-button size="small" link @click="copyFull">
                  <el-icon><CopyDocument /></el-icon>复制全部
                </el-button>
              </div>
            </div>
            <pre class="result-body-content">{{ resultBodyFormatted || result.response.body }}</pre>
          </div>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 3: 历史 ===== -->
      <el-tab-pane label="历史记录" name="history">
        <div v-if="!history.length" class="result-empty">
          <el-empty :image-size="60" description="暂无历史记录" />
        </div>
        <div v-else class="history-list">
          <div
            v-for="(rec, idx) in history"
            :key="rec.id"
            class="history-item"
            :class="{ active: result?.id === rec.id }"
            @click="viewHistory(rec)"
          >
            <span class="history-idx">#{{ idx + 1 }}</span>
            <span class="history-time">{{ rec.timestamp }}</span>
            <el-tag v-if="rec.error" type="danger" size="small">Error</el-tag>
            <el-tag v-else :type="statusColor(rec.response.status)" size="small" effect="plain">
              {{ rec.response.status }}
            </el-tag>
            <span class="history-duration">{{ rec.duration }}ms</span>
            <span class="history-url" :title="rec.request.url">{{ rec.request.url }}</span>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<style lang="scss" scoped>
.test-dialog {
  :deep(.el-dialog__body) {
    padding-top: 0;
  }
}

.test-tabs {
  :deep(.el-tabs__content) {
    min-height: 400px;
  }
}

/* ===== 参数面板 ===== */
.test-params-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.scenario-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.save-scenario-inline {
  display: flex;
  align-items: center;
  gap: 6px;
}

.params-table {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  overflow: hidden;
}

.params-header,
.params-row {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  gap: 8px;
  font-size: 13px;
}

.params-header {
  background: var(--el-fill-color-light);
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.params-row {
  border-top: 1px solid var(--el-border-color-light);
  &:hover { background: var(--el-fill-color-lighter); }
}

.col-name { width: 140px; flex-shrink: 0; font-family: monospace; }
.col-type { width: 72px; flex-shrink: 0; }
.col-req { width: 40px; flex-shrink: 0; text-align: center; }
.col-value { flex: 1; min-width: 160px; }
.col-desc { width: 140px; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--el-text-color-secondary); font-size: 12px; }

/* ===== 执行栏 ===== */
.execute-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-light);
}

.execute-left,
.execute-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.timeout-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* ===== 结果面板 ===== */
.result-empty {
  padding: 40px 0;
}

.result-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.summary-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-right {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);

  strong {
    color: var(--el-text-color-primary);
  }
}

.result-error {
  margin-bottom: 4px;
}

.result-collapse {
  :deep(.el-collapse-item__header) {
    font-weight: 600;
    font-size: 13px;
  }
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-row {
  display: flex;
  gap: 12px;
}

.detail-label {
  width: 72px;
  flex-shrink: 0;
  font-weight: 600;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding-top: 2px;
}

.detail-value {
  font-size: 13px;
}

.mono {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  word-break: break-all;
}

.detail-pre {
  margin: 0;
  flex: 1;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  background: var(--el-fill-color-lighter);
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.result-body-section {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  overflow: hidden;
}

.result-body-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  font-weight: 600;
  font-size: 13px;
}

.body-actions {
  display: flex;
  gap: 4px;
}

.result-body-content {
  margin: 0;
  padding: 12px;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* ===== 历史记录 ===== */
.history-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  border: 1px solid transparent;

  &:hover {
    background: var(--el-fill-color-lighter);
  }

  &.active {
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-5);
  }
}

.history-idx {
  width: 32px;
  flex-shrink: 0;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.history-time {
  width: 72px;
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.history-duration {
  width: 56px;
  flex-shrink: 0;
  text-align: right;
  font-family: monospace;
}

.history-url {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
