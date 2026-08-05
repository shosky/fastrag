<script setup lang="ts">
import type { ParseStrategy, ParseStrategyForm, ParseStrategyAdvanced } from '@/types/knowledge'
import {
  PARSE_METHOD_OPTIONS, EXTENSION_OPTIONS, DEFAULT_ADVANCED,
  CHUNK_LENGTH_OPTIONS, DELIMITER_OPTIONS,
  TABLE_MODE_OPTIONS,
} from '@/types/knowledge'
import { Plus, Edit, Delete, Search, Refresh, ArrowLeft, Star, QuestionFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { useParseStrategy } from '@/composables/useParseStrategy'
import * as api from '@/api'

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
  vlmModel: '',
  enableGraphBuild: false,
})

// 模型列表（从 API 加载）
const llmModels = ref<any[]>([])
const vlmModels = ref<any[]>([])

onMounted(async () => {
  try {
    const [llmRes, vlmRes] = await Promise.all([
      api.getModels({ purpose: 'LLM' }).catch(() => []),
      api.getModels({ purpose: 'VLM' }).catch(() => []),
    ])
    llmModels.value = (llmRes as any)?.list || llmRes || []
    vlmModels.value = (vlmRes as any)?.list || vlmRes || []
  } catch {
    // ignore
  }
})

// 高级参数折叠状态（el-collapse v-model 需要 string[]）
const advancedCollapsed = ref<string[]>([])
// 自定义切片长度输入
const customChunkLength = ref('')

// 扩展名冲突检测（实时，基于已加载的策略列表）
const conflicts = computed<any[]>(() => {
  if (form.value.extensions.length === 0) return []
  return strategies.value.filter((s: any) => {
    if (isEdit.value && s.id === editingId.value) return false
    return form.value.extensions.some((ext: string) => (s.extensions || []).includes(ext))
  })
})

const formRules = {
  name: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入策略描述', trigger: 'blur' }],
  extensions: [{ required: true, message: '请选择文件扩展名', trigger: 'change' }],
  parseMethod: [{ required: true, message: '请选择解析方法', trigger: 'change' }],
}
const formRef = ref()

// 快捷访问高级参数（避免重复写 form.value.advanced!）
const adv = computed(() => form.value.advanced!)

/**
 * 兼容归一化：旧平铺 advanced（chunkLength / delimiter / tableMode 等）
 * 映射进新分组结构；新分组结构优先。
 */
function normalizeAdvanced(advanced: any): ParseStrategyAdvanced {
  const base = JSON.parse(JSON.stringify(DEFAULT_ADVANCED)) as ParseStrategyAdvanced
  if (!advanced) return base
  if (advanced.parse) base.parse = { ...base.parse, ...advanced.parse }
  if (advanced.chunk) base.chunk = { ...base.chunk, ...advanced.chunk }
  if (advanced.index) base.index = { ...base.index, ...advanced.index }
  // 兼容旧平铺字段（仅当新分组未提供时）
  if (!advanced.chunk) {
    if (advanced.chunkLength != null) base.chunk.chunkLength = Number(advanced.chunkLength)
    if (advanced.overlap != null) base.chunk.overlap = Number(advanced.overlap)
    if (advanced.delimiter != null) base.chunk.delimiters = [String(advanced.delimiter)]
  }
  if (!advanced.parse) {
    if (advanced.tableMode != null) base.parse.tableMode = advanced.tableMode
    if (advanced.enablePptWholePage != null) base.parse.enablePptWholePage = !!advanced.enablePptWholePage
    if (advanced.enableDocSummary != null) base.parse.enableDocSummary = !!advanced.enableDocSummary
    if (advanced.keyframeIntervalSeconds != null) base.parse.keyframeIntervalSeconds = Number(advanced.keyframeIntervalSeconds)
    if (advanced.keyframeHashThreshold != null) base.parse.keyframeHashThreshold = Number(advanced.keyframeHashThreshold)
  }
  return base
}

/** 列表展示用：提取策略生效的分片参数（无 advanced 时回退系统默认） */
function chunkParams(s: ParseStrategy) {
  const adv: any = s.advanced
  if (!adv) return { chunkLength: 2000, overlap: 100 }
  if (adv.chunk) return { chunkLength: adv.chunk.chunkLength, overlap: adv.chunk.overlap }
  return { chunkLength: adv.chunkLength ?? 2000, overlap: adv.overlap ?? 100 }
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
  customChunkLength.value = ''
  advancedCollapsed.value = []
  dialogVisible.value = true
}

// 切片长度选择（0 = 自定义）
function selectChunkLength(value: number) {
  if (form.value.advanced) {
    form.value.advanced.chunk.chunkLength = value
  }
}

