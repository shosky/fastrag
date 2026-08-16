<script setup lang="ts">
import type { ParseStrategy, ParseStrategyForm, ParseStrategyAdvanced, ParseMethodMeta, ChunkStrategyType } from '@/types/knowledge'
import {
  DEFAULT_ADVANCED,
  DELIMITER_OPTIONS,
  TABLE_MODE_OPTIONS,
  CHUNK_STRATEGY_OPTIONS, STRATEGY_TAG_TYPE_MAP, STRATEGY_TYPE_MAP,
  PARENT_AGG_LEVEL_OPTIONS, CHUNK_LENGTH_OPTIONS_PARENT_CHILD,
} from '@/types/knowledge'
import { Plus, Edit, Delete, Search, Refresh, ArrowLeft, Star, QuestionFilled, Check, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { useParseStrategy } from '@/composables/useParseStrategy'
import * as api from '@/api'
import ChunkLengthSelect from './components/ChunkLengthSelect.vue'

const router = useRouter()
const route = useRoute()
const kbId = route.params.id as string

// --- 数据层（替代硬编码） ---
const {
  strategies,
  loading,
  load,
  create,
  update,
  remove,
  setDefault,
  detectConflicts,
} = useParseStrategy(kbId)

// 搜索
const searchText = ref('')
const filteredStrategies = computed(() => {
  return strategies.value.filter((s) => {
    if (searchText.value && !s.name.toLowerCase().includes(searchText.value.toLowerCase())) {
      return false
    }
    return true
  })
})

// --- Dialog 状态 ---
const dialogVisible = ref(false)
const dialogTitle = ref('创建解析策略')
const isEdit = ref(false)
const editingId = ref<string>('')

const form = ref<ParseStrategyForm>({
  name: '',
  description: '',
  extensions: [],
  parseMethod: 'default',
  advanced: normalizeAdvanced(null),
  llmModel: '',
})

// 模型列表（从 API 加载）
const llmModels = ref<any[]>([])
const embedModels = ref<any[]>([])

onMounted(async () => {
  try {
    const llmRes = await api.getModels({ purpose: 'LLM' }).catch(() => [])
    llmModels.value = (llmRes as any)?.list || llmRes || []
  } catch {
    // ignore
  }
  try {
    // Embedding 模型单独拉取（语义切片用）：对话模型不能用于向量化，必须区分 purpose
    const embedRes = await api.getModels({ purpose: 'EMBEDDING' }).catch(() => [])
    embedModels.value = (embedRes as any)?.list || embedRes || []
  } catch {
    // ignore
  }
})

// --- 文档类型元数据（来自后端 /parse-strategies/meta，单一事实来源，避免前后端口径漂移） ---
const methodMeta = ref<ParseMethodMeta[]>([])
const supportedExtensions = ref<string[]>([])

async function loadMethodMeta() {
  try {
    const res = (await api.getParseStrategyMeta()) as any
    methodMeta.value = res?.methods || []
    supportedExtensions.value = res?.supportedExtensions || []
  } catch {
    // 后端未提供时保持空（策略列表等其余功能照常）
  }
}

/** 文档类型显示名 */
function methodLabel(code: string): string {
  return methodMeta.value.find((m) => m.code === code)?.label || code
}

/** 某文档类型可选扩展名：default 兼容全部受支持扩展名，其余只兼容自身扩展名集 */
function selectableExtensions(code: string): string[] {
  if (code === 'default') return supportedExtensions.value
  return methodMeta.value.find((m) => m.code === code)?.extensions || []
}

/** 切换文档类型：按该类型的扩展名集重置勾选 */
function handleMethodChange() {
  const m = methodMeta.value.find((x) => x.code === form.value.parseMethod)
  if (m) form.value.extensions = [...m.extensions]
}

// 扩展名选项（随文档类型过滤）
const extensionOptions = computed(() =>
  selectableExtensions(form.value.parseMethod).map((ext) => ({ label: ext, value: ext })),
)

// 表格处理模式适用的文档类型
const TABLE_METHODS = ['pdf', 'doc', 'docx', 'xlsx']
// 关键帧参数适用的文档类型（后端仅在 parseVideo 消费，audio 走纯 ASR）
const KEYFRAME_METHODS = ['video']

// 高级参数折叠状态（el-collapse v-model 需要 string[]）
const advancedCollapsed = ref<string[]>([])

// 扩展名冲突检测（调用后端 /conflicts 接口作为单一事实源；防抖 300ms 实时提示）
const conflicts = ref<any[]>([])
let conflictTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => form.value.extensions,
  () => {
    clearTimeout(conflictTimer)
    const exts = form.value.extensions
    if (exts.length === 0) {
      conflicts.value = []
      return
    }
    conflictTimer = setTimeout(async () => {
      conflicts.value = await detectConflicts(exts, isEdit.value ? editingId.value : undefined)
    }, 300)
  },
)

