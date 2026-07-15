<script setup lang="ts">
import { reactive, ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  TOOL_CATEGORIES,
  HTTP_METHOD_OPTIONS,
  AUTH_TYPE_OPTIONS,
  BODY_TYPE_OPTIONS,
  defaultHttpConfig,
  inputsToSchema,
  schemaToInputs,
} from '@/mock/tools'
import type { Tool, HttpToolConfig, ToolInputParam } from '@/mock/tools'
import ToolTestDialog from './ToolTestDialog.vue'

const props = withDefaults(
  defineProps<{
    mode: 'create' | 'edit'
    initialData?: Tool | null
  }>(),
  { initialData: null },
)

const emit = defineEmits<{
  (e: 'submit', data: Tool): void
  (e: 'cancel'): void
}>()

// ==================== 表单数据 ====================

function defaultForm(): Tool {
  return {
    id: '',
    name: '',
    identifier: '',
    description: '',
    type: 'http',
    tags: ['HTTP工具'],
    icon: '#409eff',
    httpConfig: defaultHttpConfig(),
    inputs: [],
    enabled: true,
    outputMapping: '',
    createdAt: '',
  }
}

const form = reactive<Tool>(defaultForm())

// ==================== Schema 文本 ====================

const inputSchemaText = ref('')
const outputSchemaText = ref('')

/** 从 Body JSON 文本中自动提取 ${xxx} 或 {{xxx}} 变量，同步到入参表单 */
function extractVarsFromBody() {
  const body = form.httpConfig?.body || ''
  // 同时匹配 ${xxx} 和 {{xxx}}
  const matches = body.matchAll(/\$\{([^}]+)\}|(?<!\$)\{\{([^}]+)\}\}/g)
  const vars = new Set<string>()
  for (const m of matches) {
    const name = m[1] || m[2]
    if (name) vars.add(name)
  }
  if (vars.size === 0) {
    // Body 清空时不同步，保留已有
    return
  }
  const existingNames = new Set(form.inputs.map((i) => i.name))
  for (const v of vars) {
    if (!existingNames.has(v)) {
      form.inputs.push({ name: v, description: '', type: 'string', isToolParam: true, required: true })
    }
  }
  // 同步到 Schema 文本
  inputSchemaText.value = JSON.stringify(inputsToSchema(form.inputs.filter((i) => i.name)), null, 2)
}

/** 将 Body 中的 {{xxx}} 归一化为 ${xxx} */
function normalizeBodyTemplate(text: string): string {
  return text.replace(/\{\{([^}]+)\}\}/g, '$${$1}')
}

// ==================== Props 同步 ====================

function syncFromProps() {
  Object.assign(form, defaultForm())
  inputSchemaText.value = ''
  outputSchemaText.value = ''

  if (props.initialData) {
    form.id = props.initialData.id
    form.name = props.initialData.name
    form.identifier = props.initialData.identifier
    form.description = props.initialData.description
    form.type = props.initialData.type
    form.tags = props.initialData.tags ? [...props.initialData.tags] : []
    form.icon = props.initialData.icon
    form.enabled = props.initialData.enabled === true || props.initialData.enabled === 1

    // 入参：兼容多种数据格式
    const rawInputs = props.initialData.inputs as any
    if (props.initialData.inputSchema?.properties) {
      // 标准 inputSchema 优先
      inputSchemaText.value = JSON.stringify(props.initialData.inputSchema, null, 2)
      form.inputs = schemaToInputs(props.initialData.inputSchema)
    } else if (rawInputs && typeof rawInputs === 'object' && rawInputs.properties) {
      // 后端返回的 inputs 本身就是 JSON Schema 对象
      inputSchemaText.value = JSON.stringify(rawInputs, null, 2)
      form.inputs = schemaToInputs(rawInputs)
    } else if (Array.isArray(rawInputs) && rawInputs.length) {
      // 旧版前端格式：ToolInputParam[] 数组
      form.inputs = rawInputs.map((i: any) => ({ ...i }))
      inputSchemaText.value = JSON.stringify(inputsToSchema(form.inputs.filter((i: any) => i.name)), null, 2)
    }

    // 出参：兼容 outputs / outputSchema
    const rawOutputs = props.initialData.outputs as any
    if (props.initialData.outputSchema) {
      outputSchemaText.value = JSON.stringify(props.initialData.outputSchema, null, 2)
    } else if (rawOutputs && typeof rawOutputs === 'object' && rawOutputs.properties) {
      outputSchemaText.value = JSON.stringify(rawOutputs, null, 2)
    }

    // HTTP 配置
    if (props.initialData.httpConfig) {
      const h = props.initialData.httpConfig
      form.httpConfig = {
        method: h.method,
        url: h.url,
        authType: h.authType,
        authValue: h.authValue || '',
        params: h.params.map((p) => ({ ...p })),
        bodyType: h.bodyType,
        body: normalizeBodyTemplate(h.body || ''),
        headers: h.headers.map((hd) => ({ ...hd })),
      }
    } else {
      form.httpConfig = defaultHttpConfig()
    }
  }
}

