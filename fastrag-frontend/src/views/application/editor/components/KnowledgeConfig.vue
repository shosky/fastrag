<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, ArrowRight } from '@element-plus/icons-vue'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// 全量知识库列表
const allKbs = ref<any[]>([])
const boundKbIds = ref<string[]>([])
const categories = ref<Array<{ id: string; name: string }>>([
  { id: 'all', name: '全部' },
])

// UI 状态
const kbSearchKeyword = ref('')
const categorySearchKeyword = ref('')
const selectedCategory = ref('all')
const saving = ref(false)
const loading = ref(false)

// 筛选后的知识库列表
const filteredKBs = computed(() => {
  let list = allKbs.value
  if (selectedCategory.value !== 'all') {
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
    if (cats.length) {
      categories.value = [
        { id: 'all', name: '全部' },
        ...cats.map((c: any) => ({ id: c.name || c.id, name: c.name || c.id })),
      ]
    }
  } catch (e) {
    // keep default
  }
}

async function loadBoundKbs() {
  try {
    const res: any = await api.getAppKbBindings(appId())
    const bindings = Array.isArray(res) ? res : (res?.list || res?.records || [])
    boundKbIds.value = bindings.map((b: any) => b.kbId)
    allKbs.value.forEach((kb: any) => {
      kb.selected = boundKbIds.value.includes(kb.id)
    })
  } catch (e) {
    boundKbIds.value = []
  }
}

async function handleSave() {
  saving.value = true
  try {
    const currentlySelected = allKbs.value.filter((kb: any) => kb.selected).map((kb: any) => kb.id)
    const toAdd = currentlySelected.filter(id => !boundKbIds.value.includes(id))
    const toRemove = boundKbIds.value.filter(id => !currentlySelected.includes(id))

    for (const kbId of toRemove) {
      const binding = await findBindingByKbId(kbId)
      if (binding) {
        await api.unbindAppKb(appId(), binding.id)
      }
    }
    for (const kbId of toAdd) {
      await api.bindAppKb(appId(), { kbId, priority: 0 })
    }

    boundKbIds.value = currentlySelected
    ElMessage.success(`知识库配置已保存（新增 ${toAdd.length}，解绑 ${toRemove.length}）`)
  } catch (e) {
    ElMessage.error('保存失败，请重试')
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

onMounted(async () => {
  loadCategories()
  await loadAllKbs()
  await loadBoundKbs()
})
</script>

<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">绑定团队知识库</div>
        <div class="list-actions">
          <el-checkbox :model-value="allSelected" @change="handleSelectAll">全选</el-checkbox>
          <span class="selected-info">已选 {{ selectedKBCount }} 个知识库</span>
        </div>
      </div>

      <div class="kb-binding-content" v-loading="loading">
        <!-- 左侧分类 -->
        <div class="kb-categories">
          <el-input v-model="categorySearchKeyword" placeholder="搜索分类" clearable size="small">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-scrollbar style="height: 340px; margin-top: 8px">
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
          </el-scrollbar>
        </div>

        <!-- 右侧知识库列表 -->
        <div class="kb-list">
          <div class="kb-list-header">
            <el-input v-model="kbSearchKeyword" placeholder="搜索知识库" clearable size="small" style="width: 260px">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </div>
          <el-scrollbar style="height: 340px">
            <div v-for="kb in filteredKBs" :key="kb.id" class="kb-item">
              <el-checkbox v-model="kb.selected" />
              <span class="kb-name">{{ kb.name }}</span>
              <span class="kb-meta">{{ kb.embeddingModel || '-' }} | {{ kb.dimension || '-' }}维</span>
            </div>
            <el-empty v-if="!filteredKBs.length && !loading" description="暂无知识库" :image-size="48" />
          </el-scrollbar>
        </div>
      </div>
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

.list-actions {
  display: flex;
  align-items: center;
  gap: $spacing-base;

  .selected-info {
    font-size: 13px;
    color: $text-secondary;
  }
}

.kb-binding-content {
  display: flex;
  gap: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: $radius-base;
  min-height: 380px;
  background: var(--el-bg-color);
}

.kb-categories {
  width: 180px;
  border-right: 1px solid var(--el-border-color-lighter);
  padding: $spacing-sm;

  .category-item {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    padding: 8px 12px;
    cursor: pointer;
    border-radius: $radius-sm;
    font-size: 13px;
    color: var(--el-text-color-regular);

    &:hover { background: var(--el-fill-color-light); }
    &.active {
      background: var(--el-color-primary-light-9);
      color: var(--el-color-primary);
      font-weight: 500;
    }

    .expand-icon { font-size: 12px; }
  }
}

.kb-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: $spacing-sm;
}

.kb-list-header {
  margin-bottom: $spacing-sm;
}

.kb-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: $radius-sm;
  transition: background 0.15s;

  &:hover { background: var(--el-fill-color-light); }

  .kb-name {
    flex: 1;
    font-size: 14px;
    color: var(--el-text-color-primary);
    margin-left: $spacing-sm;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .kb-meta {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
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
