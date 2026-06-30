<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const dbList = ref<any[]>([])
const query = ref({ keyword: '', dbType: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await api.getDatabases(query.value)
    dbList.value = Array.isArray(res) ? res : (res as any)?.list || []
  } catch {
    dbList.value = []
  } finally {
    loading.value = false
  }
}

const showDialog = ref(false)
const editingId = ref<string | null>(null)
const formData = ref({ name: '', dbType: 'mysql', host: '', port: 3306, username: 'root', password: '', dbName: '', status: 'connected' })

function handleAdd() { editingId.value = null; formData.value = { name: '', dbType: 'mysql', host: '', port: 3306, username: 'root', password: '', dbName: '', status: 'connected' }; showDialog.value = true }
function handleEdit(row: any) { editingId.value = row.id; formData.value = { ...row }; showDialog.value = true }

async function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入名称'); return }
  if (editingId.value) await api.updateDatabase(editingId.value, formData.value)
  else await api.createDatabase(formData.value)
  showDialog.value = false; await loadData(); ElMessage.success('保存成功')
}

async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确认删除该数据库实例？', '删除确认', { type: 'warning' })
    await api.deleteDatabase(row.id); await loadData(); ElMessage.success('删除成功') } catch {}
}

async function handleTestConn(row: any) {
  const res: any = await api.testDatabaseConnection(row.id)
  ElMessage.success(res?.connected ? '连接成功' : '连接失败')
}

async function handleQuery(row: any) {
  const { value } = await ElMessageBox.prompt('请输入SQL查询语句', '执行查询', { inputPlaceholder: 'SELECT * FROM ...' })
  if (!value) return
  const res: any = await api.queryDatabase(row.id, value)
  ElMessage.success(`查询完成，返回 ${res?.rowCount || 0} 行`)
}
function handleDownloadTemplate() {
  const csv = 'id,name,type,length,comment\nid,VARCHAR(32),,主键\nname,VARCHAR(128),,名称\nstatus,VARCHAR(16),,状态\ncreatedAt,DATETIME,,创建时间\n'
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=UTF-8' })
  const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'table_template.csv'
  a.click(); URL.revokeObjectURL(url); ElMessage.success('建表模板已下载')
}
function handleDownloadSample() {
  const sample = {
    name: '示例数据库',
    dbType: 'mysql',
    host: '192.168.1.200',
    port: 3306,
    username: 'admin',
    password: 'password123',
    dbName: 'sample_db',
    description: '这是一个示例数据库配置',
  }
  const blob = new Blob([JSON.stringify(sample, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'database_sample.json'
  a.click(); URL.revokeObjectURL(url); ElMessage.success('示例表单已下载')
}
function handleUploadConfig() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]
      if (!file) return
      const text = await file.text()
      const data = JSON.parse(text)
      dbList.value.unshift({
        id: 'db_imp_' + Date.now(),
        name: data.name || '导入数据库',
        dbType: data.dbType || 'mysql',
        host: data.host || 'localhost',
        port: data.port || 3306,
        username: data.username || 'root',
        dbName: data.dbName || '',
        status: 'connected',
        createdAt: new Date().toLocaleString('zh-CN'),
      })
      ElMessage.success(`数据库「${data.name || '导入数据库'}」已成功导入`)
    } catch {
      ElMessage.error('导入失败，请检查JSON格式是否正确')
    }
  }
  input.click()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">数据库实例管理</div>
        <div>
          <el-button size="small" @click="handleDownloadTemplate">下载建表模板</el-button>
          <el-button size="small" @click="handleDownloadSample">下载示例表单</el-button>
          <el-button size="small" @click="handleUploadConfig">上传表单</el-button>
          <el-button type="primary" @click="handleAdd">新增数据库</el-button>
        </div>
      </div>
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="搜索名称" clearable style="width:200px" @keyup.enter="loadData" />
        <el-select v-model="query.dbType" placeholder="类型" clearable style="width:140px" @change="loadData">
          <el-option label="MySQL" value="mysql" /><el-option label="PostgreSQL" value="postgresql" /><el-option label="ClickHouse" value="clickhouse" /><el-option label="SQLite" value="sqlite" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
      </div>
      <el-table :data="dbList" stripe>
        <el-table-column prop="name" label="名称" show-overflow-tooltip />
        <el-table-column prop="dbType" label="类型" width="90" />
        <el-table-column prop="host" label="主机" width="140" />
        <el-table-column prop="port" label="端口" width="70" />
        <el-table-column prop="dbName" label="库名" width="120" />
        <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status==='connected'?'success':'danger'" size="small">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="180"><template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleTestConn(row)">测试</el-button>
          <el-button link type="success" size="small" @click="handleQuery(row)">查询</el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
      <el-empty v-if="!dbList.length && !loading" description="暂无数据库实例" />
    </div>

    <el-dialog v-model="showDialog" :title="editingId?'编辑数据库':'新增数据库'" width="560px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="formData.name" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="formData.dbType" style="width:160px"><el-option label="MySQL" value="mysql" /><el-option label="PostgreSQL" value="postgresql" /><el-option label="ClickHouse" value="clickhouse" /><el-option label="SQLite" value="sqlite" /></el-select></el-form-item>
        <el-form-item label="主机"><el-input v-model="formData.host" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="formData.port" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="formData.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="formData.password" type="password" /></el-form-item>
        <el-form-item label="库名"><el-input v-model="formData.dbName" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog=false">取消</el-button><el-button type="primary" @click="handleSave">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
</style>
