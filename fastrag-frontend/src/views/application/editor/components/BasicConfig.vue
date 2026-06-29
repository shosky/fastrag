<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'
import { Download, UploadFilled, ZoomIn } from '@element-plus/icons-vue'

const props = defineProps<{
  appInfo: {
    id: string
    name: string
    type: string
    description: string
    accessToken: string
  }
}>()

// ===========================================================================
// 基础配置
// ===========================================================================
const basicForm = ref({
  memoryRounds: 5,
  outputFormat: 'markdown',
  timeoutSeconds: 30,
  greeting: '',
  goodbyeMessage: '',
})
const advancedForm = ref({
  model: '',
  temperature: 0.7,
  maxTokens: 2048,
  topP: 1,
  frequencyPenalty: 0,
  presencePenalty: 0,
})
const showAdvanced = ref(false)
const showDetailDialog = ref(false)
const detailData = ref('')

const appId = () => props.appInfo.id

async function loadBasic() {
  try {
    const r: any = await api.getAppBasicConfig(appId())
    if (r) Object.assign(basicForm.value, r)
    if (r?.advanced) Object.assign(advancedForm.value, r.advanced)
  } catch { /* ignore */ }
}

async function saveBasic() {
  await api.saveAppBasicConfig(appId(), basicForm.value)
  ElMessage.success('基础配置已保存')
}

async function saveAdvancedOpts() {
  await api.saveAppAdvanced(appId(), advancedForm.value)
  ElMessage.success('高级选项已保存')
}

async function handleViewDetail() {
  try {
    const r: any = await api.getAppBasicConfig(appId())
    detailData.value = JSON.stringify({ basic: r, advanced: advancedForm.value }, null, 2)
    showDetailDialog.value = true
  } catch {
    ElMessage.error('获取配置详情失败')
  }
}

async function handleExportBasic() {
  try {
    const r: any = await api.exportAppConfig(appId())
    const blob = new Blob([JSON.stringify(r, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `basic_config_${appId()}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('基础配置已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}

async function handleImportBasic() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e: any) => {
    try {
      const file = e.target.files[0]
      if (!file) return
      const text = await file.text()
      const data = JSON.parse(text)
      await api.importAppConfig(appId(), data)
      await loadBasic()
      ElMessage.success('基础配置已导入')
    } catch {
      ElMessage.error('导入失败，请检查文件格式')
    }
  }
  input.click()
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('复制成功')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

onMounted(() => {
  loadBasic()
})
</script>

<template>
  <div class="config-section">
    <!-- 基础信息配置 -->
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">基础信息配置</div>
        <div class="section-actions">
          <el-button size="small" :icon="ZoomIn" @click="handleViewDetail">查看配置详情</el-button>
          <el-button size="small" :icon="Download" @click="handleExportBasic">导出配置</el-button>
          <el-button size="small" :icon="UploadFilled" @click="handleImportBasic">导入配置</el-button>
        </div>
      </div>
      <el-form label-width="120px" style="margin-top:16px">
        <el-form-item label="对话记忆轮数">
          <el-input-number v-model="basicForm.memoryRounds" :min="0" :max="50" />
        </el-form-item>
        <el-form-item label="输出格式">
          <el-select v-model="basicForm.outputFormat" style="width:160px">
            <el-option label="Markdown" value="markdown" />
            <el-option label="HTML" value="html" />
            <el-option label="纯文本" value="text" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时秒数">
          <el-input-number v-model="basicForm.timeoutSeconds" :min="5" :max="120" />
        </el-form-item>
        <el-form-item label="开场白">
          <el-input v-model="basicForm.greeting" type="textarea" :rows="2" placeholder="请输入开场白" />
        </el-form-item>
        <el-form-item label="结束语">
          <el-input v-model="basicForm.goodbyeMessage" type="textarea" :rows="2" placeholder="请输入结束语" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveBasic">保存基础配置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 高级选项 -->
    <div class="card-panel" style="margin-top:16px">
      <div class="section-header">
        <div class="section-title">高级选项</div>
        <el-button size="small" text @click="showAdvanced = !showAdvanced">
          {{ showAdvanced ? '收起' : '展开' }}
        </el-button>
      </div>
      <el-form v-if="showAdvanced" label-width="120px" style="margin-top:16px">
        <el-form-item label="模型选择">
          <el-input v-model="advancedForm.model" placeholder="默认使用系统模型" />
        </el-form-item>
        <el-form-item label="温度">
          <el-slider v-model="advancedForm.temperature" :min="0" :max="2" :step="0.1" show-input style="width:300px" />
        </el-form-item>
        <el-form-item label="最大Token数">
          <el-input-number v-model="advancedForm.maxTokens" :min="128" :max="8192" :step="128" />
        </el-form-item>
        <el-form-item label="Top P">
          <el-slider v-model="advancedForm.topP" :min="0" :max="1" :step="0.05" show-input style="width:300px" />
        </el-form-item>
        <el-form-item label="频率惩罚">
          <el-slider v-model="advancedForm.frequencyPenalty" :min="-2" :max="2" :step="0.1" show-input style="width:300px" />
        </el-form-item>
        <el-form-item label="存在惩罚">
          <el-slider v-model="advancedForm.presencePenalty" :min="-2" :max="2" :step="0.1" show-input style="width:300px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveAdvancedOpts">保存高级选项</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 原有基本信息 + 开发信息 -->
    <div class="card-panel" style="margin-top:16px">
      <div class="section-title">基本信息</div>
      <el-form label-width="100px" style="max-width:700px; margin-top:16px">
        <el-form-item label="应用类型：">
          <el-input :value="basicForm.outputFormat ? props.appInfo.type : props.appInfo.type" disabled />
        </el-form-item>
        <el-form-item label="应用名称：" required>
          <el-input v-model="props.appInfo.name" disabled />
        </el-form-item>
        <el-form-item label="应用简介：">
          <el-input :value="props.appInfo.description" type="textarea" :rows="2" disabled />
        </el-form-item>
      </el-form>
    </div>

    <div class="card-panel" style="margin-top:16px">
      <div class="section-title">开发信息</div>
      <el-form label-width="100px" style="max-width:700px; margin-top:16px">
        <el-form-item label="AccessToken：">
          <el-input :value="props.appInfo.accessToken" readonly>
            <template #append>
              <el-button @click="copyToClipboard(props.appInfo.accessToken)">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
    </div>

    <!-- 配置详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="配置详情" width="640px">
      <pre style="background:#f5f7fa;padding:16px;border-radius:4px;overflow:auto;max-height:400px;font-size:13px;white-space:pre-wrap;word-break:break-all">{{ detailData }}</pre>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
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
  flex-wrap: wrap;
  gap: 8px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
}

.card-panel {
  background: var(--el-bg-color-overlay);
  border-radius: 8px;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
}
</style>
