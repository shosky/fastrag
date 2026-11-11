<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import WidgetCardRenderer from '@/views/conversation/components/widget/WidgetCardRenderer.vue'
import { registry } from '@/views/conversation/components/widget/registry'
import { parseTemplate, toPreviewSnapshot, type WidgetNode } from '@/utils/widget/snapshot'
import { toWidgetSaveRequest, type WidgetFormDraft } from '@/api/widget'

// ===========================================================================
// WidgetForm —— 新建页/编辑页共享表单（PAGE-202/203）
//
// 结构：基础信息（名称*/描述/图标）+ 三件套三 tab JSON 文本域 + 行级错误区 +
// 预览面板（仅编辑页，showPreview；toPreviewSnapshot → WidgetCardRenderer preview 变体）。
//
// STATE-205 校验失败行级：保存失败 message 按 \n 拆行落对应 tab（关键词映射），
// 点击错误聚焦 tab，不弹窗；本地预校验（名称非空 / 三件套 JSON 合法 / 白名单外组件）
// 前置拦截减少一次往返（MOD ## seam 深化机会）。
// STATE-206 预览态：编辑内容派生 + 手动刷新；sys.submit 禁用 + 提示文案（渲染器内置）。
// ===========================================================================

const props = withDefaults(
  defineProps<{
    mode: 'create' | 'edit'
    /** 编辑页回显草稿（toWidgetDraft 产出）；新建页忽略 */
    initial?: WidgetFormDraft | null
    saving?: boolean
    /** 预览面板开关（仅编辑页启用） */
    showPreview?: boolean
  }>(),
  { initial: null, saving: false, showPreview: false }
)

const emit = defineEmits<{
  (e: 'submit', draft: WidgetFormDraft): void
  (e: 'cancel'): void
}>()

// ── 三件套初始骨架（预填最小合法示例，可清空改写——PAGE-202 布局）──
// 仅组件内部使用（form 初始化兜底），无外部消费者，不导出（<script setup> 禁 ES module export）
const EMPTY_DRAFT: WidgetFormDraft = {
  name: '',
  description: '',
  icon: '',
  template: JSON.stringify(
    {
      type: 'Card',
      props: {},
      children: [
        { type: 'Title', props: { text: '卡片标题', level: 2 } },
        { type: 'Text', props: { value: '{{message}}' } },
        {
          type: 'Form',
          props: {},
          children: [
            { type: 'Input', props: { name: 'reply', label: '回复内容', placeholder: '请输入' } },
          ],
        },
      ],
    },
    null,
    2
  ),
  schemaJson: JSON.stringify({ message: 'string' }, null, 2),
  defaultJson: JSON.stringify({ message: '你好，这是一条示例数据' }, null, 2),
}

const form = reactive<WidgetFormDraft>({
  name: props.initial?.name ?? '',
  description: props.initial?.description ?? '',
  icon: props.initial?.icon ?? '',
  template: props.initial?.template ?? EMPTY_DRAFT.template,
  schemaJson: props.initial?.schemaJson ?? EMPTY_DRAFT.schemaJson,
  defaultJson: props.initial?.defaultJson ?? EMPTY_DRAFT.defaultJson,
})

// ── 三件套三 tab ──
type TripleTab = 'template' | 'schema' | 'default'
const activeTab = ref<TripleTab>('template')
const templateAreaRef = ref<InstanceType<typeof import('element-plus').ElInput> | null>(null)
const schemaAreaRef = ref<InstanceType<typeof import('element-plus').ElInput> | null>(null)
const defaultAreaRef = ref<InstanceType<typeof import('element-plus').ElInput> | null>(null)

function areaRefOf(tab: TripleTab) {
  return tab === 'template' ? templateAreaRef : tab === 'schema' ? schemaAreaRef : defaultAreaRef
}

// ── 行级错误区（STATE-205）──
export interface WidgetFormLineError {
  tab: TripleTab | 'basic'
  message: string
}

const lineErrors = ref<WidgetFormLineError[]>([])

/** 保存失败 message 按 \n 拆行，关键词映射到对应 tab（后端 ERR-WG-SCHEMA-INVALID 契约） */
function setErrorFromApi(message: string) {
  const lines = String(message || '')
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
  lineErrors.value = lines.length
    ? lines.map((l) => ({ tab: tabOfLine(l), message: l }))
    : [{ tab: 'template', message: String(message || '保存失败') }]
}

