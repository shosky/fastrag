<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{
  appInfo: {
    id: string
    name: string
    type: string
    description: string
    accessToken: string
  }
}>()

const appId = () => props.appInfo.id

const basicForm = ref({
  memoryRounds: 5,
  outputFormat: 'markdown',
  timeoutSeconds: 30,
  greeting: '',
  goodbyeMessage: '',
})
const advancedForm = ref({
  model: '',
  temperature: 0.7,
  maxTokens: 2048,
  topP: 1,
  frequencyPenalty: 0,
  presencePenalty: 0,
})
const promptForm = ref({
  prompt: '',
})
const summaryForm = ref({
  summaryThreshold: 8000,
  summaryPrompt: '',
})
const runControlForm = ref({
  maxTurns: 10,
  maxSteps: 15,
  retryTimes: 2,
})
const showAdvanced = ref(true)
const showPrompt = ref(true)
const showSummary = ref(false)
const showRunControl = ref(false)
const saving = ref(false)

// 模型列表
const modelOptions = ref<{ code: string; name: string }[]>([])
const modelsLoading = ref(false)

async function loadModels() {
  modelsLoading.value = true
  try {
    const list: any[] = await api.getModels({ purpose: 'LLM' })
    // 只显示在线模型
    modelOptions.value = (list || [])
      .filter((m: any) => m.status === 'online')
      .map((m: any) => ({
        code: m.code || m.id,
        name: m.name || m.code,
      }))
  } catch (e) {
    /* ignore */
  } finally {
    modelsLoading.value = false
  }
}

async function loadBasic() {
  try {
    const r: any = await api.getAppBasicConfig(appId())
    if (r) {
      // 只取基本配置字段，避免 advancedOptions JSON 字符串覆盖
      basicForm.value = {
        memoryRounds: r.memoryRounds ?? 5,
        outputFormat: r.outputFormat ?? 'markdown',
        timeoutSeconds: r.timeoutSeconds ?? 30,
        greeting: r.greeting ?? '',
        goodbyeMessage: r.goodbyeMessage ?? '',
      }
      // 从 advancedOptions JSON 字符串解析高级配置
      if (r.advancedOptions) {
        try {
          const adv = typeof r.advancedOptions === 'string'
            ? JSON.parse(r.advancedOptions)
            : r.advancedOptions
          if (adv) Object.assign(advancedForm.value, adv)
        } catch (e) { /* ignore parse error */ }
      }
    }
  } catch (e) { /* ignore */ }
}

async function loadConfig() {
  try {
    const r: any = await api.getAppConfig(appId())
    if (r) {
      promptForm.value.prompt = r.prompt ?? ''
      summaryForm.value.summaryThreshold = r.summaryThreshold ?? 8000
      summaryForm.value.summaryPrompt = r.summaryPrompt ?? ''
      runControlForm.value.maxTurns = r.maxTurns ?? 10
      runControlForm.value.maxSteps = r.maxSteps ?? 15
      runControlForm.value.retryTimes = r.retryTimes ?? 2
      // 同步 maxTokens 到高级表单
      if (r.maxTokens) advancedForm.value.maxTokens = r.maxTokens
      if (r.model) advancedForm.value.model = r.model
      if (r.temperature != null) advancedForm.value.temperature = r.temperature
    }
  } catch (e) { /* ignore */ }
}

