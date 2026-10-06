<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { Promotion, Close } from '@element-plus/icons-vue'
import * as api from '@/api'
import KbScopePicker from './KbScopePicker.vue'
import CapabilityPicker from './CapabilityPicker.vue'
import type { PresetApp } from '@/types/conversation'

// ===========================================================================
// 输入区：应用下拉 + 预设 chip + KB 多选 popover + 能力 popover + 模型下拉 + 文本框 + 发送/停止
//
// - kbIds / modelId / appId 经 defineModel 双向绑定到页面级 composerState（单一事实源）；
//   组件内不持有范围状态，只做渲染与用户改动上报。
// - 应用下拉与预设 chip 双向同步（同源 composerState.appId）：下拉选中=挂预设
//   （chip 随显），选「直聊（不挂应用）」/chip × 移除=清 appId 回直聊；数据源 getApps
//   （零新增后端调用）。null（页面态直聊）↔ ''（下拉值域）经 selectedAppId 归一。
// - 挂预设（presetApp 非空）时 KB 多选禁用并提示「知识库范围由应用决定」（D02），
//   能力 popover 隐藏（能力走应用绑定）；预设 chip 可移除转直聊（D02）。
// - Enter 发送 / Shift+Enter 换行（INFERRED-3，沿 runtime 现状）；流式期间禁重复提交。
// ===========================================================================

const props = defineProps<{
  disabled?: boolean
  presetApp?: PresetApp | null
}>()

const emit = defineEmits<{
  (e: 'submit', text: string): void
  (e: 'stop'): void
  (e: 'remove-preset'): void
}>()

const kbIds = defineModel<string[]>('kbIds', { default: () => [] })
const modelId = defineModel<string>('modelId', { default: '' })
const appIdModel = defineModel<string | null>('appId', { default: null })
// 直聊能力（B02 演进）：挂预设时隐藏（能力走应用绑定），仅直聊可选并随 stream 透传
const toolIds = defineModel<string[]>('toolIds', { default: () => [] })
const mcpIds = defineModel<string[]>('mcpIds', { default: () => [] })
const skillIds = defineModel<string[]>('skillIds', { default: () => [] })

// ── 应用下拉（控制栏左侧；'' 为直聊，null 页面态归一）──
const selectedAppId = computed<string>({
  get: () => appIdModel.value ?? '',
  set: (v) => { appIdModel.value = v || null },
})

const appOptions = ref<Array<{ id: string; name: string }>>([])
const appsLoading = ref(false)

async function loadApps() {
  appsLoading.value = true
  try {
    // 兼容 PageResult / 数组两种返回形态（沿 resolveAppName / loadApps 惯例）
    const res: any = await api.getApps()
    const raw = res?.list || res || []
    appOptions.value = (Array.isArray(raw) ? raw : []).map((a: any) => ({ id: a.id, name: a.name || a.id }))
  } catch {
    appOptions.value = []
  } finally {
    appsLoading.value = false
  }
}

const inputText = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

// ── 模型下拉（过滤 online，沿 BasicConfig.vue:55-71 惯例）──
const modelOptions = ref<Array<{ code: string; name: string }>>([])
const modelsLoading = ref(false)

async function loadModels() {
  modelsLoading.value = true
  try {
    // 兼容 PageResult / 数组两种返回形态（沿 runtime loadModels 惯例）
    const res: any = await api.getModels({ purpose: 'LLM' })
    const raw = res?.list || res || []
    modelOptions.value = (Array.isArray(raw) ? raw : [])
      .filter((m: any) => m.status === 'online')
      .map((m: any) => ({ code: m.code || m.id, name: m.name || m.code }))
  } catch {
    modelOptions.value = []
  } finally {
    modelsLoading.value = false
  }
}

const canSubmit = computed(() => !!inputText.value.trim() && !props.disabled)

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submit()
  }
}

/** 发送（Enter 发送时禁流式重复提交：disabled 即 isStreaming 期间直接短路） */
function submit() {
  if (!canSubmit.value) return
  const text = inputText.value.trim()
  inputText.value = ''
  emit('submit', text)
}

/** 编辑重发回填 / 空态引导聚焦 */
function setText(text: string) {
  inputText.value = text
}

function focus() {
  nextTick(() => textareaRef.value?.focus())
}

onMounted(() => {
  loadModels()
  loadApps()
})

defineExpose({ focus, setText })
</script>

