<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const members = ref<Array<{ id: string; name: string; account: string; role: string; status: string }>>([])
const searchKeyword = ref('')
const saving = ref(false)

const showMemberDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const memberForm = ref({ name: '', account: '', role: '查看者' })

const showRoleDialog = ref(false)
const memberRoleForm = ref({ role: '查看者' })

const roleOptions = [
  { label: '管理员', value: '管理员' },
  { label: '编辑', value: '编辑' },
  { label: '查看者', value: '查看者' },
]

const filteredMembers = computed(() => {
  if (!searchKeyword.value) return members.value
  return members.value.filter(m =>
    m.name.includes(searchKeyword.value) || m.account.includes(searchKeyword.value)
  )
})

async function loadMembers() {
  try {
    const res: any = await api.getPersonnelSimple()
    const personnelList = Array.isArray(res) ? res : (res?.list || res?.records || [])
    const appRes: any = await api.getAppBasicConfig(appId())
    const advanced = appRes?.advancedOptions || appRes?.advanced || {}
    const appMembers = advanced?.members || []
    members.value = appMembers.length ? appMembers : []
  } catch (e) {
    members.value = []
  }
}

function handleAddMember() {
  isEditing.value = false
  editingId.value = ''
  memberForm.value = { name: '', account: '', role: '查看者' }
  showMemberDialog.value = true
}

function handleEditMember(row: any) {
  isEditing.value = true
  editingId.value = row.id
  memberForm.value = { name: row.name, account: row.account, role: row.role }
  showMemberDialog.value = true
}

async function handleSaveMember() {
  if (!memberForm.value.name || !memberForm.value.account) {
    ElMessage.warning('请填写完整信息')
    return
  }
  saving.value = true
  try {
    if (isEditing.value) {
      const member = members.value.find(m => m.id === editingId.value)
      if (member) Object.assign(member, memberForm.value)
      ElMessage.success('成员信息已更新')
    } else {
      members.value.push({
        id: String(Date.now()),
        name: memberForm.value.name,
        account: memberForm.value.account,
        role: memberForm.value.role,
        status: '启用',
      })
      ElMessage.success('成员添加成功')
    }
    showMemberDialog.value = false
    await persistMembers()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function handleAssignRole(row: any) {
  editingId.value = row.id
  memberRoleForm.value = { role: row.role }
  showRoleDialog.value = true
}

async function handleSaveRole() {
  saving.value = true
  try {
    const member = members.value.find(m => m.id === editingId.value)
    if (member) member.role = memberRoleForm.value.role
    showRoleDialog.value = false
    await persistMembers()
    ElMessage.success('角色分配成功')
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDeleteMember(id: string) {
  try {
    await ElMessageBox.confirm('确定移除该成员?', '删除确认', { type: 'warning' })
    members.value = members.value.filter(m => m.id !== id)
    await persistMembers()
    ElMessage.success('成员已移除')
  } catch (e) {}
}

async function persistMembers() {
  try {
    const current: any = await api.getAppBasicConfig(appId())
    const advanced = current?.advancedOptions || current?.advanced || {}
    await api.saveAppAdvanced(appId(), { ...advanced, members: members.value })
  } catch (e) {}
}

onMounted(loadMembers)
</script>

<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">成员管理</div>
      </div>
      <p style="font-size:13px;color:var(--el-text-color-secondary);margin-bottom:16px">
        管理应用的成员及其角色权限
      </p>
      <div class="toolbar">
        <el-button type="primary" size="small" @click="handleAddMember">
          <el-icon><Plus /></el-icon>添加成员
        </el-button>
        <el-input v-model="searchKeyword" placeholder="搜索成员..." prefix-icon="Search" style="width:260px" size="small" clearable />
      </div>
      <el-table :data="filteredMembers" border stripe size="small" style="width:100%;margin-top:12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="name" label="成员名称" min-width="140" />
        <el-table-column prop="account" label="账号" min-width="160" />
        <el-table-column prop="role" label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="row.role === '管理员' ? 'danger' : row.role === '编辑' ? 'warning' : 'info'" size="small">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditMember(row)">编辑</el-button>
            <el-button type="warning" link size="small" @click="handleAssignRole(row)">角色</el-button>
            <el-popconfirm title="确定移除该成员?" @confirm="handleDeleteMember(row.id)">
              <template #reference>
                <el-button type="danger" link size="small">移除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!filteredMembers.length" description="暂无成员" :image-size="60" />
    </div>

    <el-dialog v-model="showMemberDialog" :title="isEditing ? '编辑成员' : '添加成员'" width="480px">
      <el-form :model="memberForm" label-width="100px">
        <el-form-item label="成员名称" required>
          <el-input v-model="memberForm.name" placeholder="请输入成员名称" />
        </el-form-item>
        <el-form-item label="账号" required>
          <el-input v-model="memberForm.account" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="memberForm.role" style="width:100%">
            <el-option v-for="role in roleOptions" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMemberDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveMember">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRoleDialog" title="分配角色" width="400px">
      <el-form label-width="100px">
        <el-form-item label="成员">
          <span>{{ members.find(m => m.id === editingId)?.name }}</span>
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="memberRoleForm.role" style="width:100%">
            <el-option v-for="role in roleOptions" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRoleDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section { padding-bottom: 24px; }

.card-panel {
  background: var(--el-bg-color-overlay);
  border-radius: $radius-base;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.section-title { font-size: 15px; font-weight: 600; color: $text-primary; }

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $spacing-sm;
}
</style>
