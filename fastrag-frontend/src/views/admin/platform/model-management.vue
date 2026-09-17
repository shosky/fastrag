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
  status: string
}

const MODEL_PURPOSES = ['LLM', 'Embedding', 'Rerank', 'ASR', 'TTS', 'OCR', 'IMAGE_GEN']
const MODEL_BRANDS = ['OpenAI', '阿里云', '百度', '智谱', '深度求索', '月之暗面', 'BAAI']
const MODEL_PURPOSE_COLORS: Record<string, string> = {
  'LLM': '',
  'Embedding': 'success',
  'Rerank': 'warning',
  'ASR': 'info',
  'TTS': 'danger',
  'OCR': 'info',
  'IMAGE_GEN': 'warning',
}

const models = ref<ModelRecord[]>([])
const activeTab = ref('list')
const loading = ref(false)

const presets = ref<any[]>([])
const presetLoading = ref(false)
const showPresetDialog = ref(false)
const presetDialogTitle = ref('')
const editingPresetId = ref<string | null>(null)
const presetForm = ref({ name: '', type: 'llm', models: [] as string[] })

const selectedModelId = ref<string | null>(null)
const trainingRecords = ref<any[]>([])
const testReports = ref<any[]>([])
const callLogs = ref<any[]>([])

// ===== 模型阈值设置 =====
const showThresholdDialog = ref(false)
const thresholdModel = ref<any>(null)
const thresholdForm = ref<Record<string, any>>({ scoreThreshold: 0.6, rerankThreshold: 0.3, maxTokens: 2048 })
async function handleThreshold(model: any) {
  thresholdModel.value = model
  try {
    const res: any = await api.getModelThreshold(model.id)
    thresholdForm.value = { scoreThreshold: 0.6, rerankThreshold: 0.3, maxTokens: 2048, ...(res?.threshold || {}) }
  } catch { /* 保持默认值 */ }
  showThresholdDialog.value = true
}
async function handleSaveThreshold() {
  try {
    await api.updateModelThreshold(thresholdModel.value.id, thresholdForm.value)
    ElMessage.success('阈值已保存')
    showThresholdDialog.value = false
  } catch { ElMessage.error('保存失败') }
}
async function loadModels() {
  loading.value = true
  try {
    const res = await api.getModels()
    models.value = (res as any)?.list || (res as any) || []
  } finally {
    loading.value = false
  }
}

async function loadPresets() {
  presetLoading.value = true
  try {
    presets.value = ((await api.getModelPresets()) as any)?.list || ((await api.getModelPresets()) as any) || []
  } catch {
    presets.value = []
  } finally {
    presetLoading.value = false
  }
}

function handleAddPreset() {
  editingPresetId.value = null
  presetForm.value = { name: '', type: 'llm', models: [] }
  presetDialogTitle.value = '新增模型预置'
  showPresetDialog.value = true
}

function handleEditPreset(row: any) {
  editingPresetId.value = row.id
  presetForm.value = { name: row.name, type: row.type, models: Array.isArray(row.models) ? row.models : [] }
  presetDialogTitle.value = '编辑模型预置'
  showPresetDialog.value = true
}

async function handleSavePreset() {
  if (!presetForm.value.name) { ElMessage.warning('请输入预置名称'); return }
  if (!presetForm.value.models.length) { ElMessage.warning('请至少选择一个模型'); return }
  try {
    const data = { name: presetForm.value.name, type: presetForm.value.type, models: presetForm.value.models }
    if (editingPresetId.value) { await api.updateModelPreset(editingPresetId.value, data); ElMessage.success('更新成功') }
    else { await api.createModelPreset(data); ElMessage.success('创建成功') }
    showPresetDialog.value = false
    await loadPresets()
  } catch { ElMessage.error('保存失败') }
}

