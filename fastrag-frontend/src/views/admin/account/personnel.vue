<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { PersonnelRecord } from '@/mock/auth-roles'
import type { RoleMeta } from '@/types/auth'
import type { OrgNode } from '@/mock/org'
import { PERMISSIONS } from '@/types/auth'
import { usePagination } from '@/composables/usePagination'
import * as api from '@/api'

// --- State ---
const searchName = ref('')
const filterStatus = ref('')
const filterRole = ref('')
const showDrawer = ref(false)
const drawerTitle = ref('添加人员')
const showRoleDialog = ref(false)
const editingId = ref('')

const formData = ref({
  username: '',
  realName: '',
  phone: '',
  orgId: '',
  password: '',
  email: '',
})

// --- 从 API 加载数据 ---
const personnelList = ref<PersonnelRecord[]>([])
const roleOptions = ref<RoleMeta[]>([])
const selectedRoles = ref<string[]>([])

async function loadData() {
  const [personnelRes, roleRes] = await Promise.all([
    api.getPersonnel({ page: 1, pageSize: 1000 }),
    api.getRoles(),
  ])
  personnelList.value = (personnelRes as any)?.list || (personnelRes as any) || []
  roleOptions.value = (roleRes as any)?.list || (roleRes as any) || []
}

onMounted(() => {
  loadData()
  loadOrgOptions()
})

// --- 搜索过滤 ---
const filteredPersonnel = computed(() => {
  return personnelList.value.filter((p) => {
    if (searchName.value && !p.realName.includes(searchName.value) && !p.username.includes(searchName.value)) {
      return false
    }
    if (filterStatus.value && p.status !== filterStatus.value) {
      return false
    }
    if (filterRole.value && !(p.roleNames || []).includes(filterRole.value)) {
      return false
    }
    return true
  })
})

// --- 分页 ---
const { currentPage, pageSize, handleCurrentChange, handleSizeChange } = usePagination(10)

const paginatedPersonnel = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredPersonnel.value.slice(start, start + pageSize.value)
})

// --- 组织选项（树形结构） ---
const orgTreeData = ref<OrgNode[]>([])

async function loadOrgOptions() {
  orgTreeData.value = (await api.getOrgTree()) as any || []
}

// --- CRUD ---
function handleAdd() {
  drawerTitle.value = '添加人员'
  editingId.value = ''
  formData.value = { username: '', realName: '', phone: '', orgId: '', password: '', email: '' }
  showDrawer.value = true
}

function handleEdit(row: PersonnelRecord) {
  drawerTitle.value = '编辑人员'
  editingId.value = row.id
  formData.value = {
    username: row.username,
    realName: row.realName,
    phone: row.phone,
    orgId: row.orgId || '',
    password: '',
    email: row.email,
  }
  showDrawer.value = true
}

function handleRoleConfig(row: PersonnelRecord) {
  editingId.value = row.id
  selectedRoles.value = [...(row.roleIds || [])]
  showRoleDialog.value = true
}

// --- Switch 状态切换 ---
async function handleStatusChange(row: PersonnelRecord) {
  const action = row.status === 'enabled' ? '禁用' : '启用'
  try {
    await api.updatePersonnelStatus(row.id, row.status === 'enabled' ? 'disabled' : 'enabled')
    await loadData()
    ElMessage.success(`${action}成功`)
  } catch {
    // 失败时恢复状态（重新加载）
    await loadData()
  }
}

async function handleSave() {
  if (!formData.value.username.trim() || !formData.value.realName.trim()) {
    ElMessage.warning('请输入用户名和姓名')
    return
  }

  if (!editingId.value && !formData.value.password.trim()) {
    ElMessage.warning('请输入初始密码')
    return
  }

  if (editingId.value) {
    // 编辑
    await api.updatePersonnel(editingId.value, {
      realName: formData.value.realName,
      phone: formData.value.phone,
      orgId: formData.value.orgId,
      email: formData.value.email,
    })
    await loadData()
    ElMessage.success('更新成功')
  } else {
    // 新增
    await api.createPersonnel({
      username: formData.value.username,
      realName: formData.value.realName,
      phone: formData.value.phone,
      email: formData.value.email,
      password: formData.value.password,
      orgId: formData.value.orgId,
      roleIds: [roleOptions.value[2]?.id || '3'],
      status: 'enabled',
    })
    await loadData()
    ElMessage.success('添加成功')
  }
  showDrawer.value = false
}

