<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { OrgNode } from '@/mock/org'
import { getOrgTree, createOrg, updateOrg, deleteOrg, getFlatOrgList } from '@/mock/org'

const searchName = ref('')
const showDrawer = ref(false)
const drawerTitle = ref('新增组织')
const editingId = ref('')

const formData = ref({
  name: '',
  alias: '',
  parentId: '',
})

// --- 从 mock 层加载组织树 ---
const orgTree = ref<OrgNode[]>([])

function loadOrgTree() {
  orgTree.value = getOrgTree()
}

onMounted(() => {
  loadOrgTree()
})

// --- 上级组织选项（扁平列表，排除自身） ---
const parentOptions = ref<{ id: string; name: string; path: string }[]>([])

function refreshParentOptions() {
  parentOptions.value = getFlatOrgList().filter((o) => o.id !== editingId.value)
}

// --- CRUD ---
function handleAdd() {
  drawerTitle.value = '新增组织'
  editingId.value = ''
  formData.value = { name: '', alias: '', parentId: '' }
  refreshParentOptions()
  showDrawer.value = true
}

function handleAddChild(parentId: string) {
  drawerTitle.value = '新增下级组织'
  editingId.value = ''
  formData.value = { name: '', alias: '', parentId }
  refreshParentOptions()
  showDrawer.value = true
}

function handleEdit(row: OrgNode) {
  drawerTitle.value = '编辑组织'
  editingId.value = row.id
  formData.value = { name: row.name, alias: row.alias || '', parentId: row.parentId || '' }
  refreshParentOptions()
  showDrawer.value = true
}

async function handleDelete(row: OrgNode) {
  try {
    await ElMessageBox.confirm(
      `确定删除组织「${row.name}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning' },
    )
    const result = deleteOrg(row.id)
    if (result.success) {
      loadOrgTree()
      ElMessage.success('删除成功')
    } else {
      ElMessage.warning(result.message || '删除失败')
    }
  } catch {}
}

function handleSave() {
  if (!formData.value.name.trim()) {
    ElMessage.warning('请输入组织名称')
    return
  }

  if (editingId.value) {
    updateOrg(editingId.value, { name: formData.value.name, alias: formData.value.alias })
    ElMessage.success('更新成功')
  } else {
    createOrg({ name: formData.value.name, alias: formData.value.alias, parentId: formData.value.parentId })
    ElMessage.success('创建成功')
  }
  loadOrgTree()
  showDrawer.value = false
}

function handleImport() {
  ElMessage.info('导入组织功能开发中')
}

function handleThirdPartyImport() {
  ElMessage.info('第三方平台导入功能开发中')
}
</script>

<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">组织管理</div>
        <div>
          <el-button @click="handleImport">导入组织</el-button>
          <el-button @click="handleThirdPartyImport">第三方平台导入</el-button>
          <el-button type="primary" @click="handleAdd">新增组织</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-input v-model="searchName" placeholder="搜索组织名称" clearable style="width: 200px" />
        <el-button type="primary">查询</el-button>
        <el-button @click="searchName = ''">重置</el-button>
      </div>

      <el-table :data="orgTree" stripe row-key="id" default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column prop="name" label="组织名称" min-width="200" />
        <el-table-column prop="level" label="组织层级" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.level === 1 ? 'danger' : row.level === 2 ? 'primary' : 'info'">
              L{{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="memberCount" label="人数" width="80" align="center" />
        <el-table-column prop="relatedCount" label="关联人数" width="80" align="center" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleAddChild(row.id)">新增下级</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row as OrgNode)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row as OrgNode)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑抽屉 -->
    <el-drawer v-model="showDrawer" :title="drawerTitle" size="400px">
      <el-form label-width="80px">
        <el-form-item label="组织名称" required>
          <el-input v-model="formData.name" placeholder="请输入组织名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="别名">
          <el-input v-model="formData.alias" placeholder="请输入别名（可选）" />
        </el-form-item>
        <el-form-item label="上级组织">
          <el-select v-model="formData.parentId" placeholder="无（顶级组织）" clearable style="width: 100%">
            <el-option
              v-for="org in parentOptions"
              :key="org.id"
              :label="org.path"
              :value="org.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDrawer = false">取消</el-button>
        <el-button type="primary" @click="handleSave">确定</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}
</style>
