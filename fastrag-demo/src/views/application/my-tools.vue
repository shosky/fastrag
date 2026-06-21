<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTools,
  deleteTool,
  toggleToolEnabled,
  getTypeLabel,
  TOOL_CATEGORIES,
} from '@/mock/tools'
import type { Tool, ToolType } from '@/mock/tools'

const router = useRouter()

// --- 列表数据 ---
const tools = ref<Tool[]>(getTools())

const searchKeyword = ref('')
const selectedType = ref<ToolType | ''>('')

const filteredTools = computed(() => {
  let list = tools.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(
      (t) =>
        t.name.toLowerCase().includes(kw) ||
        t.identifier.toLowerCase().includes(kw) ||
        t.description.toLowerCase().includes(kw),
    )
  }
  if (selectedType.value) {
    list = list.filter((t) => t.type === selectedType.value)
  }
  return list
})

// --- 路由跳转 ---
function openCreateHttp() {
  router.push('/application/my-tools/create')
}

function openEdit(tool: Tool) {
  router.push(`/application/my-tools/${tool.id}/edit`)
}

async function handleDelete(tool: Tool) {
  try {
    await ElMessageBox.confirm(`确定删除工具「${tool.name}」吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    deleteTool(tool.id)
    tools.value = getTools()
    ElMessage.success('删除成功')
  } catch {}
}

function handleToggleEnabled(tool: Tool) {
  toggleToolEnabled(tool.id)
  tools.value = getTools()
  ElMessage.success(tool.enabled ? '已禁用' : '已启用')
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-title">
        <h2>我的工具</h2>
        <p>管理智能体可调用的工具，包括内置工具、知识库工具和自定义 HTTP 工具</p>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索工具名称 / 标识 / 描述"
          clearable
          style="width: 320px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="selectedType" placeholder="全部类型" clearable style="width: 140px">
          <el-option v-for="t in TOOL_CATEGORIES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="openCreateHttp">
          <el-icon><Plus /></el-icon>HTTP 工具
        </el-button>
      </div>
    </div>

    <!-- 工具列表 -->
    <div class="tools-grid" v-if="filteredTools.length">
      <div v-for="tool in filteredTools" :key="tool.id" class="tool-card">
        <div class="tool-card-body" @click="openEdit(tool)">
          <div class="tool-title-row">
            <div class="tool-icon">
              <el-icon :size="16" color="#909399"><SetUp /></el-icon>
            </div>
            <h4 :title="tool.name">{{ tool.name }}</h4>
            <span class="type-text">{{ getTypeLabel(tool.type) }}</span>
          </div>
          <p class="tool-identifier">{{ tool.identifier }}</p>
          <p class="tool-desc">{{ tool.description }}</p>
          <div class="tool-tags">
            <span v-for="tag in tool.tags" :key="tag" class="tag-text">{{ tag }}</span>
          </div>
        </div>
        <div class="tool-card-footer">
          <div class="usage">
            <el-icon><Calendar /></el-icon>
            <span>{{ tool.createdAt }}</span>
          </div>
          <div class="actions">
            <el-switch :model-value="tool.enabled" size="small" @change="handleToggleEnabled(tool)" />
            <el-button link type="primary" size="small" @click="openEdit(tool)">编辑</el-button>
            <el-dropdown trigger="click">
              <el-icon class="more-icon"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openEdit(tool)">编辑</el-dropdown-item>
                  <el-dropdown-item divided @click="handleDelete(tool)">
                    <span style="color: var(--el-color-danger)">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无工具，点击右上角创建 HTTP 工具" />
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-header {
  margin-bottom: $spacing-lg;

  h2 {
    margin: 0 0 $spacing-xs;
    font-size: 20px;
    color: $text-primary;
  }

  p {
    margin: 0;
    font-size: 14px;
    color: $text-secondary;
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
}

.toolbar-left {
  display: flex;
  gap: $spacing-sm;
}

.toolbar-right {
  display: flex;
  gap: $spacing-sm;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: $spacing-base;
}

.tool-card {
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  overflow: hidden;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;

  &:hover {
    box-shadow: $shadow-base;
    border-color: $border-base;
    transform: translateY(-2px);
  }
}

.tool-card-body {
  padding: $spacing-base;
  flex: 1;
  cursor: pointer;
}

.tool-title-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-xs;

  .tool-icon {
    flex-shrink: 0;
    width: 32px;
    height: 32px;
    border-radius: $radius-sm;
    background: $bg-hover;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  h4 {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    min-width: 0;
  }

  .type-text {
    flex-shrink: 0;
    font-size: 12px;
    color: $text-secondary;
  }
}

.tool-identifier {
  margin: 0 0 $spacing-xs;
  font-size: 12px;
  color: $text-secondary;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-desc {
  margin: 0 0 $spacing-sm;
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 36px;
}

.tool-tags {
  display: flex;
  gap: $spacing-xs;
  flex-wrap: wrap;

  .tag-text {
    font-size: 12px;
    color: $text-secondary;
    background: $bg-hover;
    border-radius: $radius-sm;
    padding: 2px 8px;
  }
}

.tool-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-sm $spacing-base;
  border-top: 1px solid $border-lighter;

  .usage {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: $text-secondary;
  }

  .actions {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
  }

  .more-icon {
    cursor: pointer;
    color: $text-secondary;
    padding: 2px;

    &:hover {
      color: $color-primary;
    }
  }
}
</style>
