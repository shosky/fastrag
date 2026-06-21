<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const searchName = ref('')
const showDrawer = ref(false)
const drawerTitle = ref('新增团队')
const showMemberDialog = ref(false)

const formData = ref({
  name: '',
  description: '',
  members: [] as string[],
})

const teamList = ref([
  { id: '1', name: '项目管理团队', description: '负责项目管理和协调', memberCount: 8 },
  { id: '2', name: '产品研发团队', description: '负责产品研发和迭代', memberCount: 15 },
  { id: '3', name: '客户成功团队', description: '负责客户支持和成功', memberCount: 6 },
])

const memberList = ref([
  { id: '1', name: '张三', username: 'zhangsan' },
  { id: '2', name: '李四', username: 'lisi' },
  { id: '3', name: '王五', username: 'wangwu' },
])

function handleAdd() {
  drawerTitle.value = '新增团队'
  formData.value = { name: '', description: '', members: [] }
  showDrawer.value = true
}

function handleEdit(team: any) {
  drawerTitle.value = '编辑团队'
  formData.value = { name: team.name, description: team.description, members: ['1', '2'] }
  showDrawer.value = true
}

async function handleDelete(team: any) {
  try {
    await ElMessageBox.confirm('是否删除当前团队?', '删除确认', { type: 'warning' })
    teamList.value = teamList.value.filter(t => t.id !== team.id)
    ElMessage.success('删除成功')
  } catch {}
}

function handleViewMembers(team: any) {
  showMemberDialog.value = true
}

async function handleDeleteMember(member: any) {
  try {
    await ElMessageBox.confirm(`是否删除人员：${member.name}？`, '删除确认', { type: 'warning' })
    ElMessage.success('删除成功')
  } catch {}
}

function handleSave() {
  if (!formData.value.name) {
    ElMessage.warning('请输入团队名称')
    return
  }
  showDrawer.value = false
  ElMessage.success('保存成功')
}
</script>

<template>
  <div class="page-container">
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
    </div>

    <el-drawer v-model="showDrawer" :title="drawerTitle" size="400px">
      <el-form label-width="80px">
        <el-form-item label="团队名称" required>
          <el-input v-model="formData.name" placeholder="请输入团队名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="团队成员">
          <el-select v-model="formData.members" multiple placeholder="请选择成员" style="width: 100%">
            <el-option v-for="m in memberList" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDrawer = false">取消</el-button>
        <el-button type="primary" @click="handleSave">确定</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="showMemberDialog" title="团队成员" width="500px">
      <div class="filter-bar">
        <el-input placeholder="搜索成员" clearable style="width: 200px" />
        <el-button type="primary">查询</el-button>
      </div>
      <el-table :data="memberList" stripe size="small">
        <el-table-column prop="name" label="人员名称" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="handleDeleteMember(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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
