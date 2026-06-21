<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'

const kbSearchKeyword = ref('')
const categorySearchKeyword = ref('')
const selectedCategory = ref('all')
const bindPersonalKB = ref('no')
const bindTeamKB = ref(true)

const categories = ref([
  { id: 'all', name: '全部' },
  { id: 'market', name: '市场运营' },
  { id: 'project', name: '项目管理' },
  { id: 'product', name: '产品研发' },
  { id: 'research', name: '市场研究' },
  { id: 'rag', name: 'RAG测试集' },
  { id: 'learning', name: '学习资源' },
  { id: 'enterprise', name: '企业管理' },
])

const availableKBs = ref([
  { id: '1', name: '物产定制化功能操作手册', embeddingModel: 'text-embedding-v4', dimension: 1024, category: 'product', selected: false },
  { id: '2', name: '企业资质管理', embeddingModel: 'bge-m3', dimension: 1024, category: 'enterprise', selected: true },
  { id: '3', name: '心愿汇', embeddingModel: 'bge-m3', dimension: 1024, category: 'project', selected: false },
  { id: '4', name: 'SaaP攻略2026', embeddingModel: 'bge-m3', dimension: 1024, category: 'market', selected: false },
  { id: '5', name: '数字员工宣发', embeddingModel: 'bge-m3', dimension: 1024, category: 'market', selected: false },
  { id: '6', name: '2025年会', embeddingModel: 'bge-m3', dimension: 1024, category: 'enterprise', selected: false },
  { id: '7', name: '2024年度年会珍贵记录', embeddingModel: 'bge-m3', dimension: 1024, category: 'enterprise', selected: true },
  { id: '8', name: '深港科创项目知识库', embeddingModel: 'bge-m3', dimension: 1024, category: 'project', selected: false },
  { id: '9', name: '市场营销和商机知识库', embeddingModel: 'bge-m3', dimension: 1024, category: 'market', selected: false },
  { id: '10', name: '数字员工开发库', embeddingModel: 'bge-m3', dimension: 1024, category: 'product', selected: false },
  { id: '11', name: '中汇项目管理知识库', embeddingModel: 'bge-m3', dimension: 1024, category: 'project', selected: false },
])

const filteredKBs = computed(() => {
  let list = availableKBs.value
  if (selectedCategory.value !== 'all') {
    list = list.filter(kb => kb.category === selectedCategory.value)
  }
  if (kbSearchKeyword.value) {
    list = list.filter(kb => kb.name.includes(kbSearchKeyword.value))
  }
  return list
})

const selectedKBCount = computed(() => availableKBs.value.filter(kb => kb.selected).length)
const allSelected = computed(() => filteredKBs.value.length > 0 && filteredKBs.value.every(kb => kb.selected))

function handleSelectAll() {
  const newVal = !allSelected.value
  filteredKBs.value.forEach(kb => {
    kb.selected = newVal
  })
}

function handleSaveKB() {
  ElMessage.success('知识库配置保存成功')
}
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
              <span class="kb-meta">嵌入模型:{{ kb.embeddingModel }} | 维度:{{ kb.dimension }}</span>
            </div>
            <el-empty v-if="!filteredKBs.length" description="暂无知识库" />
          </div>
        </div>
      </div>
    </div>

    <div class="kb-footer">
      <el-button type="primary" @click="handleSaveKB">保 存</el-button>
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
