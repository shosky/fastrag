<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { RoleMeta } from '@/types/auth'
import { PERMISSION_TREE, ROLE_LABELS } from '@/types/auth'
import * as api from '@/api'

const router = useRouter()

// --- State ---
const searchName = ref('')
const showDialog = ref(false)
const dialogTitle = ref('新增角色')
const editingId = ref('')
const treeRef = ref()

const formData = ref({
  name: '',
  description: '',
  permissions: [] as string[],
})

// --- 从 API 加载角色列表 ---
const roleList = ref<RoleMeta[]>([])

async function loadRoles() {
  roleList.value = (await api.getRoles()) as any || []
}

onMounted(() => {
  loadRoles()
})

// --- 搜索过滤 ---
const filteredRoles = computed(() => {
  if (!searchName.value) return roleList.value
  return roleList.value.filter((r) =>
    r.name.includes(searchName.value) || r.description.includes(searchName.value),
  )
})

// --- 当前编辑的角色是否为默认角色（默认角色权限不可改） ---
const isEditingDefault = computed(() => {
  if (!editingId.value) return false
  const role = roleList.value.find((r) => r.id === editingId.value)
  return role?.isDefault || false
})

// --- CRUD ---
function handleAdd() {
  dialogTitle.value = '新增角色'
  editingId.value = ''
  formData.value = { name: '', description: '', permissions: [] }
  showDialog.value = true
}

function handleEdit(row: RoleMeta) {
  dialogTitle.value = '编辑角色'
  editingId.value = row.id
  formData.value = {
    name: row.name,
    description: row.description,
    permissions: [...row.permissions],
  }
  showDialog.value = true
}

async function handleDelete(row: RoleMeta) {
  if (row.isDefault) {
    ElMessage.warning('默认角色不可删除')
    return
  }
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除？', '删除确认', { type: 'warning' })
    await api.deleteRole(row.id)
    await loadRoles()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleSetDefault(row: RoleMeta) {
  try {
    await ElMessageBox.confirm(`确认将「${row.name}」设为默认角色？`, '设为默认', { type: 'warning' })
    await api.setDefaultRole(row.id)
    await loadRoles()
    ElMessage.success('设置成功')
  } catch {}
}

async function handleSave() {
  if (!formData.value.name.trim()) {
    ElMessage.warning('请输入角色名称')
    return
  }

  // 从 el-tree 获取当前勾选的权限
  const checkedKeys = treeRef.value?.getCheckedKeys(false) || []
  const halfCheckedKeys = treeRef.value?.getHalfCheckedKeys() || []
  const allPerms = [...checkedKeys, ...halfCheckedKeys].filter(
    (key) => !PERMISSION_TREE.some((group) => group.key === key),
  )

  if (editingId.value) {
    await api.updateRole(editingId.value, {
      name: formData.value.name,
      description: formData.value.description,
      permissions: isEditingDefault.value ? formData.value.permissions : allPerms,
    })
    await loadRoles()
    ElMessage.success('更新成功')
  } else {
    await api.createRole({
      name: formData.value.name,
      description: formData.value.description,
      permissions: allPerms,
    })
    await loadRoles()
    ElMessage.success('创建成功')
  }
  showDialog.value = false
}

// --- 跳转权限配置页 ---
function handlePermConfig(row: RoleMeta) {
  router.push(`/admin/account/roles/${row.id}/permissions`)
}

function handleSearch() {}
function handleReset() {
  searchName.value = ''
}
</script>

<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">角色管理</div>
        <el-button type="primary" @click="handleAdd">新增角色</el-button>
      </div>

      <div class="filter-bar">
        <el-input v-model="searchName" placeholder="搜索角色名称" clearable style="width: 200px" />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="filteredRoles" stripe>
        <el-table-column prop="name" label="角色名称" min-width="120">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.isDefault" type="success" size="small" style="margin-left: 8px">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="权限描述" min-width="200" />
        <el-table-column label="权限数" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">
              {{ row.permissions.includes('*') ? '全部' : row.permissions.length }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handlePermConfig(row as RoleMeta)">权限配置</el-button>
            <el-button link type="primary" size="small" @click="handleSetDefault(row as RoleMeta)">设为默认</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row as RoleMeta)">编辑</el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="(row as RoleMeta).isDefault"
              @click="handleDelete(row as RoleMeta)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="showDialog" :title="dialogTitle" width="650px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="角色名称" required>
          <el-input v-model="formData.name" placeholder="请输入角色名称" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="权限描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="请输入权限描述" />
        </el-form-item>
        <el-form-item label="权限配置">
          <div v-if="isEditingDefault" class="roles-page__default-hint">
            <el-tag type="warning" size="small">默认角色</el-tag>
            <span>默认角色拥有全部权限，不可修改。</span>
          </div>
          <el-tree
            v-else
            ref="treeRef"
            :data="PERMISSION_TREE"
            show-checkbox
            node-key="key"
            :default-checked-keys="formData.permissions"
            :props="{ children: 'children', label: 'label' }"
            default-expand-all
          />
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

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.roles-page {
  &__default-hint {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    background: #fff3e0;
    border-radius: $radius-sm;
    font-size: 13px;
    color: $text-secondary;
  }
}
</style>
