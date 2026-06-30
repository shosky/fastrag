<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, DocumentAdd } from '@element-plus/icons-vue'
import * as api from '@/api'

const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({ type: 'custom' })

// 上传插件
const uploadedFile = ref<File | null>(null)
const uploading = ref(false)
// JSON 导入
const jsonContent = ref('')
const jsonImportMode = ref<'paste' | 'file'>('paste')
const jsonFile = ref<File | null>(null)

// API 配置表单
interface KeyValuePair { key: string; value: string }
const apiConfigForm = ref<{
  method: string
  url: string
  authType: string
  headers: KeyValuePair[]
  params: KeyValuePair[]
  bodyType: string
  body: string
}>({
  method: 'GET',
  url: '',
  authType: 'none',
  headers: [{ key: '', value: '' }],
  params: [{ key: '', value: '' }],
  bodyType: 'none',
  body: '',
})

// 类型映射：前端类型（插件管理页用）↔ 后端类型（Tool 实体）
const FRONT_TYPE_TO_BACK: Record<string, string> = {
  custom: 'http',
  uploaded: 'plugin',
  json_import: 'builtin',
}
const BACK_TYPE_TO_FRONT: Record<string, string> = {
  http: 'custom',
  plugin: 'uploaded',
  builtin: 'json_import',
  knowledge: 'json_import',
}
const TYPE_LABELS: Record<string, string> = {
  custom: '自定义',
  uploaded: '上传',
  json_import: 'JSON导入',
}

// UI 选项常量
const HTTP_METHODS = ['GET', 'POST', 'PUT', 'DELETE'] as const
const AUTH_OPTIONS = [
  { label: '无', value: 'none' },
  { label: 'API Key', value: 'apiKey' },
  { label: 'Bearer Token', value: 'bearer' },
]
const BODY_TYPE_OPTIONS = [
  { label: '无', value: 'none' },
  { label: 'JSON', value: 'json' },
  { label: 'Form Data', value: 'form-data' },
  { label: 'x-www-form-urlencoded', value: 'x-www-form-urlencoded' },
  { label: 'XML', value: 'xml' },
  { label: '纯文本', value: 'raw-text' },
]

// ==================== 状态工具函数 ====================

function statusColor(val: string | number | boolean | undefined | null): string {
  if (val === 'active' || val === 1 || val === true) return 'success'
  if (val === 'inactive' || val === 0 || val === false) return 'info'
  return 'info'
}

function statusText(val: string | number | boolean | undefined | null): string {
  if (val === 'active' || val === 1 || val === true) return '启用'
  if (val === 'inactive' || val === 0 || val === false) return '禁用'
  return String(val ?? '-')
}

// ==================== 数据加载 ====================

