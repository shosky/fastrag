<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAuth } from '@/composables/useAuth'
import * as api from '@/api'

/**
 * 知识库列表页左侧分类面板（nav-shell 修订 #18，ADP 知识库页对标）。
 * 全部/未分类/各分类（计数徽标）+ 分类名搜索（独立于知识库搜索词）+ 面板折叠
 * + 行内分类管理（新增/编辑/删除，受 kb:category:* 权限，对话框含所属组织[超管]）。
 */
const props = defineProps<{
  /** 当前选中分类（'' = 全部） */
  modelValue: string
  /** 知识库列表（用于前端计数口径） */
  knowledgeBases: any[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  /** 分类增删改后通知父页面刷新数据 */
  (e: 'changed'): void
}>()

const userStore = useUserStore()
const { hasPermission } = useAuth()

// ============ 折叠态 ============
const collapsed = ref(false)

// ============ 分类数据 ============
const categories = ref<any[]>([])
const categorySearch = ref('')

/** 分类名集合（用于判定知识库「未分类」） */
const categoryNames = computed(() => new Set(categories.value.map((c) => c.name)))

interface TreeRow {
  id: string
  name: string
  color?: string
  count: number
  uncategorized?: boolean
}

const rows = computed<TreeRow[]>(() => {
  const kbs = props.knowledgeBases
  const list: TreeRow[] = [{ id: '', name: '全部', count: kbs.length }]
  list.push({
    id: '__uncategorized__',
    name: '未分类',
    count: kbs.filter((kb: any) => !kb.category || !categoryNames.value.has(kb.category)).length,
    uncategorized: true,
  })
  for (const cat of categories.value) {
    list.push({
      id: cat.id || cat.name,
      name: cat.name,
      color: cat.color,
      count: kbs.filter((kb: any) => kb.category === cat.name || kb.category === cat.id).length,
    })
  }
  return list
})

/** 搜索只过滤分类列表（全部/未分类恒显） */
const visibleRows = computed(() => {
  const kw = categorySearch.value.trim()
  return rows.value.filter((r) => r.uncategorized || r.id === '' || !kw || r.name.includes(kw))
})

function select(id: string) {
  emit('update:modelValue', id === '__uncategorized__' ? '__uncategorized__' : id)
}

function isRowActive(row: TreeRow): boolean {
  if (row.id === '') return props.modelValue === ''
  if (row.uncategorized) return props.modelValue === '__uncategorized__'
  return props.modelValue === row.id
}

function handleSelect(row: TreeRow) {
  const next = isRowActive(row) && row.id !== '' ? '' : (row.uncategorized ? '__uncategorized__' : row.id)
  emit('update:modelValue', next)
}

async function loadCategories() {
  const catRes: any = await api.getKbCategories()
  categories.value = Array.isArray(catRes) ? catRes : catRes?.list || []
}

onMounted(loadCategories)

// ============ 分类管理（自 categories.vue 迁入，修订 #18） ============
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const orgTreeData = ref<any[]>([])

const isManager = computed(() => {
  const perms = userStore.permissions || []
  return perms.includes('*')
})

const colorPresets = ['#1890ff', '#52c41a', '#faad14', '#f56c6c', '#722ed1', '#13c2c2', '#eb2f96', '#5cdbd3']

function canCreate(): boolean {
  return hasPermission('kb:category:create' as any)
}

function canEdit(): boolean {
  return hasPermission('kb:category:edit' as any)
}

function canDelete(): boolean {
  return hasPermission('kb:category:delete' as any)
}

async function loadOrgTree() {
  if (!isManager.value) return
  try {
    const res: any = await api.getOrgTree()
    orgTreeData.value = Array.isArray(res) ? res : res?.list || []
  } catch {
    orgTreeData.value = []
  }
}

function handleAdd() {
  editingId.value = null
  formData.value = {
    name: '',
    description: '',
    color: '#1890ff',
    icon: 'Folder',
    sort: categories.value.length,
    orgId: isManager.value ? '' : (userStore.userInfo?.orgId || ''),
  }
  dialogTitle.value = '新增分类'
  showDialog.value = true
}

function handleRowCommand(cmd: string, row: TreeRow) {
  if (cmd === 'edit') handleEdit(row)
  if (cmd === 'delete') handleDelete(row)
}

function handleEdit(cat: any) {
  const row = categories.value.find((c) => (c.id || c.name) === cat.id)
  if (!row) return
  editingId.value = row.id
  formData.value = { ...row }
  dialogTitle.value = '编辑分类'
  showDialog.value = true
}

async function handleDelete(cat: any) {
  const row = categories.value.find((c) => (c.id || c.name) === cat.id)
  if (!row) return
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '提示', { type: 'warning' })
    await api.deleteKbCategory(row.id)
    ElMessage.success('删除成功')
    if (props.modelValue === row.id || props.modelValue === row.name) emit('update:modelValue', '')
    await loadCategories()
    emit('changed')
  } catch {}
}

function orgName(orgId: string): string {
  if (!orgId) return '未分配'
  function walk(nodes: any[]): string | undefined {
    for (const node of nodes) {
      if (node.id === orgId) return node.name
      if (node.children) {
        const found = walk(node.children)
        if (found) return found
      }
    }
  }
  return walk(orgTreeData.value) || '本组织'
}

