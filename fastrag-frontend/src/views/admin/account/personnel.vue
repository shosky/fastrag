<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { PersonnelRecord } from '@/mock/auth-roles'
import type { RoleMeta } from '@/types/auth'
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
  orgName: '',
  password: '',
  email: '',
})

// --- 从 API 加载数据 ---
const personnelList = ref<PersonnelRecord[]>([])
const roleOptions = ref<RoleMeta[]>([])
const selectedRole = ref('')

async function loadData() {
  const [personnelRes, roleRes] = await Promise.all([
    api.getPersonnel(),
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
    if (filterRole.value && p.roleName !== filterRole.value) {
      return false
    }
    return true
  })
})

// --- 组织选项（从 API 加载） ---
const orgOptions = ref<string[]>([])

async function loadOrgOptions() {
  orgOptions.value = (await api.getDepartments()) as any || []
}

// --- CRUD ---
function handleAdd() {
  drawerTitle.value = '添加人员'
  editingId.value = ''
  formData.value = { username: '', realName: '', phone: '', orgName: '', password: '', email: '' }
  showDrawer.value = true
}

function handleEdit(row: PersonnelRecord) {
  drawerTitle.value = '编辑人员'
  editingId.value = row.id
  formData.value = {
    username: row.username,
    realName: row.realName,
    phone: row.phone,
    orgName: row.orgName,
    password: '',
    email: row.email,
  }
  showDrawer.value = true
}

function handleRoleConfig(row: PersonnelRecord) {
  editingId.value = row.id
  selectedRole.value = row.roleId
  showRoleDialog.value = true
}

async function handleDisable(row: PersonnelRecord) {
  const action = row.status === 'enabled' ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}「${row.realName}」吗？`, `${action}确认`, { type: 'warning' })
    await api.updatePersonnel(row.id, { status: row.status === 'enabled' ? 'disabled' : 'enabled' })
    await loadData()
    ElMessage.success(`${action}成功`)
  } catch {}
}

async function handleResetPassword(row: PersonnelRecord) {
  try {
    await ElMessageBox.confirm(`重置后默认密码改为 123456，确认重置？`, '重置密码', { type: 'warning' })
    ElMessage.success('密码已重置为 123456')
  } catch {}
}

async function handleSave() {
  if (!formData.value.username.trim() || !formData.value.realName.trim()) {
    ElMessage.warning('请输入用户名和姓名')
    return
  }

  if (editingId.value) {
    // 编辑
    await api.updatePersonnel(editingId.value, {
      realName: formData.value.realName,
      phone: formData.value.phone,
      orgName: formData.value.orgName,
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
      orgName: formData.value.orgName,
      roleId: roleOptions.value[2]?.id || '3',
      roleName: roleOptions.value[2]?.name || '知识库用户',
      status: 'enabled',
    })
    await loadData()
    ElMessage.success('添加成功')
  }
  showDrawer.value = false
}

async function handleSaveRole() {
  if (!selectedRole.value) {
    ElMessage.warning('请选择角色')
    return
  }
  const role = roleOptions.value.find((r) => r.id === selectedRole.value)
  if (role) {
    await api.assignRole(editingId.value, role.id)
    await loadData()
    ElMessage.success('角色配置成功')
  }
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
          <el-button type="primary" @click="handleAdd">添加人员</el-button>
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

      <el-table :data="filteredPersonnel" stripe>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="phone" label="手机号码" width="130" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.roleName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orgName" label="组织/部门" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'danger'" size="small">
              {{ row.status === 'enabled' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row as PersonnelRecord)">编辑</el-button>
            <el-button link type="primary" size="small" @click="handleRoleConfig(row as PersonnelRecord)">角色配置</el-button>
            <el-dropdown trigger="click">
              <el-button link type="primary" size="small">更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleDisable(row as PersonnelRecord)">
                    {{ (row as PersonnelRecord).status === 'enabled' ? '禁用' : '启用' }}
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleResetPassword(row as PersonnelRecord)">重置密码</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">共 {{ filteredPersonnel.length }} 条</div>
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
          <el-select v-model="formData.orgName" placeholder="请选择" style="width: 100%">
            <el-option v-for="org in orgOptions" :key="org" :label="org" :value="org" />
          </el-select>
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

    <!-- 角色配置对话框 -->
    <el-dialog v-model="showRoleDialog" title="角色配置" width="400px">
      <el-form label-width="80px">
        <el-form-item label="角色选择">
          <el-select v-model="selectedRole" placeholder="请选择角色" style="width: 100%">
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

.table-footer {
  margin-top: $spacing-base;
  font-size: 13px;
  color: $text-secondary;
}
</style>