watch(() => props.initialData, () => syncFromProps(), { immediate: true })

// ==================== 计算属性 ====================

const paramVariables = computed(() =>
  form.inputs.filter((i) => i.name).map((i) => `\${${i.name}}`),
)

/** 可用工具类型：内置工具 + HTTP 工具 */
const toolTypeOptions = computed(() =>
  TOOL_CATEGORIES.filter((t) => t.value !== 'knowledge'),
)

// ==================== Body 编辑 ====================

function onBodyChange() {
  // 将 {{xxx}} 归一化为 ${xxx}
  form.httpConfig!.body = normalizeBodyTemplate(form.httpConfig!.body)
  extractVarsFromBody()
}

// ==================== 变量插入 ====================

function insertVariable(target: 'body' | 'paramValue', param: ToolInputParam, paramIndex?: number) {
  if (!param.name) return
  const insertion = `\${${param.name}}`
  if (target === 'body') {
    form.httpConfig!.body = form.httpConfig!.body ? form.httpConfig!.body + insertion : insertion
    extractVarsFromBody()
  } else if (target === 'paramValue' && paramIndex !== undefined) {
    const row = form.httpConfig!.params[paramIndex]
    if (row) row.value = row.value ? row.value + insertion : insertion
  }
}

// ==================== Params / Headers 管理 ====================

function addParam() { form.httpConfig!.params.push({ key: '', value: '' }) }
function removeParam(i: number) { form.httpConfig!.params.splice(i, 1) }
function addHeader() { form.httpConfig!.headers.push({ key: '', value: '' }) }
function removeHeader(i: number) { form.httpConfig!.headers.splice(i, 1) }

// ==================== 提交 ====================

function validate(): boolean {
  if (!form.name.trim()) { ElMessage.warning('请输入工具名称'); return false }
  if (!form.httpConfig?.url.trim()) { ElMessage.warning('请输入请求地址'); return false }
  return true
}

function parseSchema(text: string) {
  if (!text.trim()) return undefined
  try {
    const schema = JSON.parse(text)
    if (schema.properties) return schema
    return undefined
  } catch { return undefined }
}

function handleSubmit() {
  if (!validate()) return
  if (!form.identifier.trim()) {
    form.identifier = form.name.toLowerCase().replace(/\s+/g, '_')
  }
  const payload: Tool = {
    ...form,
    enabled: form.enabled === true || form.enabled === 1,
    tags: [...form.tags],
    inputs: form.inputs.map((i) => ({ ...i })),
    inputSchema: parseSchema(inputSchemaText.value),
    outputSchema: parseSchema(outputSchemaText.value),
    httpConfig: {
          ...form.httpConfig!,
          params: form.httpConfig!.params.map((p) => ({ ...p })),
          headers: form.httpConfig!.headers.map((h) => ({ ...h })),
        },
  }
  emit('submit', payload)
}

// ==================== cURL 导入 ====================

const curlVisible = ref(false)
const curlText = ref('')
const curlError = ref('')

function handleCurlImport() {
  curlText.value = ''
  curlError.value = ''
  curlVisible.value = true
}

