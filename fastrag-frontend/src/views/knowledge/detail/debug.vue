<script setup lang="ts">
import type { RetrievalConfig } from '@/types/knowledge'
import type { SearchResultItem } from '@/types/evaluation'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Search, ChatDotRound, FolderOpened } from '@element-plus/icons-vue'
import { useConversation } from '@/composables/useConversation'
import { searchRetrieval } from '@/api'
import { getSuggestion } from '@/mock/search-correction'
import { applyQueryRules } from '@/mock/query-rules'

const route = useRoute()
const router = useRouter()
const kbId = route.params.id as string
const activeTab = ref('retrieval')

// 检索调试参数
const retrievalConfig = ref<RetrievalConfig>({
  mode: 'vector',
  topK: 10,
  similarityThreshold: 0.0,
  bm25RecallCount: 50,
  vectorWeight: 0.7,
  bm25Weight: 0.3,
  bm25SparseDropRate: 0.0,
})

const retrievalQuery = ref('')
const retrievalResults = ref<SearchResultItem[]>([])
const retrievalLoading = ref(false)
const correctionHint = ref('')

async function handleRetrievalSearch() {
  if (!retrievalQuery.value.trim()) return
  retrievalLoading.value = true
  retrievalResults.value = []
  correctionHint.value = ''

  try {
    // 纠错
    let effectiveQuery = retrievalQuery.value
    const { suggestedQuery, reason } = getSuggestion(retrievalQuery.value)
    if (suggestedQuery) {
      correctionHint.value = reason
      effectiveQuery = suggestedQuery
    }

    // 查询重写
    const { rewritten, appliedRules } = applyQueryRules(effectiveQuery)
    if (appliedRules.length > 0) {
      effectiveQuery = rewritten
    }

    // 真实检索
    retrievalResults.value = await searchRetrieval({
      knowledgeId: kbId,
      query: effectiveQuery,
      config: retrievalConfig.value,
    })
  } catch {
    retrievalResults.value = []
  } finally {
    retrievalLoading.value = false
  }
}

// ====== 多轮问答调试 ======
const qaConfig = ref<RetrievalConfig>({
  mode: 'hybrid',
  topK: 5,
  similarityThreshold: 0.0,
  bm25RecallCount: 50,
  vectorWeight: 0.7,
  bm25Weight: 0.3,
  bm25SparseDropRate: 0.0,
})

const selectedModel = ref('qwen3-32b')
const promptContent = ref('根据以下上下文回答用户问题。\n\n上下文：\n${context}\n\n用户问题：${question}\n\n请用简洁准确的语言回答：')

const { messages, loading: qaLoading, turnCount, sendMessage, clear: clearChat } = useConversation(kbId)

const qaInput = ref('')
const chatContainer = ref<HTMLElement | null>(null)

