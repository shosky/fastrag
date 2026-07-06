<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled, Search, ArrowRight } from '@element-plus/icons-vue'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// 知识库配置（对接真实 API）
// ===========================================================================

// 全量知识库列表（从 API 加载，分页拉取第 1 页）
const allKbs = ref<any[]>([])

// 已绑定的知识库 ID 集合
const boundKbIds = ref<Set<string>>(new Set())

// 分类列表（从后端动态加载）
const categories = ref<Array<{ id: string; name: string }>>([
  { id: 'all', name: '全部' },
])

// UI 状态
const kbSearchKeyword = ref('')
const categorySearchKeyword = ref('')
const selectedCategory = ref('all')
const bindPersonalKB = ref('no')
const bindTeamKB = ref(true)
const saving = ref(false)
const loading = ref(false)

// 筛选后的知识库列表
const filteredKBs = computed(() => {
  let list = allKbs.value
  if (selectedCategory.value !== 'all') {
    // category 存储的是分类名称文本（如"技术文档"），按名称匹配
    list = list.filter((kb: any) => kb.category === selectedCategory.value)
  }
  if (kbSearchKeyword.value) {
    list = list.filter((kb: any) => kb.name?.includes(kbSearchKeyword.value))
  }
  return list
})

const selectedKBCount = computed(() => allKbs.value.filter((kb: any) => kb.selected).length)
const allSelected = computed(() => filteredKBs.value.length > 0 && filteredKBs.value.every((kb: any) => kb.selected))

function handleSelectAll() {
  const newVal = !allSelected.value
  filteredKBs.value.forEach((kb: any) => {
    kb.selected = newVal
  })
}

// ===========================================================================
// API 调用
// ===========================================================================

async function loadAllKbs() {
  loading.value = true
  try {
    // getKnowledgeBases 调用 GET /api/kb，返回分页结构 { list, total, page, pageSize }
    const res: any = await api.getKnowledgeBases({ page: 1, pageSize: 100 })
    allKbs.value = (res?.list || res?.records || [])
  } catch (e) {
    allKbs.value = []
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const res: any = await api.getKbCategories()
    const cats = Array.isArray(res) ? res : (res?.list || res?.records || [])
    // 后端返回分类 name 作为标识，前端用它做筛选值
    if (cats.length) {
      categories.value = [
        { id: 'all', name: '全部' },
        ...cats.map((c: any) => ({ id: c.name || c.id, name: c.name || c.id })),
      ]
    }
  } catch (e) {
    // 加载分类失败，保留默认"全部"
  }
}

async function loadBoundKbs() {
  try {
    const res: any = await api.getAppKbBindings(appId())
    const bindings = Array.isArray(res) ? res : (res?.list || res?.records || [])
    boundKbIds.value = new Set(bindings.map((b: any) => b.kbId))
    // 同步选中状态
    allKbs.value.forEach((kb: any) => {
      kb.selected = boundKbIds.value.has(kb.id)
    })
  } catch (e) {
    boundKbIds.value = new Set()
  }
}

async function handleSaveKB() {
  saving.value = true
  try {
    // 差量同步：当前勾选的 vs 已绑定的
    const currentlySelected = new Set(
      allKbs.value.filter((kb: any) => kb.selected).map((kb: any) => kb.id)
    )

    // 需要新增绑定的
    const toAdd = [...currentlySelected].filter(id => !boundKbIds.value.has(id))
    // 需要解绑的
    const toRemove = [...boundKbIds.value].filter(id => !currentlySelected.has(id))

    // 先解绑
    for (const kbId of toRemove) {
      // 找到绑定 ID（API 需要绑定记录 ID，不是 KB ID）
      const binding = await findBindingByKbId(kbId)
      if (binding) {
        await api.unbindAppKb(appId(), binding.id)
      }
    }

    // 再绑定
    for (const kbId of toAdd) {
      await api.bindAppKb(appId(), { kbId, priority: 0 })
    }

    // 更新已绑定集合
    boundKbIds.value = currentlySelected
    ElMessage.success(`知识库配置已保存（新增 ${toAdd.length}，解绑 ${toRemove.length}）`)
  } catch (e) {
    ElMessage.error('保存失败，请重试')
    // 回滚：重新加载已绑定状态
    await loadBoundKbs()
  } finally {
    saving.value = false
  }
}

async function findBindingByKbId(kbId: string): Promise<any> {
  try {
    const res: any = await api.getAppKbBindings(appId())
    const bindings = Array.isArray(res) ? res : (res?.list || res?.records || [])
    return bindings.find((b: any) => b.kbId === kbId)
  } catch (e) {
    return null
  }
}

// 导出/导入
function handleExportKB() {
  const data = {
    bindTeamKB: bindTeamKB.value,
    bindPersonalKB: bindPersonalKB.value,
    selectedKBs: allKbs.value.filter((kb: any) => kb.selected).map((kb: any) => ({ id: kb.id, name: kb.name })),
  }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'kb_bindings.json'
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('知识库配置已导出')
}

function handleImportKB() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = (e: any) => {
    try {
      const file = e.target.files[0]
      if (!file) return
      const reader = new FileReader()
      reader.onload = (ev: any) => {
        try {
          const data = JSON.parse(ev.target.result)
          if (data.bindTeamKB !== undefined) bindTeamKB.value = data.bindTeamKB
          if (data.bindPersonalKB !== undefined) bindPersonalKB.value = data.bindPersonalKB
          if (data.selectedKBs?.length) {
            const selectedIds = new Set(data.selectedKBs.map((k: any) => k.id))
            allKbs.value.forEach((kb: any) => { kb.selected = selectedIds.has(kb.id) })
          }
          ElMessage.success('知识库配置已导入')
        } catch (e) {
          ElMessage.error('导入文件格式错误')
        }
      }
      reader.readAsText(file)
    } catch (e) {
      ElMessage.error('读取文件失败')
    }
  }
  input.click()
}

