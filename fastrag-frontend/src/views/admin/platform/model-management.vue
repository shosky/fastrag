<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

interface ModelRecord {
  id: string
  name: string
  code: string
  purpose: string
  brand: string
  apiUrl: string
  contextWindow?: number
  enableThinking?: boolean
  status: string
}

const MODEL_PURPOSES = ['LLM', 'Embedding', 'Rerank', 'ASR', 'TTS', 'OCR']
const MODEL_BRANDS = ['OpenAI', '阿里云', '百度', '智谱', '深度求索', '月之暗面', 'BAAI']
const MODEL_PURPOSE_COLORS: Record<string, string> = {
  'LLM': '',
  'Embedding': 'success',
  'Rerank': 'warning',
  'ASR': 'info',
  'TTS': 'danger',
  'OCR': 'info',
}

const models = ref<ModelRecord[]>([])
const loading = ref(false)

// --- 模型测试 ---
const showTestDialog = ref(false)
const testingModelId = ref<string | null>(null)
const testingModelName = ref('')
const testingModelPurpose = ref('LLM')

// LLM 入参
const testPrompt = ref('你好，请简单介绍一下你自己')
// Embedding 入参
const testEmbeddingText = ref('你好世界')
// Rerank 入参
const testRerankQuery = ref('这是一段测试查询文本')
const testRerankDocs = ref('这是一条测试文档内容\n这是另一条测试文档内容\n第三条测试文档')

const testResult = ref<any>(null)
const testLoading = ref(false)

// --- 新增/编辑 ---
const showDialog = ref(false)
const dialogTitle = ref('新增模型')
const editingId = ref<string | null>(null)
const formData = ref({
  name: '',
  code: '',
  purpose: 'LLM',
  brand: '',
  apiUrl: '',
  apiKey: '',
  contextWindow: 4096,
  enableThinking: false,
  status: 'online',
})

