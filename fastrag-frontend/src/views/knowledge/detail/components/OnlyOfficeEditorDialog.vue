<script setup lang="ts">
/**
 * OnlyOffice Document Server 在线编辑弹窗。
 *
 * <p>提供四项联动：
 * <ul>
 *   <li>在线渲染 / 编辑 Office 文件（docx/xlsx/pptx）</li>
 *   <li>chunk → 跳转到原件：监听 {@code focusChunkId} 变化，调
 *       {@code connector.scrollToPage} 滚动到对应页（页码从
 *       {@code chunk.pageNumber} 取得）</li>
 *   <li>原件选区 → 创建 chunk：开启 {@code selectionMode} 后监听 OO 的
 *       {@code onSelectionChanged}，弹窗确认后调 {@code api.createChunk}</li>
 *   <li>编辑保存 → 自动重分片：OO callback (status=2/6) 在
 *       {@code OnlyOfficeController.handleCallback} 内部已经触发 {@code reChunkFile}，
 *       弹窗仅监听 {@code onDocumentStateChange} 给用户提示</li>
 * </ul>
 *
 * <p>chunk 边界画框：依赖 OnlyOffice 插件 {@code /onlyoffice-plugin/chunk-boundary}，
 * 通过 {@code window.message} 接收插件发来的事件。
 */
import { ref, watch, computed, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Close, FullScreen, Refresh, Check, DocumentCopy, MagicStick } from '@element-plus/icons-vue'
import type { KnowledgeFile } from '@/types/knowledge'
import { useOnlyOfficeEditor, loadOnlyOfficeApi } from '@/composables/useOnlyOffice'
import * as api from '@/api'

interface ChunkCardLite {
  id: string
  index: number
  pageNumber?: number
  title?: string
}

