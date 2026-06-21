<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getModels,
  createModel,
  updateModel,
  deleteModel,
  toggleModelStatus,
  getTrainingRecords,
  getTestReports,
  getCallLogs,
  MODEL_PURPOSES,
  MODEL_BRANDS,
  MODEL_PURPOSE_COLORS,
} from '@/mock/models'
import type { ModelPurpose, ModelStatus, ModelRecord, TrainingRecord, TestReport, CallLog } from '@/mock/models'

// 列表 —— 来自统一 mock 层（与知识库表单共享）
const models = ref<ModelRecord[]>(getModels())
const activeTab = ref('list')

// 生命周期数据
const selectedModelId = ref<string | null>(null)
const trainingRecords = ref<TrainingRecord[]>([])
const testReports = ref<TestReport[]>([])
const callLogs = ref<CallLog[]>([])

function loadLifecycle(modelId: string) {
  selectedModelId.value = modelId
  trainingRecords.value = getTrainingRecords(modelId)
  testReports.value = getTestReports(modelId)
  callLogs.value = getCallLogs(modelId)
  activeTab.value = 'lifecycle'
}

const showDialog = ref(false)
const dialogTitle = ref('新增模型')
const editingId = ref<string | null>(null)
const formData = ref({
  name: '',
  code: '',
  purpose: '大语言模型' as ModelPurpose,
  brand: '',
  apiUrl: '',
  apiKey: '',
  status: 'online' as ModelStatus,
})

function handleAdd() {
  dialogTitle.value = '新增模型'
  editingId.value = null
  formData.value = { name: '', code: '', purpose: '大语言模型', brand: '', apiUrl: '', apiKey: '', status: 'online' }
  showDialog.value = true
}

function handleEdit(model: ModelRecord) {
  dialogTitle.value = '编辑模型'
  editingId.value = model.id
  formData.value = { name: model.name, code: model.code, purpose: model.purpose, brand: model.brand, apiUrl: model.apiUrl, apiKey: '', status: model.status }
  showDialog.value = true
}

function handleClone(model: ModelRecord) {
  dialogTitle.value = '复刻模型'
  editingId.value = null
  formData.value = { name: model.name + '_副本', code: model.code + '_copy', purpose: model.purpose, brand: model.brand, apiUrl: model.apiUrl, apiKey: '', status: model.status }
  showDialog.value = true
}

async function handleDelete(model: ModelRecord) {
  try {
    await ElMessageBox.confirm('删除后该模型配置将不可继续使用，确认删除？', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    deleteModel(model.id)
    models.value = getModels()
    ElMessage.success('删除成功')
  } catch {}
}

function handleToggleStatus(model: ModelRecord) {
  toggleModelStatus(model.id)
  models.value = getModels()
  ElMessage.success(model.status === 'online' ? '已下架' : '已上架')
}

function handleSave() {
  if (!formData.value.name || !formData.value.code) {
    ElMessage.warning('请填写必填项')
    return
  }
  if (editingId.value) {
    updateModel(editingId.value, {
      name: formData.value.name,
      code: formData.value.code,
      purpose: formData.value.purpose,
      brand: formData.value.brand,
      apiUrl: formData.value.apiUrl,
      status: formData.value.status,
    })
  } else {
    createModel({
      name: formData.value.name,
      code: formData.value.code,
      purpose: formData.value.purpose,
      brand: formData.value.brand,
      apiUrl: formData.value.apiUrl,
      status: formData.value.status,
    })
  }
  models.value = getModels()
  showDialog.value = false
  ElMessage.success('保存成功')
}
</script>

<template>
  <div class="page-container">
    <div class="section-header">
      <h3>模型管理</h3>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>新增模型
      </el-button>
    </div>

    <div v-if="activeTab === 'list'" class="model-grid">
      <div v-for="model in models" :key="model.id" class="model-card">
        <div class="card-header">
          <div class="model-brand">{{ model.brand }}</div>
          <div class="card-actions">
            <el-button link size="small" @click="handleClone(model)">复刻</el-button>
          </div>
        </div>
        <h4>{{ model.name }}</h4>
        <div class="model-meta">
          <el-tag :type="MODEL_PURPOSE_COLORS[model.purpose]" size="small">{{ model.purpose }}</el-tag>
          <el-tag :type="model.status === 'online' ? 'success' : 'info'" size="small">
            {{ model.status === 'online' ? '已上架' : '已下架' }}
          </el-tag>
        </div>
        <div class="model-code">编码：{{ model.code }}</div>
        <div class="card-footer">
          <el-button size="small" @click="handleEdit(model)">编辑</el-button>
          <el-button size="small" @click="loadLifecycle(model.id)">生命周期</el-button>
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

    <!-- 生命周期详情 -->
    <div v-if="activeTab === 'lifecycle' && selectedModelId" class="lifecycle-section">
      <div class="section-header">
        <h3>模型生命周期：{{ models.find(m => m.id === selectedModelId)?.name }}</h3>
        <el-button @click="activeTab = 'list'">返回列表</el-button>
      </div>

      <el-tabs>
        <!-- 训练记录 -->
        <el-tab-pane label="训练记录">
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
            <el-table-column prop="inputTokens" label="输入Token" width="90" align="right" />
            <el-table-column prop="outputTokens" label="输出Token" width="90" align="right" />
            <el-table-column prop="duration" label="耗时(ms)" width="80" align="right" />
            <el-table-column prop="status" label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'success' ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="timestamp" label="时间" width="170" />
          </el-table>
          <el-empty v-if="callLogs.length === 0" description="暂无调用日志" :image-size="60" />
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
</style>
