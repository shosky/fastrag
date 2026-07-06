<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// 界面配置
// ===========================================================================

const uiConfig = ref({
  welcome: '您好,有什么我可以帮助您',
  signature: '',
  title: '标题',
  placeholder: '请输入您的问题',
  textInfo: '帮助中心',
  brandText: 'Powered by AIS',
  colorTemplate: 'default',
  chatWindowColor: 'rgb(1, 103, 229)',
  userMessageColor: 'rgba(1, 103, 229, 0.12)',
  questionFontColor: 'rgb(51, 51, 51)',
  replyMessageColor: 'rgb(238, 238, 238)',
  replyFontColor: 'rgb(51, 51, 51)',
})

const colorTemplates = [
  { label: '默认', value: 'default', window: 'rgb(1, 103, 229)', userMsg: 'rgba(1, 103, 229, 0.12)', replyBg: 'rgb(238, 238, 238)' },
  { label: '生态绿', value: 'green', window: 'rgb(34, 139, 34)', userMsg: 'rgba(34, 139, 34, 0.12)', replyBg: 'rgb(238, 238, 238)' },
  { label: '蜜蜡黄', value: 'yellow', window: 'rgb(218, 165, 32)', userMsg: 'rgba(218, 165, 32, 0.12)', replyBg: 'rgb(238, 238, 238)' },
  { label: '琥珀橙', value: 'orange', window: 'rgb(255, 140, 0)', userMsg: 'rgba(255, 140, 0, 0.12)', replyBg: 'rgb(238, 238, 238)' },
  { label: '中国红', value: 'red', window: 'rgb(220, 20, 60)', userMsg: 'rgba(220, 20, 60, 0.12)', replyBg: 'rgb(238, 238, 238)' },
  { label: '炫酷黑', value: 'black', window: 'rgb(30, 30, 30)', userMsg: 'rgba(30, 30, 30, 0.12)', replyBg: 'rgb(60, 60, 60)' },
  { label: '自定义', value: 'custom', window: '', userMsg: '', replyBg: '' },
]

const saving = ref(false)

function applyColorTemplate(tplValue: string) {
  if (tplValue === 'custom') return
  const tpl = colorTemplates.find(t => t.value === tplValue)
  if (tpl) {
    uiConfig.value.chatWindowColor = tpl.window
    uiConfig.value.userMessageColor = tpl.userMsg
    uiConfig.value.replyMessageColor = tpl.replyBg
  }
}

async function loadUiConfig() {
  try {
    const res: any = await api.getAppDialogConfig(appId())
    if (res) {
      uiConfig.value.backgroundColor = res.backgroundColor || uiConfig.value.chatWindowColor
    }
  } catch (e) {
    // 静默处理，使用默认值
  }
}