const formRules = {
  name: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  extensions: [{ required: true, message: '请选择文件扩展名', trigger: 'change' }],
  parseMethod: [{ required: true, message: '请选择文档类型', trigger: 'change' }],
}
const formRef = ref()

// 快捷访问高级参数：将 chunk 子对象字段提升到顶层，确保模板双向绑定
// 正确读写到 advanced.chunk.xxx（而非在 advanced 顶层创建幽灵属性）
const adv = computed(() => {
  const a = form.value.advanced
  if (!a) return {} as any
  // 把 chunk 内字段代理到顶层（读：从 chunk 取值；写：通过 setter 同步回 chunk）
  return new Proxy(a, {
    get(target: any, prop: string | symbol) {
      if (prop in target.chunk) return target.chunk[prop]
      if (prop === 'parse') return target.parse
      if (prop === 'index') return target.index
      return (target as any)[prop]
    },
    set(target: any, prop: string | symbol, value: any) {
      if (prop === 'parse' || prop === 'index') {
        ;(target as any)[prop] = value
      } else {
        target.chunk[prop] = value
      }
      return true
    },
  })
})

/**
 * 归一化 advanced：将新旧格式统一为 { parse, chunk, index } 分组结构。
 * - 新分组结构优先（直接合并）
 * - 旧平铺字段（chunkLength / overlap / tableMode 等）兼容映射到对应子对象
 * - 最终只返回三个子对象，不含顶层平铺幽灵字段
 */
function normalizeAdvanced(advanced: any): ParseStrategyAdvanced {
  const base = JSON.parse(JSON.stringify(DEFAULT_ADVANCED)) as ParseStrategyAdvanced
  if (!advanced) return base
  // 新分组结构：直接合并
  if (advanced.parse) base.parse = { ...base.parse, ...advanced.parse }
  if (advanced.chunk) {
    base.chunk = { ...base.chunk, ...advanced.chunk }
    if (!base.chunk.strategy) base.chunk.strategy = 'rule_fixed'
  }
  if (advanced.index) base.index = { ...base.index, ...advanced.index }
  // 旧平铺字段：仅在新分组未提供时兼容映射
  if (!advanced.chunk) {
    if (advanced.chunkLength != null) base.chunk.chunkLength = Number(advanced.chunkLength)
    if (advanced.overlap != null) base.chunk.overlap = Number(advanced.overlap)
    if (advanced.strategy != null) base.chunk.strategy = advanced.strategy
    if (advanced.delimiters != null) base.chunk.delimiters = advanced.delimiters
    else if (advanced.delimiter != null) base.chunk.delimiters = [String(advanced.delimiter)]
    if (advanced.titlePrefix != null) base.chunk.titlePrefix = advanced.titlePrefix
    if (advanced.headingPath != null) base.chunk.headingPath = advanced.headingPath
    if (advanced.parentMaxChunkLength != null) base.chunk.parentMaxChunkLength = Number(advanced.parentMaxChunkLength)
    if (advanced.parentAggLevel != null) base.chunk.parentAggLevel = advanced.parentAggLevel
    if (advanced.semanticThreshold != null) base.chunk.semanticThreshold = Number(advanced.semanticThreshold)
    if (advanced.embeddingModel != null) base.chunk.embeddingModel = advanced.embeddingModel
  }
  if (!advanced.parse) {
    if (advanced.tableMode != null) base.parse.tableMode = advanced.tableMode
    if (advanced.keyframeIntervalSeconds != null) base.parse.keyframeIntervalSeconds = Number(advanced.keyframeIntervalSeconds)
    if (advanced.keyframeHashThreshold != null) base.parse.keyframeHashThreshold = Number(advanced.keyframeHashThreshold)
  }
  return base
}

