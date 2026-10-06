<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import * as api from '@/api'

// ===========================================================================
// KB 多选弹层（ChatComposer 内 popover 消费）
// 数据与分类过滤收敛自 KnowledgeConfig.vue:137-188 惯例
// （getKnowledgeBases({page:1,pageSize:100}) + getKbCategories）。
// 挂预设时整层禁用（disabled），提示「知识库范围由应用决定」由外层 tooltip 呈现。
// ===========================================================================

const props = defineProps<{
  modelValue: string[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void
}>()

const visible = ref(false)
const loading = ref(false)
const loaded = ref(false)

const allKbs = ref<Array<{ id: string; name: string; category?: string }>>([])
const categories = ref<Array<{ id: string; name: string }>>([{ id: 'all', name: '全部' }])
const selectedCategory = ref('all')
const kbSearchKeyword = ref('')

const filteredKBs = computed(() => {
  let list = allKbs.value
  if (selectedCategory.value !== 'all') {
    list = list.filter(kb => kb.category === selectedCategory.value)
  }
  if (kbSearchKeyword.value) {
    list = list.filter(kb => kb.name?.includes(kbSearchKeyword.value))
  }
  return list
})

function isSelected(id: string): boolean {
  return props.modelValue.includes(id)
}

function toggleKb(id: string) {
  const next = isSelected(id)
    ? props.modelValue.filter(v => v !== id)
    : [...props.modelValue, id]
  emit('update:modelValue', next)
}

function clearAll() {
  emit('update:modelValue', [])
}

async function load() {
  if (loaded.value) return
  loading.value = true
  try {
    const [kbRes, catRes] = await Promise.all([
      api.getKnowledgeBases({ page: 1, pageSize: 100 }) as Promise<any>,
      api.getKbCategories().catch(() => null) as Promise<any>,
    ])
    allKbs.value = (kbRes?.list || kbRes?.records || []).map((k: any) => ({
      id: k.id, name: k.name, category: k.category,
    }))
    const cats = Array.isArray(catRes) ? catRes : (catRes?.list || catRes?.records || [])
    if (cats.length) {
      categories.value = [
        { id: 'all', name: '全部' },
        ...cats.map((c: any) => ({ id: c.name || c.id, name: c.name || c.id })),
      ]
    }
    loaded.value = true
  } catch {
    allKbs.value = []
  } finally {
    loading.value = false
  }
}

watch(visible, (v) => { if (v) load() })
</script>

<template>
  <el-popover v-model:visible="visible" :width="420" trigger="click" :disabled="disabled" placement="top-start">
    <template #reference>
      <button class="kb-picker-btn" :disabled="disabled" title="选择知识库范围">
        <el-icon :size="14"><Collection /></el-icon>
        <span>知识库</span>
        <span v-if="modelValue.length" class="kb-count">{{ modelValue.length }}</span>
      </button>
    </template>
    <div class="kb-scope-picker">
      <div class="kb-categories">
        <div class="kb-categories__list">
          <div
            v-for="cat in categories"
            :key="cat.id"
            class="category-item"
            :class="{ active: selectedCategory === cat.id }"
            @click="selectedCategory = cat.id"
          >
            <span>{{ cat.name }}</span>
          </div>
        </div>
      </div>
      <div class="kb-list">
        <el-input v-model="kbSearchKeyword" placeholder="搜索知识库" clearable size="small">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-scrollbar class="kb-list__scroll" v-loading="loading">
          <template v-if="!loading">
            <div v-for="kb in filteredKBs" :key="kb.id" class="kb-item" @click="toggleKb(kb.id)">
              <el-checkbox :model-value="isSelected(kb.id)" @click.prevent />
              <span class="kb-name">{{ kb.name }}</span>
            </div>
            <el-empty v-if="!filteredKBs.length" description="暂无知识库，请先创建知识库" :image-size="48" />
          </template>
        </el-scrollbar>
        <div class="kb-list__footer">
          <span class="selected-info">已选 {{ modelValue.length }} 个</span>
          <el-button link size="small" :disabled="!modelValue.length" @click="clearAll">清空</el-button>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<style lang="scss" scoped>
.kb-picker-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid #eef0f0;
  border-radius: 6px;
  background: transparent;
  color: #4c4d4d;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  &:hover:not(:disabled) { border-color: #3996ae; color: #046a82; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  .kb-count {
    min-width: 16px;
    height: 16px;
    line-height: 16px;
    padding: 0 4px;
    border-radius: 8px;
    background: #046a82;
    color: #fff;
    font-size: 11px;
    text-align: center;
  }
}

.kb-scope-picker {
  display: flex;
  gap: 0;
  height: 300px;
  border: 1px solid #f0f2f2;
  border-radius: 6px;
  overflow: hidden;
}

.kb-categories {
  width: 110px;
  border-right: 1px solid #f0f2f2;
  &__list {
    height: 100%;
    overflow-y: auto;
  }
  .category-item {
    padding: 8px 12px;
    font-size: 13px;
    color: #4c4d4d;
    cursor: pointer;
    &:hover { background: #f5f7f7; }
    &.active {
      background: #f7fbfd;
      color: #046a82;
      font-weight: 500;
    }
  }
}

.kb-list {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 8px;
  &__scroll {
    flex: 1;
    margin-top: 6px;
  }
  .kb-item {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 8px;
    border-radius: 4px;
    cursor: pointer;
    &:hover { background: #f5f7f7; }
    .kb-name {
      flex: 1;
      min-width: 0;
      font-size: 13px;
      color: #151616;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: 6px;
    border-top: 1px solid #f0f2f2;
    .selected-info { font-size: 12px; color: #979999; }
  }
}
</style>