/** 解析 cURL 命令字符串 */
function parseCurl(input: string): {
  method: string
  url: string
  headers: { key: string; value: string }[]
  body: string
  authType: string
  authValue: string
} | null {
  const result = {
    method: 'GET',
    url: '',
    headers: [] as { key: string; value: string }[],
    body: '',
    authType: 'none' as string,
    authValue: '',
  }

  // 去掉换行续行符（行尾 \）
  let str = input.replace(/\\\r?\n/g, ' ').trim()
  if (!str.startsWith('curl ')) return null

  // 去掉开头的 curl
  str = str.slice(5).trim()

  // 简易 tokenizer：处理单引号、双引号、转义
  function tokenize(s: string): string[] {
    const tokens: string[] = []
    let i = 0
    const current: string[] = []
    function push() {
      if (current.length) {
        tokens.push(current.join(''))
        current.length = 0
      }
    }
    while (i < s.length) {
      const ch = s[i]
      if (ch === "'") {
        // 单引号字符串：直到下一个单引号
        i++
        const start = i
        while (i < s.length && s[i] !== "'") i++
        current.push(s.slice(start, i))
        if (i < s.length) i++ // 跳过结束引号
      } else if (ch === '"') {
        // 双引号字符串：支持转义
        i++
        while (i < s.length) {
          if (s[i] === '\\' && i + 1 < s.length) {
            current.push(s[i + 1])
            i += 2
          } else if (s[i] === '"') {
            i++
            break
          } else {
            current.push(s[i])
            i++
          }
        }
      } else if (ch === ' ' || ch === '\t') {
        push()
        i++
      } else {
        current.push(ch)
        i++
      }
    }
    push()
    return tokens
  }

  const tokens = tokenize(str)
  let urlFound = false

  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i]

    if (t === '-X' || t === '--request') {
      if (i + 1 < tokens.length) {
        result.method = tokens[++i].toUpperCase()
      }
    } else if (t === '-H' || t === '--header') {
      if (i + 1 < tokens.length) {
        const header = tokens[++i]
        const colonIdx = header.indexOf(':')
        if (colonIdx > 0) {
          const key = header.slice(0, colonIdx).trim()
          let val = header.slice(colonIdx + 1).trim()
          // 处理 Authorization
          if (key.toLowerCase() === 'authorization') {
            if (val.toLowerCase().startsWith('bearer ')) {
              result.authType = 'bearer'
              result.authValue = val.slice(7).trim()
            } else if (val.toLowerCase().startsWith('basic ')) {
              result.authType = 'apiKey'
              result.authValue = val
            } else {
              result.authType = 'apiKey'
              result.authValue = val
            }
          } else if (key.toLowerCase() === 'content-type') {
            // 根据 Content-Type 推断 bodyType（不存 bodyType 到 header）
            if (val.includes('application/json')) {
              // bodyType 将隐式设为 json，不存 headers
            } else if (val.includes('application/x-www-form-urlencoded')) {
              // bodyType 设为 form-urlencoded
            }
            result.headers.push({ key, value: val })
          } else {
            result.headers.push({ key, value: val })
          }
        } else {
          result.headers.push({ key: header, value: '' })
        }
      }
    } else if (t === '-d' || t === '--data' || t === '--data-raw' || t === '--data-binary') {
      if (i + 1 < tokens.length) {
        result.body = tokens[++i]
        if (result.method === 'GET') result.method = 'POST'
      }
    } else if (t.startsWith('--') && t !== '--data' && t !== '--data-raw' && t !== '--data-binary' && t !== '--request' && t !== '--header') {
      // 跳过未知的 -- 选项及其值
      // --compressed, --insecure, --location, --silent 等无值
      const noVal = ['--compressed', '--insecure', '--location', '--silent', '--verbose', '-v']
      if (!noVal.includes(t) && !t.startsWith('--data') && i + 1 < tokens.length && !tokens[i + 1].startsWith('-')) {
        i++ // 跳过选项的值
      }
    } else if (t.startsWith('-') && t !== '-X' && t !== '-H' && t !== '-d') {
      // 跳过其他短选项
      const noValShort = ['-k', '-s', '-v', '-L', '-l', '-i', '-I', '-O', '-o', '-C']
      if (!noValShort.includes(t) && !t.startsWith('--data') && i + 1 < tokens.length && !tokens[i + 1].startsWith('-')) {
        i++
      }
    } else if (!t.startsWith('-') && !urlFound) {
      // 非选项 token，且还没找到 URL
      // 去掉引号包裹
      result.url = t.replace(/^['"]|['"]$/g, '')
      urlFound = true
    }
  }

  return result
}

