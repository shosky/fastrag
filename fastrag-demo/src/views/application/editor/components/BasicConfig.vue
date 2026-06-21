<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  appInfo: {
    id: string
    name: string
    type: string
    description: string
    accessToken: string
  }
}>()

const basicForm = ref({
  name: props.appInfo.name,
  type: props.appInfo.type,
  description: props.appInfo.description,
  icon: '',
  tags: [] as string[],
})

const availableTags = ['测试', '应用中心', '写作', '创作', '客服', '机器人', '问答', '文档']

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('复制成功')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

function handleSaveBasic() {
  ElMessage.success('基本信息保存成功')
}
</script>

<template>
  <div class="config-section">
    <!-- 基本信息 -->
    <div class="section-group">
      <h3 class="section-title">基本信息</h3>
      <el-form label-width="100px" style="max-width: 700px">
        <el-form-item label="应用类型：">
          <el-input :value="basicForm.type" disabled />
        </el-form-item>
        <el-form-item label="应用名称：" required>
          <el-input v-model="basicForm.name" placeholder="请输入应用名称" />
        </el-form-item>
        <el-form-item label="应用图标：">
          <div class="icon-upload">
            <div class="icon-preview" v-if="basicForm.icon">
              <el-icon :size="24" color="#409eff"><ChatDotRound /></el-icon>
            </div>
            <div class="icon-upload-btn" v-else>
              <el-icon :size="24"><Plus /></el-icon>
            </div>
            <el-button link type="primary">快捷选取</el-button>
          </div>
          <div class="icon-tip">支持文件格式：jpg, jpeg, png, gif，图片大小不超过1M</div>
        </el-form-item>
        <el-form-item label="应用标签：">
          <el-select v-model="basicForm.tags" multiple placeholder="选择标签" style="width: 100%">
            <el-option v-for="tag in availableTags" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="应用简介：" required>
          <el-input v-model="basicForm.description" type="textarea" :rows="4" placeholder="请输入你的问题开始我的服务哦。" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSaveBasic">保 存</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 开发信息 -->
    <div class="section-group">
      <h3 class="section-title">开发信息</h3>
      <el-form label-width="100px" style="max-width: 700px">
        <el-form-item label="AccessToken：">
          <el-input :value="appInfo.accessToken" readonly>
            <template #append>
              <el-button @click="copyToClipboard(appInfo.accessToken)">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
            </template>
          </el-input>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-group {
  margin-bottom: $spacing-xl;
  padding-bottom: $spacing-xl;
  border-bottom: 1px solid $border-lighter;

  &:last-child {
    border-bottom: none;
  }

  .section-title {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: $spacing-lg;
  }
}

.icon-upload {
  display: flex;
  align-items: center;
  gap: $spacing-base;
}

.icon-preview {
  width: 64px;
  height: 64px;
  border-radius: $radius-base;
  background: rgba(64, 158, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-upload-btn {
  width: 64px;
  height: 64px;
  border-radius: $radius-base;
  border: 2px dashed $border-base;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: $color-primary;
    color: $color-primary;
  }
}

.icon-tip {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}
</style>
