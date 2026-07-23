<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'
import { storage } from '@/utils/storage'

const loading = ref(false)

// ===== 品牌信息 =====
const brandForm = reactive({
  systemName: '',
  slogan: '',
  copyright: '',
  orgName: '',
})

const logoUrl = ref('')
const showPreview = ref(false)
const uploadUrl = computed(() => {
  const baseURL = (window as any).__BASE_URL__ || import.meta.env.VITE_API_BASE || ''
  return `${baseURL}/api/upload`
})
const token = computed(() => storage.get('token') || '')

// ===== 语言与时区 =====
const localeForm = reactive({
  defaultLanguage: 'zh-CN',
  timezone: 'Asia/Shanghai',
})

// ===== 通用参数 =====
const generalForm = reactive({
  chunkSize: 512,
  chunkOverlap: 50,
  searchTopK: 10,
  retrievalMode: 'hybrid',
  enableRerank: false,
  maxTokens: 2048,
})

// ===== 加载配置 =====
async function loadSettings() {
  loading.value = true
  try {
    // 加载品牌信息
    const brandRes: any = await api.getSysConfigs('brand')
    const brandConfigs = brandRes?.data || []
    brandConfigs.forEach((item: any) => {
      if (item.configKey === 'system_name') brandForm.systemName = item.configValue || ''
      if (item.configKey === 'system_slogan') brandForm.slogan = item.configValue || ''
      if (item.configKey === 'copyright') brandForm.copyright = item.configValue || ''
      if (item.configKey === 'logo_url') logoUrl.value = item.configValue || ''
      if (item.configKey === 'org_name') {
        try {
          const parsed = JSON.parse(item.configValue)
          brandForm.orgName = parsed?.value || ''
        } catch { brandForm.orgName = item.configValue || '' }
      }
    })

    // 加载通用参数（含语言时区）
    const generalRes: any = await api.getSysConfigs('general')
    const generalConfigs = generalRes?.data || []
    generalConfigs.forEach((item: any) => {
      if (item.configKey === 'general_settings') {
        try {
          const parsed = JSON.parse(item.configValue)
          localeForm.defaultLanguage = parsed?.defaultLanguage || 'zh-CN'
          localeForm.timezone = parsed?.timezone || 'Asia/Shanghai'
        } catch { /* ignore */ }
      }
      if (item.configKey === 'general_chunk_size') generalForm.chunkSize = parseInt(item.configValue) || 512
      if (item.configKey === 'general_chunk_overlap') generalForm.chunkOverlap = parseInt(item.configValue) || 50
      if (item.configKey === 'general_search_top_k') generalForm.searchTopK = parseInt(item.configValue) || 10
      if (item.configKey === 'general_retrieval_mode') generalForm.retrievalMode = item.configValue || 'hybrid'
      if (item.configKey === 'general_enable_rerank') generalForm.enableRerank = item.configValue === 'true'
      if (item.configKey === 'general_max_tokens') generalForm.maxTokens = parseInt(item.configValue) || 2048
    })
  } finally {
    loading.value = false
  }
}

onMounted(loadSettings)

// ===== Logo 上传 =====
function handleUploadSuccess(response: any) {
  if (response?.data) {
    logoUrl.value = response.data
    ElMessage.success('Logo 上传成功')
  }
}

function beforeUpload(file: File) {
  const isImage = ['image/jpeg', 'image/png'].includes(file.type)
  const isLt1M = file.size / 1024 / 1024 < 1
  if (!isImage) {
    ElMessage.error('仅支持 jpg/jpeg/png 格式')
    return false
  }
  if (!isLt1M) {
    ElMessage.error('图片大小不能超过 1MB')
    return false
  }
  return true
}

function handleDeleteLogo() {
  logoUrl.value = ''
}

function handlePreview() {
  showPreview.value = true
}