function confirmCurlImport() {
  const parsed = parseCurl(curlText.value.trim())
  if (!parsed) {
    curlError.value = '无法解析该 cURL 命令，请确认以 curl 开头'
    return
  }
  if (!parsed.url) {
    curlError.value = '未找到请求地址'
    return
  }

  // 填充表单
  form.httpConfig!.method = parsed.method as any
  form.httpConfig!.url = parsed.url
  form.httpConfig!.authType = parsed.authType as any
  form.httpConfig!.authValue = parsed.authValue
  form.httpConfig!.headers = parsed.headers.length
    ? parsed.headers.map((h) => ({ key: h.key, value: h.value }))
    : [{ key: '', value: '' }]

  if (parsed.body) {
    // 尝试格式化 JSON body
    try {
      form.httpConfig!.body = JSON.stringify(JSON.parse(parsed.body), null, 2)
      form.httpConfig!.bodyType = 'json'
    } catch {
      form.httpConfig!.body = parsed.body
      form.httpConfig!.bodyType = 'raw-text'
    }
  } else {
    form.httpConfig!.body = ''
    form.httpConfig!.bodyType = 'none'
  }

  // 提取 Query Params 中的变量
  const urlObj = new URL(parsed.url)
  const searchParams = urlObj.searchParams
  const queryVars: { key: string; value: string }[] = []
  searchParams.forEach((val, key) => {
    queryVars.push({ key, value: val })
  })
  if (queryVars.length) {
    form.httpConfig!.params = queryVars
    // 从 URL 中移除 query string
    form.httpConfig!.url = parsed.url.split('?')[0]
  } else {
    form.httpConfig!.params = [{ key: '', value: '' }]
  }

  curlVisible.value = false
  ElMessage.success('cURL 导入成功')
}

// ==================== 测试功能 ====================

const testVisible = ref(false)
</script>