async function handleQASend() {
  if (!qaInput.value.trim()) return
  const question = qaInput.value
  qaInput.value = ''
  await sendMessage(question, qaConfig.value, selectedModel.value)
  // 自动滚动到底部
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

function handleClearChat() {
  clearChat()
}

function handlePublish() {
  // 发布配置
}
</script>

<template>
  <div class="page-container">
    <div class="debug-header">
      <el-button @click="router.push(`/knowledge/${kbId}`)">
        <el-icon><ArrowLeft /></el-icon>返回主页
      </el-button>
      <h3>知识库调试</h3>
    </div>

    <el-tabs v-model="activeTab" class="debug-tabs">
      <!-- 检索调试 -->
      <el-tab-pane label="检索调试" name="retrieval">
        <div class="debug-layout">
          <div class="params-panel">
            <h4>参数配置</h4>
            <el-form label-width="100px" size="small">
              <el-form-item label="召回数量">
                <el-input-number v-model="retrievalConfig.topK" :min="1" :max="20" />
              </el-form-item>
              <el-form-item label="语义权重">
                <el-slider v-model="retrievalConfig.vectorWeight" :min="0" :max="1" :step="0.1" show-input />
              </el-form-item>
              <el-form-item label="检索模式">
                <el-select v-model="retrievalConfig.mode" style="width: 100%">
                  <el-option label="向量检索" value="vector" />
                  <el-option label="全文检索" value="fulltext" />
                  <el-option label="混合检索" value="hybrid" />
                </el-select>
              </el-form-item>
            </el-form>
          </div>
          <div class="result-panel">
            <div class="search-bar">
              <el-input v-model="retrievalQuery" placeholder="输入检索内容..." @keyup.enter="handleRetrievalSearch">
                <template #append>
                  <el-button :loading="retrievalLoading" @click="handleRetrievalSearch">
                    <el-icon><Search /></el-icon>
                  </el-button>
                </template>
              </el-input>
            </div>
            <div v-if="retrievalResults.length" class="results">
              <div class="result-header">
                <span>命中结果：{{ retrievalResults.length }} 条</span>
                <span v-if="correctionHint" class="result-correction">{{ correctionHint }}</span>
              </div>
              <div v-for="item in retrievalResults" :key="item.index" class="result-item">
                <div class="result-title">
                  <span>#{{ item.index }}</span>
                  <el-tag size="small" type="info">相似度 {{ item.similarity.toFixed(1) }}%</el-tag>
                  <el-link type="primary" :underline="false" @click="router.push(`/knowledge/${kbId}/chunks/${item.fileId}`)">
                    <el-icon><FolderOpened /></el-icon> {{ item.source }}
                  </el-link>
                </div>
                <div class="result-content" v-html="item.previewSnippet || item.content" />
              </div>
            </div>
            <el-empty v-else-if="!retrievalLoading" description="请输入检索内容进行测试" />
          </div>
        </div>
      </el-tab-pane>

      <!-- 问答调试 -->
      <el-tab-pane label="问答调试" name="qa">
        <div class="debug-layout">
          <div class="params-panel">
            <h4>对话配置</h4>
            <el-form label-width="100px" size="small">
              <el-form-item label="选择模型">
                <el-select v-model="selectedModel" style="width: 100%">
                  <el-option label="qwen3-32b" value="qwen3-32b" />
                  <el-option label="DeepSeek-V3" value="deepseek-v3" />
                  <el-option label="Kimi-K2" value="kimi-k2" />
                </el-select>
              </el-form-item>
              <el-form-item label="Top K">
                <el-input-number v-model="qaConfig.topK" :min="1" :max="20" />
              </el-form-item>
              <el-form-item label="检索模式">
                <el-select v-model="qaConfig.mode" style="width: 100%">
                  <el-option label="混合检索" value="hybrid" />
                  <el-option label="向量检索" value="vector" />
                  <el-option label="全文检索" value="fulltext" />
                </el-select>
              </el-form-item>
            </el-form>
            <h4>Prompt 模板</h4>
            <el-input v-model="promptContent" type="textarea" :rows="6" />
            <div class="prompt-vars">
              <el-tag size="small" @click="promptContent += '${context}'">${context}</el-tag>
              <el-tag size="small" @click="promptContent += '${question}'">${question}</el-tag>
            </div>
            <div style="margin-top: 12px; display: flex; gap: 8px">
              <el-button type="primary" size="small" @click="handlePublish">发布配置</el-button>
              <el-button size="small" @click="handleClearChat">清空对话</el-button>
            </div>
          </div>
          <div class="chat-panel">
            <!-- 对话统计 -->
            <div v-if="turnCount > 0" class="chat-stats">
              <span>第 {{ turnCount }} 轮对话</span>
              <span>上下文窗口：最近 3 轮</span>
            </div>
            <!-- 消息列表 -->
            <div ref="chatContainer" class="chat-messages">
              <div v-if="!messages.length" class="welcome">
                <el-icon :size="48" class="welcome-icon"><ChatDotRound /></el-icon>
                <p>多轮问答调试</p>
                <p class="sub">支持上下文连续对话、指代消解、回答溯源</p>
              </div>
              <div v-for="(msg, idx) in messages" :key="idx" :class="['message', msg.role]">
                <div class="message-content">
                  <div class="message-text">{{ msg.content }}</div>
                  <!-- 回答溯源 -->
                  <div v-if="msg.role === 'assistant' && msg.sources && msg.sources.length > 0" class="message-sources">
                    <div class="sources-title">引用来源：</div>
                    <div
                      v-for="(src, sIdx) in msg.sources.slice(0, 3)"
                      :key="sIdx"
                      class="source-item"
                      @click="router.push(`/knowledge/${kbId}/chunks/${src.fileId}`)"
                    >
                      <el-icon><FolderOpened /></el-icon>
                      <span class="source-name">{{ src.source }}</span>
                      <span class="source-score">相似度 {{ src.similarity.toFixed(1) }}%</span>
                    </div>
                  </div>
                  <div v-if="msg.model" class="message-meta">
                    <span>模型：{{ msg.model }}</span>
                    <span>耗时：{{ msg.time }}</span>
                    <span>{{ msg.timestamp }}</span>
                  </div>
                </div>
              </div>
              <!-- 加载态 -->
              <div v-if="qaLoading" class="message assistant">
                <div class="message-content">
                  <div class="message-text typing">正在检索知识库并生成回答...</div>
                </div>
              </div>
            </div>
            <!-- 输入区 -->
            <div class="chat-input">
              <el-input
                v-model="qaInput"
                placeholder='输入问题...（支持上下文连续提问，如"它的价格是多少"）'
                :disabled="qaLoading"
                @keyup.enter="handleQASend"
              >
                <template #append>
                  <el-button :loading="qaLoading" @click="handleQASend">发送</el-button>
                </template>
              </el-input>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.debug-header {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  margin-bottom: $spacing-base;
  h3 { margin: 0; }
}

.debug-layout {
  display: flex;
  gap: $spacing-base;
  height: calc(100vh - 240px);
}

.params-panel {
  width: 300px;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-base;
  overflow-y: auto;
  flex-shrink: 0;

  h4 { margin: 0 0 $spacing-base; font-size: 14px; }
}

.prompt-vars {
  margin-top: $spacing-sm;
  display: flex;
  gap: $spacing-xs;
  .el-tag { cursor: pointer; }
}

.result-panel, .chat-panel {
  flex: 1;
  background: $bg-white;
  border-radius: $radius-base;
  display: flex;
  flex-direction: column;
}

.search-bar {
  padding: $spacing-base;
  border-bottom: 1px solid $border-lighter;
}

.results {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-base;
}

.result-header {
  font-size: 12px;
  color: $text-secondary;
  margin-bottom: $spacing-base;
}

.result-item {
  padding: $spacing-base;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  margin-bottom: $spacing-sm;

  .result-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
    margin-bottom: $spacing-sm;
  }

  .result-content {
    font-size: 13px;
    color: $text-regular;
    margin-bottom: $spacing-sm;
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-base;
}

.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: $text-secondary;

  .sub { font-size: 12px; }
}

