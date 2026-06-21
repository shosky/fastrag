<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const form = ref({
  systemName: 'AIS 智能知识服务平台',
  slogan: '让知识触手可及',
  copyright: '© 2026 TorchV. All rights reserved.',
})

const logoUrl = ref('https://via.placeholder.com/120x120?text=AIS')
const showPreview = ref(false)

function handleSubmit() {
  if (!form.value.systemName) {
    ElMessage.warning('请输入系统名称')
    return
  }
  ElMessage.success('更新成功')
}

function handleDeleteLogo() {
  logoUrl.value = ''
}

function handlePreview() {
  showPreview.value = true
}
</script>

<template>
  <div class="page-container">
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
          <el-button type="primary" @click="handleSubmit">提交</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-dialog v-model="showPreview" title="Logo 预览" width="400px">
      <div style="text-align: center">
        <img :src="logoUrl" alt="Logo Preview" style="max-width: 100%" />
      </div>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.desc {
  color: $text-secondary;
  font-size: 13px;
  margin-bottom: $spacing-base;
}

.logo-area {
  display: flex;
  align-items: flex-start;
  gap: $spacing-base;
}

.logo-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;

  img {
    width: 120px;
    height: 120px;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    object-fit: contain;
  }

  .logo-actions {
    display: flex;
    gap: $spacing-xs;
  }
}

.logo-upload {
  .upload-tip {
    font-size: 12px;
    color: $text-secondary;
    margin-top: $spacing-xs;
  }
}
</style>
