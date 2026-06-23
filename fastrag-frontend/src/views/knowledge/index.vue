<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
import { useUserStore } from '@/stores/user'
import * as api from '@/api'

const router = useRouter()
const { hasPermission } = useAuth()
const userStore = useUserStore()
const activeTab = ref('all')
const searchKeyword = ref('')
const selectedCategory = ref('')
const showTagPanel = ref(false)
const selectedTags = ref<string[]>([])
const loading = ref(false)

// 分类
const categories = ref<any[]>([])

// 知识库列表
const knowledgeBases = ref<any[]>([])

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const [kbRes, catRes] = await Promise.all([
      api.getKnowledgeBases(),
      api.getKnowledgeBaseCategories(),
    ])
    knowledgeBases.value = (kbRes as any)?.list || (kbRes as any) || []
    categories.value = (catRes as any) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

// 标签列表（从知识库数据动态汇总）
const allTags = computed(() => {
  const set = new Set<string>()
  knowledgeBases.value.forEach((kb: any) => (kb.tags || []).forEach((t: string) => set.add(t)))
  return Array.from(set)
})

const filteredKBs = computed(() => {
  let list = knowledgeBases.value
  if (activeTab.value === 'my') {
    // 用 ACL 过滤：只显示当前用户可访问的知识库
    const userId = userStore.userInfo?.id || ''
    list = list.filter((kb: any) => kb.creator === userId || kb.creatorName === userStore.username)
  }
  if (searchKeyword.value) {
    list = list.filter((kb: any) => kb.name?.includes(searchKeyword.value))
  }
  if (selectedCategory.value) {
    list = list.filter((kb: any) => kb.category === selectedCategory.value)
  }
  if (selectedTags.value.length > 0) {
    list = list.filter((kb: any) => selectedTags.value.some(tag => (kb.tags || []).includes(tag)))
  }
  return list
})

function handleCategoryClick(id: string) {
  selectedCategory.value = selectedCategory.value === id ? '' : id
}

function handleTagToggle(tag: string) {
  const idx = selectedTags.value.indexOf(tag)
  if (idx > -1) {
    selectedTags.value.splice(idx, 1)
  } else {
    selectedTags.value.push(tag)
  }
}

function goToDetail(id: string) {
  router.push(`/knowledge/${id}`)
}

function goToCreate() {
  router.push('/knowledge/create')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="knowledge-header">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="全部知识库" name="all" />
        <el-tab-pane label="我的知识库" name="my" />
      </el-tabs>
      <el-button v-permission="'kb:create'" type="primary" @click="goToCreate">
        <el-icon><Plus /></el-icon>创建知识库
      </el-button>
    </div>

    <div class="knowledge-body">
      <!-- 左侧分类树 -->
      <div class="category-sidebar">
        <div class="sidebar-title">分类</div>
        <el-input v-model="searchKeyword" placeholder="搜索分类" clearable size="small">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="category-list">
          <div
            v-for="cat in categories"
            :key="cat.id"
            class="category-item"
            :class="{ active: selectedCategory === cat.id }"
            @click="handleCategoryClick(cat.id)"
          >
            <span>{{ cat.name }}</span>
            <el-badge :value="cat.count" type="info" />
          </div>
        </div>
      </div>

      <!-- 右侧内容区 -->
      <div class="knowledge-content">
        <!-- 搜索和标签筛选 -->
        <div class="filter-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索知识库"
            clearable
            style="width: 300px"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="showTagPanel = !showTagPanel">
            <el-icon><PriceTag /></el-icon>标签
          </el-button>
        </div>

        <!-- 标签面板 -->
        <div v-if="showTagPanel" class="tag-panel">
          <div class="tag-list">
            <el-check-tag
              v-for="tag in allTags"
              :key="tag"
              :checked="selectedTags.includes(tag)"
              @change="handleTagToggle(tag)"
            >
              {{ tag }}
            </el-check-tag>
          </div>
        </div>

        <!-- 知识库卡片列表 -->
        <div class="kb-grid">
          <div
            v-for="kb in filteredKBs"
            :key="kb.id"
            class="kb-card"
            @click="goToDetail(kb.id)"
          >
            <div class="kb-card-header">
              <el-icon :size="32" class="kb-card__icon"><Collection /></el-icon>
              <el-tag size="small" :type="kb.type === '团队' ? 'info' : 'warning'">{{ kb.type }}</el-tag>
            </div>
            <div class="kb-card-body">
              <h4>{{ kb.name }}</h4>
              <p class="desc">{{ kb.description }}</p>
              <div class="meta">
                <span>模型：{{ kb.embeddingModel }}</span>
                <span>维度：{{ kb.dimension }}</span>
              </div>
              <div class="tags">
                <el-tag v-for="tag in kb.tags" :key="tag" size="small" type="info">{{ tag }}</el-tag>
              </div>
              <div class="footer">
                <span>{{ kb.creator }}</span>
                <span>{{ kb.usedSize }}</span>
              </div>
            </div>
          </div>
        </div>

        <el-empty v-if="!filteredKBs.length && !loading" description="暂无知识库" />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.knowledge-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.knowledge-body {
  display: flex;
  gap: $spacing-base;
}

.category-sidebar {
  width: 200px;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  flex-shrink: 0;

  .sidebar-title {
    font-weight: 600;
    margin-bottom: $spacing-sm;
  }

  .category-list {
    margin-top: $spacing-sm;
  }
}

.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm;
  border-radius: $radius-sm;
  cursor: pointer;
  font-size: 13px;

  &:hover { background: $bg-hover; }
  &.active {
    background: $bg-active;
    color: $color-primary;
  }
}

.knowledge-content {
  flex: 1;
}

.tag-panel {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  margin-bottom: $spacing-base;
  display: flex;
  align-items: center;
  gap: $spacing-base;

  .tag-list {
    flex: 1;
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-sm;
  }
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: $spacing-base;
}

.kb-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid $border-lighter;

  &:hover {
    box-shadow: $shadow-base;
    border-color: $color-primary;
  }

  .kb-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-base;
  }

  &__icon {
    color: $color-primary;
  }

  h4 {
    margin: 0 0 $spacing-xs;
    font-size: 15px;
  }

  .desc {
    font-size: 12px;
    color: $text-secondary;
    margin-bottom: $spacing-sm;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
  }

  .meta {
    font-size: 12px;
    color: $text-secondary;
    margin-bottom: $spacing-sm;
    span { margin-right: $spacing-base; }
  }

  .tags {
    display: flex;
    gap: $spacing-xs;
    margin-bottom: $spacing-sm;
  }

  .footer {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: $text-secondary;
  }
}
</style>