/** 列表展示用：提取策略的分片策略标签 */
function chunkStrategyTag(s: ParseStrategy): { tag: string; type: string } {
  const adv: any = s.advanced
  let strategy: ChunkStrategyType = 'rule_fixed'
  if (adv?.chunk?.strategy) {
    strategy = adv.chunk.strategy as ChunkStrategyType
  } else if (adv?.strategy) {
    strategy = adv.strategy as ChunkStrategyType
  }
  const opt = STRATEGY_TYPE_MAP[strategy] || STRATEGY_TYPE_MAP.rule_fixed
  return { tag: opt.tag, type: STRATEGY_TAG_TYPE_MAP[strategy] || 'info' }
}

// 当前表单选中的分片策略
const selectedStrategy = computed(() => {
  return STRATEGY_TYPE_MAP[adv.value.strategy] || STRATEGY_TYPE_MAP.rule_fixed
})

/** 切换分片策略 */
function selectStrategy(type: ChunkStrategyType) {
  adv.value.strategy = type
  // 切换策略时重置为合理默认值
  const defaults = JSON.parse(JSON.stringify(DEFAULT_ADVANCED)).chunk
  adv.value.chunkLength = defaults.chunkLength
  adv.value.overlap = defaults.overlap
  adv.value.delimiters = defaults.delimiters
  adv.value.titlePrefix = defaults.titlePrefix
  adv.value.headingPath = defaults.headingPath
  adv.value.parentMaxChunkLength = defaults.parentMaxChunkLength
  adv.value.parentAggLevel = defaults.parentAggLevel
  adv.value.semanticThreshold = defaults.semanticThreshold
  adv.value.embeddingModel = defaults.embeddingModel
}

function goBack() {
  router.push(`/knowledge/${kbId}`)
}

// 创建（内联对话框）
function handleCreate() {
  isEdit.value = false
  editingId.value = ''
  dialogTitle.value = '创建解析策略'
  form.value = { name: '', description: '', extensions: [], parseMethod: 'default', advanced: normalizeAdvanced(null) }
  advancedCollapsed.value = []
  conflicts.value = []
  dialogVisible.value = true
}

// 恢复默认高级参数
function resetAdvanced() {
  if (form.value.advanced) {
    form.value.advanced = normalizeAdvanced(null)
    ElMessage.success('已恢复默认高级参数')
  }
}

// 编辑（预填所有字段，含高级参数）
function handleEdit(strategy: ParseStrategy) {
  isEdit.value = true
  editingId.value = strategy.id
  dialogTitle.value = '编辑解析策略'
  form.value = {
    name: strategy.name,
    description: strategy.description,
    extensions: [...strategy.extensions],
    parseMethod: strategy.parseMethod,
    advanced: normalizeAdvanced(strategy.advanced),
    llmModel: strategy.llmModel || '',
  }
  advancedCollapsed.value = []
  dialogVisible.value = true
}