async function handleSaveRole() {
  if (!selectedRoles.value || selectedRoles.value.length === 0) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  await api.assignRoles(editingId.value, selectedRoles.value)
  await loadData()
  ElMessage.success('角色配置成功')
  showRoleDialog.value = false
}

function handleSearch() {
  // computed 自动过滤
}

function handleReset() {
  searchName.value = ''
  filterStatus.value = ''
  filterRole.value = ''
}
</script>

<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">人员管理</div>
        <div>
          <el-button type="primary" v-permission="PERMISSIONS.ADMIN_USER_CREATE" @click="handleAdd">添加人员</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-input v-model="searchName" placeholder="人员查找" clearable style="width: 150px" />
        <el-select v-model="filterStatus" placeholder="账号状态" clearable style="width: 120px">
          <el-option label="启用" value="enabled" />
          <el-option label="禁用" value="disabled" />
        </el-select>
        <el-select v-model="filterRole" placeholder="角色" clearable style="width: 150px">
          <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.name" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="paginatedPersonnel" stripe>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="phone" label="手机号码" width="130" />
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="name in (row.roleNames || [])" :key="name" size="small" style="margin: 1px 2px">
              {{ name }}
            </el-tag>
            <span v-if="!row.roleNames || row.roleNames.length === 0" style="color: #999">未分配</span>
          </template>
        </el-table-column>
        <el-table-column prop="orgName" label="组织/部门" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              v-permission="PERMISSIONS.ADMIN_USER_DISABLE"
              :model-value="row.status === 'enabled'"
              @change="handleStatusChange(row as PersonnelRecord)"
              inline-prompt
              active-text="启"
              inactive-text="禁"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" v-permission="PERMISSIONS.ADMIN_USER_EDIT" @click="handleEdit(row as PersonnelRecord)">编辑</el-button>
            <el-button link type="primary" size="small" v-permission="PERMISSIONS.ADMIN_USER_ROLE_CONFIG" @click="handleRoleConfig(row as PersonnelRecord)">角色配置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="personnel-page__pagination">
        <el-pagination
          v-if="filteredPersonnel.length > pageSize"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredPersonnel.length"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 添加/编辑人员抽屉 -->
    <el-drawer v-model="showDrawer" :title="drawerTitle" size="500px">
      <el-form label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="formData.username" placeholder="请输入用户名" :disabled="drawerTitle === '编辑人员'" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="formData.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号码">
          <el-input v-model="formData.phone" placeholder="请输入手机号码" />
        </el-form-item>
        <el-form-item v-if="drawerTitle === '添加人员'" label="初始密码">
          <el-input v-model="formData.password" type="password" placeholder="请输入初始密码" show-password />
        </el-form-item>
        <el-form-item label="组织/部门">
          <el-tree-select
            v-model="formData.orgId"
            :data="orgTreeData"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="请选择组织"
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="formData.email" placeholder="请输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDrawer = false">取消</el-button>
        <el-button type="primary" @click="handleSave">确定</el-button>
      </template>
    </el-drawer>

    <!-- 角色配置对话框（多选） -->
    <el-dialog v-model="showRoleDialog" title="角色配置" width="420px">
      <el-form label-width="80px">
        <el-form-item label="角色选择">
          <el-select v-model="selectedRoles" multiple placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="r in roleOptions"
              :key="r.id"
              :label="r.name"
              :value="r.id"
            >
              <span>{{ r.name }}</span>
              <span style="color: #999; font-size: 12px; margin-left: 8px">{{ r.description }}</span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRoleDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRole">确定</el-button>
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

.personnel-page__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