async function handleDeletePreset(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除模型预置「${row.name}」？`, '删除确认', { type: 'warning' })
    await api.deleteModelPreset(row.id)
    await loadPresets()
    ElMessage.success('删除成功')
  } catch {}
}

function handleUsePreset(row: any) {
  ElMessage.success(`已应用预置「${row.name}」，共 ${row.models?.length || 0} 个模型`)
}

onMounted(() => {
  loadModels()
  loadPresets()
})

async function loadLifecycle(modelId: string) {
  selectedModelId.value = modelId
  activeTab.value = 'lifecycle'
  loading.value = true
  try {
    const [trainings, reports] = await Promise.all([
      api.getModelTrainings(modelId),
      api.getModelTestReports(modelId),
    ])
    trainingRecords.value = Array.isArray(trainings) ? trainings : (trainings as any)?.list || []
    testReports.value = Array.isArray(reports) ? reports : (reports as any)?.list || []
    // 调用日志（真实读取 model_call_log 表）
    callLogs.value = ((await api.getModelCallLogs(modelId)) as any) || []
  } catch {
    trainingRecords.value = []
    testReports.value = []
    callLogs.value = []
  } finally {
    loading.value = false
  }
}

// 新增训练弹窗
const showTrainDialog = ref(false)
const trainForm = ref({
  dataset: '',
  epochs: 10,
  learningRate: 0.001,
  batchSize: 32,
  description: '',
})

function handleShowTrain() {
  trainForm.value = { dataset: '', epochs: 10, learningRate: 0.001, batchSize: 32, description: '' }
  showTrainDialog.value = true
}

async function handleSubmitTrain() {
  if (!trainForm.value.dataset) {
    ElMessage.warning('请填写训练数据集')
    return
  }
  try {
    await api.trainModel(selectedModelId.value!, {
      dataset: trainForm.value.dataset,
      epochs: trainForm.value.epochs,
      learningRate: trainForm.value.learningRate,
      batchSize: trainForm.value.batchSize,
      description: trainForm.value.description,
    })
    ElMessage.success('训练任务已提交')
    showTrainDialog.value = false
    // 刷新训练记录
    const trainings = await api.getModelTrainings(selectedModelId.value!)
    trainingRecords.value = Array.isArray(trainings) ? trainings : (trainings as any)?.list || []
  } catch {
    ElMessage.error('提交训练任务失败')
  }
}

async function handleStartTrain() {
  await api.trainModel(selectedModelId.value!, {})
  ElMessage.success('训练任务已提交')
  const trainings = await api.getModelTrainings(selectedModelId.value!)
  trainingRecords.value = Array.isArray(trainings) ? trainings : (trainings as any)?.list || []
}

async function handleStartTest() {
  await api.testModel(selectedModelId.value!, {})
  ElMessage.success('测试任务已提交')
  const reports = await api.getModelTestReports(selectedModelId.value!)
  testReports.value = Array.isArray(reports) ? reports : (reports as any)?.list || []
}

// ===== 对话测试（真实调用 LLM） =====
const showChatTestDialog = ref(false)
const chatTestPrompt = ref('')
const chatTestResult = ref<{ response: string; latency: number; success: boolean } | null>(null)
const chatTestLoading = ref(false)

function handleShowChatTest() {
  chatTestPrompt.value = '你好，请用一句话做自我介绍'
  chatTestResult.value = null
  showChatTestDialog.value = true
}

async function handleRunChatTest() {
  if (!chatTestPrompt.value.trim()) { ElMessage.warning('请输入测试提示词'); return }
  chatTestLoading.value = true
  try {
    const res: any = await api.testModel(selectedModelId.value!, { prompt: chatTestPrompt.value })
    let response = '', latency = 0, success = false
    try {
      const m = typeof res?.metrics === 'string' ? JSON.parse(res.metrics) : (res?.metrics || {})
      response = m.response || ''; latency = m.latency || 0; success = !!m.success
    } catch { /* metrics 解析失败按原样展示 */ }
    if (!response && typeof res?.metrics === 'string') response = res.metrics
    chatTestResult.value = { response, latency, success }
    const reports = await api.getModelTestReports(selectedModelId.value!)
    testReports.value = Array.isArray(reports) ? reports : (reports as any)?.list || []
    ElMessage.success(success ? '测试完成' : '测试失败，请检查模型配置')
  } catch {
    ElMessage.error('测试请求失败')
  } finally { chatTestLoading.value = false }
}

// ===== 模型调用（真实调用 LLM 并写调用日志） =====
const showInvokeDialog = ref(false)
const invokePrompt = ref('')
const invokeResult = ref<{ response: string; durationMs: number; success: boolean } | null>(null)
const invokeLoading = ref(false)

function handleShowInvoke() {
  invokePrompt.value = ''
  invokeResult.value = null
  showInvokeDialog.value = true
}

async function handleInvoke() {
  if (!invokePrompt.value.trim()) { ElMessage.warning('请输入调用内容'); return }
  invokeLoading.value = true
  try {
    invokeResult.value = (await api.invokeModel(selectedModelId.value!, { prompt: invokePrompt.value })) as any
    // 刷新调用日志
    if (activeTab.value === 'lifecycle') callLogs.value = ((await api.getModelCallLogs(selectedModelId.value!)) as any) || []
  } catch {
    ElMessage.error('调用失败')
  } finally { invokeLoading.value = false }
}

const showDialog = ref(false)
const dialogTitle = ref('新增模型')
const editingId = ref<string | null>(null)
const formData = ref({
  name: '',
  code: '',
  purpose: '大语言模型' as any,
  brand: '',
  apiUrl: '',
  apiKey: '',
  contextWindow: 4096,
  status: 'online' as any,
})

function handleAdd() {
  dialogTitle.value = '新增模型'
  editingId.value = null
  formData.value = { name: '', code: '', purpose: '大语言模型', brand: '', apiUrl: '', apiKey: '', contextWindow: 4096, status: 'online' }
  showDialog.value = true
}

function handleEdit(model: ModelRecord) {
  dialogTitle.value = '编辑模型'
  editingId.value = model.id
  formData.value = { name: model.name, code: model.code, purpose: model.purpose, brand: model.brand, apiUrl: model.apiUrl, apiKey: '', contextWindow: (model as any).contextWindow || 4096, status: model.status }
  showDialog.value = true
}

function handleClone(model: ModelRecord) {
  dialogTitle.value = '复刻模型'
  editingId.value = null
  formData.value = { name: model.name + '_副本', code: model.code + '_copy', purpose: model.purpose, brand: model.brand, apiUrl: model.apiUrl, apiKey: '', contextWindow: (model as any).contextWindow || 4096, status: model.status }
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

// 批量导入模型
const showImportDialog = ref(false)
const importText = ref('')

function openImport() {
  importText.value = ''
  showImportDialog.value = true
}

async function handleImport() {
  let models: any[] = []
  try {
    models = JSON.parse(importText.value)
    if (!Array.isArray(models)) throw new Error()
  } catch {
    ElMessage.warning('请输入有效的JSON数组')
    return
  }
  await api.importModels(models)
  showImportDialog.value = false
  await loadModels()
  ElMessage.success(`成功导入 ${models.length} 个模型`)
}

async function handleSave() {
  if (!formData.value.name || !formData.value.code) {
    ElMessage.warning('请填写必填项')
    return
  }
  if (editingId.value) {
    await api.updateModel(editingId.value, {
      name: formData.value.name,
      code: formData.value.code,
      purpose: formData.value.purpose,
      brand: formData.value.brand,
      apiUrl: formData.value.apiUrl,
      contextWindow: formData.value.contextWindow,
      status: formData.value.status,
    })
  } else {
    await api.createModel({
      name: formData.value.name,
      code: formData.value.code,
      purpose: formData.value.purpose,
      brand: formData.value.brand,
      apiUrl: formData.value.apiUrl,
      contextWindow: formData.value.contextWindow,
      status: formData.value.status,
    })
  }
  await loadModels()
  showDialog.value = false
  ElMessage.success('保存成功')
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

    <el-tabs v-model="activeTab" style="margin-top: 12px">
      <el-tab-pane label="模型列表" name="list">
        <div class="model-grid">
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
            <div class="card-footer">
              <el-button size="small" @click="handleEdit(model)">编辑</el-button>
          <el-button size="small" @click="handleThreshold(model)">阈值</el-button>
              <el-button size="small" @click="loadLifecycle(model.id)">生命周期</el-button>
              <el-button size="small" type="success" @click="selectedModelId = model.id; handleShowInvoke()">调用</el-button>
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
      </el-tab-pane>

      <el-tab-pane label="模型预置" name="presets">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">模型预置配置</div>
            <el-button size="small" type="primary" @click="handleAddPreset">新增预置</el-button>
          </div>
          <el-table :data="presets" stripe size="small" v-loading="presetLoading">
            <el-table-column prop="name" label="预置名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="120">
              <template #default="{ row }">
                <el-tag :type="row.type === 'llm' ? 'primary' : row.type === 'embedding' ? 'success' : 'warning'" size="small">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="models" label="包含模型" min-width="200">
              <template #default="{ row }">
                <el-tag v-for="m in (row.models || [])" :key="m" size="small" style="margin: 2px">{{ m }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditPreset(row)">编辑</el-button>
                <el-button link type="success" size="small" @click="handleUsePreset(row)">应用</el-button>
                <el-button link type="danger" size="small" @click="handleDeletePreset(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!presets.length && !presetLoading" description="暂无模型预置" :image-size="60" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 生命周期详情 -->
    <div v-if="activeTab === 'lifecycle' && selectedModelId" class="lifecycle-section">
      <div class="section-header">
        <h3>模型生命周期：{{ models.find(m => m.id === selectedModelId)?.name }}</h3>
        <el-button @click="activeTab = 'list'">返回列表</el-button>
      </div>

      <el-tabs>
        <!-- 训练记录 -->
        <el-tab-pane label="训练记录">
          <div class="section-header">
            <div class="section-title">训练记录</div>
            <div style="display:flex;gap:8px">
              <el-button size="small" type="primary" @click="handleShowTrain">新增训练</el-button>
              <el-button size="small" @click="handleStartTrain">快速训练</el-button>
            </div>
          </div>
          <el-table :data="trainingRecords" stripe size="small">
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'completed' ? 'success' : row.status === 'running' ? 'warning' : 'danger'" size="small">
                  {{ row.status === 'completed' ? '完成' : row.status === 'running' ? '训练中' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="dataSize" label="数据量" width="100" align="right" />
            <el-table-column prop="epochs" label="轮次" width="60" align="center" />
            <el-table-column label="准确率" width="80" align="right">
              <template #default="{ row }">{{ row.metrics.accuracy ? (row.metrics.accuracy * 100).toFixed(1) + '%' : '-' }}</template>
            </el-table-column>
            <el-table-column label="Loss" width="80" align="right">
              <template #default="{ row }">{{ row.metrics.loss?.toFixed(4) || '-' }}</template>
            </el-table-column>
            <el-table-column prop="startedAt" label="开始时间" width="170" />
            <el-table-column prop="completedAt" label="完成时间" width="170" />
          </el-table>
          <el-empty v-if="trainingRecords.length === 0" description="暂无训练记录" :image-size="60" />
        </el-tab-pane>

        <!-- 测试报告 -->
        <el-tab-pane label="测试报告">
          <div class="section-header">
            <div class="section-title">测试报告</div>
            <div style="display:flex;gap:8px">
              <el-button size="small" type="success" @click="handleShowChatTest">对话测试</el-button>
              <el-button size="small" type="primary" @click="handleStartTest">开始测试</el-button>
            </div>
          </div>
          <el-table :data="testReports" stripe size="small">
            <el-table-column prop="testSet" label="测试集" min-width="150" />
            <el-table-column label="准确率" width="80" align="right">
              <template #default="{ row }">{{ (row.metrics.accuracy * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column label="精确率" width="80" align="right">
              <template #default="{ row }">{{ (row.metrics.precision * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column label="召回率" width="80" align="right">
              <template #default="{ row }">{{ (row.metrics.recall * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column label="F1" width="80" align="right">
              <template #default="{ row }">{{ (row.metrics.f1 * 100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column prop="testedAt" label="测试时间" width="170" />
          </el-table>
          <el-empty v-if="testReports.length === 0" description="暂无测试报告" :image-size="60" />
        </el-tab-pane>

        <!-- 调用日志 -->
        <el-tab-pane label="调用日志">
          <el-table :data="callLogs" stripe size="small">
            <el-table-column prop="caller" label="调用方" min-width="120" />
            <el-table-column prop="tokens" label="Token数" width="90" align="right" />
            <el-table-column prop="duration" label="耗时(ms)" width="90" align="right" />
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'success' ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="timestamp" label="时间" width="170" />
          </el-table>
          <el-empty v-if="callLogs.length === 0" description="暂无调用日志（模型调用/对话测试后自动记录）" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </div>

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
          <el-input v-model="formData.apiUrl" placeholder="请输入接口地址" />
        </el-form-item>
        <el-form-item label="模型密钥">
          <el-input v-model="formData.apiKey" type="password" placeholder="请输入模型密钥" show-password />
        </el-form-item>
        <el-form-item label="上下文窗口">
          <el-input-number v-model="formData.contextWindow" :min="512" :max="1000000" :step="1024" style="width: 100%" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px">模型支持的最大上下文 Token 数，常用值：4096、8192、32768、128000</div>
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

    <!-- 对话测试弹窗（真实调用 LLM） -->
    <el-dialog v-model="showChatTestDialog" title="模型对话测试" width="560px">
      <el-form label-width="80px">
        <el-form-item label="提示词">
          <el-input v-model="chatTestPrompt" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <div v-if="chatTestResult" class="chat-test-result">
        <div><b>响应：</b></div>
        <div style="white-space:pre-wrap;margin-top:4px">{{ chatTestResult.response || '（空响应）' }}</div>
        <div style="margin-top:8px;color:#909399;font-size:12px">
          耗时 {{ chatTestResult.latency }}ms · {{ chatTestResult.success ? '成功' : '失败' }}
        </div>
      </div>
      <template #footer>
        <el-button @click="showChatTestDialog = false">关闭</el-button>
        <el-button type="primary" :loading="chatTestLoading" @click="handleRunChatTest">开始测试</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showThresholdDialog" :title="`模型阈值设置：${thresholdModel?.name || ''}`" width="480px" :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item label="相似度阈值">
          <el-slider v-model="thresholdForm.scoreThreshold" :min="0" :max="1" :step="0.05" show-input style="width: 100%" />
        </el-form-item>
        <el-form-item label="重排阈值">
          <el-slider v-model="thresholdForm.rerankThreshold" :min="0" :max="1" :step="0.05" show-input style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大 Tokens">
          <el-input-number v-model="thresholdForm.maxTokens" :min="64" :max="131072" :step="64" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showThresholdDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveThreshold">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模型调用弹窗（真实调用 LLM，写调用日志） -->
    <el-dialog v-model="showInvokeDialog" :title="'调用模型：' + (models.find(m => m.id === selectedModelId)?.name || '')" width="560px">
      <el-form label-width="80px">
        <el-form-item label="输入内容">
          <el-input v-model="invokePrompt" type="textarea" :rows="3" placeholder="输入要发送给模型的内容" />
        </el-form-item>
      </el-form>
      <div v-if="invokeResult" class="chat-test-result">
        <div><b>响应：</b></div>
        <div style="white-space:pre-wrap;margin-top:4px">{{ invokeResult.response || '（空响应）' }}</div>
        <div style="margin-top:8px;color:#909399;font-size:12px">
          耗时 {{ invokeResult.durationMs }}ms · {{ invokeResult.success ? '成功' : '失败' }}（已写入调用日志）
        </div>
      </div>
      <template #footer>
        <el-button @click="showInvokeDialog = false">关闭</el-button>
        <el-button type="primary" :loading="invokeLoading" @click="handleInvoke">调用</el-button>
      </template>
    </el-dialog>

    <!-- 新增训练弹窗 -->
    <el-dialog v-model="showTrainDialog" title="新增训练" width="520px">
      <el-form label-width="100px">
        <el-form-item label="训练模型">
          <el-tag>{{ models.find(m => m.id === selectedModelId)?.name || selectedModelId }}</el-tag>
        </el-form-item>
        <el-form-item label="训练数据集" required>
          <el-input v-model="trainForm.dataset" placeholder="请输入数据集名称或路径" />
        </el-form-item>
        <el-form-item label="训练轮次">
          <el-input-number v-model="trainForm.epochs" :min="1" :max="1000" style="width:160px" />
        </el-form-item>
        <el-form-item label="学习率">
          <el-input-number v-model="trainForm.learningRate" :min="0.00001" :max="1" :step="0.0001" :precision="5" style="width:160px" />
        </el-form-item>
        <el-form-item label="批大小">
          <el-select v-model="trainForm.batchSize" style="width:160px">
            <el-option :value="8" label="8" />
            <el-option :value="16" label="16" />
            <el-option :value="32" label="32" />
            <el-option :value="64" label="64" />
            <el-option :value="128" label="128" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="trainForm.description" type="textarea" :rows="3" placeholder="可选，训练任务备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTrainDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitTrain">提交训练</el-button>
      </template>
    </el-dialog>

    <!-- 模型预置 新增/编辑 -->
    <el-dialog v-model="showPresetDialog" :title="presetDialogTitle" width="520px">
      <el-form label-width="100px">
        <el-form-item label="预置名称" required>
          <el-input v-model="presetForm.name" placeholder="如：通义千问大模型" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="presetForm.type" style="width: 160px">
            <el-option label="大语言模型" value="llm" />
            <el-option label="嵌入模型" value="embedding" />
            <el-option label="重排序" value="rerank" />
          </el-select>
        </el-form-item>
        <el-form-item label="包含模型" required>
          <el-select v-model="presetForm.models" multiple filterable allow-create default-first-option style="width: 100%">
            <el-option label="qwen3-72b" value="qwen3-72b" />
            <el-option label="qwen-max" value="qwen-max" />
            <el-option label="qwen-plus" value="qwen-plus" />
            <el-option label="bge-m3" value="bge-m3" />
            <el-option label="bge-large-zh" value="bge-large-zh" />
            <el-option label="bge-reranker-v2-m3" value="bge-reranker-v2-m3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPresetDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSavePreset">保存</el-button>
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

// 生命周期详情区
.lifecycle-section {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
}

.chat-test-result {
  margin-top: $spacing-sm;
  padding: $spacing-sm;
  background: var(--el-fill-color-light);
  border-radius: $radius-base;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-all;
}
</style>