function tabOfLine(line: string): TripleTab | 'basic' {
  if (/名称|name/i.test(line)) return 'basic'
  if (/数据结构|schema|变量/i.test(line)) return 'schema'
  if (/默认值|default/i.test(line)) return 'default'
  return 'template' // 模板相关/未知行落模板 tab
}

async function focusError(err: WidgetFormLineError) {
  if (err.tab === 'basic') return
  activeTab.value = err.tab
  await nextTick()
  areaRefOf(err.tab).value?.focus()
}

function clearErrors() {
  lineErrors.value = []
}

// ── 本地预校验（名称 / JSON 合法 / 组件白名单——双端集合由 registry 守护测试钉住）──

/** 递归收集模板树组件名（坏 JSON 由 parse 判定，此处收 collect） */
function collectTypes(node: WidgetNode, acc: Set<string>) {
  acc.add(node.type)
  for (const c of Array.isArray(node.children) ? node.children : []) collectTypes(c, acc)
}

function validateLocal(): WidgetFormLineError[] {
  const errors: WidgetFormLineError[] = []
  if (!form.name.trim()) errors.push({ tab: 'basic', message: '名称必填，不能为空白' })

  const triples: Array<[TripleTab, string, string]> = [
    ['template', form.template, '模板'],
    ['schema', form.schemaJson, '数据结构'],
    ['default', form.defaultJson, '默认值'],
  ]
  for (const [tab, raw, label] of triples) {
    if (!raw.trim()) continue
    try {
      JSON.parse(raw)
    } catch {
      errors.push({ tab, message: `${label}不是合法 JSON` })
    }
  }
  // 白名单外组件（前端半边预校验；后端三档校验为最终裁决）
  const root = parseTemplate(form.template)
  if (root) {
    const types = new Set<string>()
    collectTypes(root, types)
    for (const t of types) {
      if (!registry[t]) errors.push({ tab: 'template', message: `模板包含白名单外组件「${t}」` })
    }
  }
  return errors
}

// ── 格式化（本地 JSON pretty，不调接口）──
const formatHint = ref('')

function formatCurrentTab() {
  const tab = activeTab.value
  const area = areaRefOf(tab)
  const raw = tab === 'template' ? form.template : tab === 'schema' ? form.schemaJson : form.defaultJson
  try {
    const pretty = JSON.stringify(JSON.parse(raw), null, 2)
    if (tab === 'template') form.template = pretty
    else if (tab === 'schema') form.schemaJson = pretty
    else form.defaultJson = pretty
    formatHint.value = ''
  } catch {
    formatHint.value = 'JSON 解析失败，无法格式化'
    area.value?.focus()
  }
}

// ── 预览面板（仅编辑页；STATE-206 编辑内容派生 + 手动刷新）──
const previewSnapshot = ref<ReturnType<typeof toPreviewSnapshot>>(null)
const previewTick = ref(0)

function refreshPreview() {
  previewSnapshot.value = toPreviewSnapshot(form.template, form.defaultJson)
  previewTick.value++
}

defineExpose({ setErrorFromApi, clearErrors, validateLocal, refreshPreview })

/** 提交：本地预校验通过后向页面层 emit（API 调用归页面，错误经 setErrorFromApi 回落） */
function handleSubmit() {
  const local = validateLocal()
  if (local.length) {
    lineErrors.value = local
    return
  }
  emit('submit', toWidgetSaveRequest({ ...form }))
}
</script>

