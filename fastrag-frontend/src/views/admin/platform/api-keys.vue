<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const showCreateDialog = ref(false)

const formData = ref({
  name: '',
  description: '',
  expiry: '7天',
  permissions: [] as string[],
})

const keyList = ref([
  { id: '1', name: '截图上传', key: 'sk-ais-xxxx****xxxx', createdAt: '2026-06-01', expiry: '2026-06-08', desc: '用于截图上传功能' },
  { id: '2', name: '外部系统对接', key: 'sk-ais-yyyy****yyyy', createdAt: '2026-05-15', expiry: '2026-12-15', desc: '外部系统 API 对接' },
])

const permissionGroups = ref([
  { name: '知识管理', permissions: ['知识库分类', '知识库容器', '知识库文件', '知识库文件预览'] },
  { name: '应用管理', permissions: ['应用列表', '应用配置', '应用对话'] },
])

function handleCreate() {
  formData.value = { name: '', description: '', expiry: '7天', permissions: [] }
  showCreateDialog.value = true
}

function handleEdit(row: any) {
  formData.value = { name: row.name, description: row.desc, expiry: '7天', permissions: [] }
  showCreateDialog.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除？', '删除确认', { type: 'warning' })
    keyList.value = keyList.value.filter(k => k.id !== row.id)
    ElMessage.success('删除成功')
  } catch {}
}

function handleCopyKey(key: string) {
  ElMessage.success('复制成功')
}

function handleSave() {
  showCreateDialog.value = false
  ElMessage.success('创建成功')
}
</script>

<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="section-header">
        <div>
          <div class="section-title">开放密钥</div>
          <el-alert title="API key 生成后，将不会再次显示" type="warning" :closable="false" style="margin-top: 8px" />
        </div>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </div>

      <el-table :data="keyList" stripe>
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column label="开放密钥" width="200">
          <template #default="{ row }">
            <div class="key-cell">
              <span>{{ row.key }}</span>
              <el-button link size="small" @click="handleCopyKey(row.key)"><el-icon><CopyDocument /></el-icon></el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="120" />
        <el-table-column prop="expiry" label="到期时间" width="120" />
        <el-table-column prop="desc" label="描述信息" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showCreateDialog" title="创建 API Key" width="500px">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="formData.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="到期时间">
          <el-select v-model="formData.expiry" style="width: 100%">
            <el-option label="7天" value="7天" />
            <el-option label="30天" value="30天" />
            <el-option label="90天" value="90天" />
            <el-option label="365天" value="365天" />
            <el-option label="永不过期" value="永不过期" />
          </el-select>
        </el-form-item>
        <el-form-item label="权限列表">
          <div v-for="group in permissionGroups" :key="group.name" class="permission-group">
            <el-checkbox :label="group.name" />
            <div class="permission-items">
              <el-checkbox v-for="p in group.permissions" :key="p" :label="p" size="small" />
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">关闭</el-button>
        <el-button type="primary" @click="handleSave">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.key-cell {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
}

.permission-group {
  margin-bottom: $spacing-base;
  .permission-items { margin-left: $spacing-lg; display: flex; flex-wrap: wrap; gap: $spacing-sm; }
}
</style>