const props = defineProps<{
  modelValue: boolean
  kbId?: string
  file: KnowledgeFile | null
  /** 由 AiChunkPanel chunk-click 传入；变化时编辑器自动滚动到对应页 */
  focusChunkId?: string | null
  /** 由 AiChunkPanel 传入的当前文件全部 chunk（用于跳页时反查 pageNumber） */
  chunks?: ChunkCardLite[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'chunk-created', chunk: unknown): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const PLACEHOLDER_ID = 'onlyoffice-editor-placeholder'

const {
  instance,
  isReady,
  error: ooError,
  init,
  destroy,
  scrollToPage,
  getSelectedText,
  highlightText,
} = useOnlyOfficeEditor()

// ---- 状态 ----
const loading = ref(false)
const loadProgress = ref(0)
const docState = ref<'clean' | 'modified' | 'saving'>('clean')
const selectionMode = ref(false)
const boundaryPanelOpen = ref(false)
const chunksForBoundary = ref<ChunkCardLite[]>([])
const statusText = computed(() => {
  if (loading.value) return `正在加载编辑器… ${loadProgress.value}%`
  if (ooError.value) return `错误：${ooError.value}`
  if (!isReady.value) return '初始化中…'
  if (docState.value === 'modified') return '● 已修改（等待保存）'
  if (docState.value === 'saving') return '正在保存到 FastRAG…'
  return '✓ 已同步'
})

// ---- 加载 / 卸载 ----
watch(visible, async (v) => {
  if (v && props.file) {
    await loadEditor()
  } else if (!v) {
    destroy()
    selectionMode.value = false
    boundaryPanelOpen.value = false
    docState.value = 'clean'
  }
})

watch(() => props.file?.id, async (newId, oldId) => {
  if (!visible.value || !newId) return
  if (newId === oldId) return
  await loadEditor()
})

async function loadEditor() {
  if (!props.kbId || !props.file) return
  loading.value = true
  loadProgress.value = 10
  try {
    const config = await api.getOnlyOfficeConfig(props.kbId, props.file.id)
    loadProgress.value = 50
    // 注入选区模式 / 保存状态事件回调
    config.editorConfig.callbackEvents = config.editorConfig.callbackEvents || {}
    const evts: any = config.editorConfig
    evts.events = evts.events || {}
    evts.events.onAppReady = () => {
      loading.value = false
      loadProgress.value = 100
    }
    evts.events.onDocumentReady = () => {
      loading.value = false
    }
    evts.events.onDocumentStateChange = (e: any) => {
      docState.value = e?.data ? 'modified' : 'clean'
    }
    evts.events.onSelectionChanged = () => {
      if (selectionMode.value) {
        handleSelectionCaptured()
      }
    }
    evts.events.onError = (e: any) => {
      console.error('[OnlyOffice] error:', e)
      ElNotification.error(`OnlyOffice 错误: ${e?.errorDescription || '未知错误'}`)
    }

    await nextTick()
    await init(PLACEHOLDER_ID, config)
  } catch (e: any) {
    console.error('[OnlyOffice] loadEditor failed:', e)
    ElNotification.error(`OnlyOffice 加载失败: ${e?.message || e}`)
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => destroy())

// ---- chunk → 跳转 ----
watch(() => props.focusChunkId, (id) => {
  if (!id || !props.chunks) return
  const card = props.chunks.find((c) => c.id === id)
  if (!card) return
  const page = card.pageNumber ?? 1
  scrollToPage(page)
  ElMessage.success(`已跳转到分片 #${card.index + 1}${card.title ? ' · ' + card.title : ''}（第 ${page} 页）`)
})

// ---- 选区 → 创建 chunk ----
async function handleSelectionCaptured() {
  const text = await getSelectedText()
  const trimmed = (text || '').trim()
  if (!trimmed || trimmed.length < 2) {
    return
  }
  // 防止弹窗嵌套过深，最多展示前 60 字
  const preview = trimmed.length > 60 ? trimmed.slice(0, 60) + '…' : trimmed
  try {
    await ElMessageBox.confirm(
      `将选中内容创建为新分片？\n\n预览：\n${preview}`,
      '创建分片',
      { confirmButtonText: '创建', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return  // 取消
  }
  try {
    if (!props.kbId || !props.file) return
    const created = await api.createChunk(props.kbId, {
      fileId: props.file.id,
      content: trimmed,
      chunkType: 'text',
    })
    ElNotification.success('已创建分片')
    emit('chunk-created', created)
  } catch (e: any) {
    ElNotification.error(`创建失败: ${e?.message || e}`)
  }
}

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  ElMessage.info(selectionMode.value ? '请在文档中框选文字，松手后会弹确认窗' : '已退选区创建分片模式')
}

// ---- chunk 边界侧边栏 ----
function openChunkBoundary() {
  if (!props.chunks || props.chunks.length === 0) {
    ElMessage.warning('当前文件尚未生成任何分片')
    return
  }
  chunksForBoundary.value = [...props.chunks]
  boundaryPanelOpen.value = true
}

function focusChunkFromBoundary(card: ChunkCardLite) {
  const page = card.pageNumber ?? 1
  scrollToPage(page)
  if (card.title) {
    highlightText(card.title)
  }
}

// 监听来自 OnlyOffice 插件的 postMessage（chunk-boundary 插件）
function onPluginMessage(e: MessageEvent) {
  if (e?.data?.type === 'oo-chunk-click') {
    const card = chunksForBoundary.value.find((c) => c.id === e.data.chunkId)
    if (card) focusChunkFromBoundary(card)
  }
}
window.addEventListener('message', onPluginMessage)
onBeforeUnmount(() => window.removeEventListener('message', onPluginMessage))

// ---- 顶部按钮 ----
function handleRefresh() {
  loadEditor()
}
function close() {
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    fullscreen
    :show-close="false"
    :modal="true"
    class="onlyoffice-dialog"
    @keydown.esc="close"
  >
    <template #header>
      <div class="oo-header">
        <span class="oo-title">
          <el-icon><DocumentCopy /></el-icon>
          <span>{{ file?.name || 'OnlyOffice 编辑器' }}</span>
          <el-tag v-if="file?.extension" size="small" effect="plain" class="oo-tag">.{{ file.extension }}</el-tag>
        </span>
        <div class="oo-actions">
          <el-button
            :type="selectionMode ? 'primary' : 'default'"
            size="default"
            @click="toggleSelectionMode"
          >
            <el-icon><MagicStick /></el-icon>
            <span>{{ selectionMode ? '选区模式已开启' : '选区创建 chunk' }}</span>
          </el-button>
          <el-button size="default" @click="openChunkBoundary">
            <el-icon><DocumentCopy /></el-icon>
            <span>Chunk 边界</span>
          </el-button>
          <el-button size="default" @click="handleRefresh" :disabled="loading">
            <el-icon><Refresh /></el-icon>
            <span>重新加载</span>
          </el-button>
          <el-divider direction="vertical" />
          <span class="oo-status">{{ statusText }}</span>
          <el-button size="default" @click="close">
            <el-icon><Close /></el-icon>
            <span>关闭</span>
          </el-button>
        </div>
      </div>
    </template>

    <div class="oo-body">
      <!-- OO iframe 占位 -->
      <div :id="PLACEHOLDER_ID" class="oo-iframe" />

      <!-- 加载占位 -->
      <div v-if="loading" class="oo-loading">
        <el-progress :percentage="loadProgress" :stroke-width="14" />
        <p class="oo-loading-text">{{ statusText }}</p>
      </div>

      <!-- chunk 边界侧边栏 -->
      <transition name="el-zoom-in-right">
        <aside v-if="boundaryPanelOpen" class="oo-boundary">
          <header>
            <span>Chunk 边界（{{ chunksForBoundary.length }}）</span>
            <el-button link size="small" @click="boundaryPanelOpen = false">×</el-button>
          </header>
          <ul>
            <li
              v-for="card in chunksForBoundary"
              :key="card.id"
              class="boundary-item"
              @click="focusChunkFromBoundary(card)"
            >
              <div class="boundary-title">
                #{{ card.index + 1 }}
                <span v-if="card.pageNumber">· 第 {{ card.pageNumber }} 页</span>
              </div>
              <div class="boundary-text">{{ card.title || '(无标题)' }}</div>
            </li>
          </ul>
        </aside>
      </transition>
    </div>
  </el-dialog>
</template>

<style scoped lang="scss">
.onlyoffice-dialog {
  :deep(.el-dialog__header) {
    padding: 0;
    margin: 0;
  }
  :deep(.el-dialog__body) {
    padding: 0;
    height: calc(100vh - 60px);
    background: #f5f7fa;
  }
}

.oo-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 0 16px;
  height: 60px;
  background: linear-gradient(135deg, #f0f4ff, #fafbff);
  border-bottom: 1px solid #e4e7ed;
}

.oo-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;

  .oo-tag {
    margin-left: 4px;
  }
}

.oo-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.oo-status {
  font-size: 13px;
  color: #67c23a;
  white-space: nowrap;
}

.oo-body {
  position: relative;
  width: 100%;
  height: 100%;
}

.oo-iframe {
  width: 100%;
  height: 100%;
}

.oo-loading {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  background: rgba(255, 255, 255, 0.92);
  pointer-events: none;

  .oo-loading-text {
    font-size: 14px;
    color: #606266;
  }

  :deep(.el-progress) {
    width: 360px;
  }
}

.oo-boundary {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 320px;
  max-height: calc(100% - 24px);
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;

  header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 14px;
    background: #fafbff;
    border-bottom: 1px solid #ebeef5;
    font-size: 13px;
    font-weight: 600;
    color: #303133;
  }

  ul {
    margin: 0;
    padding: 8px;
    list-style: none;
    overflow-y: auto;
    flex: 1;
  }

  .boundary-item {
    padding: 8px 10px;
    margin-bottom: 6px;
    border: 1px solid #ebeef5;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      background: #ecf5ff;
      border-color: #b3d8ff;
    }
  }

  .boundary-title {
    font-size: 12px;
    font-weight: 600;
    color: #409eff;
    margin-bottom: 4px;
  }

  .boundary-text {
    font-size: 12px;
    color: #606266;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