async function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入分类名称'); return }
  if (!formData.value.orgId) { ElMessage.warning('请选择所属组织'); return }
  try {
    if (editingId.value) {
      await api.updateKbCategory(editingId.value, formData.value)
      ElMessage.success('更新成功')
    } else {
      await api.createKbCategory(formData.value)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    await loadCategories()
    emit('changed')
  } catch { ElMessage.error('操作失败') }
}

onMounted(loadOrgTree)

// 测试缝：CRUD 与对话框状态在 jsdom 内经处理器直驱
defineExpose({
  loadCategories,
  collapsed,
  categorySearch,
  formData,
  showDialog,
  handleAdd,
  handleEdit,
  handleDelete,
  handleSave,
})
</script>

<template>
  <!-- 折叠窄条 -->
  <div v-if="collapsed" class="category-panel category-panel--collapsed">
    <div class="collapse-btn" title="展开分类" @click="collapsed = false">
      <el-icon :size="14"><Expand /></el-icon>
    </div>
    <div class="collapsed-label">分类</div>
  </div>

  <!-- 展开面板 -->
  <div v-else class="category-panel">
    <div class="panel-head">
      <span class="panel-title">分类</span>
      <div class="panel-actions">
        <el-tooltip v-if="canCreate()" content="新增分类" placement="top">
          <el-icon class="action-icon" @click="handleAdd"><Plus /></el-icon>
        </el-tooltip>
        <el-tooltip content="收起" placement="top">
          <el-icon class="action-icon" @click="collapsed = true"><Fold /></el-icon>
        </el-tooltip>
      </div>
    </div>

    <el-input v-model="categorySearch" placeholder="分类名称" clearable size="small">
      <template #prefix><el-icon><Search /></el-icon></template>
    </el-input>

    <div class="category-list">
      <div
        v-for="row in visibleRows"
        :key="row.id || '__all__'"
        class="category-item"
        :class="{ active: isRowActive(row) }"
        @click="handleSelect(row)"
      >
        <span v-if="row.color" class="category-color-dot" :style="{ background: row.color }" />
        <span class="category-name" :class="{ uncategorized: row.uncategorized }">{{ row.name }}</span>
        <span class="category-count">{{ row.count }}</span>
        <span
          v-if="!row.uncategorized && row.id !== '' && (canEdit() || canDelete())"
          class="category-ops"
          @click.stop
        >
          <!-- 行操作「…」更多菜单（ADP 式，修订 #18） -->
          <el-dropdown trigger="click" @command="(cmd: string) => handleRowCommand(cmd, row)">
            <el-icon class="op-icon" title="更多操作"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="canEdit()" command="edit">编辑</el-dropdown-item>
                <el-dropdown-item v-if="canDelete()" command="delete">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </span>
      </div>
    </div>

    <!-- 新增/编辑分类对话框（自 categories.vue 迁入） -->
    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="分类名称" required>
          <el-input v-model="formData.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" placeholder="请输入分类描述" />
        </el-form-item>
        <el-form-item v-if="isManager" label="所属组织" required>
          <el-tree-select
            v-model="formData.orgId"
            :data="orgTreeData"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="请选择所属组织"
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-else label="所属组织">
          <el-tag type="info">{{ orgName(formData.orgId) }}</el-tag>
        </el-form-item>
        <el-form-item label="颜色">
          <div style="display:flex;gap:8px;flex-wrap:wrap">
            <div
              v-for="color in colorPresets"
              :key="color"
              class="color-pick"
              :class="{ 'color-pick--active': formData.color === color }"
              :style="{ background: color }"
              @click="formData.color = color"
            />
          </div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sort" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.category-panel {
  width: 200px;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-base;
  flex-shrink: 0;
  align-self: flex-start;

  &--collapsed {
    width: 40px;
    padding: $spacing-sm;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $spacing-sm;
  }
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-sm;
}

.panel-title {
  font-weight: 600;
  font-size: 14px;
}

.panel-actions {
  display: flex;
  gap: $spacing-xs;
}

.action-icon {
  color: $text-secondary;
  cursor: pointer;
  padding: 2px;
  border-radius: $radius-sm;

  &:hover {
    background: $bg-hover;
    color: $color-primary;
  }
}

.collapse-btn {
  cursor: pointer;
  color: $text-secondary;
  padding: 4px;
  border-radius: $radius-sm;

  &:hover {
    background: $bg-hover;
    color: $color-primary;
  }
}

.collapsed-label {
  writing-mode: vertical-lr;
  font-size: 12px;
  color: $text-secondary;
  letter-spacing: 2px;
  cursor: pointer;
}

.category-list {
  margin-top: $spacing-sm;
}

.category-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm;
  border-radius: $radius-sm;
  cursor: pointer;
  font-size: 13px;

  &:hover {
    background: $bg-hover;

    .category-ops {
      display: inline-flex;
    }

    .category-count {
      display: none;
    }
  }

  &.active {
    background: $bg-active;
    color: $color-primary;
  }

  .category-color-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .category-name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;

    &.uncategorized {
      color: $text-secondary;
    }
  }

  .category-count {
    font-size: 12px;
    color: $text-placeholder;
  }

  .category-ops {
    display: none;
    gap: 2px;
  }

  .op-icon {
    padding: 2px;
    border-radius: $radius-sm;
    color: $text-secondary;

    &:hover {
      background: $bg-active;
      color: $color-primary;
    }

    &--danger:hover {
      background: rgba(245, 108, 108, 0.1);
      color: $color-danger;
    }
  }
}

.color-pick {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;

  &--active {
    border-color: $text-primary;
    transform: scale(1.1);
  }

  &:hover {
    transform: scale(1.1);
  }
}
</style>