<template>
  <div class="input-area-wrapper">
    <div class="input-container">
      <!-- 预设 chip（挂载时；移除 = 转直聊） -->
      <div v-if="presetApp" class="preset-strip">
        <el-tag closable type="primary" size="small" @close="emit('remove-preset')">
          预设：{{ presetApp.name }}
        </el-tag>
      </div>

      <div class="input-box" :class="{ focused: inputText.length > 0 }">
        <div class="input-toolbar">
          <!-- 应用下拉（控制栏左侧；首项直聊，选中应用=挂预设，chip 与下拉同源双向同步） -->
          <el-select
            v-model="selectedAppId"
            class="app-picker"
            size="small"
            :loading="appsLoading"
          >
            <el-option label="直聊（不挂应用）" value="" />
            <el-option v-for="a in appOptions" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
          <div class="toolbar-divider"></div>
          <template v-if="presetApp">
            <!-- 挂预设时 KB 多选禁用 + 提示「知识库范围由应用决定」（D02） -->
            <el-tooltip content="知识库范围由应用决定" placement="top">
              <KbScopePicker v-model="kbIds" disabled />
            </el-tooltip>
          </template>
          <el-tooltip v-else content="选择对话检索的知识库范围" placement="top">
            <KbScopePicker v-model="kbIds" />
          </el-tooltip>
          <!-- 能力 popover：仅直聊（挂预设时能力走应用绑定，整层隐藏） -->
          <CapabilityPicker
            v-if="!presetApp"
            v-model:tool-ids="toolIds"
            v-model:mcp-ids="mcpIds"
            v-model:skill-ids="skillIds"
          />
          <div class="toolbar-divider"></div>
          <el-select
            v-model="modelId"
            placeholder="模型"
            size="small"
            class="model-picker"
            :loading="modelsLoading"
            clearable
          >
            <template v-if="!modelsLoading && !modelOptions.length">
              <el-option disabled value="-" label="暂无在线模型，请先在模型管理配置" />
            </template>
            <el-option v-for="m in modelOptions" :key="m.code" :label="m.name" :value="m.code" />
          </el-select>
        </div>
        <textarea
          ref="textareaRef"
          v-model="inputText"
          class="user-input"
          placeholder="输入您的问题..."
          :disabled="disabled"
          rows="1"
          @keydown="handleKeydown"
        ></textarea>
        <div class="input-footer-bar">
          <div class="footer-left">
            <span class="scope-hint">
              {{ presetApp ? `按应用「${presetApp.name}」配置执行` : (kbIds.length ? `检索范围：${kbIds.length} 个知识库` : '不限知识库范围') }}
            </span>
          </div>
          <div class="footer-right">
            <button
              v-if="!disabled"
              class="send-button"
              :disabled="!inputText.trim()"
              title="发送"
              @click="submit"
            >
              <el-icon :size="16"><Promotion /></el-icon>
            </button>
            <button v-else class="send-button stop" title="停止生成" @click="emit('stop')">
              <el-icon :size="16"><Close /></el-icon>
            </button>
          </div>
        </div>
      </div>
      <div class="input-hint">
        <span>Enter 发送 · Shift+Enter 换行</span>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
// ── Color Palette（沿 runtime.vue 输入区配色）──
$primary: #046a82;
$primary-light: #3996ae;
$text-primary: #151616;
$text-secondary: #4c4d4d;
$text-muted: #979999;
$text-placeholder: #bdbfbf;
$border: #eef0f0;
$bg-page: #ffffff;
$bg-card: #ffffff;
$bg-hover: #f5f7f7;
$shadow-md: 0 2px 8px rgba(0,0,0,0.05);

.input-area-wrapper {
  flex-shrink: 0;
  padding: 0 24px 16px;
  background: $bg-page;
}
.input-container {
  width: 92%;
  max-width: 1200px;
  margin: 0 auto;
}

.preset-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.input-box {
  border: 1px solid $border;
  border-radius: 0.8rem;
  box-shadow: $shadow-md;
  background: $bg-card;
  padding: 0.5rem 0.75rem 0.4rem;
  transition: all 0.3s ease;
  &:focus-within { border-color: $primary-light; box-shadow: 0 0 0 2px rgba(4,106,130,0.08); }
}

.input-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
  flex-wrap: wrap; // 能力 chip 内联换行（直聊多选时不挤压模型下拉）
}

.toolbar-divider {
  width: 1px; height: 16px;
  background: $border; margin: 0 4px;
}

.app-picker {
  width: 150px;
  :deep(.el-select__wrapper) { border: none; box-shadow: none; min-height: 28px; padding: 0 4px; font-size: 12px; }
  :deep(.el-select__placeholder) { font-size: 12px; }
}

.model-picker {
  width: 130px;
  :deep(.el-select__wrapper) { border: none; box-shadow: none; min-height: 28px; padding: 0 4px; font-size: 12px; }
  :deep(.el-select__placeholder) { font-size: 12px; }
}

.user-input {
  width: 100%;
  min-height: 44px;
  max-height: 200px;
  padding: 0;
  border: none;
  background: transparent;
  color: $text-primary;
  font-size: 15px;
  line-height: 1.5;
  font-family: inherit;
  outline: none;
  resize: none;
  overflow-y: auto;
  &::placeholder { color: $text-placeholder; }
}

.input-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.footer-left {
  display: flex;
  align-items: center;
  .scope-hint { font-size: 12px; color: $text-muted; }
}

.send-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px; height: 32px;
  border: none; border-radius: 50%;
  background: $primary;
  color: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 6px rgba(4,106,130,0.15);
  &:hover { background: $primary-light; box-shadow: 0 4px 10px rgba(4,106,130,0.2); }
  &:disabled { opacity: 0.4; cursor: not-allowed; box-shadow: none; }
  &.stop { background: #e74c3c; &:hover { background: #c0392b; } }
}

.input-hint {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
  font-size: 12px;
  color: $text-muted;
  padding: 0 4px;
}
</style>
