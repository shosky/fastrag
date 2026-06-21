<script setup lang="ts">
import type { ParseStrategy, ParseStrategyForm, ParseStrategyAdvanced } from '@/types/knowledge'
import {
  PARSE_METHOD_OPTIONS, EXTENSION_OPTIONS, DEFAULT_ADVANCED,
  CHUNK_LENGTH_OPTIONS, INDEX_FIELD_OPTIONS, DELIMITER_OPTIONS,
  TABLE_MODE_OPTIONS,
} from '@/types/knowledge'
import { Plus, Edit, Delete, Search, Refresh, ArrowLeft, Star } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { useParseStrategy } from '@/composables/useParseStrategy'

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
  advanced: { ...DEFAULT_ADVANCED },
})

// 高级参数折叠状态（el-collapse v-model 需要 string[]）
const advancedCollapsed = ref<string[]>([])
// 自定义切片长度输入
const customChunkLength = ref('')

// 扩展名冲突检测（实时）
const conflicts = computed(() => {
  if (form.value.extensions.length === 0) return []
  return detectConflicts(form.value.extensions, isEdit.value ? editingId.value : undefined)
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

function goBack() {
  router.push(`/knowledge/${kbId}`)
}

// 创建（内联对话框）
function handleCreate() {
  isEdit.value = false
  editingId.value = ''
  dialogTitle.value = '创建解析策略'
  form.value = { name: '', description: '', extensions: [], parseMethod: 'default', advanced: { ...DEFAULT_ADVANCED } }
  customChunkLength.value = ''
  advancedCollapsed.value = []
  dialogVisible.value = true
}

// 切片长度选择（0 = 自定义）
function selectChunkLength(value: number) {
  if (form.value.advanced) {
    form.value.advanced.chunkLength = value
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
    advanced: strategy.advanced ? { ...strategy.advanced } : { ...DEFAULT_ADVANCED },
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
    const result = setDefault(strategy.id)
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
    const result = remove(strategy.id)
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

        <!-- 高级参数（可折叠） -->
        <el-collapse v-model="advancedCollapsed" class="parse-strategy-page__advanced-collapse">
          <el-collapse-item title="高级参数（切片/索引/增强）" name="advanced">
            <!-- 切片策略 -->
            <div class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">切片方式</label>
              <el-radio-group v-model="adv.splitMethod">
                <el-radio-button value="fixed">按固定长度</el-radio-button>
                <el-radio-button value="delimiter">按分隔符</el-radio-button>
              </el-radio-group>
            </div>

            <!-- 固定长度切片 -->
            <div v-if="adv.splitMethod === 'fixed'" class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">切片最长字符数</label>
              <div class="parse-strategy-page__chunk-lengths">
                <el-button
                  v-for="opt in CHUNK_LENGTH_OPTIONS"
                  :key="opt.value"
                  :type="adv.chunkLength === opt.value ? 'primary' : 'default'"
                  size="small"
                  @click="selectChunkLength(opt.value)"
                >
                  {{ opt.label }}
                </el-button>
                <el-input
                  v-if="adv.chunkLength === 0"
                  v-model="customChunkLength"
                  placeholder="自定义"
                  size="small"
                  style="width: 100px"
                  @change="(v: string) => { if (adv) adv.chunkLength = Number(v) || 2000 }"
                />
              </div>
            </div>

            <!-- 分隔符切片 -->
            <div v-if="adv.splitMethod === 'delimiter'" class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">分隔符</label>
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
            </div>

            <!-- 索引字段 -->
            <div class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">索引字段</label>
              <el-checkbox-group v-model="adv.indexFields">
                <el-checkbox
                  v-for="opt in INDEX_FIELD_OPTIONS"
                  :key="opt.value"
                  :value="opt.value"
                  :label="opt.value"
                >
                  {{ opt.label }}
                </el-checkbox>
              </el-checkbox-group>
            </div>

            <!-- 文档摘要 + PPT 整页解析 -->
            <div class="parse-strategy-page__switch-row">
              <el-switch v-model="adv.enableDocSummary" />
              <span>文档摘要（AI 自动生成摘要）</span>
            </div>
            <div v-if="form.parseMethod === 'pptx'" class="parse-strategy-page__switch-row">
              <el-switch v-model="adv.enablePptWholePage" />
              <span>PPT 整页解析（每页作为完整单元）</span>
            </div>

            <!-- 表格处理模式 -->
            <div class="parse-strategy-page__field-group">
              <label class="parse-strategy-page__field-label">表格处理模式</label>
              <el-radio-group v-model="adv.tableMode">
                <el-radio-button
                  v-for="opt in TABLE_MODE_OPTIONS"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </el-radio-button>
              </el-radio-group>
              <span class="parse-strategy-page__field-hint">
                {{ TABLE_MODE_OPTIONS.find(o => o.value === adv.tableMode)?.desc }}
              </span>
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
</style>