async function handleSave() {
  saving.value = true
  try {
    // 保存对话样式配置
    await api.saveAppDialogConfig(appId(), {
      backgroundColor: uiConfig.value.chatWindowColor,
      showAvatar: 1,
      showFeedback: 1,
      showSuggestions: 1,
    })

    // 文本配置和颜色模板存储到 advancedOptions
    await api.saveAppAdvanced(appId(), {
      ui: {
        welcome: uiConfig.value.welcome,
        signature: uiConfig.value.signature,
        title: uiConfig.value.title,
        placeholder: uiConfig.value.placeholder,
        textInfo: uiConfig.value.textInfo,
        brandText: uiConfig.value.brandText,
        colorTemplate: uiConfig.value.colorTemplate,
        userMessageColor: uiConfig.value.userMessageColor,
        questionFontColor: uiConfig.value.questionFontColor,
        replyMessageColor: uiConfig.value.replyMessageColor,
        replyFontColor: uiConfig.value.replyFontColor,
      },
    })

    ElMessage.success('界面配置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadUiConfig()
})
</script>

<template>
  <div class="config-section">
    <h3>界面配置</h3>
    <div class="ui-layout">
      <div class="ui-form-area">
        <!-- 文本配置 -->
        <div class="config-group">
          <h4 class="group-title">文本配置</h4>
          <el-form label-width="100px" style="width: 100%">
            <el-form-item label="欢迎语：">
              <el-input v-model="uiConfig.welcome" placeholder="请输入欢迎语" />
            </el-form-item>
            <el-form-item label="签名：">
              <el-input v-model="uiConfig.signature" placeholder="请输入" />
            </el-form-item>
            <el-form-item label="标题：">
              <el-input v-model="uiConfig.title" placeholder="标题" />
            </el-form-item>
            <el-form-item label="占位文本：">
              <el-input v-model="uiConfig.placeholder" placeholder="请输入您的问题" />
            </el-form-item>
            <el-form-item label="文本信息：">
              <el-input v-model="uiConfig.textInfo" placeholder="帮助中心" />
            </el-form-item>
            <el-form-item label="技术品牌：">
              <el-input v-model="uiConfig.brandText" placeholder="Powered by AIS" />
            </el-form-item>
          </el-form>
        </div>

        <!-- 颜色配置 -->
        <div class="config-group">
          <h4 class="group-title">颜色配置</h4>
          <el-form label-width="100px" style="width: 100%">
            <el-form-item label="模板：">
              <div class="color-templates">
                <div
                  v-for="tpl in colorTemplates"
                  :key="tpl.value"
                  class="color-template-item"
                  :class="{ active: uiConfig.colorTemplate === tpl.value }"
                  @click="uiConfig.colorTemplate = tpl.value; applyColorTemplate(tpl.value)"
                >
                  <div
                    class="template-color"
                    :style="{ background: tpl.window || '#ccc' }"
                  ></div>
                  <span>{{ tpl.label }}</span>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="窗口颜色：">
              <el-input v-model="uiConfig.chatWindowColor" :disabled="uiConfig.colorTemplate !== 'custom'" />
            </el-form-item>
            <el-form-item label="用户消息：">
              <el-input v-model="uiConfig.userMessageColor" :disabled="uiConfig.colorTemplate !== 'custom'" />
            </el-form-item>
            <el-form-item label="提问字体：">
              <el-input v-model="uiConfig.questionFontColor" :disabled="uiConfig.colorTemplate !== 'custom'" />
            </el-form-item>
            <el-form-item label="回复消息：">
              <el-input v-model="uiConfig.replyMessageColor" :disabled="uiConfig.colorTemplate !== 'custom'" />
            </el-form-item>
            <el-form-item label="回复字体：">
              <el-input v-model="uiConfig.replyFontColor" :disabled="uiConfig.colorTemplate !== 'custom'" />
            </el-form-item>
          </el-form>
        </div>

        <div class="form-actions">
          <el-button type="primary" :loading="saving" @click="handleSave">保 存</el-button>
        </div>
      </div>

      <!-- 预览区域 -->
      <div class="ui-preview">
        <div class="preview-chat-widget">
          <div class="preview-widget-header" :style="{ background: uiConfig.chatWindowColor }">
            <span>{{ uiConfig.title || '标题' }}</span>
            <div class="header-actions">
              <el-icon><RefreshRight /></el-icon>
              <el-icon><Close /></el-icon>
            </div>
          </div>
          <div class="preview-widget-body">
            <div class="bot-message">
              <div class="bot-avatar">
                <el-icon :size="16" color="#fff"><ChatDotRound /></el-icon>
              </div>
              <div class="message-bubble bot">{{ uiConfig.welcome || '您好,有什么我可以帮助您' }}</div>
            </div>
            <div class="user-message">
              <div class="message-bubble user">你好</div>
            </div>
          </div>
          <div class="preview-widget-footer" :style="{ background: uiConfig.chatWindowColor }">
            <div class="input-placeholder">{{ uiConfig.placeholder }}</div>
            <div class="send-icon">▶</div>
          </div>
          <div class="widget-brand">{{ uiConfig.brandText || 'Powered by AIS' }}</div>
        </div>
        <div class="help-center-link">{{ uiConfig.textInfo }}</div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section {
  h3 { margin: 0 0 $spacing-lg; }
}

.ui-layout {
  display: flex;
  gap: $spacing-xl;
}

.ui-form-area {
  flex: 1;
}

.form-actions {
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
  border-top: 1px solid $border-lighter;
}

.config-group {
  margin-bottom: $spacing-xl;

  .group-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: $spacing-base;
    padding-bottom: $spacing-sm;
    border-bottom: 1px solid $border-lighter;
  }
}

.color-templates {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.color-template-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  cursor: pointer;
  padding: $spacing-sm;
  border-radius: $radius-sm;
  border: 2px solid transparent;

  &:hover {
    background: $bg-hover;
  }

  &.active {
    border-color: $color-primary;
    background: $bg-active;
  }

  .template-color {
    width: 40px;
    height: 40px;
    border-radius: $radius-sm;
    border: 1px solid $border-lighter;
  }

  span {
    font-size: 12px;
    color: $text-secondary;
  }
}

.ui-preview {
  width: 320px;
  flex-shrink: 0;
}

.preview-chat-widget {
  border: 1px solid $border-lighter;
  border-radius: $radius-lg;
  overflow: hidden;
  box-shadow: $shadow-base;
}

.preview-widget-header {
  padding: $spacing-base;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .header-actions {
    display: flex;
    gap: $spacing-sm;
    cursor: pointer;
  }
}

.preview-widget-body {
  min-height: 300px;
  padding: $spacing-base;
  background: #f5f5f5;
}

.bot-message {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-base;
}

.bot-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: $color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-bubble {
  padding: $spacing-sm $spacing-base;
  border-radius: $radius-base;
  max-width: 200px;
  font-size: 13px;

  &.bot {
    background: #fff;
    color: $text-primary;
  }

  &.user {
    background: $color-primary;
    color: #fff;
    margin-left: auto;
  }
}

.user-message {
  display: flex;
  justify-content: flex-end;
  margin-bottom: $spacing-base;
}

.preview-widget-footer {
  padding: $spacing-base;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .input-placeholder {
    font-size: 13px;
    opacity: 0.8;
  }

  .send-icon {
    cursor: pointer;
  }
}

.widget-brand {
  text-align: center;
  padding: $spacing-sm;
  font-size: 11px;
  color: $text-secondary;
  background: #fff;
}

.help-center-link {
  text-align: right;
  margin-top: $spacing-sm;
  font-size: 12px;
  color: $color-primary;
  cursor: pointer;
}
</style>