// 设为默认
async function handleSetDefault(strategy: ParseStrategy) {
  try {
    await ElMessageBox.confirm(
      `确定将「${strategy.name}」设为默认解析策略吗？`,
      '设为默认',
      { type: 'info', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
    const result = await setDefault(strategy.id)
    if (result.success) {
      ElMessage.success(`已将「${strategy.name}」设为默认策略`)
    } else {
      ElMessage.error(result.message || '设置失败')
    }
  } catch {
    // 用户取消
  }
}

// 删除
async function handleDelete(strategy: ParseStrategy) {
  if (strategy.isDefault) {
    ElMessage.warning('默认策略不能删除')
    return
  }
  const refCount = strategy.fileCount || 0
  const refHint = refCount > 0
    ? `\n\n⚠️ 当前有 ${refCount} 个文件正在使用此策略，删除后这些文件将回退为「自动匹配（按扩展名）」。`
    : ''
  try {
    await ElMessageBox.confirm(
      `确定要删除策略「${strategy.name}」吗？此操作不可恢复。${refHint}`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    const result = await remove(strategy.id)
    if (result.success) {
      ElMessage.success('策略已删除')
    } else {
      ElMessage.error(result.message || '删除失败')
    }
  } catch {
    // 用户取消
  }
}

// 提交（创建或编辑，真正持久化）
async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    // 冲突警告（提交前实时调后端 /conflicts，允许强制保存但有提示）
    const freshConflicts = await detectConflicts(form.value.extensions, isEdit.value ? editingId.value : undefined)
    if (freshConflicts.length > 0) {
      const names = freshConflicts.map((c) => c.name).join('、')
      try {
        await ElMessageBox.confirm(
          `以下策略已包含相同扩展名：${names}。继续保存将造成扩展名冲突，确定吗？`,
          '扩展名冲突警告',
          { type: 'warning', confirmButtonText: '仍然保存', cancelButtonText: '返回修改' },
        )
      } catch {
        return // 返回修改
      }
    }

    // index.embedFields 为二期占位字段，后端一期固定策略消费，提交时省略（后端有默认值兜底）
    const payload = JSON.parse(JSON.stringify(form.value)) as ParseStrategyForm
    if (payload.advanced?.index) {
      delete payload.advanced.index
    }

    if (isEdit.value) {
      // 编辑：真正持久化
      await update(editingId.value, payload)
      ElMessage.success('策略已更新')
    } else {
      // 创建
      await create(payload)
      ElMessage.success('策略已创建')
    }
    dialogVisible.value = false
  })
}

// 真刷新
function handleRefresh() {
  load()
  ElMessage.success('列表已刷新')
}

// 生命周期
onMounted(() => {
  load()
  loadMethodMeta()
})
</script>