<template>
  <div class="tool-form-wrap">
    <!-- ====== 第一区：工具信息 ====== -->
    <div class="form-section section-info">
      <el-form label-position="top">
        <div class="info-row">
          <el-form-item label="工具名称" required class="info-field-lg">
            <el-input v-model="form.name" placeholder="工具名称" />
          </el-form-item>
          <el-form-item label="工具标识" class="info-field-md">
            <el-input v-model="form.identifier" placeholder="留空自动生成" />
          </el-form-item>
          <el-form-item label="工具类型" class="info-field-sm">
            <el-radio-group v-model="form.type">
              <el-radio-button v-for="t in toolTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="启用" class="info-field-xs">
            <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
          </el-form-item>
        </div>
        <el-form-item label="工具描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="工具描述（供 LLM 理解工具用途）" />
        </el-form-item>
      </el-form>
    </div>

    <!-- ====== 第二区：请求配置 ====== -->
    <div class="form-section">
      <div class="section-header">
        <span>请求配置</span>
        <el-button size="small" type="primary" plain @click="handleCurlImport">cURL 导入</el-button>
      </div>
      <div class="request-layout">
        <!-- 左列：Method + URL + Params + Headers -->
        <div class="request-left">
          <div class="request-config">
            <el-select v-model="form.httpConfig!.method" style="width: 110px">
              <el-option v-for="m in HTTP_METHOD_OPTIONS" :key="m" :label="m" :value="m" />
            </el-select>
            <el-input v-model="form.httpConfig!.url" placeholder="请求地址" style="flex: 1" />
          </div>
          <div class="auth-row">
            <span class="auth-label">鉴权：</span>
            <el-select v-model="form.httpConfig!.authType" size="small" style="width: 140px">
              <el-option v-for="a in AUTH_TYPE_OPTIONS" :key="a.value" :label="a.label" :value="a.value" />
            </el-select>
            <el-input
              v-if="form.httpConfig!.authType === 'apiKey'"
              v-model="form.httpConfig!.authValue"
              size="small"
              placeholder="API Key 值"
              style="flex: 1"
            />
            <el-input
              v-else-if="form.httpConfig!.authType === 'bearer'"
              v-model="form.httpConfig!.authValue"
              size="small"
              placeholder="Bearer Token"
              style="flex: 1"
            />
            <el-input
              v-else-if="form.httpConfig!.authType === 'oauth2'"
              v-model="form.httpConfig!.authValue"
              size="small"
              placeholder="OAuth2 Access Token"
              style="flex: 1"
            />
          </div>
          <!-- Params -->
          <div class="subsection-header">
            <span>Query Params</span>
            <el-button size="small" link @click="addParam"><el-icon><Plus /></el-icon></el-button>
          </div>
          <div v-for="(row, idx) in form.httpConfig!.params" :key="idx" class="kv-row">
            <el-input v-model="row.key" size="small" placeholder="key" style="flex: 1" />
            <div class="kv-value-cell">
              <el-input v-model="row.value" size="small" placeholder="value，可用 ${参数名}" style="flex: 1" />
              <el-popover v-if="paramVariables.length" placement="bottom" :width="220" trigger="click">
                <template #reference>
                  <el-icon class="var-btn-icon" title="插入变量"><Promotion /></el-icon>
                </template>
                <div class="var-popover">
                  <el-tag v-for="v in paramVariables" :key="v" size="small" type="info" class="var-tag"
                    @click="insertVariable('paramValue', form.inputs.find(i => v === '${' + i.name + '}') || form.inputs[0], idx)">
                    {{ v }}
                  </el-tag>
                </div>
              </el-popover>
            </div>
            <el-button link type="danger" size="small" @click="removeParam(idx)"><el-icon><Delete /></el-icon></el-button>
          </div>
          <!-- Headers -->
          <div class="subsection-header">
            <span>Headers</span>
            <el-button size="small" link @click="addHeader"><el-icon><Plus /></el-icon></el-button>
          </div>
          <div v-for="(row, idx) in form.httpConfig!.headers" :key="idx" class="kv-row">
            <el-input v-model="row.key" size="small" placeholder="key" style="flex: 1" />
            <el-input v-model="row.value" size="small" placeholder="value" style="flex: 1" />
            <el-button link type="danger" size="small" @click="removeHeader(idx)"><el-icon><Delete /></el-icon></el-button>
          </div>
        </div>
        <!-- 右列：Body -->
        <div class="request-right">
          <div class="subsection-header">
            <span>Body（入参 JSON 模板）</span>
            <div class="body-type-inline">
              <el-radio-group v-model="form.httpConfig!.bodyType" size="small">
                <el-radio-button v-for="b in BODY_TYPE_OPTIONS" :key="b.value" :value="b.value">{{ b.label }}</el-radio-button>
              </el-radio-group>
            </div>
          </div>
          <el-input
            v-model="form.httpConfig!.body"
            type="textarea"
            :rows="14"
            :disabled="form.httpConfig!.bodyType === 'none'"
            placeholder='在此编写请求体 JSON，使用 ${参数名} 定义入参&#10;&#10;示例：&#10;{&#10;  "city": "${city}",&#10;  "unit": "${unit}"&#10;}&#10;&#10;系统会自动从 ${xxx} 提取变量生成入参 Schema'
            class="body-textarea"
            @input="onBodyChange"
          />
        </div>
      </div>
    </div>

    <!-- ====== 第三区：入参 / 出参 Schema（并排） ====== -->
    <div class="schema-row">
      <div class="schema-col">
        <div class="schema-col-header">
          <span>入参 Schema</span>
          <el-tag v-if="paramVariables.length" size="small" type="info">{{ paramVariables.length }} 个变量</el-tag>
        </div>
	        <el-input
	          v-model="inputSchemaText"
	          type="textarea"
	          :rows="8"
	          placeholder='{"type":"object","properties":{"city":{"type":"string","description":"城市名称"}},"required":["city"]}'
	          class="schema-textarea"
	        />
        <div v-if="!form.inputs.length" class="empty-schema-hint">
          在 Body 中使用 <code>${'{参数名}'}</code> 自动生成
        </div>
      </div>
      <div class="schema-col">
        <div class="schema-col-header">
          <span>出参 Schema（可选）</span>
        </div>
	        <el-input
	          v-model="outputSchemaText"
	          type="textarea"
	          :rows="8"
	          placeholder='{"type":"object","properties":{"temperature":{"type":"number"},"weather":{"type":"string"}}}'
	          class="schema-textarea"
	        />
      </div>
    </div>

    <!-- ====== 底部固定操作栏 ====== -->
    <div class="form-footer-bar">
      <div class="footer-left">
        <el-button @click="emit('cancel')">取消</el-button>
      </div>
      <div class="footer-right">
        <el-button @click="testVisible = true">
          <el-icon><VideoPlay /></el-icon>测试
        </el-button>
        <el-button type="primary" @click="handleSubmit">{{ mode === 'create' ? '创建' : '保存' }}</el-button>
      </div>
    </div>

    <!-- ====== cURL 导入弹窗 ====== -->
    <el-dialog v-model="curlVisible" title="cURL 导入" width="640px" top="10vh" destroy-on-close>
      <el-alert v-if="curlError" type="error" :description="curlError" show-icon closable class="curl-error-alert" />
      <el-input
        v-model="curlText"
        type="textarea"
        :rows="12"
        placeholder="在此粘贴 cURL 命令，例如：&#10;curl -X POST https://api.example.com/v1/chat \&#10;  -H 'Content-Type: application/json' \&#10;  -H 'Authorization: Bearer sk-xxx' \&#10;  -d '{&#10;    &quot;prompt&quot;: &quot;hello&quot;&#10;  }'"
        class="curl-textarea"
      />
      <template #footer>
        <el-button @click="curlVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCurlImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- ====== 测试工具弹窗 ====== -->
    <ToolTestDialog
      v-model:visible="testVisible"
      :http-config="form.httpConfig!"
      :inputs="form.inputs"
      :input-schema="parseSchema(inputSchemaText)"
      :output-schema="parseSchema(outputSchemaText)"
    />
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.tool-form-wrap {
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  padding: $spacing-md $spacing-lg;
}