onMounted(() => {
  loadCategories()
  loadAllKbs()
  loadBoundKbs()
})
</script>

<template>
  <div class="config-section">
    <h3>知识库配置</h3>
    <p class="desc">为应用绑定知识库，使其能够基于知识库内容回答问题</p>

    <div class="kb-config-options">
      <div class="kb-option-item">
        <span class="option-label">指定知识库检索</span>
        <el-tooltip content="开启后将仅在绑定的知识库中检索" placement="top">
          <el-icon><QuestionFilled /></el-icon>
        </el-tooltip>
        <el-switch v-model="bindTeamKB" />
      </div>

      <div class="kb-option-item">
        <span class="option-label">绑定个人知识库</span>
        <el-tooltip content="是否允许使用个人知识库" placement="top">
          <el-icon><QuestionFilled /></el-icon>
        </el-tooltip>
        <el-radio-group v-model="bindPersonalKB">
          <el-radio label="yes">是</el-radio>
          <el-radio label="no">否</el-radio>
        </el-radio-group>
      </div>
    </div>

    <div class="kb-binding-section">
      <div class="kb-binding-header">
        <span class="section-label">绑定团队知识库</span>
        <el-tooltip content="选择要绑定到此应用的团队知识库" placement="top">
          <el-icon><QuestionFilled /></el-icon>
        </el-tooltip>
      </div>

      <div class="kb-binding-content">
        <!-- 左侧分类 -->
        <div class="kb-categories">
          <el-input
            v-model="categorySearchKeyword"
            placeholder="搜索分类"
            clearable
            size="small"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <div class="category-list">
            <div
              v-for="cat in categories"
              :key="cat.id"
              class="category-item"
              :class="{ active: selectedCategory === cat.id }"
              @click="selectedCategory = cat.id"
            >
              <el-icon v-if="cat.id !== 'all'" class="expand-icon"><ArrowRight /></el-icon>
              <span>{{ cat.name }}</span>
            </div>
          </div>
        </div>

        <!-- 右侧知识库列表 -->
        <div class="kb-list">
          <div class="kb-list-header">
            <el-input
              v-model="kbSearchKeyword"
              placeholder="搜索知识库"
              clearable
              size="small"
              style="width: 300px"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <div class="kb-list-actions">
              <el-checkbox :model-value="allSelected" @change="handleSelectAll">全选</el-checkbox>
              <span class="selected-info">已选{{ selectedKBCount }}个知识库</span>
            </div>
          </div>

          <div class="kb-list-body">
            <div v-for="kb in filteredKBs" :key="kb.id" class="kb-item">
              <el-checkbox v-model="kb.selected" />
              <span class="kb-name">{{ kb.name }}</span>
              <span class="kb-meta">嵌入模型:{{ kb.embeddingModel || '-' }} | 维度:{{ kb.dimension || '-' }}</span>
            </div>
            <el-empty v-if="!filteredKBs.length" description="暂无知识库" />
          </div>
        </div>
      </div>
    </div>

    <div class="kb-footer">
      <el-button type="primary" :loading="saving" @click="handleSaveKB">保 存</el-button>
      <el-button style="margin-left:8px" @click="handleExportKB">导出配置</el-button>
      <el-button @click="handleImportKB">导入配置</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section {
  h3 { margin: 0 0 $spacing-lg; }
  .desc { color: $text-secondary; margin-bottom: $spacing-base; }
}

.kb-config-options {
  margin-bottom: $spacing-lg;
  padding: $spacing-base;
  background: $bg-hover;
  border-radius: $radius-base;
}

.kb-option-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;

  &:last-child {
    margin-bottom: 0;
  }

  .option-label {
    font-size: 14px;
    color: $text-primary;
  }
}

.kb-binding-section {
  margin-bottom: $spacing-lg;
}

.kb-binding-header {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-base;

  .section-label {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }
}

.kb-binding-content {
  display: flex;
  gap: $spacing-base;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  min-height: 400px;
}

.kb-categories {
  width: 200px;
  border-right: 1px solid $border-lighter;
  padding: $spacing-base;

  .category-list {
    margin-top: $spacing-sm;
  }

  .category-item {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    padding: $spacing-sm $spacing-base;
    cursor: pointer;
    border-radius: $radius-sm;
    font-size: 13px;
    color: $text-regular;

    &:hover {
      background: $bg-hover;
    }

    &.active {
      background: $bg-active;
      color: $color-primary;
      font-weight: 500;
    }

    .expand-icon {
      font-size: 12px;
    }
  }
}

.kb-list {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.kb-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-base;
  border-bottom: 1px solid $border-lighter;
}

.kb-list-actions {
  display: flex;
  align-items: center;
  gap: $spacing-base;

  .selected-info {
    font-size: 13px;
    color: $text-secondary;
  }
}

.kb-list-body {
  flex: 1;
  overflow-y: auto;
}

.kb-item {
  display: flex;
  align-items: center;
  padding: $spacing-base;
  border-bottom: 1px solid $border-extra-light;

  &:hover {
    background: $bg-hover;
  }

  .kb-name {
    flex: 1;
    font-size: 14px;
    color: $text-primary;
    margin-left: $spacing-sm;
  }

  .kb-meta {
    font-size: 12px;
    color: $text-secondary;
  }
}

.kb-footer {
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
}
</style>
