<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const searchName = ref('')
const showDrawer = ref(false)
const drawerTitle = ref('新增团队')
const showMemberDialog = ref(false)
const loading = ref(false)
const currentTeamId = ref('')

const formData = ref({
  name: '',
  description: '',
})

const teamList = ref<any[]>([])
const memberList = ref<any[]>([])

async function loadTeams() {
  loading.value = true
  try {
    const res = await api.getTeams()
    teamList.value = (res as any) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadTeams)

function handleAdd() {
  drawerTitle.value = '新增团队'
  currentTeamId.value = ''
  formData.value = { name: '', description: '' }
  showDrawer.value = true
}

function handleEdit(team: any) {
  drawerTitle.value = '编辑团队'
  currentTeamId.value = team.id
  formData.value = { name: team.name, description: team.description }
  showDrawer.value = true
}

async function handleDelete(team: any) {
  try {
    await ElMessageBox.confirm('是否删除当前团队?', '删除确认', { type: 'warning' })
    await api.deleteTeam(team.id)
    await loadTeams()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleViewMembers(team: any) {
  currentTeamId.value = team.id
  try {
    const res = await api.getTeamMembers(team.id)
    memberList.value = (res as any) || []
  } catch {
    memberList.value = []
  }
  showMemberDialog.value = true
}

async function handleDeleteMember(member: any) {
  try {
    await ElMessageBox.confirm(`是否删除人员：${member.name}？`, '删除确认', { type: 'warning' })
    await api.removeTeamMember(currentTeamId.value, member.id)
    const res = await api.getTeamMembers(currentTeamId.value)
    memberList.value = (res as any) || []
    await loadTeams()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleSave() {
  if (!formData.value.name) {
    ElMessage.warning('请输入团队名称')
    return
  }
  if (drawerTitle.value === '新增团队') {
    await api.createTeam({ name: formData.value.name, description: formData.value.description })
  } else {
    await api.updateTeam(currentTeamId.value, { name: formData.value.name, description: formData.value.description })
  }
  showDrawer.value = false
  await loadTeams()
  ElMessage.success('保存成功')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">团队管理</div>
        <el-button type="primary" @click="handleAdd">新增团队</el-button>
      </div>
      <div class="filter-bar">
        <el-input v-model="searchName" placeholder="搜索团队名称" clearable style="width: 200px" />
        <el-button type="primary">查询</el-button>
        <el-button @click="searchName = ''">重置</el-button>
      </div>
      <div class="team-grid">
        <div v-for="team in teamList" :key="team.id" class="team-card">
          <h4>{{ team.name }}</h4>
          <p>{{ team.description }}</p>
          <div class="team-meta">
            <el-icon><User /></el-icon>
            <span>{{ team.memberCount }} 人</span>
          </div>
          <div class="team-actions">
            <el-button size="small" @click="handleEdit(team)">编辑</el-button>
            <el-button size="small" @click="handleViewMembers(team)">查看成员</el-button>
            <el-button size="small" type="danger" @click="handleDelete(team)">删除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-if="!teamList.length && !loading" description="暂无团队" />
    </div>

    <el-drawer v-model="showDrawer" :title="drawerTitle" size="400px">
      <el-form label-width="80px">
        <el-form-item label="团队名称" required>
          <el-input v-model="formData.name" placeholder="请输入团队名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDrawer = false">取消</el-button>
        <el-button type="primary" @click="handleSave">确定</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="showMemberDialog" title="团队成员" width="500px">
      <el-table :data="memberList" stripe size="small">
        <el-table-column prop="name" label="姓名" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="handleDeleteMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!memberList.length" description="暂无成员" :image-size="60" />
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

.team-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: $spacing-base;
}

.team-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  border: 1px solid $border-lighter;

  h4 { margin: 0 0 $spacing-xs; }
  p { font-size: 13px; color: $text-secondary; margin: 0 0 $spacing-sm; }

  .team-meta {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    font-size: 13px;
    color: $text-secondary;
    margin-bottom: $spacing-base;
  }

  .team-actions {
    display: flex;
    gap: $spacing-sm;
  }
}
</style>