// ===== 保存配置 =====
async function saveAll() {
  if (!brandForm.systemName) {
    ElMessage.warning('请输入系统名称')
    return
  }
  loading.value = true
  try {
    const localeSettings = JSON.stringify({
      defaultLanguage: localeForm.defaultLanguage,
      timezone: localeForm.timezone,
    })

    await Promise.all([
      // 品牌信息
      api.saveSysConfig({ configKey: 'system_name', configValue: brandForm.systemName, configType: 'brand', description: '系统名称' }),
      api.saveSysConfig({ configKey: 'system_slogan', configValue: brandForm.slogan, configType: 'brand', description: '宣传语' }),
      api.saveSysConfig({ configKey: 'copyright', configValue: brandForm.copyright, configType: 'brand', description: '版权信息' }),
      api.saveSysConfig({ configKey: 'logo_url', configValue: logoUrl.value, configType: 'brand', description: '系统 Logo URL' }),
      api.saveSysConfig({ configKey: 'org_name', configValue: JSON.stringify({ value: brandForm.orgName }), configType: 'brand', description: '组织名称' }),
      // 语言与时区
      api.saveSysConfig({ configKey: 'general_settings', configValue: localeSettings, configType: 'general', description: '通用设置' }),
      // 通用参数
      api.saveSysConfig({ configKey: 'general_chunk_size', configValue: String(generalForm.chunkSize), configType: 'general', description: '默认分片大小' }),
      api.saveSysConfig({ configKey: 'general_chunk_overlap', configValue: String(generalForm.chunkOverlap), configType: 'general', description: '默认分片重叠' }),
      api.saveSysConfig({ configKey: 'general_search_top_k', configValue: String(generalForm.searchTopK), configType: 'general', description: '默认搜索返回条数' }),
      api.saveSysConfig({ configKey: 'general_retrieval_mode', configValue: generalForm.retrievalMode, configType: 'general', description: '默认检索模式' }),
      api.saveSysConfig({ configKey: 'general_enable_rerank', configValue: generalForm.enableRerank ? 'true' : 'false', configType: 'general', description: '是否启用重排序' }),
      api.saveSysConfig({ configKey: 'general_max_tokens', configValue: String(generalForm.maxTokens), configType: 'general', description: '默认最大生成Token数' }),
    ])
    ElMessage.success('配置保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    loading.value = false
  }
}

// ===== 重置为默认值 =====
async function handleResetToDefault() {
  try {
    await ElMessageBox.confirm('确认将所有配置重置为默认值？此操作不可撤销。', '确认重置', {
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
      type: 'warning',
    })
    loading.value = true
    const defaultRes: any = await api.getDefaultConfigs()
    const defaultConfigs = defaultRes?.data || []
    const configKeys = defaultConfigs.map((c: any) => c.configKey)
    await api.resetDefaultConfig(configKeys as any)
    ElMessage.success('已重置为默认值')
    await loadSettings()
  } catch { /* 用户取消或失败 */ }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-title">通用设置</div>
      <p class="desc">统一维护系统对外展示的基础品牌信息、语言时区以及文档处理、检索等通用参数。</p>

      <!-- ===== 品牌信息 ===== -->
      <div class="form-section">
        <div class="form-section__title">品牌信息</div>
        <el-form label-width="100px" style="max-width: 600px">
          <el-form-item label="系统名称" required>
            <el-input v-model="brandForm.systemName" placeholder="请输入系统名称" />
          </el-form-item>
          <el-form-item label="宣传语">
            <el-input v-model="brandForm.slogan" placeholder="请输入宣传语" />
          </el-form-item>
          <el-form-item label="组织名称">
            <el-input v-model="brandForm.orgName" placeholder="请输入组织名称" />
          </el-form-item>
          <el-form-item label="系统 Logo">
            <div class="logo-area">
              <div v-if="logoUrl" class="logo-preview">
                <img :src="logoUrl" alt="Logo" />
                <div class="logo-actions">
                  <el-button size="small" @click="handlePreview">预览</el-button>
                  <el-button size="small" type="danger" @click="handleDeleteLogo">删除</el-button>
                </div>
              </div>
              <div v-else class="logo-upload">
                <el-upload
                  :action="uploadUrl"
                  :headers="{ Authorization: `Bearer ${token}` }"
                  :on-success="handleUploadSuccess"
                  :before-upload="beforeUpload"
                  :limit="1"
                  accept=".jpg,.jpeg,.png"
                >
                  <el-button>上传图片</el-button>
                </el-upload>
                <p class="upload-tip">支持 jpg/jpeg/png，不超过 1MB，推荐比例 1:1</p>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="版权信息">
            <el-input v-model="brandForm.copyright" placeholder="请输入版权信息，留空使用默认值" />
          </el-form-item>
        </el-form>
      </div>

      <!-- ===== 语言与时区 ===== -->
      <div class="form-section">
        <div class="form-section__title">语言与时区</div>
        <el-form label-width="150px" style="max-width: 600px">
          <el-form-item label="默认语言">
            <el-select v-model="localeForm.defaultLanguage" style="width: 100%">
              <el-option label="简体中文 (zh-CN)" value="zh-CN" />
              <el-option label="英文 (en-US)" value="en-US" />
              <el-option label="繁体中文 (zh-TW)" value="zh-TW" />
            </el-select>
          </el-form-item>
          <el-form-item label="时区">
            <el-select v-model="localeForm.timezone" style="width: 100%">
              <el-option label="亚洲/上海 (Asia/Shanghai)" value="Asia/Shanghai" />
              <el-option label="亚洲/香港 (Asia/Hong_Kong)" value="Asia/Hong_Kong" />
              <el-option label="亚洲/东京 (Asia/Tokyo)" value="Asia/Tokyo" />
              <el-option label="美国/纽约 (America/New_York)" value="America/New_York" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- ===== 通用参数 ===== -->
      <div class="form-section">
        <div class="form-section__title">通用参数</div>
        <el-form label-width="180px" style="max-width: 600px">
          <el-form-item label="默认分片大小">
            <el-input-number v-model="generalForm.chunkSize" :min="128" :max="2048" :step="128" />
          </el-form-item>
          <el-form-item label="默认分片重叠">
            <el-input-number v-model="generalForm.chunkOverlap" :min="0" :max="500" :step="10" />
          </el-form-item>
          <el-form-item label="默认搜索返回条数">
            <el-input-number v-model="generalForm.searchTopK" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="默认检索模式">
            <el-select v-model="generalForm.retrievalMode" style="width: 100%">
              <el-option label="混合检索 (hybrid)" value="hybrid" />
              <el-option label="全文检索 (fulltext)" value="fulltext" />
              <el-option label="向量检索 (vector)" value="vector" />
            </el-select>
          </el-form-item>
          <el-form-item label="启用重排序">
            <el-switch v-model="generalForm.enableRerank" />
          </el-form-item>
          <el-form-item label="默认最大生成 Token 数">
            <el-input-number v-model="generalForm.maxTokens" :min="512" :max="8192" :step="512" />
          </el-form-item>
        </el-form>
      </div>

      <!-- ===== 操作按钮 ===== -->
      <div class="form-actions">
        <el-button type="primary" size="large" @click="saveAll">保存配置</el-button>
        <el-button size="large" @click="handleResetToDefault">重置为默认值</el-button>
      </div>
    </div>

    <el-dialog v-model="showPreview" title="Logo 预览" width="400px">
      <div style="text-align: center">
        <img :src="logoUrl" alt="Logo Preview" style="max-width: 100%; max-height: 400px" />
      </div>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.desc {
  font-size: 13px;
  color: $text-secondary;
  margin: $spacing-sm 0 $spacing-lg;
}

.form-section {
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid $border-lighter;

  &:last-of-type {
    border-bottom: none;
    margin-bottom: 0;
    padding-bottom: 0;
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 20px;
    padding-left: 10px;
    border-left: 3px solid $color-primary;
    line-height: 1;
  }
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid $border-lighter;
}

.logo-area {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.logo-preview {
  display: flex;
  align-items: center;
  gap: $spacing-base;

  img {
    width: 80px;
    height: 80px;
    object-fit: contain;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
  }

  .logo-actions {
    display: flex;
    flex-direction: column;
    gap: $spacing-xs;
  }
}

.upload-tip {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}
</style>
