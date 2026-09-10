<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
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

async function loadAll() { await Promise.all([loadData(), loadAllDatabases()]) }

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

async function openDetail(row: any) {
  detailData.value = row
  showDetailDialog.value = true
  // 先加载实例列表，确保详情里能展示数据库实例的完整信息
  if (!allDatabases.value.length) await loadAllDatabases()
}

/** 根据 dbId 获取数据库实例的详细信息 */
function getDbInfo(dbId: string) {
  return allDatabases.value.find(d => d.id === dbId)
}

// ===== 配置数据库表：从实例加载真实表结构，支持手动补录 =====
const tableOptions = ref<string[]>([])
const tablesLoading = ref(false)
async function loadTables(dbId: string) {
  tableOptions.value = []
  if (!dbId) return
  tablesLoading.value = true
  try {
    const res: any = await api.getDatabaseTables(dbId)
    const list = Array.isArray(res) ? res : (res?.list || res?.tables || res?.records || [])
    tableOptions.value = list.map((t: any) => (typeof t === 'string' ? t : (t.name || t.tableName || ''))).filter(Boolean)
  } catch { tableOptions.value = [] } finally { tablesLoading.value = false }
}
watch(() => form.value.dbId, v => loadTables(v))
// allowedTables 后端存逗号分隔字符串，前端用多选编辑
const allowedTablesList = computed({
  get: () => form.value.allowedTables ? form.value.allowedTables.split(',').map(s => s.trim()).filter(Boolean) : [],
  set: (v: string[]) => { form.value.allowedTables = (v || []).join(',') },
})

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

// ===== 创建数据库（新建实例并自动绑定到本应用） =====
const DB_TYPE_OPTIONS = ['mysql', 'postgresql', 'oracle', 'sqlserver', 'dm', 'sqlite']
const showCreateDialog = ref(false)
const creatingDb = ref(false)
const createDbFormDefault = { name: '', dbType: 'mysql', host: '', port: 3306, dbName: '', username: '', password: '', description: '' }
const createDbForm = ref({ ...createDbFormDefault })
function openCreateDb() { createDbForm.value = { ...createDbFormDefault }; showCreateDialog.value = true }
watch(() => createDbForm.value.dbType, t => {
  if (t === 'mysql') createDbForm.value.port = 3306
  else if (t === 'postgresql') createDbForm.value.port = 5432
  else if (t === 'oracle') createDbForm.value.port = 1521
  else if (t === 'sqlserver') createDbForm.value.port = 1433
  else if (t === 'dm') createDbForm.value.port = 5236
})
async function handleCreateDb() {
  if (!createDbForm.value.name.trim()) { ElMessage.warning('请输入数据库名称'); return }
  if (!createDbForm.value.host.trim()) { ElMessage.warning('请输入主机地址'); return }
  creatingDb.value = true
  try {
    const created: any = await api.createDatabase({ ...createDbForm.value })
    await loadAllDatabases()
    // 新建的数据库自动绑定到当前应用
    if (created?.id) await api.createAppDbBinding(appId(), { dbId: created.id, alias: '', allowedTables: '' })
    showCreateDialog.value = false
    await loadData()
    ElMessage.success('数据库已创建并绑定到本应用')
  } catch { ElMessage.error('创建数据库失败') } finally { creatingDb.value = false }
}

onMounted(loadAll)
</script>
<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">数据库配置</div>
        <div style="display:flex;gap:8px">
          <el-button type="primary" size="small" @click="openCreateDb">创建数据库</el-button>
          <el-button size="small" @click="openAdd">添加已有数据库</el-button>
        </div>
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
          <el-select
            v-model="allowedTablesList"
            placeholder="从数据库加载表结构后选择，留空表示全部"
            style="width:100%"
            multiple
            filterable
            allow-create
            default-first-option
            :loading="tablesLoading"
            :loading-text="'正在加载表结构...'"
          >
            <el-option v-for="t in tableOptions" :key="t" :label="t" :value="t" />
          </el-select>
          <div style="font-size:12px;color:#909399;margin-top:4px">
            {{ form.dbId ? (tableOptions.length ? `已加载 ${tableOptions.length} 张表` : '未加载到表结构，可手动输入表名') : '请先选择数据库' }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog=false">取消</el-button><el-button type="primary" @click="handleSave">{{ isEditing ? '保存' : '添加' }}</el-button></template>
    </el-dialog>

    <!-- 创建数据库：新建实例并自动绑定到本应用 -->
    <el-dialog v-model="showCreateDialog" title="创建数据库" width="560px">
      <el-form label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="createDbForm.name" placeholder="如：订单业务库" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createDbForm.dbType" style="width:100%">
            <el-option v-for="t in DB_TYPE_OPTIONS" :key="t" :label="t.toUpperCase()" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机" required>
          <el-input v-model="createDbForm.host" placeholder="如：192.168.1.100" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="createDbForm.port" :min="1" :max="65535" style="width:100%" />
        </el-form-item>
        <el-form-item label="数据库名">
          <el-input v-model="createDbForm.dbName" placeholder="连接的数据库名" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="createDbForm.username" placeholder="数据库账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="createDbForm.password" type="password" show-password placeholder="数据库密码" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createDbForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreateDialog=false">取消</el-button><el-button type="primary" :loading="creatingDb" @click="handleCreateDb">创建并绑定</el-button></template>
    </el-dialog>

    <el-dialog v-model="showDetailDialog" title="数据库详情" width="560px">
      <div v-if="detailData" style="display:grid;gap:10px">
        <div><strong>数据库：</strong>{{ getDbInfo(detailData.dbId)?.name || detailData.dbId }}</div>
        <div><strong>类型：</strong>{{ getDbInfo(detailData.dbId)?.dbType || getDbInfo(detailData.dbId)?.type || '-' }}</div>
        <div v-if="getDbInfo(detailData.dbId)"><strong>主机：</strong>{{ getDbInfo(detailData.dbId)?.host || '-' }}<template v-if="getDbInfo(detailData.dbId)?.port">:{{ getDbInfo(detailData.dbId)?.port }}</template></div>
        <div v-if="getDbInfo(detailData.dbId)?.dbName"><strong>数据库名：</strong>{{ getDbInfo(detailData.dbId)?.dbName }}</div>
        <div v-if="getDbInfo(detailData.dbId)?.description"><strong>描述：</strong>{{ getDbInfo(detailData.dbId)?.description }}</div>
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