<template>
  <div class="parse-strategy-page">
    <!-- Page header -->
    <div class="parse-strategy-page__header">
      <div class="parse-strategy-page__header-left">
        <el-button :icon="ArrowLeft" link class="parse-strategy-page__back" @click="goBack">
          返回
        </el-button>
        <el-divider direction="vertical" />
        <h2 class="parse-strategy-page__title">解析策略管理</h2>
      </div>
      <div class="parse-strategy-page__actions">
        <el-button type="primary" :icon="Plus" @click="handleCreate">创建策略</el-button>
      </div>
    </div>

    <!-- Search bar -->
    <div class="parse-strategy-page__search">
      <el-input
        v-model="searchText"
        placeholder="搜索策略名称"
        clearable
        class="parse-strategy-page__search-input"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button :icon="Refresh" circle @click="handleRefresh" />
    </div>

    <!-- Strategy table -->
    <el-table
      v-loading="loading"
      :data="filteredStrategies"
      stripe
      row-key="id"
      :row-style="{ height: '60px' }"
      :cell-style="{ padding: '8px 0' }"
      class="parse-strategy-page__table"
    >
      <el-table-column prop="name" label="策略名称" min-width="180" show-overflow-tooltip>
        <template #header>
          <span>策略名称</span>
          <el-tooltip
            content="覆盖链：上传请求参数 > 选中的策略 > KB 默认策略 > 系统默认值"
            placement="top"
          >
            <el-icon style="margin-left: 4px; vertical-align: middle; cursor: pointer">
              <QuestionFilled />
            </el-icon>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          <div class="parse-strategy-page__name-cell">
            <span class="parse-strategy-page__name">{{ row.name }}</span>
            <el-tag v-if="row.isDefault" type="info" size="small">默认</el-tag>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="description" label="策略描述" min-width="200" show-overflow-tooltip />

      <el-table-column label="文件扩展名" min-width="200">
        <template #default="{ row }">
          <div class="parse-strategy-page__extensions">
            <el-tag
              v-for="ext in row.extensions"
              :key="ext"
              size="small"
              class="parse-strategy-page__extension-tag"
            >
              {{ ext }}
            </el-tag>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="文档类型" width="160" align="center">
        <template #default="{ row }">
          <el-tag :type="row.parseMethod === 'default' ? 'info' : 'primary'" size="small">
            {{ methodLabel(row.parseMethod) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="分片策略" width="140" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="(chunkStrategyTag(row as ParseStrategy)).type">
            {{ (chunkStrategyTag(row as ParseStrategy)).tag }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="更新时间" width="160" align="center">
        <template #default="{ row }">
          {{ row.updatedAt }}
        </template>
      </el-table-column>

      <el-table-column label="操作" width="220" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :icon="Edit" @click="handleEdit(row as ParseStrategy)">
            编辑
          </el-button>
          <el-button
            v-if="!(row as ParseStrategy).isDefault"
            type="warning"
            link
            :icon="Star"
            @click="handleSetDefault(row as ParseStrategy)"
          >
            设默认
          </el-button>
          <el-button
            v-if="!(row as ParseStrategy).isDefault"
            type="danger"
            link
            :icon="Delete"
            @click="handleDelete(row as ParseStrategy)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 快速创建/编辑 dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="800px"
      top="5vh"
      :close-on-click-modal="false"
      destroy-on-close
      class="parse-strategy-page__dialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="策略名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入策略名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="策略描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入策略描述"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="文档类型" prop="parseMethod">
          <el-select
            v-model="form.parseMethod"
            placeholder="请选择文档类型"
            style="width: 100%"
            @change="handleMethodChange"
          >
            <el-option
              v-for="m in methodMeta"
              :key="m.code"
              :label="m.label"
              :value="m.code"
            />
          </el-select>
          <div class="form-tip">每种文档类型有专属解析参数；「自动识别」适合混合文档（按扩展名自动推断解析方式）</div>
        </el-form-item>

        <el-form-item label="文件扩展名" prop="extensions">
          <el-select
            v-model="form.extensions"
            multiple
            filterable
            placeholder="已按文档类型过滤，可取消部分扩展名"
            style="width: 100%"
          >
            <el-option
              v-for="option in extensionOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <!-- 实时冲突提示 -->
          <div v-if="conflicts.length > 0" class="parse-strategy-page__conflict-warn">
            <el-icon><WarningFilled /></el-icon>
            扩展名与以下策略冲突：{{ conflicts.map(c => c.name).join('、') }}
          </div>
        </el-form-item>

        <!-- 分片策略卡片选择器 -->
        <el-divider content-position="left">分片策略</el-divider>

        <div class="parse-strategy-page__strategy-cards">
          <div
            v-for="(opt, idx) in CHUNK_STRATEGY_OPTIONS"
            :key="opt.value"
            class="parse-strategy-page__strategy-card"
            :class="[
              adv.strategy === opt.value ? 'parse-strategy-page__strategy-card--active' : '',
              idx === CHUNK_STRATEGY_OPTIONS.length - 1 ? 'parse-strategy-page__strategy-card--wide' : '',
            ]"
            @click="selectStrategy(opt.value)"
          >
            <div class="parse-strategy-page__strategy-card-header">
              <el-icon :size="18"><component :is="opt.icon" /></el-icon>
              <span class="parse-strategy-page__strategy-card-label">{{ opt.label }}</span>
              <el-icon
                v-if="adv.strategy === opt.value"
                :size="14"
                class="parse-strategy-page__strategy-card-check"
              >
                <Check />
              </el-icon>
            </div>
            <span class="parse-strategy-page__strategy-card-desc">{{ opt.description }}</span>
          </div>
        </div>

        <!-- 策略参数面板（根据选中的策略动态显示） -->
        <el-divider content-position="left">策略参数</el-divider>

        <!-- ====== 规则·固定大小 ====== -->
        <template v-if="adv.strategy === 'rule_fixed'">
          <el-form-item label="分片长度（字符数）">
            <ChunkLengthSelect v-model="adv.chunkLength" />
            <div class="form-tip">按固定字符数切开文本，适合大多数文档</div>
          </el-form-item>
          <el-form-item label="分隔符">
            <el-select
              v-model="adv.delimiters"
              multiple
              filterable
              allow-create
              placeholder="选择或输入分隔符"
              style="width: 100%"
            >
              <el-option
                v-for="opt in DELIMITER_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <div class="form-tip">按分隔符优先切分文本（如段落空行），超长片段再按分片长度硬切</div>
          </el-form-item>
          <el-form-item label="重叠字符数">
            <el-input-number v-model="adv.overlap" :min="0" :max="500" :step="10" />
            <div class="form-tip">相邻分片的重叠内容，避免跨块语义断裂</div>
          </el-form-item>
        </template>

        <!-- ====== 规则·递归字符切分 ====== -->
        <template v-if="adv.strategy === 'rule_recursive'">
          <el-form-item label="最大块长度（字符数）">
            <ChunkLengthSelect v-model="adv.chunkLength" />
            <div class="form-tip">超长分片按下一级分隔符继续切分</div>
          </el-form-item>
          <el-form-item label="递归分隔符（按优先级排列）">
            <el-select
              v-model="adv.delimiters"
              multiple
              filterable
              allow-create
              placeholder="选择或输入分隔符"
              style="width: 100%"
            >
              <el-option
                v-for="opt in DELIMITER_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <div class="form-tip">优先使用第一个分隔符切分，切出过长块时用第二个，以此类推</div>
          </el-form-item>
          <el-form-item label="重叠字符数">
            <el-input-number v-model="adv.overlap" :min="0" :max="500" :step="10" />
            <div class="form-tip">相邻分片的重叠内容</div>
          </el-form-item>
        </template>

        <!-- ====== 结构感知切片 ====== -->
        <template v-if="adv.strategy === 'structure_aware'">
          <el-form-item label="单块最大长度（字符数）">
            <ChunkLengthSelect v-model="adv.chunkLength" />
            <div class="form-tip">当某个结构块（如标题段落）超过此长度时，二次切分</div>
          </el-form-item>
          <el-form-item label="标题前缀">
            <div class="parse-strategy-page__switch-row">
              <el-switch v-model="adv.titlePrefix" />
              <span>正文内嵌标题前缀（chunk 自包含章节上下文）</span>
            </div>
          </el-form-item>
          <el-form-item label="层级路径">
            <div class="parse-strategy-page__switch-row">
              <el-switch v-model="adv.headingPath" />
              <span>生成层级路径元数据（如 第一章 &gt; 1.1 背景）</span>
            </div>
          </el-form-item>
        </template>

        <!-- ====== 语义切片 ====== -->
        <template v-if="adv.strategy === 'semantic'">
          <el-form-item label="Embedding 模型">
            <el-select v-model="adv.embeddingModel" clearable placeholder="选择 Embedding 模型" style="width: 100%">
              <el-option label="不指定（使用知识库级 Embedding 模型）" value="" />
              <el-option
                v-for="m in embedModels"
                :key="m.id"
                :label="`${m.name} (${m.brand || m.code})`"
                :value="m.code || m.name"
              />
            </el-select>
            <div class="form-tip">用于计算相邻句子的向量相似度，找出语义突变断点；留空时使用知识库级模型</div>
          </el-form-item>
          <el-form-item label="语义突变阈值">
            <div class="parse-strategy-page__slider-row">
              <el-slider v-model="adv.semanticThreshold" :min="0" :max="100" :step="1" :format-tooltip="(v: number) => (v / 100).toFixed(2)" />
              <span class="parse-strategy-page__slider-value">{{ (adv.semanticThreshold / 100).toFixed(2) }}</span>
            </div>
            <div class="form-tip">相似度低于此阈值时视为语义断点，值越大切分越细</div>
          </el-form-item>
          <el-form-item label="单块最大长度（字符数）">
            <ChunkLengthSelect v-model="adv.chunkLength" />
            <div class="form-tip">单块硬上限，超过时按句子边界强制切分</div>
          </el-form-item>
        </template>

        <!-- ====== 父子切片 ====== -->
        <template v-if="adv.strategy === 'parent_child'">
          <el-form-item label="子分片长度（字符数）">
            <ChunkLengthSelect v-model="adv.chunkLength" :options="CHUNK_LENGTH_OPTIONS_PARENT_CHILD" />
            <div class="form-tip">子分片用于向量召回，长度越小召回越精准</div>
          </el-form-item>
          <el-form-item label="重叠字符数">
            <el-input-number v-model="adv.overlap" :min="0" :max="500" :step="10" />
            <div class="form-tip">相邻子分片的重叠内容，避免跨块语义断裂</div>
          </el-form-item>
          <el-form-item label="兜底分隔符">
            <el-select
              v-model="adv.delimiters"
              multiple
              filterable
              allow-create
              placeholder="选择或输入分隔符"
              style="width: 100%"
            >
              <el-option
                v-for="opt in DELIMITER_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <div class="form-tip">无结构文档按分隔符切分</div>
          </el-form-item>

          <el-divider content-position="left">父分片配置</el-divider>

          <el-form-item label="父分片最大长度（字符数）">
            <el-input-number v-model="adv.parentMaxChunkLength" :min="500" :max="10000" :step="500" />
            <div class="form-tip">标题聚合后超过此长度时，按句子边界二次切分</div>
          </el-form-item>
          <el-form-item label="聚合层级">
            <el-radio-group v-model="adv.parentAggLevel">
              <el-radio-button
                v-for="opt in PARENT_AGG_LEVEL_OPTIONS"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
              </el-radio-button>
            </el-radio-group>
            <div class="form-tip">按指定级别的标题将子分片聚合为父分片；「自动」由系统根据文档结构智能选择</div>
          </el-form-item>
        </template>

        <!-- 高级参数（可折叠：模型/解析细节，普通用户无需关注） -->
        <el-collapse v-model="advancedCollapsed" class="parse-strategy-page__advanced-collapse">
          <el-collapse-item title="高级参数（模型/解析细节）" name="advanced">
            <el-form-item label="LLM 模型（智能分段/内容提取）">
              <el-select v-model="form.llmModel" clearable placeholder="选择用于智能解析的 LLM 模型" style="width: 100%">
                <el-option label="不使用 LLM" value="" />
                <el-option
                  v-for="m in llmModels"
                  :key="m.id"
                  :label="`${m.name} (${m.brand || m.code})`"
                  :value="m.code || m.name"
                />
              </el-select>
              <div class="form-tip">用于智能分段、内容提取、摘要生成等</div>
            </el-form-item>

            <!-- ========== 解析配置（按文档类型显示专属参数） ========== -->
            <div v-if="TABLE_METHODS.includes(form.parseMethod)" class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">表格处理模式</label>
              <el-radio-group v-model="adv.parse.tableMode">
                <el-radio-button
                  v-for="opt in TABLE_MODE_OPTIONS"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </el-radio-button>
              </el-radio-group>
              <span class="parse-strategy-page__field-hint">
                {{ TABLE_MODE_OPTIONS.find(o => o.value === adv.parse.tableMode)?.desc }}
              </span>
            </div>

            <div v-if="KEYFRAME_METHODS.includes(form.parseMethod)" class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">关键帧采样间隔（秒）</label>
              <el-input-number v-model="adv.parse.keyframeIntervalSeconds" :min="1" :max="600" :step="5" />
              <label class="parse-strategy-page__field-label">关键帧哈希阈值</label>
              <el-input-number v-model="adv.parse.keyframeHashThreshold" :min="0" :max="64" />
              <span class="parse-strategy-page__field-hint">视频解析专用：关键帧 OCR + ASR 联合分块</span>
            </div>

            <div v-if="form.parseMethod === 'image'" class="parse-strategy-page__field-hint" style="margin-bottom: 8px">
              图片解析使用系统内置 OCR 与视觉描述能力（引擎配置二期开放）
            </div>

            <!-- ========== 索引配置（二期开放） ========== -->

            <!-- 恢复默认 -->
            <div class="parse-strategy-page__switch-row" style="margin-top: 12px">
              <el-button size="small" @click="resetAdvanced">恢复默认值</el-button>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-form>

      <template #footer>
        <div class="parse-strategy-page__dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">
            {{ isEdit ? '更新' : '创建' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.parse-strategy-page {
  padding: $spacing-lg;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-lg;
    background: $bg-white;
    border-radius: $radius-base;
    padding: $spacing-base $spacing-lg;
    box-shadow: $shadow-sm;
  }

  &__header-left {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__back {
    font-size: 14px;
  }

  &__title {
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    margin: 0;
  }

  &__actions {
    display: flex;
    gap: $spacing-sm;
  }

  &__search {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: $spacing-lg;
  }

  &__search-input {
    width: 300px;
  }

  &__table {
    width: 100%;
    background: $bg-white;
    border-radius: $radius-base;
    box-shadow: $shadow-sm;
  }

  &__name-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__name {
    font-weight: 500;
    color: $text-primary;
  }

  &__extensions {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  &__extension-tag {
    margin: 0;
  }

  &__conflict-warn {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-top: 6px;
    font-size: 12px;
    color: $color-warning;
  }

  &__dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  &__dialog {
    :deep(.el-dialog__body) {
      max-height: calc(90vh - 140px);
      overflow-y: auto;
    }
  }

  &__advanced-collapse {
    margin-top: $spacing-sm;
    border: none;

    :deep(.el-collapse-item__header) {
      font-size: 14px;
      font-weight: 500;
      color: $color-primary;
      height: 40px;
    }

    :deep(.el-collapse-item__wrap) {
      border: none;
    }

    :deep(.el-collapse-item__content) {
      padding-bottom: 0;
    }
  }

  &__field-group {
    margin-bottom: $spacing-base;
  }

  &__field-label {
    display: block;
    font-size: 13px;
    font-weight: 500;
    color: $text-primary;
    margin-bottom: $spacing-xs;
  }

  &__field-hint {
    display: block;
    font-size: 12px;
    color: $text-secondary;
    margin-top: 4px;
  }

  &__switch-row {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-sm;
    font-size: 14px;
    color: $text-regular;
  }

  &__strategy-cards {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: $spacing-sm;
  }

  &__strategy-card {
    display: flex;
    flex-direction: column;
    gap: $spacing-xs;
    padding: $spacing-sm $spacing-base;
    border: 2px solid $border-base;
    border-radius: $radius-card;
    cursor: pointer;
    transition: all 0.2s ease;
    background: $bg-white;
    user-select: none;

    &:hover {
      border-color: mix($color-primary, $border-base, 30%);
      box-shadow: $shadow-card;
    }

    &--active {
      border-color: $color-primary;
      background: $color-primary-light;

      .parse-strategy-page__strategy-card-header {
        color: $color-primary;
      }
    }

    &--wide {
      grid-column: span 2;
    }
  }

  &__strategy-card-header {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }

  &__strategy-card-check {
    margin-left: auto;
    color: $color-primary;
  }

  &__strategy-card-label {
    font-weight: 500;
  }

  &__strategy-card-desc {
    font-size: 12px;
    line-height: 1.5;
    color: $text-secondary;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
  }

  &__slider-row {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    flex: 1;

    .el-slider {
      flex: 1;
    }
  }

  &__slider-value {
    min-width: 36px;
    text-align: center;
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }
}

.form-tip {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}
</style>