async function handleSave() {
  saving.value = true
  try {
    await api.saveAppBasicConfig(appId(), basicForm.value)
    await api.saveAppAdvanced(appId(), advancedForm.value)
    // 保存系统提示词
    await api.saveAppPrompt(appId(), promptForm.value.prompt)
    // 保存上下文压缩配置
    await api.saveAppSummary(appId(), {
      summaryThreshold: summaryForm.value.summaryThreshold,
      summaryPrompt: summaryForm.value.summaryPrompt,
    })
    // 保存运行控制参数
    await api.saveAppMaxTurns(appId(), runControlForm.value.maxTurns)
    await api.saveAppMaxSteps(appId(), runControlForm.value.maxSteps)
    await api.saveAppRetryTimes(appId(), runControlForm.value.retryTimes)
    ElMessage.success('基础配置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadBasic()
  loadConfig()
  loadModels()
})
</script>

<template>
  <div class="config-section">
    <!-- 基础信息 -->
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">基础信息配置</div>
      </div>
      <el-form label-width="120px">
        <el-form-item label="对话记忆轮数">
          <el-input-number v-model="basicForm.memoryRounds" :min="0" :max="50" />
        </el-form-item>
        <el-form-item label="输出格式">
          <el-select v-model="basicForm.outputFormat" style="width: 160px">
            <el-option label="Markdown" value="markdown" />
            <el-option label="HTML" value="html" />
            <el-option label="纯文本" value="text" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时秒数">
          <el-input-number v-model="basicForm.timeoutSeconds" :min="5" :max="120" />
        </el-form-item>
        <el-form-item label="开场白">
          <el-input v-model="basicForm.greeting" type="textarea" :rows="2" placeholder="请输入开场白" />
        </el-form-item>
        <el-form-item label="结束语">
          <el-input v-model="basicForm.goodbyeMessage" type="textarea" :rows="2" placeholder="请输入结束语" />
        </el-form-item>
      </el-form>
    </div>

    <!-- 高级选项 -->
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">高级选项</div>
        <el-button size="small" text @click="showAdvanced = !showAdvanced">
          {{ showAdvanced ? '收起' : '展开' }}
        </el-button>
      </div>
      <el-form v-if="showAdvanced" label-width="120px">
        <el-form-item label="模型选择">
          <el-select
            v-model="advancedForm.model"
            placeholder="选择 LLM 模型"
            clearable
            filterable
            :loading="modelsLoading"
            style="width: 100%"
          >
            <el-option
              v-for="m in modelOptions"
              :key="m.code"
              :label="m.name"
              :value="m.code"
            />
          </el-select>
          <div v-if="modelOptions.length === 0 && !modelsLoading" style="font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px">
            暂无在线 LLM 模型，请先在模型管理中添加并启用模型
          </div>
        </el-form-item>
        <el-form-item label="温度">
          <el-slider v-model="advancedForm.temperature" :min="0" :max="2" :step="0.1" show-input style="width: 300px" />
        </el-form-item>
        <el-form-item label="最大Token数">
          <el-input-number v-model="advancedForm.maxTokens" :min="128" :max="8192" :step="128" />
        </el-form-item>
        <el-form-item label="Top P">
          <el-slider v-model="advancedForm.topP" :min="0" :max="1" :step="0.05" show-input style="width: 300px" />
        </el-form-item>
        <el-form-item label="频率惩罚">
          <el-slider v-model="advancedForm.frequencyPenalty" :min="-2" :max="2" :step="0.1" show-input style="width: 300px" />
        </el-form-item>
        <el-form-item label="存在惩罚">
          <el-slider v-model="advancedForm.presencePenalty" :min="-2" :max="2" :step="0.1" show-input style="width: 300px" />
        </el-form-item>
      </el-form>
      <div v-else style="font-size: 13px; color: var(--el-text-color-secondary)">展开后可调整模型参数</div>
    </div>

    <!-- 系统提示词 -->
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">系统提示词</div>
        <el-button size="small" text @click="showPrompt = !showPrompt">
          {{ showPrompt ? '收起' : '展开' }}
        </el-button>
      </div>
      <el-form v-if="showPrompt" label-width="120px">
        <el-form-item label="提示词">
          <el-input
            v-model="promptForm.prompt"
            type="textarea"
            :rows="6"
            placeholder="请输入系统提示词，用于定义 AI 的角色、行为规则和回答风格"
          />
        </el-form-item>
      </el-form>
      <div v-else style="font-size: 13px; color: var(--el-text-color-secondary)">展开后可设置系统提示词</div>
    </div>

    <!-- 上下文压缩 -->
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">上下文压缩</div>
        <el-button size="small" text @click="showSummary = !showSummary">
          {{ showSummary ? '收起' : '展开' }}
        </el-button>
      </div>
      <el-form v-if="showSummary" label-width="120px">
        <el-form-item label="摘要阈值">
          <el-input-number v-model="summaryForm.summaryThreshold" :min="1000" :max="32000" :step="1000" />
          <span style="font-size: 12px; color: var(--el-text-color-secondary); margin-left: 8px">当上下文 token 数超过此值时触发摘要压缩</span>
        </el-form-item>
        <el-form-item label="摘要提示词">
          <el-input
            v-model="summaryForm.summaryPrompt"
            type="textarea"
            :rows="4"
            placeholder="请输入上下文压缩的提示词（留空使用默认提示词）"
          />
        </el-form-item>
      </el-form>
      <div v-else style="font-size: 13px; color: var(--el-text-color-secondary)">展开后可配置上下文压缩策略</div>
    </div>

    <!-- 运行控制 -->
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">运行控制</div>
        <el-button size="small" text @click="showRunControl = !showRunControl">
          {{ showRunControl ? '收起' : '展开' }}
        </el-button>
      </div>
      <el-form v-if="showRunControl" label-width="120px">
        <el-form-item label="最大轮数">
          <el-input-number v-model="runControlForm.maxTurns" :min="1" :max="100" />
          <span style="font-size: 12px; color: var(--el-text-color-secondary); margin-left: 8px">单次对话最大交互轮数</span>
        </el-form-item>
        <el-form-item label="最大步数">
          <el-input-number v-model="runControlForm.maxSteps" :min="1" :max="50" />
          <span style="font-size: 12px; color: var(--el-text-color-secondary); margin-left: 8px">Agent 最大执行步数</span>
        </el-form-item>
        <el-form-item label="重试次数">
          <el-input-number v-model="runControlForm.retryTimes" :min="0" :max="10" />
          <span style="font-size: 12px; color: var(--el-text-color-secondary); margin-left: 8px">模型调用失败时的重试次数</span>
        </el-form-item>
      </el-form>
      <div v-else style="font-size: 13px; color: var(--el-text-color-secondary)">展开后可配置运行控制参数</div>
    </div>

    <!-- 固定底部保存按钮 -->
    <div class="save-bar">
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section {
  padding-bottom: 72px; /* 给固定底栏留空间 */
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}

.card-panel {
  background: var(--el-bg-color-overlay);
  border-radius: $radius-base;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  margin-bottom: $spacing-base;
}

.save-bar {
  position: sticky;
  bottom: 0;
  margin-top: $spacing-lg;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 4px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
  z-index: 10;
}
</style>