// 恢复默认高级参数
function resetAdvanced() {
  if (form.value.advanced) {
    form.value.advanced = normalizeAdvanced(null)
    customChunkLength.value = ''
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
    vlmModel: strategy.vlmModel || '',
    enableGraphBuild: strategy.enableGraphBuild === 1,
  }
  customChunkLength.value = ''
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
  try {
    await ElMessageBox.confirm(
      `确定要删除策略「${strategy.name}」吗？此操作不可恢复。`,
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

    // 冲突警告（允许但有提示）
    if (conflicts.value.length > 0) {
      const names = conflicts.value.map((c) => c.name).join('、')
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

    if (isEdit.value) {
      // 编辑：真正持久化
      update(editingId.value, form.value)
      ElMessage.success('策略已更新')
    } else {
      // 创建
      create(form.value)
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

      <el-table-column label="解析方法" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.parseMethod === 'default' ? 'info' : 'primary'" size="small">
            {{ PARSE_METHOD_OPTIONS.find(opt => opt.value === row.parseMethod)?.label || row.parseMethod }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="分片参数" width="140" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">
            {{ chunkParams(row as ParseStrategy).chunkLength }} / {{ chunkParams(row as ParseStrategy).overlap }}
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
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
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

        <el-form-item label="文件扩展名" prop="extensions">
          <el-select
            v-model="form.extensions"
            multiple
            filterable
            allow-create
            placeholder="请选择或输入文件扩展名"
            style="width: 100%"
          >
            <el-option
              v-for="option in EXTENSION_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <!-- 实时冲突提示 -->
          <div v-if="conflicts.length > 0" class="parse-strategy-page__conflict-warn">
            <el-icon><Star /></el-icon>
            扩展名与以下策略冲突：{{ conflicts.map(c => c.name).join('、') }}
          </div>
        </el-form-item>

        <el-form-item label="解析方法" prop="parseMethod">
          <el-select
            v-model="form.parseMethod"
            placeholder="请选择解析方法"
            style="width: 100%"
          >
            <el-option
              v-for="option in PARSE_METHOD_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <!-- 模型配置 -->
        <el-divider>解析模型配置</el-divider>

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

        <el-form-item label="VLM 模型（图片/表格理解）">
          <el-select v-model="form.vlmModel" clearable placeholder="选择用于图片理解的 VLM 模型" style="width: 100%">
            <el-option label="不使用 VLM" value="" />
            <el-option
              v-for="m in vlmModels"
              :key="m.id"
              :label="`${m.name} (${m.brand || m.code})`"
              :value="m.code || m.name"
            />
          </el-select>
          <div class="form-tip">用于理解文档中的图片、表格、图表等视觉内容</div>
        </el-form-item>

        <!-- 知识图谱自动构建 -->
        <div class="parse-strategy-page__switch-row">
          <el-switch v-model="form.enableGraphBuild" />
          <span>构建知识图谱（使用 LLM 从文档内容提取实体和关系）</span>
        </div>

        <!-- 高级参数（可折叠，按分组组织） -->
        <el-collapse v-model="advancedCollapsed" class="parse-strategy-page__advanced-collapse">
          <el-collapse-item title="高级参数（解析/分片/索引）" name="advanced">
            <!-- ========== 解析配置 ========== -->
            <div class="parse-strategy-page__field-group">
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

            <div v-if="form.parseMethod === 'pptx'" class="parse-strategy-page__switch-row">
              <el-switch v-model="adv.parse.enablePptWholePage" />
              <span>PPT 整页解析（每页作为完整单元）</span>
            </div>

            <div v-if="form.parseMethod === 'video' || form.parseMethod === 'audio'" class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">关键帧采样间隔（秒）</label>
              <el-input-number v-model="adv.parse.keyframeIntervalSeconds" :min="1" :max="600" :step="5" />
              <label class="parse-strategy-page__field-label">关键帧哈希阈值</label>
              <el-input-number v-model="adv.parse.keyframeHashThreshold" :min="0" :max="64" />
              <span class="parse-strategy-page__field-hint">视频/音频解析专用：关键帧 OCR + ASR 联合分块</span>
            </div>

            <!-- ========== 分片配置 ========== -->
            <div class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">目标分片长度（字符数）</label>
              <div class="parse-strategy-page__chunk-lengths">
                <el-button
                  v-for="opt in CHUNK_LENGTH_OPTIONS"
                  :key="opt.value"
                  :type="adv.chunk.chunkLength === opt.value ? 'primary' : 'default'"
                  size="small"
                  @click="selectChunkLength(opt.value)"
                >
                  {{ opt.label }}
                </el-button>
                <el-input
                  v-if="adv.chunk.chunkLength === 0"
                  v-model="customChunkLength"
                  placeholder="自定义"
                  size="small"
                  style="width: 100px"
                  @change="(v: string) => { if (adv) adv.chunk.chunkLength = Number(v) || 2000 }"
                />
              </div>
              <span class="parse-strategy-page__field-hint">段落将累积至接近该长度再分片，超过才按句号切分</span>
            </div>

            <div class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">分片重叠（字符数）</label>
              <el-input-number v-model="adv.chunk.overlap" :min="0" :max="500" :step="10" />
              <span class="parse-strategy-page__field-hint">相邻 chunk 的重叠内容，避免跨块语义断裂</span>
            </div>

            <div class="parse-strategy-page__switch-row">
              <el-switch v-model="adv.chunk.titlePrefix" />
              <span>正文内嵌标题前缀（chunk 自包含章节上下文）</span>
            </div>
            <div class="parse-strategy-page__switch-row">
              <el-switch v-model="adv.chunk.headingPath" />
              <span>生成层级路径元数据（如 第一章 &gt; 1.1 背景）</span>
            </div>

            <div class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">兜底分隔符（无结构文档按分隔符切分）</label>
              <el-select
                v-model="adv.chunk.delimiters"
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
            </div>

            <!-- ========== 索引配置 ========== -->
            <div class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">索引字段（一期固定）</label>
              <div>
                <el-tag
                  v-for="f in adv.index.embedFields"
                  :key="f"
                  size="small"
                  class="parse-strategy-page__extension-tag"
                >
                  {{ f }}
                </el-tag>
              </div>
              <span class="parse-strategy-page__field-hint">embedding 输入 = content + headingPath + fileName，二期开放配置</span>
            </div>

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

  &__chunk-lengths {
    display: flex;
    gap: $spacing-xs;
    align-items: center;
  }

  &__switch-row {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-sm;
    font-size: 14px;
    color: $text-regular;
  }
}

.form-tip {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}
</style>
