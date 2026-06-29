<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const dbList = ref<any[]>([])
const loading = ref(false)
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const showDetailDialog = ref(false)
const detailData = ref<any>(null)
const formDefault = { name: '', dbType: 'mysql', host: '', port: 3306, database: '', username: '', password: '', description: '' }
const form = ref({ ...formDefault })

async function loadData() {
  loading.value = true
  try {
    const res: any = await api.getAppDbBindings(appId())
    dbList.value = Array.isArray(res) ? res : []
  } finally { loading.value = false }
  if (!dbList.value.length) {
    dbList.value = [
      { id: 'db1', name: '生产数据库', dbType: 'mysql', host: '192.168.1.100', port: 3306, database: 'production_db', username: 'app_user', description: '主业务数据库' },
      { id: 'db2', name: '日志数据库', dbType: 'postgresql', host: '192.168.1.101', port: 5432, database: 'log_db', username: 'log_reader', description: '日志存储' },
    ]
  }
}

function openAdd() { isEditing.value = false; editingId.value = ''; form.value = { ...formDefault }; showDialog.value = true }
function openEdit(row: any) { isEditing.value = true; editingId.value = row.id; form.value = { name: row.name, dbType: row.dbType || 'mysql', host: row.host || '', port: row.port || 3306, database: row.database || '', username: row.username || '', password: '', description: row.description || '' }; showDialog.value = true }
function openDetail(row: any) { detailData.value = row; showDetailDialog.value = true }

async function handleSave() {
  if (!form.value.name || !form.value.host) { ElMessage.warning('请填写必填项'); return }
  try {
    if (isEditing.value) { await api.updateAppDbBinding(appId(), editingId.value, form.value); ElMessage.success('已更新') }
    else { await api.createAppDbBinding(appId(), form.value); ElMessage.success('已创建') }
    showDialog.value = false; await loadData()
  } catch { ElMessage.error('操作失败') }
}

async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确认删除该数据库配置？', '确认', { type: 'warning' }); await api.deleteAppDbBinding(appId(), row.id); await loadData(); ElMessage.success('已删除') } catch {}
}

onMounted(loadData)
</script>
<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header"><div class="section-title">数据库配置</div><el-button type="primary" size="small" @click="openAdd">新增配置</el-button></div>
      <el-table :data="dbList" stripe size="small" style="margin-top:12px" v-loading="loading">
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="dbType" label="类型" width="80" />
        <el-table-column prop="host" label="主机" width="140" />
        <el-table-column prop="port" label="端口" width="70" />
        <el-table-column prop="database" label="数据库" width="120" />
        <el-table-column prop="username" label="用户名" width="100" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!dbList.length && !loading" description="暂无数据库配置" :image-size="60" />
    </div>
    <el-dialog v-model="showDialog" :title="isEditing ? '编辑数据库配置' : '新增数据库配置'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="form.dbType" style="width:160px"><el-option label="MySQL" value="mysql" /><el-option label="PostgreSQL" value="postgresql" /><el-option label="Oracle" value="oracle" /><el-option label="SQL Server" value="sqlserver" /></el-select></el-form-item>
        <el-form-item label="主机" required><el-input v-model="form.host" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="form.port" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="数据库名"><el-input v-model="form.database" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog=false">取消</el-button><el-button type="primary" @click="handleSave">{{ isEditing ? '保存' : '创建' }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="showDetailDialog" title="数据库详情" width="500px">
      <div v-if="detailData" style="display:grid;gap:12px">
        <div><strong>名称：</strong>{{ detailData.name }}</div>
        <div><strong>类型：</strong>{{ detailData.dbType }}</div>
        <div><strong>主机：</strong>{{ detailData.host }}:{{ detailData.port }}</div>
        <div><strong>数据库：</strong>{{ detailData.database }}</div>
        <div><strong>用户名：</strong>{{ detailData.username }}</div>
        <div><strong>描述：</strong>{{ detailData.description || '-' }}</div>
      </div>
      <template #footer><el-button @click="showDetailDialog=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; gap:8px; }
.section-title { font-size: 15px; font-weight: 600; }
.card-panel { background: var(--el-bg-color-overlay); border-radius: 8px; padding: 20px; border: 1px solid var(--el-border-color-light); }
</style>
