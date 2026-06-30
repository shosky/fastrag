<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const dbList = ref<any[]>([])
const allDatabases = ref<any[]>([])  // 系统中已有的数据库实例
const loading = ref(false)
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const showDetailDialog = ref(false)
const detailData = ref<any>(null)
const formDefault = { dbId: '', alias: '', allowedTables: '' }
const form = ref({ ...formDefault })

async function loadData() {
  loading.value = true
  try {
    // 加载当前应用的数据库绑定
    const res: any = await api.getAppDbBindings(appId())
    dbList.value = Array.isArray(res) ? res : []
  } finally { loading.value = false }
}

/** 加载系统中已有的数据库实例列表（供选择添加） */
async function loadAllDatabases() {
  try {
    const res: any = await api.getDatabases()
    allDatabases.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
  } catch {
    allDatabases.value = []
  }
}

function openAdd() {
  isEditing.value = false
  editingId.value = ''
  form.value = { ...formDefault }
  loadAllDatabases()  // 加载已有数据库列表供选择
  showDialog.value = true
}

function openEdit(row: any) {
  isEditing.value = true
  editingId.value = row.id
  form.value = {
    dbId: row.dbId || '',
    alias: row.alias || '',
    allowedTables: row.allowedTables || '',
  }
  loadAllDatabases()
  showDialog.value = true
}

function openDetail(row: any) { detailData.value = row; showDetailDialog.value = true }

/** 根据 dbId 获取数据库实例的详细信息 */
function getDbInfo(dbId: string) {
  return allDatabases.value.find(d => d.id === dbId)
}

async function handleSave() {
  if (!form.value.dbId) { ElMessage.warning('请选择已有数据库'); return }
  try {
    if (isEditing.value) { await api.updateAppDbBinding(appId(), editingId.value, form.value); ElMessage.success('已更新') }
    else { await api.createAppDbBinding(appId(), form.value); ElMessage.success('已添加') }
    showDialog.value = false; await loadData()
  } catch { ElMessage.error('操作失败') }
}

async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确认解除该数据库绑定？', '确认', { type: 'warning' }); await api.deleteAppDbBinding(appId(), row.id); await loadData(); ElMessage.success('已解除绑定') } catch {}
}

onMounted(loadData)
</script>
<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">数据库配置</div>
        <el-button type="primary" size="small" @click="openAdd">添加已有数据库</el-button>
      </div>
      <p style="font-size:13px;color:#909399;margin-bottom:12px">从系统中已注册的数据库实例中选择添加，绑定到当前应用</p>
      <el-table :data="dbList" stripe size="small" style="margin-top:12px" v-loading="loading">
        <el-table-column label="数据库名称" min-width="140">
          <template #default="{ row }">{{ getDbInfo(row.dbId)?.name || row.dbId || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ getDbInfo(row.dbId)?.dbType || getDbInfo(row.dbId)?.type || '-' }}</template>
        </el-table-column>
        <el-table-column label="别名" width="120">
          <template #default="{ row }">{{ row.alias || '-' }}</template>
        </el-table-column>
        <el-table-column label="允许的表" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.allowedTables || '全部' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!dbList.length && !loading" description="暂无数据库绑定" :image-size="60" />
    </div>

    <!-- 添加/编辑弹窗：从已有数据库中选择 -->
    <el-dialog v-model="showDialog" :title="isEditing ? '编辑数据库绑定' : '添加已有数据库'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="选择数据库" required>
          <el-select v-model="form.dbId" placeholder="请选择已有数据库实例" style="width:100%" filterable>
            <el-option v-for="db in allDatabases" :key="db.id" :label="db.name + ' (' + (db.dbType || db.type) + ')'" :value="db.id">
              <div style="display:flex;justify-content:space-between">
                <span>{{ db.name }}</span>
                <span style="color:#909399;font-size:12px">{{ db.dbType || db.type }} / {{ db.host || '-' }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="别名">
          <el-input v-model="form.alias" placeholder="可选，为数据库设置别名" />
        </el-form-item>
        <el-form-item label="允许的表">
          <el-input v-model="form.allowedTables" placeholder="逗号分隔表名，留空表示全部" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog=false">取消</el-button><el-button type="primary" @click="handleSave">{{ isEditing ? '保存' : '添加' }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="showDetailDialog" title="数据库绑定详情" width="500px">
      <div v-if="detailData" style="display:grid;gap:12px">
        <div><strong>数据库：</strong>{{ getDbInfo(detailData.dbId)?.name || detailData.dbId }}</div>
        <div><strong>类型：</strong>{{ getDbInfo(detailData.dbId)?.dbType || getDbInfo(detailData.dbId)?.type || '-' }}</div>
        <div><strong>别名：</strong>{{ detailData.alias || '-' }}</div>
        <div><strong>允许的表：</strong>{{ detailData.allowedTables || '全部' }}</div>
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