<template>
  <div class="widget-form" :class="`is-${mode}`">
    <!-- 基础信息 -->
    <el-form label-width="88px" label-position="right" class="widget-form__basic">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="Widget 名称（必填）" maxlength="50" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" placeholder="一句话描述卡片用途" maxlength="200" />
      </el-form-item>
      <el-form-item label="图标">
        <el-input v-model="form.icon" placeholder="Element Plus 图标名，留空回退 Postcard" maxlength="50" />
      </el-form-item>
    </el-form>

    <!-- 三件套三 tab JSON 文本域 -->
    <el-tabs v-model="activeTab" class="widget-form__tabs">
      <el-tab-pane label="模板" name="template">
        <div class="widget-form__editor-head">
          <span class="widget-form__editor-tip" v-pre>组件树 JSON（{type,props,children[]}，字符串 prop 支持 {{var}} 插值）</span>
          <el-button size="small" @click="formatCurrentTab">格式化</el-button>
        </div>
        <el-input
          ref="templateAreaRef"
          v-model="form.template"
          type="textarea"
          :rows="14"
          spellcheck="false"
          class="widget-form__json"
        />
      </el-tab-pane>
      <el-tab-pane label="数据结构" name="schema">
        <div class="widget-form__editor-head">
          <span class="widget-form__editor-tip" v-pre>变量声明 JSON（模板 {{var}} 的变量集合）</span>
          <el-button size="small" @click="formatCurrentTab">格式化</el-button>
        </div>
        <el-input
          ref="schemaAreaRef"
          v-model="form.schemaJson"
          type="textarea"
          :rows="14"
          spellcheck="false"
          class="widget-form__json"
        />
      </el-tab-pane>
      <el-tab-pane label="默认值" name="default">
        <div class="widget-form__editor-head">
          <span class="widget-form__editor-tip">占位数据 JSON（default 先填充、data 覆盖的合并源）</span>
          <el-button size="small" @click="formatCurrentTab">格式化</el-button>
        </div>
        <el-input
          ref="defaultAreaRef"
          v-model="form.defaultJson"
          type="textarea"
          :rows="14"
          spellcheck="false"
          class="widget-form__json"
        />
      </el-tab-pane>
    </el-tabs>
    <div v-if="formatHint" class="widget-form__format-hint">{{ formatHint }}</div>

    <!-- 行级错误区（STATE-205：落对应 tab，点击聚焦，不弹窗） -->
    <div v-if="lineErrors.length" class="widget-form__errors" role="alert">
      <div class="widget-form__errors-title">保存被拦截（{{ lineErrors.length }} 项）：</div>
      <button
        v-for="(err, i) in lineErrors"
        :key="i"
        type="button"
        class="widget-form__error-line"
        @click="focusError(err)"
      >
        {{ err.message }}
      </button>
    </div>

    <!-- 预览面板（仅编辑页；PAGE-203 桌面双栏右侧，≤768 折叠至编辑区下方） -->
    <div v-if="showPreview" class="widget-form__preview">
      <div class="widget-form__preview-head">
        <span class="widget-form__preview-title">预览</span>
        <el-button size="small" @click="refreshPreview">刷新预览</el-button>
      </div>
      <div class="widget-form__preview-body">
        <WidgetCardRenderer
          v-if="previewSnapshot"
          :key="previewTick"
          :snapshot="previewSnapshot"
          :interactive="false"
          preview
        />
        <div v-else class="widget-form__preview-empty">
          {{ previewTick === 0 ? '点击「刷新预览」按当前模板与默认值渲染' : '模板暂不可渲染，请检查 JSON' }}
        </div>
      </div>
    </div>

    <!-- 操作行 -->
    <div class="widget-form__actions">
      <el-button @click="emit('cancel')">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.widget-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 1080px;
}

.widget-form__basic {
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: var(--el-border-radius-base, 4px);
  padding: 16px 16px 0;
}

.widget-form__editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.widget-form__editor-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
.widget-form__json :deep(textarea) {
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
}

.widget-form__format-hint {
  font-size: 12px;
  color: var(--el-color-warning, #e6a23c);
}

.widget-form__errors {
  border: 1px solid var(--el-color-danger-light-5, #fde2e2);
  background: var(--el-color-danger-light-9, #fef0f0);
  border-radius: var(--el-border-radius-base, 4px);
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.widget-form__errors-title {
  font-size: 12px;
  color: var(--el-color-danger, #f56c6c);
  font-weight: 600;
}
.widget-form__error-line {
  border: none;
  background: transparent;
  text-align: left;
  font-size: 12px;
  color: var(--el-color-danger, #f56c6c);
  cursor: pointer;
  padding: 2px 0;
  line-height: 1.5;
  &:hover { text-decoration: underline; }
}

// 预览面板：桌面与编辑区并排的简化实现（本组件内纵向排布，页面层 768 断点折叠）
.widget-form__preview {
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: var(--el-border-radius-base, 4px);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.widget-form__preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.widget-form__preview-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.widget-form__preview-body {
  min-height: 80px;
}
.widget-form__preview-empty {
  border: 1px dashed var(--el-border-color-darker, #dcdfe6);
  border-radius: var(--el-border-radius-base, 4px);
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  padding: 24px 10px;
  text-align: center;
}

.widget-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
