<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// 对话调试
// ===========================================================================

const messages = ref<Array<{ role: 'user' | 'assistant'; content: string; time?: string }>>([
  { role: 'assistant', content: '您好,有什么我可以帮助您' },
])

const inputText = ref('')
const loading = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)

async function handleSend() {
  if (!inputText.value.trim() || loading.value) return

  const question = inputText.value.trim()
  inputText.value = ''

  // 添加用户消息
  messages.value.push({ role: 'user', content: question })
  loading.value = true

  const startTime = Date.now()
  try {
    const res: any = await api.runApp(appId(), question)
    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1)

    const answer = typeof res === 'string' ? res : (res?.answer || res?.content || res?.text || '抱歉，暂时无法回答')

    messages.value.push({
      role: 'assistant',
      content: answer,
      time: `${elapsed}s`,
    })
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，AI服务暂时不可用，请稍后再试',
      time: '-',
    })
  } finally {
    loading.value = false
    // 滚动到底部
    nextTick(() => {
      if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      }
    })
  }
}

function handleClear() {
  messages.value = [{ role: 'assistant', content: '您好,有什么我可以帮助您' }]
  inputText.value = ''
}

function handleRetry() {
  // 找到最后一条用户消息并重新发送
  const lastUserMsgIndex = [...messages.value].reverse().findIndex(m => m.role === 'user')
  if (lastUserMsgIndex < 0) return

  const actualIndex = messages.value.length - 1 - lastUserMsgIndex
  const question = messages.value[actualIndex].content

  // 移除最后两条（用户 + AI 回复）
  messages.value = messages.value.slice(0, actualIndex)

  inputText.value = question
  handleSend()
}
</script>

<template>
  <div class="debug-chat">
    <div class="debug-layout">
      <!-- 左侧提示 -->
      <div class="debug-sidebar">
        <div class="debug-tip">
          <p>当前参数调整后问答对话调试验证效果，满意后点击发布配置生效；若不发布不影响原有应用参数配置，离开当前页面后所做临时调整将丢失。</p>
          <el-button type="primary" size="small" @click="ElMessage.info('请点击底部「发布配置」按钮使配置生效')">发布配置</el-button>
        </div>
      </div>

      <!-- 右侧聊天窗口 -->
      <div class="debug-chat-widget">
        <div class="widget-header">
          <span>{{ appInfo.name || '应用调试' }}</span>
          <div class="header-actions">
            <el-icon class="refresh-btn" @click="handleClear"><RefreshRight /></el-icon>
          </div>
        </div>

        <div ref="messagesContainer" class="widget-messages">
          <div v-for="(msg, idx) in messages" :key="idx" :class="['message', msg.role]">
            <div v-if="msg.role === 'assistant'" class="bot-avatar">
              <el-icon :size="16" color="#fff"><ChatDotRound /></el-icon>
            </div>
            <div class="message-content">
              {{ msg.content }}
              <span v-if="msg.time" class="msg-time">{{ msg.time }}</span>
            </div>
          </div>
        </div>

        <div class="widget-input-area">
          <el-input
            v-model="inputText"
            placeholder="请输入您的问题"
            :disabled="loading"
            @keyup.enter="handleSend"
          >
            <template #append>
              <el-button class="send-btn" :loading="loading" @click="handleSend">
                <el-icon v-if="!loading"><Promotion /></el-icon>
              </el-button>
            </template>
          </el-input>
          <div class="widget-brand">Powered by AIS</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
@use 'sass:color';

.debug-chat {
  height: calc(100vh - 180px);
}

.debug-layout {
  display: flex;
  gap: $spacing-xl;
  height: 100%;
}

.debug-sidebar {
  width: 300px;
  flex-shrink: 0;

  .debug-tip {
    background: #fff7e6;
    border: 1px solid #ffd591;
    border-radius: $radius-base;
    padding: $spacing-base;

    p {
      font-size: 13px;
      color: $text-primary;
      margin: 0 0 $spacing-base;
      line-height: 1.6;
    }
  }
}

.debug-chat-widget {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid $border-lighter;
  border-radius: $radius-lg;
  overflow: hidden;
  background: $bg-white;
}

.widget-header {
  background: $color-primary;
  color: #fff;
  padding: $spacing-base $spacing-lg;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 15px;
  font-weight: 500;

  .header-actions {
    display: flex;
    gap: $spacing-sm;
    align-items: center;

    .refresh-btn {
      cursor: pointer;
      font-size: 18px;

      &:hover {
        opacity: 0.8;
      }
    }
  }
}

.widget-messages {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-lg;
  background: #f5f5f5;
}

.message {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-base;

  &.user {
    justify-content: flex-end;

    .message-content {
      background: #fff;
      color: $text-primary;
      border-radius: $radius-base $radius-base 4px $radius-base;
      max-width: 400px;
    }
  }

  &.assistant {
    .message-content {
      background: $color-primary;
      color: #fff;
      border-radius: $radius-base $radius-base $radius-base 4px;
      max-width: 400px;
    }
  }
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

.message-content {
  padding: $spacing-base;
  font-size: 14px;
  line-height: 1.6;
  max-width: 400px;
  position: relative;
}

.msg-time {
  display: block;
  font-size: 11px;
  opacity: 0.7;
  margin-top: 4px;
}

.widget-input-area {
  padding: $spacing-base;
  border-top: 1px solid $border-lighter;
  background: $color-primary;

  .el-input {
    border-radius: $radius-base;
  }

  .send-btn {
    background: $color-primary;
    color: #fff;
    border: none;

    &:hover {
      background: color.adjust($color-primary, $lightness: -10%);
    }
  }

  .widget-brand {
    text-align: center;
    font-size: 11px;
    color: rgba(255, 255, 255, 0.7);
    margin-top: $spacing-sm;
  }
}
</style>