async function loadData() {
  loading.value = true
  try {
    const res = await api.getTools()
    dataList.value = Array.isArray(res) ? res : (res as any)?.list || []
    total.value = dataList.value.length
  } catch {
    dataList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}
onMounted(loadData)

function handleSearch() { currentPage.value = 1; loadData() }
function handlePageChange(p: number) { currentPage.value = p; loadData() }
function handleSizeChange(s: number) { pageSize.value = s; currentPage.value = 1; loadData() }

// ==================== 增删改 ====================

function resetApiConfig() {
  apiConfigForm.value = {
    method: 'GET',
    url: '',
    authType: 'none',
    headers: [{ key: '', value: '' }],
    params: [{ key: '', value: '' }],
    bodyType: 'none',
    body: '',
  }
}

function handleAdd() {
  editingId.value = null
  formData.value = { type: 'custom' }
  resetApiConfig()
  uploadedFile.value = null
  jsonContent.value = ''
  jsonFile.value = null
  jsonImportMode.value = 'paste'
  dialogTitle.value = '创建自定义插件'
  showDialog.value = true
}

/** 快速创建 HTTP 工具（添加工具 + 配置API） */
function handleAddTool() {
  editingId.value = null
  formData.value = { type: 'custom' }
  resetApiConfig()
  uploadedFile.value = null
  jsonContent.value = ''
  jsonFile.value = null
  jsonImportMode.value = 'paste'
  dialogTitle.value = '添加工具 - 配置API'
  showDialog.value = true
}

/** 直接打开上传插件对话框 */
function handleUploadPlugin() {
  editingId.value = null
  formData.value = { type: 'uploaded' }
  resetApiConfig()
  uploadedFile.value = null
  jsonContent.value = ''
  jsonFile.value = null
  jsonImportMode.value = 'paste'
  dialogTitle.value = '上传插件'
  showDialog.value = true
}

/** 直接打开 JSON 导入对话框 */
function handleImportJson() {
  editingId.value = null
  formData.value = { type: 'json_import' }
  resetApiConfig()
  uploadedFile.value = null
  jsonContent.value = ''
  jsonFile.value = null
  jsonImportMode.value = 'paste'
  dialogTitle.value = 'JSON 导入插件'
  showDialog.value = true
}

async function handleEdit(row: any) {
  editingId.value = row.id
  formData.value = {
    ...row,
    type: BACK_TYPE_TO_FRONT[row.type] || row.type,
  }
  dialogTitle.value = '编辑插件'
  showDialog.value = true

  // 如果是 http 类型，加载已有 API 配置
  if (row.type === 'http') {
    try {
      const cfg: any = await api.getToolApiConfig(row.id)
      if (cfg) {
        apiConfigForm.value = {
          method: cfg.method || 'GET',
          url: cfg.url || '',
          authType: cfg.authType || 'none',
          headers: normalizeKeyValues(cfg.headers),
          params: normalizeKeyValues(cfg.params),
          bodyType: cfg.bodyType || 'none',
          body: cfg.body || '',
        }
      }
    } catch { /* 无配置时保持默认 */ }
  } else {
    resetApiConfig()
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定要删除该记录吗？', '提示', { type: 'warning' })
    await api.deleteTool(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch { /* 取消 */ }
}

async function handleSave() {
  try {
    // ===== 上传插件 =====
    if (formData.value.type === 'uploaded') {
      if (!uploadedFile.value) {
        ElMessage.warning('请选择要上传的插件文件')
        return
      }
      uploading.value = true
      await api.uploadPlugin(
        uploadedFile.value,
        formData.value.name || undefined,
        formData.value.description || undefined,
      )
      ElMessage.success('上传成功')
      showDialog.value = false
      loadData()
      return
    }

    // ===== JSON 导入插件 =====
    if (formData.value.type === 'json_import') {
      let plugins: Record<string, unknown>[]
      if (jsonImportMode.value === 'file') {
        if (!jsonFile.value) {
          ElMessage.warning('请选择 JSON 文件')
          return
        }
        const text = await jsonFile.value.text()
        plugins = JSON.parse(text)
      } else {
        if (!jsonContent.value.trim()) {
          ElMessage.warning('请输入 JSON 内容')
          return
        }
        plugins = JSON.parse(jsonContent.value)
      }
      if (!Array.isArray(plugins)) {
        plugins = [plugins]
      }
      await api.importPluginsFromJson(plugins)
      ElMessage.success(`成功导入 ${plugins.length} 个插件`)
      showDialog.value = false
      loadData()
      return
    }

    // ===== 自定义 API 插件 / 普通保存 =====
    if (!formData.value.name) {
      ElMessage.warning('请输入插件名称')
      return
    }
    const payload: any = {
      name: formData.value.name,
      description: formData.value.description || '',
      type: FRONT_TYPE_TO_BACK[formData.value.type] || formData.value.type,
    }

    if (editingId.value) {
      await api.updateTool(editingId.value, payload)
      // 保存 API 配置（将 headers/params 数组序列化为 JSON 字符串，适配后端 String 字段）
      if (formData.value.type === 'custom') {
        const apiCfg = { ...apiConfigForm.value }
        apiCfg.headers = JSON.stringify(apiCfg.headers)
        apiCfg.params = JSON.stringify(apiCfg.params)
        await api.saveToolApiConfig(editingId.value, apiCfg)
      }
      ElMessage.success('更新成功')
    } else {
      const res: any = await api.createTool(payload)
      const newId: string | undefined = res?.id
      if (newId && formData.value.type === 'custom') {
        const apiCfg = { ...apiConfigForm.value }
        apiCfg.headers = JSON.stringify(apiCfg.headers)
        apiCfg.params = JSON.stringify(apiCfg.params)
        await api.saveToolApiConfig(newId, apiCfg)
      }
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    loadData()
  } catch (e: any) {
    if (e instanceof SyntaxError) {
      ElMessage.error('JSON 格式错误，请检查输入内容')
    } else {
      ElMessage.error(e?.message || '保存失败')
    }
  } finally {
    uploading.value = false
  }
}

/** 将后端返回的 headers/params（可能是 JSON 字符串或数组）统一为 KeyValuePair[] */
function normalizeKeyValues(raw: unknown): KeyValuePair[] {
  if (Array.isArray(raw)) return raw.length ? raw : [{ key: '', value: '' }]
  if (typeof raw === 'string' && raw) {
    try {
      const parsed = JSON.parse(raw)
      return Array.isArray(parsed) && parsed.length ? parsed : [{ key: '', value: '' }]
    } catch { /* fall through */ }
  }
  return [{ key: '', value: '' }]
}

function addPair(arr: KeyValuePair[]) { arr.push({ key: '', value: '' }) }
function removePair(arr: KeyValuePair[], idx: number) { if (arr.length > 1) arr.splice(idx, 1) }
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">插件管理</div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <el-button type="primary" @click="handleAddTool">添加工具</el-button>
          <el-button @click="handleAdd">创建自定义插件</el-button>
          <el-button type="success" @click="handleUploadPlugin">
            <el-icon style="margin-right:4px"><UploadFilled /></el-icon>
            上传插件
          </el-button>
          <el-button type="warning" @click="handleImportJson">
            <el-icon style="margin-right:4px"><DocumentAdd /></el-icon>
            JSON导入
          </el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索插件名称..."
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>

      <el-table :data="dataList" stripe>
        <el-table-column prop="name" label="插件名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            {{ TYPE_LABELS[BACK_TYPE_TO_FRONT[row.type]] || row.type }}
          </template>
        </el-table-column>
        <el-table-column label="方法" width="70">
          <template #default="{ row }">
            <span v-if="row.type === 'http'" style="font-weight:600;color:var(--el-color-primary)">HTTP</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.enabled)" size="small">
              {{ statusText(row.enabled) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="showDialog" :title="dialogTitle" width="720px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="插件名称" required>
          <el-input v-model="formData.name" placeholder="请输入插件名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="请输入插件描述" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="formData.type" placeholder="请选择类型" style="width:100%">
            <el-option label="自定义" value="custom" />
            <el-option label="上传" value="uploaded" />
            <el-option label="JSON导入" value="json_import" />
          </el-select>
        </el-form-item>

        <!-- 文件上传区域：仅上传类型展示 -->
        <template v-if="formData.type === 'uploaded'">
          <el-divider content-position="left">上传插件文件</el-divider>
          <el-form-item label="选择文件" required>
            <el-upload
              :auto-upload="false"
              :show-file-list="true"
              :limit="1"
              :on-change="(f: any) => { uploadedFile = f.raw }"
              :on-remove="() => { uploadedFile = null }"
            >
              <el-button type="primary" plain>选择文件</el-button>
              <template #tip>
                <span class="text-muted">支持 .jar / .zip / .py 等插件文件</span>
              </template>
            </el-upload>
          </el-form-item>
        </template>

        <!-- JSON 导入区域：仅 JSON 导入类型展示 -->
        <template v-if="formData.type === 'json_import'">
          <el-divider content-position="left">JSON 导入</el-divider>
          <el-form-item label="导入方式">
            <el-radio-group v-model="jsonImportMode">
              <el-radio value="paste">粘贴 JSON</el-radio>
              <el-radio value="file">上传文件</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="jsonImportMode === 'paste'" label="JSON 内容" required>
            <el-input
              v-model="jsonContent"
              type="textarea"
              :rows="8"
              placeholder='[
  {
    "name": "插件名称",
    "identifier": "plugin-id",
    "description": "插件描述",
    "type": "http"
  }
]'
            />
          </el-form-item>

          <el-form-item v-if="jsonImportMode === 'file'" label="选择文件" required>
            <el-upload
              :auto-upload="false"
              :show-file-list="true"
              :limit="1"
              accept=".json"
              :on-change="(f: any) => { jsonFile = f.raw }"
              :on-remove="() => { jsonFile = null }"
            >
              <el-button type="primary" plain>选择 JSON 文件</el-button>
              <template #tip>
                <span class="text-muted">选择包含插件列表的 .json 文件</span>
              </template>
            </el-upload>
          </el-form-item>
        </template>

        <!-- API 配置区域：仅自定义类型展示 -->
        <template v-if="formData.type === 'custom'">
          <el-divider content-position="left">API 配置</el-divider>

          <el-form-item label="请求方法">
            <el-select v-model="apiConfigForm.method" style="width:120px">
              <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>

          <el-form-item label="请求 URL">
            <el-input v-model="apiConfigForm.url" placeholder="https://api.example.com/v1/endpoint" />
          </el-form-item>

          <el-form-item label="鉴权方式">
            <el-select v-model="apiConfigForm.authType" style="width:160px">
              <el-option v-for="a in AUTH_OPTIONS" :key="a.value" :label="a.label" :value="a.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="请求头">
            <div style="width:100%">
              <div v-for="(h, i) in apiConfigForm.headers" :key="i" class="kv-row">
                <el-input v-model="h.key" placeholder="Key" style="width:160px" size="small" />
                <el-input v-model="h.value" placeholder="Value" style="width:220px" size="small" />
                <el-button size="small" type="danger" link @click="removePair(apiConfigForm.headers, i)">✕</el-button>
              </div>
              <el-button size="small" @click="addPair(apiConfigForm.headers)">+ 添加请求头</el-button>
            </div>
          </el-form-item>

          <el-form-item label="查询参数">
            <div style="width:100%">
              <div v-for="(p, i) in apiConfigForm.params" :key="i" class="kv-row">
                <el-input v-model="p.key" placeholder="Key" style="width:160px" size="small" />
                <el-input v-model="p.value" placeholder="Value" style="width:220px" size="small" />
                <el-button size="small" type="danger" link @click="removePair(apiConfigForm.params, i)">✕</el-button>
              </div>
              <el-button size="small" @click="addPair(apiConfigForm.params)">+ 添加参数</el-button>
            </div>
          </el-form-item>

          <el-form-item label="请求体类型">
            <el-select v-model="apiConfigForm.bodyType" style="width:160px">
              <el-option v-for="b in BODY_TYPE_OPTIONS" :key="b.value" :label="b.label" :value="b.value" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="apiConfigForm.bodyType !== 'none'" label="请求体内容">
            <el-input
              v-model="apiConfigForm.body"
              type="textarea"
              :rows="4"
              placeholder="请求体内容（JSON / XML / 文本）"
            />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          @click="handleSave"
        >
          {{ formData.type === 'uploaded' ? '上传' : formData.type === 'json_import' ? '导入' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.kv-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  align-items: center;
}
.text-muted {
  color: #909399;
}
</style>
