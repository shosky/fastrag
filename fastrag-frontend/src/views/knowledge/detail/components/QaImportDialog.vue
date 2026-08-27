<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import * as api from '@/api'
import type { QaImportResult } from '@/types/knowledge'

const props = defineProps<{
  visible: boolean
  kbId: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'success'): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v),
})

// ---- 上传状态 ----
const importFile = ref<File | null>(null)
const overwrite = ref(false)
const importing = ref(false)

// ---- 结果状态 ----
const result = ref<QaImportResult | null>(null)
const activeTab = ref('failed')

// ---- 重置 ----
function resetState() {
  importFile.value = null
  overwrite.value = false
  importing.value = false
  result.value = null
  activeTab.value = 'failed'
}

watch(() => props.visible, (v) => {
  if (v) resetState()
})

import { watch } from 'vue'

// ---- 下载模板 ----
async function handleDownloadTemplate() {
  try {
    const blob = await api.downloadQaImportTemplate(props.kbId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'qa-pairs-import-template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('模板下载成功')
  } catch {
    ElMessage.error('模板下载失败，请重试')
  }
}

// ---- 文件选择 ----
function handleFileChange(file: UploadFile) {
  if (file.raw) {
    importFile.value = file.raw
  }
}

function handleRemoveFile() {
  importFile.value = null
}

// ---- 执行导入 ----
async function handleImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择要导入的 Excel 文件')
    return
  }

  importing.value = true
  try {
    const res: QaImportResult = await api.importQaPairs(props.kbId, importFile.value, overwrite.value)
    result.value = res

    // 默认显示失败标签页（有失败时），否则显示成功
    if (res.failCount > 0) {
      activeTab.value = 'failed'
    } else if (res.skipCount > 0) {
      activeTab.value = 'skipped'
    } else {
      activeTab.value = 'success'
    }

    if (res.failCount === 0 && res.totalRows > 0) {
      ElMessage.success(`导入完成：成功 ${res.successCount} 条` + (res.skipCount > 0 ? `，跳过 ${res.skipCount} 条` : ''))
    } else if (res.totalRows === 0) {
      ElMessage.warning('Excel 文件中没有有效数据行')
    } else {
      ElMessage.warning(`导入完成：成功 ${res.successCount} 条，跳过 ${res.skipCount} 条，失败 ${res.failCount} 条`)
    }

    emit('success')
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败，请重试')
  } finally {
    importing.value = false
  }
}

// ---- 结果表格数据 ----
const successList = computed(() => result.value?.details.filter(r => r.status === 'success') ?? [])
const skippedList = computed(() => result.value?.details.filter(r => r.status === 'skipped') ?? [])
const failedList  = computed(() => result.value?.details.filter(r => r.status === 'failed') ?? [])

