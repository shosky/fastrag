<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

interface LayoutBlock {
  id: string
  type: 'full' | 'main-side' | 'side-main' | 'two-col' | 'three-col'
  title: string
  component: string
}

const layouts = ref<LayoutBlock[]>([
  { id: '1', type: 'full', title: '推荐知识库', component: '推荐知识库' },
  { id: '2', type: 'two-col', title: '热门应用 / 快捷入口', component: '热门应用' },
  { id: '3', type: 'main-side', title: '动态 / 我的关注', component: '动态' },
])

const selectedLayout = ref<string>('1')
const showComponentDrawer = ref(false)
const currentEditLayout = ref<LayoutBlock | null>(null)
const selectedComponent = ref('')
const componentTitle = ref('')

const availableLayouts = [
  { type: 'full', label: '通栏' },
  { type: 'main-side', label: '主次布局' },
  { type: 'side-main', label: '侧主布局' },
  { type: 'two-col', label: '双栏' },
  { type: 'three-col', label: '三栏' },
]

const availableComponents = [
  '推荐知识库', '快捷入口', '热门应用', '动态', '我的关注', '我的收藏',
]

function handleAddLayout(type: string) {
  const newId = String(Date.now())
  layouts.value.push({
    id: newId,
    type: type as LayoutBlock['type'],
    title: '新布局',
    component: '',
  })
  selectedLayout.value = newId
  ElMessage.success('已添加布局')
}

function handleSelectLayout(id: string) {
  selectedLayout.value = id
}

function handleOpenComponentSettings(layout: LayoutBlock) {
  currentEditLayout.value = layout
  selectedComponent.value = layout.component
  componentTitle.value = layout.title
  showComponentDrawer.value = true
}

function handleSaveComponent() {
  if (!componentTitle.value) {
    ElMessage.warning('请输入显示标题')
    return
  }
  if (currentEditLayout.value) {
    currentEditLayout.value.component = selectedComponent.value
    currentEditLayout.value.title = componentTitle.value
  }
  showComponentDrawer.value = false
  ElMessage.success('保存成功')
}

async function handleDeleteLayout(id: string) {
  try {
    await ElMessageBox.confirm('删除布局后，该区域中的组件配置会一并移除，确认删除？', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    layouts.value = layouts.value.filter(l => l.id !== id)
    if (selectedLayout.value === id && layouts.value.length > 0) {
      selectedLayout.value = layouts.value[0].id
    }
    ElMessage.success('删除成功')
  } catch {
    // 取消
  }
}

function handleSave() {
  ElMessage.success('更新成功')
}
</script>

<template>
  <div class="page-container">
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button @click="router.push('/workspace')">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <h3>自定义工作台</h3>
      </div>
      <div class="toolbar-right">
        <el-dropdown trigger="click" @command="handleAddLayout">
          <el-button type="primary">
            <el-icon><Plus /></el-icon>添加模块
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in availableLayouts" :key="item.type" :command="item.type">
                {{ item.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button type="primary" @click="handleSave">
          <el-icon><Check /></el-icon>更新保存
        </el-button>
      </div>
    </div>

    <div class="workspace-editor">
      <div class="layout-list">
        <div class="list-title">布局列表</div>
        <div
          v-for="layout in layouts"
          :key="layout.id"
          class="layout-item"
          :class="{ active: selectedLayout === layout.id }"
          @click="handleSelectLayout(layout.id)"
        >
          <div class="layout-info">
            <el-icon><Grid /></el-icon>
            <span>{{ layout.title || '未命名布局' }}</span>
          </div>
          <div class="layout-actions">
            <el-button link size="small" @click.stop="handleOpenComponentSettings(layout)">
              <el-icon><Setting /></el-icon>
            </el-button>
            <el-button link type="danger" size="small" @click.stop="handleDeleteLayout(layout.id)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <el-empty v-if="!layouts.length" description="暂无布局" :image-size="60" />
      </div>

      <div class="layout-preview">
        <div class="preview-title">预览区域</div>
        <div class="preview-content">
          <div
            v-for="layout in layouts"
            :key="layout.id"
            class="preview-block"
            :class="{ active: selectedLayout === layout.id }"
            @click="handleSelectLayout(layout.id)"
          >
            <div class="block-header">
              <span>{{ layout.title }}</span>
              <el-tag size="small" type="info">{{ availableLayouts.find(l => l.type === layout.type)?.label }}</el-tag>
            </div>
            <div class="block-content">
              <div v-if="layout.component" class="component-placeholder">
                <span>{{ layout.component }}</span>
              </div>
              <div v-else class="empty-placeholder">请配置组件</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-drawer v-model="showComponentDrawer" title="组件设置" size="400px">
      <el-form label-width="80px">
        <el-form-item label="组件选择">
          <el-select v-model="selectedComponent" placeholder="请选择组件类型" style="width: 100%">
            <el-option v-for="comp in availableComponents" :key="comp" :label="comp" :value="comp" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示标题" required>
          <el-input v-model="componentTitle" placeholder="请输入显示标题" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showComponentDrawer = false">取消</el-button>
        <el-button type="primary" @click="handleSaveComponent">确定</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-lg;
  .toolbar-left { display: flex; align-items: center; gap: $spacing-base; h3 { margin: 0; } }
  .toolbar-right { display: flex; gap: $spacing-sm; }
}

.workspace-editor {
  display: flex;
  gap: $spacing-base;
  height: calc(100vh - 200px);
}

.layout-list {
  width: 260px;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-base;
  overflow-y: auto;
  .list-title { font-weight: 600; margin-bottom: $spacing-base; padding-bottom: $spacing-sm; border-bottom: 1px solid $border-lighter; }
}

.layout-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm $spacing-base;
  border-radius: $radius-base;
  cursor: pointer;
  margin-bottom: $spacing-xs;
  &:hover { background: $bg-hover; }
  &.active { background: #ecf5ff; color: $color-primary; }
  .layout-info { display: flex; align-items: center; gap: $spacing-sm; }
  .layout-actions { opacity: 0; transition: opacity 0.2s; }
  &:hover .layout-actions { opacity: 1; }
}

.layout-preview {
  flex: 1;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-base;
  overflow-y: auto;
  .preview-title { font-weight: 600; margin-bottom: $spacing-base; padding-bottom: $spacing-sm; border-bottom: 1px solid $border-lighter; }
}

.preview-content { display: flex; flex-direction: column; gap: $spacing-base; }

.preview-block {
  border: 2px solid $border-lighter;
  border-radius: $radius-base;
  padding: $spacing-base;
  cursor: pointer;
  transition: all 0.2s;
  &:hover { border-color: $color-primary; }
  &.active { border-color: $color-primary; box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2); }
  .block-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-sm; font-weight: 600; font-size: 13px; }
}

.block-content {
  min-height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: $radius-sm;
}

.component-placeholder { color: $text-secondary; font-size: 13px; }
.empty-placeholder { color: $text-placeholder; font-size: 13px; }
</style>
