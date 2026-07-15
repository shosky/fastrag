<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

// --- 类型定义 ---
interface DbInstanceItem {
  id: string
  name: string
  description: string
  dbType: string
  host: string
  port: number
  username: string
  password: string
  dbName: string
  jdbcUrl: string
  status: string
  readOnly: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

interface TableItem {
  id: string
  dbId: string
  tableName: string
  tableComment: string
  columns: string
  rowCount: number
  enabled: number
  syncedAt: string
}

// --- 列表数据 ---
const databases = ref<DbInstanceItem[]>([])
const loading = ref(false)

async function loadDatabases() {
  loading.value = true
  try {
    const res: any = await api.getDatabases()
    databases.value = (res?.data || res || []) as DbInstanceItem[]
  } catch (e) {
    console.error('加载数据库列表失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(loadDatabases)

const searchKeyword = ref('')
const selectedType = ref('')

const filteredDatabases = computed(() => {
  let list = databases.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(
      (d) =>
        d.name.toLowerCase().includes(kw) ||
        (d.description || '').toLowerCase().includes(kw) ||
        (d.host || '').toLowerCase().includes(kw) ||
        (d.dbName || '').toLowerCase().includes(kw),
    )
  }
  if (selectedType.value) {
    list = list.filter((d) => d.dbType === selectedType.value)
  }
  return list
})

const dbTypeOptions = [
  { label: 'MySQL', value: 'MySQL' },
  { label: 'PostgreSQL', value: 'PostgreSQL' },
  { label: 'H2', value: 'H2' },
  { label: 'Oracle', value: 'Oracle' },
  { label: 'SQL Server', value: 'SQLServer' },
]

function getTypeLabel(type: string): string {
  const map: Record<string, string> = {
    MySQL: 'MySQL',
    PostgreSQL: 'PostgreSQL',
    H2: 'H2',
    Oracle: 'Oracle',
    SQLServer: 'SQL Server',
    sqlserver: 'SQL Server',
  }
  return map[type] || type || '未知'
}

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

function getStatusType(status: string): 'success' | 'danger' | 'info' {
  return status === 'connected' ? 'success' : status === 'disconnected' ? 'danger' : 'info'
}

function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    connected: '已连接',
    disconnected: '未连接',
  }
  return map[status] || status || '未知'
}

// --- 新建/编辑弹窗 ---
const dialogVisible = ref(false)
const dialogTitle = ref('新建数据库连接')
const isEditing = ref(false)
const formLoading = ref(false)
const dbForm = ref({
  id: '',
  name: '',
  description: '',
  dbType: 'MySQL',
  host: '127.0.0.1',
  port: 3306,
  username: '',
  password: '',
  dbName: '',
  readOnly: 1,
})

const formRules = {
  name: [{ required: true, message: '请输入连接名称', trigger: 'blur' }],
  dbType: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口号', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  dbName: [{ required: true, message: '请输入数据库名', trigger: 'blur' }],
}

const formRef = ref()

function openCreate() {
  dialogTitle.value = '新建数据库连接'
  isEditing.value = false
  dbForm.value = {
    id: '',
    name: '',
    description: '',
    dbType: 'MySQL',
    host: '127.0.0.1',
    port: 3306,
    username: '',
    password: '',
    dbName: '',
    readOnly: 1,
  }
  dialogVisible.value = true
}

function openEdit(db: DbInstanceItem) {
  dialogTitle.value = '编辑数据库连接'
  isEditing.value = true
  dbForm.value = {
    id: db.id,
    name: db.name,
    description: db.description || '',
    dbType: db.dbType || 'MySQL',
    host: db.host || '127.0.0.1',
    port: db.port || 3306,
    username: db.username || '',
    password: db.password || '',
    dbName: db.dbName || '',
    readOnly: db.readOnly ?? 1,
  }
  dialogVisible.value = true
}

function onTypeChange(type: string) {
  const portMap: Record<string, number> = {
    MySQL: 3306,
    PostgreSQL: 5432,
    H2: -1,
    Oracle: 1521,
    SQLServer: 1433,
  }
  dbForm.value.port = portMap[type] || 3306
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  formLoading.value = true
  try {
    const data = { ...dbForm.value }
    delete data.id

    if (isEditing.value) {
      await api.updateDatabase(dbForm.value.id, data as any)
      ElMessage.success('更新成功')
    } else {
      await api.createDatabase(data as any)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadDatabases()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    formLoading.value = false
  }
}

// --- 连接测试 ---
const testingId = ref('')

async function handleTestConn(db: DbInstanceItem) {
  testingId.value = db.id
  try {
    const res: any = await api.testDatabaseConnection(db.id)
    const data = res?.data || res
    if (data?.connected) {
      ElMessage.success(`连接成功，耗时 ${data.latencyMs}ms`)
      // 更新本地状态
      db.status = 'connected'
    } else {
      ElMessage.error(`连接失败: ${data?.error || '未知错误'}`)
      db.status = 'disconnected'
    }
  } catch (e: any) {
    ElMessage.error(`连接失败: ${e?.message || '请求异常'}`)
    db.status = 'disconnected'
  } finally {
    testingId.value = ''
  }
}

// --- 删除 ---
async function handleDelete(db: DbInstanceItem) {
  try {
    await ElMessageBox.confirm(`确定删除数据库连接「${db.name}」吗？关联的表信息也将被删除。`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await api.deleteDatabase(db.id)
    await loadDatabases()
    ElMessage.success('删除成功')
  } catch {}
}

// --- 表浏览弹窗 ---
const tablesDialogVisible = ref(false)
const tablesLoading = ref(false)
const currentDbForTables = ref<DbInstanceItem | null>(null)
const tables = ref<TableItem[]>([])
const selectedTable = ref<TableItem | null>(null)
const tableColumns = ref<any[]>([])

async function openTablesDialog(db: DbInstanceItem) {
  currentDbForTables.value = db
  selectedTable.value = null
  tableColumns.value = []
  tablesDialogVisible.value = true
  tablesLoading.value = true
  try {
    const res: any = await api.getDatabaseTables(db.id)
    tables.value = (res?.data || res || []) as TableItem[]
  } catch (e) {
    ElMessage.error('加载表列表失败')
  } finally {
    tablesLoading.value = false
  }
}

function selectTable(table: TableItem) {
  selectedTable.value = table
  try {
    tableColumns.value = JSON.parse(table.columns || '[]')
  } catch {
    tableColumns.value = []
  }
}

async function handleSyncTables() {
  if (!currentDbForTables.value) return
  tablesLoading.value = true
  try {
    const res: any = await api.syncDatabaseTables(currentDbForTables.value.id)
    const data = res?.data || res
    if (data?.error) {
      ElMessage.error(`同步失败: ${data.error}`)
    } else {
      ElMessage.success(`同步成功，共 ${data?.syncedCount || 0} 张表`)
      await loadDatabases()
      // 刷新表列表
      const tRes: any = await api.getDatabaseTables(currentDbForTables.value.id)
      tables.value = (tRes?.data || tRes || []) as TableItem[]
    }
  } catch (e: any) {
    ElMessage.error(`同步失败: ${e?.message || '请求异常'}`)
  } finally {
    tablesLoading.value = false
  }
}

// --- SQL 工作台弹窗 ---
const sqlDialogVisible = ref(false)
const currentDbForSql = ref<DbInstanceItem | null>(null)
const sqlText = ref('')
const sqlLoading = ref(false)
const sqlResult = ref<{ columns: string[]; rows: any[]; rowCount: number; elapsedMs?: number; error?: string } | null>(null)

function openSqlDialog(db: DbInstanceItem) {
  currentDbForSql.value = db
  sqlText.value = ''
  sqlResult.value = null
  sqlDialogVisible.value = true
}

async function handleExecSql() {
  if (!sqlText.value.trim()) {
    ElMessage.warning('请输入 SQL 语句')
    return
  }
  if (!currentDbForSql.value) return

  sqlLoading.value = true
  sqlResult.value = null
  try {
    const res: any = await api.queryDatabase(currentDbForSql.value.id, sqlText.value.trim())
    const data = res?.data || res
    sqlResult.value = {
      columns: data?.columns || [],
      rows: data?.rows || [],
      rowCount: data?.rowCount || 0,
      elapsedMs: data?.elapsedMs,
      error: data?.error,
    }
    if (data?.error) {
      ElMessage.error(`SQL 执行失败: ${data.error}`)
    } else {
      ElMessage.success(`查询完成，返回 ${data?.rowCount || 0} 行，耗时 ${data?.elapsedMs || 0}ms`)
    }
  } catch (e: any) {
    sqlResult.value = {
      columns: [],
      rows: [],
      rowCount: 0,
      error: e?.message || '请求异常',
    }
    ElMessage.error(`SQL 执行失败: ${e?.message || '请求异常'}`)
  } finally {
    sqlLoading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-title">
        <h2>数据库管理</h2>
        <p>管理外部数据库连接，浏览表结构，执行 SQL 查询</p>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索名称 / 主机 / 数据库"
          clearable
          style="width: 320px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="selectedType" placeholder="全部类型" clearable style="width: 140px">
          <el-option v-for="opt in dbTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新建连接
        </el-button>
      </div>
    </div>

    <!-- 数据库卡片列表 -->
    <div v-loading="loading" class="db-grid" v-if="filteredDatabases.length">
      <div v-for="db in filteredDatabases" :key="db.id" class="db-card">
        <div class="db-card-body">
          <div class="db-title-row">
            <div class="db-type-badge" :style="{ background: getTypeColor(db.dbType) + '18', color: getTypeColor(db.dbType) }">
              {{ getTypeLabel(db.dbType) }}
            </div>
            <h4 :title="db.name">{{ db.name }}</h4>
            <el-tag :type="getStatusType(db.status)" size="small" effect="light" round>
              {{ getStatusLabel(db.status) }}
            </el-tag>
          </div>
          <p v-if="db.description" class="db-desc" :title="db.description">{{ db.description }}</p>
          <div class="db-info">
            <span class="info-item">
              <el-icon :size="13"><Monitor /></el-icon>
              {{ db.host }}{{ db.port && db.port > 0 ? ':' + db.port : '' }}
            </span>
            <span class="info-item">
              <el-icon :size="13"><Coin /></el-icon>
              {{ db.dbName || '-' }}
            </span>
            <span v-if="db.readOnly === 1" class="info-item readonly-tag">只读</span>
          </div>
        </div>
        <div class="db-card-footer">
          <div class="footer-info">
            <span class="time-text">{{ db.createdAt }}</span>
          </div>
          <div class="actions">
            <el-button
              link
              type="primary"
              size="small"
              :loading="testingId === db.id"
              @click="handleTestConn(db)"
            >
              <el-icon :size="14"><Connection /></el-icon>测试
            </el-button>
            <el-button link type="primary" size="small" @click="openTablesDialog(db)">
              <el-icon :size="14"><Grid /></el-icon>表
            </el-button>
            <el-button link type="primary" size="small" @click="openSqlDialog(db)">
              <el-icon :size="14"><Tickets /></el-icon>SQL
            </el-button>
            <el-button link type="primary" size="small" @click="openEdit(db)">编辑</el-button>
            <el-dropdown trigger="click">
              <el-icon class="more-icon"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openEdit(db)">编辑连接</el-dropdown-item>
                  <el-dropdown-item @click="handleTestConn(db)">测试连接</el-dropdown-item>
                  <el-dropdown-item @click="openTablesDialog(db)">浏览表</el-dropdown-item>
                  <el-dropdown-item @click="openSqlDialog(db)">SQL 查询</el-dropdown-item>
                  <el-dropdown-item divided @click="handleDelete(db)">
                    <span style="color: var(--el-color-danger)">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无数据库连接，点击右上角新建" />

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="dbForm"
        :rules="formRules"
        label-width="90px"
        label-position="right"
      >
        <el-form-item label="连接名称" prop="name">
          <el-input v-model="dbForm.name" placeholder="例如：生产环境 MySQL" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dbForm.description" type="textarea" :rows="2" placeholder="可选描述" maxlength="256" show-word-limit />
        </el-form-item>
        <el-form-item label="数据库类型" prop="dbType">
          <el-select v-model="dbForm.dbType" placeholder="选择类型" @change="onTypeChange" style="width: 100%">
            <el-option v-for="opt in dbTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机地址" prop="host">
          <el-input v-model="dbForm.host" placeholder="127.0.0.1" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="dbForm.port" :min="1" :max="65535" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="dbForm.username" placeholder="root" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="dbForm.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="数据库名" prop="dbName">
          <el-input v-model="dbForm.dbName" placeholder="fastrag" />
        </el-form-item>
        <el-form-item label="只读模式">
          <el-switch :model-value="dbForm.readOnly === 1" @change="(val: boolean) => dbForm.readOnly = val ? 1 : 0" />
          <span style="margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary)">开启后仅允许 SELECT 查询</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 表浏览弹窗 -->
    <el-dialog
      v-model="tablesDialogVisible"
      :title="`表浏览 - ${currentDbForTables?.name || ''}`"
      width="780px"
      destroy-on-close
    >
      <div class="tables-header">
        <el-button type="primary" size="small" :loading="tablesLoading" @click="handleSyncTables">
          <el-icon><Refresh /></el-icon>同步表结构
        </el-button>
        <span class="tables-count">共 {{ tables.length }} 张表</span>
      </div>
      <div class="tables-content">
        <div class="tables-list">
          <el-scrollbar style="height: 420px">
            <div
              v-for="table in tables"
              :key="table.id"
              :class="['table-item', { active: selectedTable?.id === table.id }]"
              @click="selectTable(table)"
            >
              <el-icon :size="14"><Grid /></el-icon>
              <span class="table-name">{{ table.tableName }}</span>
              <span v-if="table.rowCount != null" class="table-rows">{{ table.rowCount }} 行</span>
            </div>
            <el-empty v-if="!tablesLoading && tables.length === 0" description="暂无表信息，点击同步获取" :image-size="60" />
          </el-scrollbar>
        </div>
        <div class="table-detail">
          <template v-if="selectedTable">
            <div class="detail-header">
              <h4>{{ selectedTable.tableName }}</h4>
              <span v-if="selectedTable.tableComment" class="detail-comment">{{ selectedTable.tableComment }}</span>
            </div>
            <el-table :data="tableColumns" size="small" stripe max-height="360">
              <el-table-column prop="name" label="列名" min-width="120" />
              <el-table-column prop="type" label="类型" min-width="100" />
              <el-table-column prop="size" label="长度" width="70" align="center" />
              <el-table-column prop="nullable" label="可空" width="60" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.nullable" type="success" size="small">是</el-tag>
                  <el-tag v-else type="info" size="small">否</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="comment" label="备注" min-width="120">
                <template #default="{ row }">{{ row.comment || '-' }}</template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="选择左侧表查看结构" :image-size="60" />
        </div>
      </div>
    </el-dialog>

    <!-- SQL 工作台弹窗 -->
    <el-dialog
      v-model="sqlDialogVisible"
      :title="`SQL 工作台 - ${currentDbForSql?.name || ''}`"
      width="900px"
      top="5vh"
      destroy-on-close
    >
      <div class="sql-editor">
        <div class="sql-input-wrapper">
          <el-input
            v-model="sqlText"
            type="textarea"
            :rows="5"
            placeholder="输入 SQL 语句（仅支持 SELECT / SHOW / DESCRIBE / EXPLAIN）&#10;例如：SELECT * FROM users LIMIT 10"
            class="sql-textarea"
            resize="vertical"
          />
        </div>
        <div class="sql-actions">
          <el-button type="primary" :loading="sqlLoading" @click="handleExecSql" :disabled="!sqlText.trim()">
            <el-icon><VideoPlay /></el-icon>执行查询
          </el-button>
          <el-button @click="sqlText = ''" :disabled="!sqlText">清空</el-button>
        </div>
      </div>

      <div v-if="sqlResult" class="sql-result">
        <div v-if="sqlResult.error" class="sql-error">
          <el-icon :size="16"><WarningFilled /></el-icon>
          <span>{{ sqlResult.error }}</span>
        </div>
        <template v-else>
          <div class="sql-result-header">
            <span>查询结果</span>
            <span class="result-meta">共 {{ sqlResult.rowCount }} 行{{ sqlResult.elapsedMs ? `，耗时 ${sqlResult.elapsedMs}ms` : '' }}</span>
          </div>
          <el-table :data="sqlResult.rows" size="small" stripe max-height="400" border style="width: 100%">
            <el-table-column
              v-for="col in sqlResult.columns"
              :key="col"
              :prop="col"
              :label="col"
              min-width="120"
              show-overflow-tooltip
            />
            <template #empty>
              <span>无数据</span>
            </template>
          </el-table>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-header {
  margin-bottom: $spacing-lg;

  h2 {
    margin: 0 0 $spacing-xs;
    font-size: 20px;
    color: $text-primary;
  }

  p {
    margin: 0;
    font-size: 14px;
    color: $text-secondary;
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
}

.toolbar-left {
  display: flex;
  gap: $spacing-sm;
}

.toolbar-right {
  display: flex;
  gap: $spacing-sm;
}

.db-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: $spacing-base;
}

.db-card {
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  overflow: hidden;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;

  &:hover {
    box-shadow: $shadow-base;
    border-color: $border-base;
    transform: translateY(-2px);
  }
}

.db-card-body {
  padding: $spacing-base;
  flex: 1;
}

.db-title-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;

  .db-type-badge {
    flex-shrink: 0;
    font-size: 11px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: $radius-sm;
    white-space: nowrap;
  }

  h4 {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    min-width: 0;
  }
}

.db-desc {
  margin: 0 0 $spacing-sm;
  font-size: 13px;
  color: $text-secondary;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.db-info {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-wrap: wrap;

  .info-item {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: $text-secondary;
    background: $bg-hover;
    border-radius: $radius-sm;
    padding: 2px 8px;
    font-family: 'Consolas', 'Monaco', monospace;
  }

  .readonly-tag {
    color: $color-warning;
    background: rgba($color-warning, 0.1);
  }
}

.db-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-sm $spacing-base;
  border-top: 1px solid $border-lighter;

  .footer-info {
    display: flex;
    align-items: center;
  }

  .time-text {
    font-size: 12px;
    color: $text-placeholder;
  }

  .actions {
    display: flex;
    align-items: center;
    gap: 2px;
  }

  .more-icon {
    cursor: pointer;
    color: $text-secondary;
    padding: 2px;

    &:hover {
      color: $color-primary;
    }
  }
}

// --- 表浏览弹窗 ---
.tables-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;

  .tables-count {
    font-size: 13px;
    color: $text-secondary;
  }
}

.tables-content {
  display: flex;
  gap: $spacing-base;
  height: 460px;
}

.tables-list {
  width: 200px;
  flex-shrink: 0;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  overflow: hidden;
}

.table-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: 8px $spacing-base;
  font-size: 13px;
  color: $text-regular;
  cursor: pointer;
  border-bottom: 1px solid $border-light;
  transition: all 0.15s;

  &:hover {
    background: $bg-hover;
  }

  &.active {
    background: $bg-active;
    color: $color-primary;
    font-weight: 500;
  }

  .table-name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .table-rows {
    font-size: 11px;
    color: $text-placeholder;
    flex-shrink: 0;
  }
}

.table-detail {
  flex: 1;
  overflow: hidden;

  .detail-header {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-sm;

    h4 {
      margin: 0;
      font-size: 15px;
      font-weight: 600;
      color: $text-primary;
    }

    .detail-comment {
      font-size: 13px;
      color: $text-secondary;
    }
  }
}

// --- SQL 工作台 ---
.sql-editor {
  margin-bottom: $spacing-base;
}

.sql-textarea {
  :deep(.el-textarea__inner) {
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 14px;
    line-height: 1.6;
  }
}

.sql-actions {
  display: flex;
  gap: $spacing-sm;
  margin-top: $spacing-sm;
}

.sql-result {
  border-top: 1px solid $border-lighter;
  padding-top: $spacing-base;

  .sql-result-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-sm;

    font-size: 14px;
    font-weight: 500;
    color: $text-primary;

    .result-meta {
      font-size: 12px;
      color: $text-secondary;
      font-weight: 400;
    }
  }

  .sql-error {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    color: $color-danger;
    font-size: 13px;
    background: rgba($color-danger, 0.06);
    border-radius: $radius-base;
    padding: $spacing-base;
  }
}
</style>