async function loadModels() {
  loading.value = true
  try {
    const res = await api.getModels()
    models.value = (res as any)?.list || (res as any) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadModels)

// ==================== 模型测试 ====================

function openTestDialog(model: ModelRecord) {
  testingModelId.value = model.id
  testingModelName.value = model.name
  testingModelPurpose.value = model.purpose || 'LLM'
  testPrompt.value = '你好，请简单介绍一下你自己'
  testRerankQuery.value = '这是一段测试查询文本'
  testRerankDocs.value = '这是一条测试文档内容\n这是另一条测试文档内容\n第三条测试文档'
  testResult.value = null
  showTestDialog.value = true
}

async function handleSendTest() {
  const purpose = testingModelPurpose.value.toUpperCase()
  testLoading.value = true
  testResult.value = null
  try {
    let res: any
    if (purpose === 'EMBEDDING') {
      const text = testEmbeddingText.value.trim() || '你好世界'
      res = await api.testModelEmbedding(testingModelId.value!, text)
    } else if (purpose === 'RERANK') {
      const query = testRerankQuery.value.trim() || '测试查询'
      const docs = testRerankDocs.value.split('\n').map((d) => d.trim()).filter((d) => d.length > 0)
      if (docs.length === 0) {
        ElMessage.warning('请输入至少一条文档')
        testLoading.value = false
        return
      }
      res = await api.testModelRerank(testingModelId.value!, query, docs)
    } else {
      // LLM / ASR / TTS / OCR 默认走 chat
      const prompt = testPrompt.value.trim() || '你好，请简单介绍一下你自己'
      res = await api.testModelChat(testingModelId.value!, prompt)
    }
    testResult.value = res
  } catch (e: any) {
    testResult.value = { success: false, error: e?.message || '请求失败' }
  } finally {
    testLoading.value = false
  }
}

// ==================== 新增/编辑/删除 ====================

function handleAdd() {
  dialogTitle.value = '新增模型'
  editingId.value = null
  formData.value = { name: '', code: '', purpose: 'LLM', brand: '', apiUrl: '', apiKey: '', contextWindow: 4096, enableThinking: false, status: 'online' }
  showDialog.value = true
}

function handleEdit(model: ModelRecord) {
  dialogTitle.value = '编辑模型'
  editingId.value = model.id
  formData.value = {
    name: model.name,
    code: model.code,
    purpose: model.purpose,
    brand: model.brand,
    apiUrl: model.apiUrl,
    apiKey: '',
    contextWindow: model.contextWindow || 4096,
    enableThinking: model.enableThinking || false,
    status: model.status,
  }
  showDialog.value = true
}

function handleClone(model: ModelRecord) {
  dialogTitle.value = '复刻模型'
  editingId.value = null
  formData.value = {
    name: model.name + '_副本',
    code: model.code + '_copy',
    purpose: model.purpose,
    brand: model.brand,
    apiUrl: model.apiUrl,
    apiKey: '',
    contextWindow: model.contextWindow || 4096,
    enableThinking: model.enableThinking || false,
    status: model.status,
  }
  showDialog.value = true
}

async function handleDelete(model: ModelRecord) {
  try {
    await ElMessageBox.confirm('删除后该模型配置将不可继续使用，确认删除？', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await api.deleteModel(model.id)
    await loadModels()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleToggleStatus(model: ModelRecord) {
  await api.toggleModel(model.id)
  await loadModels()
  ElMessage.success(model.status === 'online' ? '已下架' : '已上架')
}

// --- 批量导入 ---
const showImportDialog = ref(false)
const importText = ref('')

function openImport() {
  importText.value = ''
  showImportDialog.value = true
}

async function handleImport() {
  let items: any[] = []
  try {
    items = JSON.parse(importText.value)
    if (!Array.isArray(items)) throw new Error()
  } catch {
    ElMessage.warning('请输入有效的JSON数组')
    return
  }
  await api.importModels(items)
  showImportDialog.value = false
  await loadModels()
  ElMessage.success(`成功导入 ${items.length} 个模型`)
}

// --- 保存 ---
async function handleSave() {
  if (!formData.value.name || !formData.value.code) {
    ElMessage.warning('请填写必填项')
    return
  }
  const payload: Record<string, any> = {
    name: formData.value.name,
    code: formData.value.code,
    purpose: formData.value.purpose,
    brand: formData.value.brand,
    apiUrl: formData.value.apiUrl,
    contextWindow: formData.value.contextWindow,
    enableThinking: formData.value.enableThinking,
    status: formData.value.status,
  }
  // 密钥字段：有值才发送（避免清空已有密钥）
  if (formData.value.apiKey.trim()) {
    payload.apiKey = formData.value.apiKey.trim()
  }

  try {
    if (editingId.value) {
      await api.updateModel(editingId.value, payload)
    } else {
      await api.createModel(payload)
    }
    await loadModels()
    showDialog.value = false
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  }
}
</script>

<template>
  <div class="page-container">
    <div class="section-header">
      <h3>模型管理</h3>
      <div>
        <el-button @click="openImport">导入模型</el-button>
        <el-button @click="api.exportModels(); ElMessage.success('已导出')">导出模型</el-button>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增模型
        </el-button>
      </div>
    </div>

    <!-- 模型卡片列表 -->
    <div v-if="models.length" class="model-grid">
      <div v-for="model in models" :key="model.id" class="model-card">
        <div class="card-header">
          <div class="model-brand">{{ model.brand }}</div>
          <div class="card-actions">
            <el-button link size="small" @click="handleClone(model)">复刻</el-button>
          </div>
        </div>
        <h4>{{ model.name }}</h4>
        <div class="model-meta">
          <el-tag :type="(MODEL_PURPOSE_COLORS[model.purpose] as any) || 'info'" size="small">{{ model.purpose }}</el-tag>
          <el-tag :type="model.status === 'online' ? 'success' : 'info'" size="small">
            {{ model.status === 'online' ? '已上架' : '已下架' }}
          </el-tag>
        </div>
        <div class="model-code">编码：{{ model.code }}</div>
        <div v-if="model.contextWindow" class="model-code">上下文窗口：{{ model.contextWindow.toLocaleString() }} tokens</div>
        <div v-if="model.enableThinking" class="model-code">
          <el-tag size="small" type="warning">思考模式已启用</el-tag>
        </div>
        <div class="card-footer">
          <el-button size="small" @click="handleEdit(model)">编辑</el-button>
          <el-button size="small" type="primary" @click="openTestDialog(model)">
            {{ model.purpose === 'Embedding' ? '向量测试' : model.purpose === 'Rerank' ? '排序测试' : '对话测试' }}
          </el-button>
          <el-button size="small" type="danger" @click="handleDelete(model)">删除</el-button>
          <el-switch
            :model-value="model.status === 'online'"
            size="small"
            active-text="上架"
            inactive-text="下架"
            @change="handleToggleStatus(model)"
          />
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无模型，请新增或导入" :image-size="80" />

    <!-- ==================== 模型测试弹窗 ==================== -->
    <el-dialog v-model="showTestDialog" :title="`模型测试：${testingModelName}`" width="700px">
      <el-form label-width="80px">
        <el-form-item label="测试模型">
          <el-tag>{{ testingModelName }}</el-tag>
          <el-tag :type="testingModelPurpose === 'LLM' ? '' : testingModelPurpose === 'Embedding' ? 'success' : testingModelPurpose === 'Rerank' ? 'warning' : 'info'" size="small" style="margin-left: 8px">
            {{ testingModelPurpose }}
          </el-tag>
        </el-form-item>

        <!-- LLM 测试：对话 -->
        <template v-if="testingModelPurpose.toUpperCase() === 'LLM' || testingModelPurpose.toUpperCase() === 'ASR' || testingModelPurpose.toUpperCase() === 'TTS' || testingModelPurpose.toUpperCase() === 'OCR'">
          <el-form-item label="输入内容">
            <el-input v-model="testPrompt" type="textarea" :rows="3" placeholder="输入要测试的内容" />
          </el-form-item>
        </template>

        <!-- Embedding 测试：文本向量化 -->
        <template v-if="testingModelPurpose.toUpperCase() === 'EMBEDDING'">
          <el-form-item label="输入文本">
            <el-input v-model="testEmbeddingText" placeholder="输入要向量化的文本" />
          </el-form-item>
        </template>

        <!-- Rerank 测试：查询 + 多文档排序 -->
        <template v-if="testingModelPurpose.toUpperCase() === 'RERANK'">
          <el-form-item label="查询语句">
            <el-input v-model="testRerankQuery" placeholder="输入查询语句" />
          </el-form-item>
          <el-form-item label="待排序文档">
            <el-input v-model="testRerankDocs" type="textarea" :rows="5" placeholder="每行一条文档" />
            <div style="font-size: 12px; color: #909399; margin-top: 4px">每行一条文档，将按与查询的相关性排序返回</div>
          </el-form-item>
        </template>

        <el-form-item>
          <el-button type="primary" :loading="testLoading" @click="handleSendTest">发送测试</el-button>
        </el-form-item>
      </el-form>

      <!-- 测试结果 -->
      <div v-if="testResult" class="test-result">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="状态">
            <el-tag :type="testResult.success ? 'success' : 'danger'" size="small">
              {{ testResult.success ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="testResult.modelName" label="模型名称">
            {{ testResult.modelName }}
          </el-descriptions-item>
          <el-descriptions-item v-if="testResult.modelCode" label="模型编码">
            {{ testResult.modelCode }}
          </el-descriptions-item>
          <el-descriptions-item v-if="testResult.apiUrl" label="接口地址">
            {{ testResult.apiUrl }}
          </el-descriptions-item>
          <el-descriptions-item v-if="testResult.elapsedMs" label="响应耗时">
            {{ testResult.elapsedMs }}ms
          </el-descriptions-item>

          <!-- LLM 结果：回复内容 -->
          <el-descriptions-item v-if="testResult.answer" label="模型回复">
            <div class="test-answer">{{ testResult.answer }}</div>
          </el-descriptions-item>

          <!-- Embedding 结果：向量维度 + 前10维预览 -->
          <el-descriptions-item v-if="testResult.dimensions" label="向量维度">
            {{ testResult.dimensions }} 维
          </el-descriptions-item>
          <el-descriptions-item v-if="testResult.vectorPreview" label="向量预览（前10维）">
            <code style="font-size: 12px">{{ JSON.stringify(testResult.vectorPreview) }}</code>
          </el-descriptions-item>
          <el-descriptions-item v-if="testResult.input" label="输入文本">
            {{ testResult.input }}
          </el-descriptions-item>

          <!-- Rerank 结果：排序列表 -->
          <el-descriptions-item v-if="testResult.results" label="排序结果">
            <el-table :data="testResult.results" stripe size="small" max-height="300">
              <el-table-column prop="index" label="原文序号" width="80" align="center" />
              <el-table-column prop="relevance_score" label="相关性分数" width="120" align="right">
                <template #default="{ row }">{{ (row.relevance_score * 100).toFixed(1) }}%</template>
              </el-table-column>
              <el-table-column prop="document" label="文档内容">
                <template #default="{ row, $index }">
                  {{ testRerankDocs.split('\n').filter((d: string) => d.trim())[row.index] || ('文档[' + row.index + ']') }}
                </template>
              </el-table-column>
            </el-table>
          </el-descriptions-item>

          <el-descriptions-item v-if="testResult.error" label="错误信息">
            <span style="color: #f56c6c">{{ testResult.error }}</span>
          </el-descriptions-item>

          <el-descriptions-item label="输入内容">
            {{ testResult.prompt || testResult.input || testResult.query || testPrompt }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- ==================== 新增/编辑弹窗 ==================== -->
    <el-dialog v-model="showDialog" :title="dialogTitle" width="600px">
      <el-form label-width="100px">
        <el-form-item label="模型用途">
          <el-select v-model="formData.purpose" style="width: 100%">
            <el-option v-for="p in MODEL_PURPOSES" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="formData.name" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="模型编码" required>
          <el-input v-model="formData.code" placeholder="请输入模型编码" />
        </el-form-item>
        <el-form-item label="模型品牌">
          <el-select v-model="formData.brand" placeholder="请选择品牌" style="width: 100%">
            <el-option v-for="b in MODEL_BRANDS" :key="b" :label="b" :value="b" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口地址">
          <el-input v-model="formData.apiUrl" placeholder="请输入接口地址，如 https://api.siliconflow.cn" />
        </el-form-item>
        <el-form-item label="模型密钥">
          <el-input v-model="formData.apiKey" type="password" placeholder="留空则不修改" show-password />
        </el-form-item>
        <el-form-item label="上下文窗口">
          <el-input-number v-model="formData.contextWindow" :min="512" :max="1000000" :step="1024" style="width: 100%" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px">模型支持的最大上下文 Token 数，常用值：4096、8192、32768、128000</div>
        </el-form-item>
        <el-form-item label="思考模式">
          <div style="display:flex;align-items:center;gap:8px">
            <el-switch v-model="formData.enableThinking" />
            <span style="font-size: 12px; color: #909399">启用后模型在推理时会展示思考过程（DeepSeek 等模型支持）</span>
          </div>
        </el-form-item>
        <el-form-item label="是否发布">
          <el-radio-group v-model="formData.status">
            <el-radio value="online">是</el-radio>
            <el-radio value="offline">否</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 批量导入弹窗 ==================== -->
    <el-dialog v-model="showImportDialog" title="批量导入模型" width="600px">
      <el-form label-width="100px">
        <el-form-item label="模型JSON">
          <el-input v-model="importText" type="textarea" :rows="10" placeholder='[{"name":"GLM-4","code":"glm-4","purpose":"LLM","brand":"智谱","apiUrl":"https://open.bigmodel.cn/api/paas/v4"}]' />
        </el-form-item>
        <p style="font-size:12px;color:#909399;">请输入JSON数组格式，每个对象包含 name/code/purpose/brand/apiUrl/apiKeyRef/status 字段</p>
      </el-form>
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" @click="handleImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: $spacing-base;
}

.model-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  border: 1px solid $border-lighter;
  transition: all 0.2s;

  &:hover {
    box-shadow: $shadow-base;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-sm;
  }

  .model-brand {
    font-size: 12px;
    color: $text-secondary;
    background: $bg-hover;
    padding: 2px 8px;
    border-radius: $radius-sm;
  }

  h4 { margin: 0 0 $spacing-sm; }

  .model-meta {
    display: flex;
    gap: $spacing-xs;
    margin-bottom: $spacing-sm;
  }

  .model-code {
    font-size: 12px;
    color: $text-secondary;
    margin-bottom: $spacing-base;
  }

  .card-footer {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding-top: $spacing-sm;
    border-top: 1px solid $border-lighter;
  }
}

.test-result {
  margin-top: 16px;
  padding: 12px;
  background: $bg-hover;
  border-radius: $radius-base;
}

.test-answer {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
  padding: 8px;
  background: $bg-white;
  border-radius: $radius-sm;
  font-family: monospace;
}
</style>
