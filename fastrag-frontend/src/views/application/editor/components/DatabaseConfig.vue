<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
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

/** 根据 dbId 获取数据库实例的详细信息 */
function getDbInfo(dbId: string) {
  return allDatabases.value.find(d => d.id === dbId)
}

/** 数据库类型颜色 */
function getTypeColor(type: string): string {
  const map: Record<string, string> = {
    MySQL: '#00758F',
    PostgreSQL: '#336791',
    H2: '#E44D26',
    Oracle: '#F80000',
    SQLServer: '#CC2927',
  }
  return map[type] || '#6B7280'
}

/** 状态文本 */
function getStatusLabel(status: string): string {
  return status === 'connected' ? '已连接' : status === 'disconnected' ? '未连接' : status || '未知'
}

function getStatusType(status: string): 'success' | 'danger' | 'info' {
  return status === 'connected' ? 'success' : status === 'disconnected' ? 'danger' : 'info'
}

async function loadData() {
  loading.value = true
  try {
    // 并行加载：当前应用的绑定 + 系统数据库实例列表
    const [bindingsRes, dbsRes]: any[] = await Promise.all([
      api.getAppDbBindings(appId()),
      api.getDatabases(),
    ])
    dbList.value = Array.isArray(bindingsRes) ? bindingsRes : (bindingsRes?.list || bindingsRes?.records || [])
    allDatabases.value = Array.isArray(dbsRes) ? dbsRes : (dbsRes?.list || dbsRes?.records || [])
  } catch (e) {
    console.error('加载数据库配置失败', e)
  } finally {
    loading.value = false
  }
}

/** 单独加载系统中已有的数据库实例列表（供弹窗选择） */
async function loadAllDatabases() {
  try {
    const res: any = await api.getDatabases()
    allDatabases.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
  } catch (e) {
    allDatabases.value = []
  }
}

function openAdd() {
  isEditing.value = false
  editingId.value = ''
  form.value = { ...formDefault }
  loadAllDatabases()
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

async function handleSave() {
  if (!form.value.dbId) { ElMessage.warning('请选择已有数据库'); return }
  try {
    if (isEditing.value) { await api.updateAppDbBinding(appId(), editingId.value, form.value); ElMessage.success('已更新') }
    else { await api.createAppDbBinding(appId(), form.value); ElMessage.success('已添加') }
    showDialog.value = false; await loadData()
  } catch (e) { ElMessage.error('操作失败') }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确认解除该数据库绑定？', '确认', { type: 'warning' })
    await api.deleteAppDbBinding(appId(), row.id)
    await loadData()
    ElMessage.success('已解除绑定')
  } catch (e) {}
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
      <p style="font-size:13px;color:var(--el-text-color-secondary);margin-bottom:12px">
        从系统中已注册的数据库实例中选择添加，绑定到当前应用
      </p>
      <el-table :data="dbList" stripe size="small" style="margin-top:12px" v-loading="loading">
        <el-table-column label="数据库名称" min-width="150">
          <template #default="{ row }">
            <div class="db-name-cell">
              <span class="db-type-dot" :style="{ background: getTypeColor(getDbInfo(row.dbId)?.dbType) }"></span>
              {{ getDbInfo(row.dbId)?.name || row.dbId || '-' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <span
              v-if="getDbInfo(row.dbId)?.dbType"
              class="db-type-tag"
              :style="{ color: getTypeColor(getDbInfo(row.dbId)?.dbType), background: getTypeColor(getDbInfo(row.dbId)?.dbType) + '12' }"
            >
              {{ getDbInfo(row.dbId)?.dbType }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="连接信息" min-width="160">
          <template #default="{ row }">
            <span v-if="getDbInfo(row.dbId)" style="font-size:12px;color:var(--el-text-color-secondary);font-family:Consolas,Monaco,monospace">
              {{ getDbInfo(row.dbId).host }}{{ getDbInfo(row.dbId).port && getDbInfo(row.dbId).port > 0 ? ':' + getDbInfo(row.dbId).port : '' }} / {{ getDbInfo(row.dbId).dbName || '-' }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="getDbInfo(row.dbId)?.status"
              :type="getStatusType(getDbInfo(row.dbId)?.status)"
              size="small"
              effect="light"
              round
            >
              {{ getStatusLabel(getDbInfo(row.dbId)?.status) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="别名" width="110">
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
        <template #empty>
          <el-empty description="暂无数据库绑定" :image-size="60" />
        </template>
      </el-table>
    </div>

    <!-- 添加/编辑弹窗：从已有数据库中选择 -->
    <el-dialog v-model="showDialog" :title="isEditing ? '编辑数据库绑定' : '添加已有数据库'" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="选择数据库" required>
          <el-select v-model="form.dbId" placeholder="请选择已有数据库实例" style="width:100%" filterable>
            <el-option v-for="db in allDatabases" :key="db.id" :label="db.name + ' (' + (db.dbType || db.type) + ')'" :value="db.id">
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span style="font-weight:500">{{ db.name }}</span>
                <span style="color:#909399;font-size:12px">
                  {{ db.dbType || db.type }} / {{ db.host || '-' }}{{ db.port && db.port > 0 ? ':' + db.port : '' }}
                </span>
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
      <template #footer>
        <el-button @click="showDialog=false">取消</el-button>
        <el-button type="primary" @click="handleSave">{{ isEditing ? '保存' : '添加' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetailDialog" title="数据库绑定详情" width="520px">
      <div v-if="detailData" class="detail-grid">
        <div class="detail-row">
          <span class="detail-label">数据库名称</span>
          <span class="detail-value">{{ getDbInfo(detailData.dbId)?.name || detailData.dbId }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">类型</span>
          <span class="detail-value">
            <span
              v-if="getDbInfo(detailData.dbId)?.dbType"
              class="db-type-tag"
              :style="{ color: getTypeColor(getDbInfo(detailData.dbId)?.dbType), background: getTypeColor(getDbInfo(detailData.dbId)?.dbType) + '12' }"
            >
              {{ getDbInfo(detailData.dbId)?.dbType }}
            </span>
            <span v-else>-</span>
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">连接地址</span>
          <span class="detail-value" v-if="getDbInfo(detailData.dbId)" style="font-family:Consolas,Monaco,monospace;font-size:13px">
            {{ getDbInfo(detailData.dbId).host }}{{ getDbInfo(detailData.dbId).port && getDbInfo(detailData.dbId).port > 0 ? ':' + getDbInfo(detailData.dbId).port : '' }}
          </span>
          <span class="detail-value" v-else>-</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">数据库名</span>
          <span class="detail-value">{{ getDbInfo(detailData.dbId)?.dbName || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">状态</span>
          <span class="detail-value">
            <el-tag
              v-if="getDbInfo(detailData.dbId)?.status"
              :type="getStatusType(getDbInfo(detailData.dbId)?.status)"
              size="small"
              effect="light"
              round
            >
              {{ getStatusLabel(getDbInfo(detailData.dbId)?.status) }}
            </el-tag>
            <span v-else>-</span>
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">别名</span>
          <span class="detail-value">{{ detailData.alias || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">允许的表</span>
          <span class="detail-value">{{ detailData.allowedTables || '全部' }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="showDetailDialog=false">关闭</el-button>
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
  gap: 8px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
}

.card-panel {
  background: var(--el-bg-color-overlay);
  border-radius: 8px;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
}

.db-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.db-type-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.db-type-tag {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
}

.detail-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.detail-label {
  flex-shrink: 0;
  width: 80px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.detail-value {
  font-size: 14px;
  color: var(--el-text-color-primary);
}
</style>
