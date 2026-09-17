<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

/**
 * 语义理解 / 语义定制配置：
 * - 意图模式：配置意图关键词模式与置信度阈值（语义理解）
 * - 定制同义词：为应用定制同义词映射（语义定制）
 * - judge：输入试判断，即时预览意图识别结果
 */
const props = defineProps<{ appInfo: any }>()

const loading = ref(false)
const saving = ref(false)
const intentPatterns = ref<{ intent: string; patterns: string[]; threshold: number }[]>([])
const customSynonyms = ref<{ standard: string; synonyms: string[] }[]>([])

// 试判断
const judgeQuery = ref('')
const judgeResult = ref<any>(null)
const judging = ref(false)

async function load() {
  if (!props.appInfo?.id) return
  loading.value = true
  try {
    const res: any = await api.getSemanticConfig(props.appInfo.id)
    intentPatterns.value = res?.intentPatterns || []
    customSynonyms.value = res?.customSynonyms || []
  } catch { } finally { loading.value = false }
}
onMounted(load)

async function save() {
  saving.value = true
  try {
    await api.saveSemanticConfig(props.appInfo.id, {
      intentPatterns: intentPatterns.value,
      customSynonyms: customSynonyms.value,
    })
    ElMessage.success('语义配置已保存')
  } catch { ElMessage.error('保存失败') } finally { saving.value = false }
}

async function handleJudge() {
  if (!judgeQuery.value.trim()) { ElMessage.warning('请输入测试语句'); return }
  judging.value = true
  try {
    judgeResult.value = await api.judgeSemantic(props.appInfo.id, judgeQuery.value)
  } catch { ElMessage.error('判断失败') } finally { judging.value = false }
}
</script>

<template>
  <div class="semantic-config" v-loading="loading">
    <div class="panel-section">
      <div class="panel-section__header">
        <span class="panel-section__title">语义理解 · 意图模式</span>
        <el-button size="small" @click="intentPatterns.push({ intent: '', patterns: [], threshold: 0.6 })">
          新增意图
        </el-button>
      </div>
      <p class="panel-section__tip">配置意图识别模式：用户输入命中任一关键词即识别为对应意图（阈值用于过滤低置信命中）。</p>
      <div v-for="(p, i) in intentPatterns" :key="i" class="intent-row">
        <el-input v-model="p.intent" placeholder="意图名称，如：查询订单" style="width: 200px" />
        <el-select v-model="p.patterns" multiple filterable allow-create default-first-option
          placeholder="关键词（回车添加）" style="flex: 1" />
        <el-input-number v-model="p.threshold" :min="0" :max="1" :step="0.05" style="width: 120px" />
        <el-button link type="danger" @click="intentPatterns.splice(i, 1)">删除</el-button>
      </div>
      <el-empty v-if="!intentPatterns.length" description="暂无意图配置" :image-size="60" />
    </div>

    <div class="panel-section">
      <div class="panel-section__header">
        <span class="panel-section__title">语义定制 · 同义词</span>
        <el-button size="small" @click="customSynonyms.push({ standard: '', synonyms: [] })">新增同义词</el-button>
      </div>
      <p class="panel-section__tip">为应用定制同义词映射：命中同义词时按标准词参与检索与理解。</p>
      <div v-for="(s, i) in customSynonyms" :key="i" class="intent-row">
        <el-input v-model="s.standard" placeholder="标准词，如：退款" style="width: 200px" />
        <el-select v-model="s.synonyms" multiple filterable allow-create default-first-option
          placeholder="同义词（回车添加）" style="flex: 1" />
        <el-button link type="danger" @click="customSynonyms.splice(i, 1)">删除</el-button>
      </div>
      <el-empty v-if="!customSynonyms.length" description="暂无定制同义词" :image-size="60" />
    </div>

    <div class="panel-section">
      <div class="panel-section__header">
        <span class="panel-section__title">语义理解试判断</span>
      </div>
      <div style="display: flex; gap: 8px; margin-bottom: 12px">
        <el-input v-model="judgeQuery" placeholder="输入语句，如：我的订单到哪了" @keyup.enter="handleJudge" />
        <el-button type="primary" :loading="judging" @click="handleJudge">判断</el-button>
      </div>
      <div v-if="judgeResult" class="judge-result">
        <el-tag v-if="judgeResult.matched" type="success">命中 {{ judgeResult.intents.length }} 个意图</el-tag>
        <el-tag v-else type="info">未命中意图</el-tag>
        <el-tag v-for="it in judgeResult.intents" :key="it.intent" style="margin-left: 8px">
          {{ it.intent }}（置信度 {{ it.confidence }}）
        </el-tag>
      </div>
    </div>

    <div class="panel-footer">
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.semantic-config { padding: 4px 0; }
.panel-section {
  margin-bottom: 24px;
  &__header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
  &__title { font-size: 14px; font-weight: 600; }
  &__tip { font-size: 12px; color: #909399; margin: 0 0 12px; }
}
.intent-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.judge-result { padding: 8px 0; }
.panel-footer { border-top: 1px solid #ebeef5; padding-top: 12px; }
</style>