.message {
  margin-bottom: $spacing-base;

  &.user .message-content {
    background: $bg-active;
    margin-left: 60px;
  }

  &.assistant .message-content {
    background: $bg-hover;
    margin-right: 60px;
  }
}

.message-content {
  padding: $spacing-base;
  border-radius: $radius-base;

  .message-text {
    line-height: 1.6;
    white-space: pre-wrap;
  }

  .message-meta {
    margin-top: $spacing-sm;
    font-size: 12px;
    color: $text-secondary;
    span { margin-right: $spacing-base; }
  }
}

// 回答溯源
.message-sources {
  margin-top: $spacing-sm;
  padding-top: $spacing-sm;
  border-top: 1px dashed $border-lighter;
}

.sources-title {
  font-size: 12px;
  color: $text-secondary;
  margin-bottom: $spacing-xs;
}

.source-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: 12px;
  color: $color-primary;
  cursor: pointer;
  padding: 2px 0;

  &:hover { text-decoration: underline; }

  .source-name { flex: 1; }
  .source-score { color: $text-secondary; }
}

// 对话统计
.chat-stats {
  display: flex;
  gap: $spacing-base;
  padding: $spacing-xs $spacing-base;
  font-size: 12px;
  color: $text-secondary;
  background: $bg-hover;
  border-bottom: 1px solid $border-lighter;
}

// 打字动画
.typing {
  color: $text-secondary;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.welcome-icon {
  color: $color-primary;
}

.chat-input {
  padding: $spacing-base;
  border-top: 1px solid $border-lighter;
}
</style>
