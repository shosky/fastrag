<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const form = ref({
  systemName: '',
  slogan: '',
  copyright: '',
})

const logoUrl = ref('')
const showPreview = ref(false)
const loading = ref(false)

async function loadSettings() {
  loading.value = true
  try {
    const res: any = await api.getDictionaries({ type: '系统信息' })
    const settings = res?.['系统信息'] || []
    settings.forEach((item: any) => {
      if (item.key === 'system_name') form.value.systemName = item.value
      if (item.key === 'system_slogan') form.value.slogan = item.value
      if (item.key === 'copyright') form.value.copyright = item.value
      if (item.key === 'logo_url') logoUrl.value = item.value
    })
  } finally {
    loading.value = false
  }
}

onMounted(loadSettings)

async function handleSubmit() {
  if (!form.value.systemName) {
    ElMessage.warning('请输入系统名称')
    return
  }
  try {
    await Promise.all([
      api.createDictionary({ type: '系统信息', key: 'system_name', value: form.value.systemName }),
      api.createDictionary({ type: '系统信息', key: 'system_slogan', value: form.value.slogan }),
      api.createDictionary({ type: '系统信息', key: 'copyright', value: form.value.copyright }),
    ])
    ElMessage.success('更新成功')
  } catch (e: any) {
    ElMessage.error(e.message || '更新失败')
  }
}

function handleDeleteLogo() {
  logoUrl.value = ''
}

function handlePreview() {
  showPreview.value = true
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-title">通用设置</div>
      <p class="desc">统一维护系统对外展示的基础品牌信息，包括系统名称、宣传语、系统 Logo 和版权信息。</p>

      <el-form label-width="100px" style="max-width: 600px; margin-top: 24px">
        <el-form-item label="系统名称" required>
          <el-input v-model="form.systemName" placeholder="请输入系统名称" />
        </el-form-item>
        <el-form-item label="宣传语">
          <el-input v-model="form.slogan" placeholder="请输入宣传语" />
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
              <el-upload action="#" :auto-upload="false" accept=".jpg,.jpeg,.png">
                <el-button>上传图片</el-button>
              </el-upload>
              <p class="upload-tip">支持 jpg/jpeg/png，不超过 1MB，推荐比例 1:1</p>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="版权信息">
          <el-input v-model="form.copyright" placeholder="请输入版权信息" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit">保存配置</el-button>
        </el-form-item>
      </el-form>
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