/* ---- 通用 section ---- */
.form-section {
  margin-bottom: $spacing-base;
  &:last-of-type { margin-bottom: 0; }
  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-xs;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
  }
}

/* ---- 工具信息 ---- */
.section-info {
  .info-row {
    display: flex;
    gap: $spacing-md;
    align-items: flex-start;
  }
  .info-field-lg { flex: 3; min-width: 0; }
  .info-field-md { flex: 2; min-width: 0; }
  .info-field-sm { flex: 2; min-width: 0; }
  .info-field-xs { flex: 0 0 80px; }
  :deep(.el-form-item) { margin-bottom: $spacing-sm; }
}

/* ---- 请求配置 ---- */
.request-layout {
  display: flex;
  gap: $spacing-lg;
}
.request-left { flex: 1; min-width: 0; }
.request-right { flex: 1; min-width: 0; }

.request-config {
  display: flex;
  gap: $spacing-xs;
  margin-bottom: $spacing-xs;
}

.auth-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: $spacing-sm;
  .auth-label { font-size: 12px; color: var(--el-text-color-secondary); }
}

.subsection-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 4px;
  margin-top: 6px;
  color: $text-primary;
  &:first-child { margin-top: 0; }
}

.kv-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 2px;
}
.kv-value-cell {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
}

.var-btn-icon {
  cursor: pointer;
  color: var(--el-color-primary);
  flex-shrink: 0;
  &:hover { color: var(--el-color-primary-light-3); }
}

.var-popover { max-height: 200px; overflow-y: auto; }
.var-tag { cursor: default; margin: 2px 4px 2px 0; }

.body-type-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.body-textarea {
  :deep(.el-textarea__inner) {
    font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
    font-size: 12px;
    line-height: 1.5;
  }
}

/* ---- Schema 并排 ---- */
.schema-row {
  display: flex;
  gap: $spacing-lg;
  margin-bottom: $spacing-base;
}
.schema-col {
  flex: 1;
  min-width: 0;
}
.schema-col-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.schema-textarea {
  :deep(.el-textarea__inner) {
    font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
    font-size: 12px;
    line-height: 1.5;
  }
}

.empty-schema-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  code {
    background: var(--el-fill-color);
    padding: 1px 4px;
    border-radius: 3px;
    font-size: 12px;
    color: var(--el-color-primary);
  }
}

/* ---- cURL 导入 ---- */
.curl-error-alert {
  margin-bottom: $spacing-sm;
}
.curl-textarea {
  :deep(.el-textarea__inner) {
    font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
    font-size: 12px;
    line-height: 1.5;
  }
}

/* ---- 底部固定栏 ---- */
.form-footer-bar {
  position: sticky;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  margin-top: $spacing-base;
  background: #fff;
  border-top: 1px solid $border-lighter;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}
.footer-left,
.footer-right {
  display: flex;
  gap: $spacing-sm;
}
</style>
