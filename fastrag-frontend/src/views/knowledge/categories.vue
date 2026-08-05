<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import * as api from '@/api'

const userStore = useUserStore()

const loading = ref(false)
const dataList = ref<any[]>([])
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const orgOptions = ref<any[]>([])

// 分类归属：所有用户（含 kb_admin）创建的分类都归属自己组织；仅超管可编辑任意分类（含未分配）
const isManager = computed(() => {
  const perms = userStore.permissions || []
  return perms.includes('*')
})

const colorPresets = ['#1890ff', '#52c41a', '#faad14', '#f56c6c', '#722ed1', '#13c2c2', '#eb2f96', '#5cdbd3']

async function loadData() {
  loading.value = true
  try {
    const res: any = await api.getKbCategories()
    dataList.value = Array.isArray(res) ? res : (res?.list || [])
  } finally { loading.value = false }
}
onMounted(() => {
  loadData()
  // 分类按组织隔离：加载组织下拉供管理员分配归属
  if (isManager.value) {
    api.getOrgFlat().then((res: any) => {
      orgOptions.value = Array.isArray(res) ? res : []
    }).catch(() => {})
  }
})

function handleAdd() {
  editingId.value = null
  formData.value = {
    name: '',
    description: '',
    color: '#1890ff',
    icon: 'Folder',
    sort: dataList.value.length,
    orgId: isManager.value ? '' : (userStore.userInfo?.orgId || ''),
  }
  dialogTitle.value = '新增分类'
  showDialog.value = true
}

function handleEdit(row: any) {
  editingId.value = row.id
  formData.value = { ...row }
  dialogTitle.value = '编辑分类'
  showDialog.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '提示', { type: 'warning' })
    await api.deleteKbCategory(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
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
    loadData()
  } catch { ElMessage.error('操作失败') }
}

function orgName(orgId: string): string {
  return orgOptions.value.find((o) => o.id === orgId)?.name || (orgId ? '本组织' : '未分配')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">知识库分类管理</div>
        <el-button v-permission="'kb:category:create'" type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增分类
        </el-button>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column label="分类名称" min-width="150">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <span v-if="row.color" class="color-dot" :style="{ background: row.color }" />
              <span style="font-weight:500">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="所属组织" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.orgId" size="small" type="info">{{ orgName(row.orgId) }}</el-tag>
            <el-tag v-else size="small" type="warning">未分配</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="知识库数量" width="120" align="center" />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'kb:category:edit'" link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'kb:category:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="分类名称" required>
          <el-input v-model="formData.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" placeholder="请输入分类描述" />
        </el-form-item>
        <el-form-item v-if="isManager" label="所属组织" required>
          <el-select v-model="formData.orgId" placeholder="请选择所属组织" style="width: 100%">
            <el-option
              v-for="org in orgOptions"
              :key="org.id"
              :label="org.name"
              :value="org.id"
            />
          </el-select>
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

.color-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
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