// ---- 关闭对话框 ----
function handleClose() {
  dialogVisible.value = false
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="导入问答对"
    width="720px"
    :close-on-click-modal="!importing"
    @closed="resetState"
  >
    <!-- ==================== 上传表单 ==================== -->
    <div v-if="!result">
      <div class="import-desc">
        <p>请上传 Excel 文件（.xlsx 或 .xls），系统将批量导入问答对到当前知识库。</p>
        <p class="import-desc-tip">
          支持列名：问题（必填）、答案（必填）、分类、关键词、优先级、状态、生效时间、失效时间
        </p>
      </div>

      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
        :on-remove="handleRemoveFile"
        :disabled="importing"
        style="width: 100%"
      >
        <div class="upload-area">
          <el-icon class="upload-icon"><Upload /></el-icon>
          <div class="upload-text">
            <span class="upload-main">将 Excel 文件拖到此处，或 <em>点击上传</em></span>
            <span class="upload-tip">仅支持 .xlsx / .xls 格式，单文件不超过 10MB，最多 10000 条</span>
          </div>
        </div>
      </el-upload>

      <div v-if="importFile" class="selected-file">
        <el-icon><Document /></el-icon>
        <span>{{ importFile.name }}</span>
        <span class="file-size">（{{ (importFile.size / 1024).toFixed(1) }} KB）</span>
      </div>

      <div class="import-options">
        <el-checkbox v-model="overwrite" :disabled="importing">
          覆盖已存在的问答对（按问题文本匹配）
        </el-checkbox>
        <span class="option-tip">不勾选时，已存在的相同问题将被跳过</span>
      </div>
    </div>

    <!-- ==================== 导入结果 ==================== -->
    <div v-else class="result-panel">
      <div class="result-summary">
        <el-row :gutter="16">
          <el-col :span="8">
            <div class="summary-item summary-success">
              <div class="summary-number">{{ result.successCount }}</div>
              <div class="summary-label">成功</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="summary-item summary-skip">
              <div class="summary-number">{{ result.skipCount }}</div>
              <div class="summary-label">跳过</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="summary-item summary-fail">
              <div class="summary-number">{{ result.failCount }}</div>
              <div class="summary-label">失败</div>
            </div>
          </el-col>
        </el-row>
        <div class="result-total">共 {{ result.totalRows }} 条数据</div>
      </div>

      <el-tabs v-model="activeTab" class="result-tabs">
        <el-tab-pane label="成功列表" name="success">
          <el-table v-if="successList.length" :data="successList" stripe size="small" max-height="360">
            <el-table-column type="index" width="50" label="#" />
            <el-table-column prop="rowNumber" label="行号" width="70" />
            <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="answer" label="答案" min-width="250" show-overflow-tooltip />
          </el-table>
          <el-empty v-else description="暂无成功导入的记录" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="跳过列表" name="skipped">
          <el-table v-if="skippedList.length" :data="skippedList" stripe size="small" max-height="360">
            <el-table-column type="index" width="50" label="#" />
            <el-table-column prop="rowNumber" label="行号" width="70" />
            <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="reason" label="跳过原因" min-width="200" show-overflow-tooltip />
          </el-table>
          <el-empty v-else description="没有跳过的记录" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="失败列表" name="failed">
          <el-table v-if="failedList.length" :data="failedList" stripe size="small" max-height="360">
            <el-table-column type="index" width="50" label="#" />
            <el-table-column prop="rowNumber" label="行号" width="70" />
            <el-table-column prop="question" label="问题" min-width="150" show-overflow-tooltip />
            <el-table-column prop="reason" label="失败原因" min-width="200">
              <template #default="{ row }">
                <span class="fail-reason">{{ row.reason }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="没有失败的记录" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ==================== 底部操作栏 ==================== -->
    <template #footer>
      <template v-if="!result">
        <el-button @click="handleClose" :disabled="importing">取消</el-button>
        <el-button @click="handleDownloadTemplate" :disabled="importing">
          <el-icon class="el-icon--left"><Download /></el-icon>
          下载模板
        </el-button>
        <el-button type="primary" @click="handleImport" :loading="importing" :disabled="!importFile">
          {{ importing ? '正在导入...' : '开始导入' }}
        </el-button>
      </template>
      <template v-else>
        <el-button type="primary" @click="handleClose">关闭</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.import-desc {
  margin-bottom: 16px;
  p {
    margin: 0 0 6px;
    font-size: 14px;
    color: #606266;
  }
  .import-desc-tip {
    font-size: 13px;
    color: #909399;
  }
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0;
  .upload-icon {
    font-size: 48px;
    color: #c0c4cc;
    margin-bottom: 12px;
  }
  .upload-text {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    .upload-main {
      font-size: 14px;
      color: #606266;
      em {
        color: #409eff;
        font-style: normal;
      }
    }
    .upload-tip {
      font-size: 12px;
      color: #909399;
    }
  }
}

.selected-file {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
  .file-size {
    color: #909399;
  }
}

.import-options {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  .option-tip {
    font-size: 12px;
    color: #909399;
  }
}

// ---- 结果面板 ----
.result-panel {
  .result-summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
    .summary-item {
      text-align: center;
      padding: 12px 0;
      border-radius: 6px;
      &.summary-success { background: #f0f9eb; }
      &.summary-skip   { background: #fdf6ec; }
      &.summary-fail   { background: #fef0f0; }
      .summary-number {
        font-size: 24px;
        font-weight: 600;
        &.success { color: #67c23a; }
        &.skip    { color: #e6a23c; }
        &.fail    { color: #f56c6c; }
      }
      .summary-label {
        font-size: 13px;
        color: #909399;
        margin-top: 4px;
      }
    }
    .result-total {
      font-size: 13px;
      color: #909399;
    }
  }

  .result-tabs {
    .fail-reason {
      color: #f56c6c;
    }
  }
}
</style>
