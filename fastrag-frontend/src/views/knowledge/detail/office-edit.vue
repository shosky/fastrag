<script setup lang="ts">
/**
 * OnlyOffice 在线编辑页（AppLayout 内路由页，非全屏）。
 *
 * <p>与原 fullscreen 弹窗实现（OnlyOfficeEditorDialog，已移除）的区别：
 * <ul>
 *   <li>挂在 AppLayout 之下，保留全局 Header / 侧栏，编辑器撑满内容区剩余高度</li>
 *   <li><b>纯编辑</b>：不含分片相关功能（选区建 chunk / chunk 边界侧栏 / chunk 跳页），
 *       分片操作请走「分片管理」或「AI分片」入口</li>
 *   <li><b>手动保存</b>：关闭 OO 自动保存（customization.autosave=false），编辑不自动落盘；
 *       页面「保存」按钮 → 后端 forcesave（OO CommandService）→ OO callback (status=6)
 *       → 落盘 + 触发重新分片，前端轮询 updatedAt 确认后提示。编辑器工具栏原生保存
 *       按钮（customization.forcesave=true）/ Ctrl+S 走同一条 forcesave 链路</li>
 * </ul>
 */
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElNotification, ElMessage } from 'element-plus'
import { ArrowLeft, Refresh, Document, Check } from '@element-plus/icons-vue'
import { useOnlyOfficeEditor } from '@/composables/useOnlyOffice'
import { getFileCategory, FILE_CATEGORY_ICONS } from '@/types/knowledge'
import type { OnlyOfficeConfig } from '@/types/onlyoffice'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()
const kbId = route.params.id as string
const fileId = route.params.fileId as string

const PLACEHOLDER_ID = 'office-edit-placeholder'

const fileInfo = ref({
  id: fileId,
  name: `文件_${fileId.slice(-6)}`,
  category: 'document' as ReturnType<typeof getFileCategory>,
  extension: '',
})

const { isReady, error: ooError, init, destroy } = useOnlyOfficeEditor()

const loading = ref(false)
const loadProgress = ref(0)
const saving = ref(false)
/** false→true：有未保存修改；true→false：一次保存完成（原生工具栏保存 / Ctrl+S 路径） */
const modified = ref(false)

/** 上次「已保存」提示时间（去重：页面按钮与编辑器原生保存两条路径可能先后触发） */
let lastSavedNotifyAt = 0

function notifySaved() {
  const now = Date.now()
  if (now - lastSavedNotifyAt < 8_000) return
  lastSavedNotifyAt = now
  ElNotification.success({
    title: '文档已保存',
    message: '已自动触发重新切片，稍后可在分片管理中查看最新结果',
    duration: 5000,
  })
}

const statusText = computed(() => {
  if (loading.value) return `正在加载编辑器… ${loadProgress.value}%`
  if (ooError.value) return `错误：${ooError.value}`
  if (!isReady.value) return '初始化中…'
  if (modified.value) return '● 有修改（未自动保存，点击「保存」落盘）'
  return '✓ 已同步'
})

onMounted(loadFileInfo)

async function loadFileInfo() {
  try {
    const res: any = await api.getFiles(kbId)
    const files = res?.list || res || []
    const file = files.find((f: any) => f.id === fileId)
    if (file) {
      fileInfo.value.name = file.name || fileInfo.value.name
      fileInfo.value.extension = file.extension || ''
      fileInfo.value.category = file.category || getFileCategory(file.name || '')
    }
  } catch {
    // ignore：文件信息加载失败不影响编辑器（OO config 自带 title）
  }
}

onMounted(loadEditor)
onBeforeUnmount(() => destroy())

async function loadEditor() {
  loading.value = true
  loadProgress.value = 10
  try {
    const config: OnlyOfficeConfig = await api.getOnlyOfficeConfig(kbId, fileId)
    loadProgress.value = 50
    // 关闭自动保存：只有手动「保存」才落盘 + 触发重新分片。
    // forcesave=true 保留编辑器工具栏原生保存按钮（Ctrl+S），与页面「保存」按钮等效
    config.editorConfig.customization = {
      ...(config.editorConfig.customization || {}),
      autosave: false,
      forcesave: true,
      goback: false,
      help: false,
      feedback: false,
      about: false,
    }
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
      const nowModified = !!e?.data
      // modified → clean：编辑器原生保存（工具栏按钮 / Ctrl+S）完成，
      // 后端 handleCallback 已在 status=6 时触发重分片
      if (modified.value && !nowModified) {
        notifySaved()
      }
      modified.value = nowModified
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

function handleReload() {
  destroy()
  loadEditor()
}

async function currentFileUpdatedAt(): Promise<string | null> {
  try {
    const res: any = await api.getFiles(kbId)
    const files = res?.list || res || []
    const file = files.find((f: any) => f.id === fileId)
    return file?.updatedAt || null
  } catch {
    return null
  }
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/**
 * 页面「保存」按钮：后端 forcesave → OO callback(status=6) 落盘 + 触发重分片。
 * 后端受理（result=initiated）后轮询文件 updatedAt 变化，确认落盘后提示。
 */
async function handleSave() {
  if (saving.value) return
  saving.value = true
  try {
    const baseline = await currentFileUpdatedAt()
    const res: any = await api.forceOnlyOfficeSave(kbId, fileId)
    const result = res?.result as string
    if (result === 'no-changes') {
      ElMessage.info('没有需要保存的修改')
      return
    }
    if (result === 'no-session') {
      ElMessage.warning('编辑会话不存在或已过期，请点击「重新加载」后再编辑保存')
      return
    }
    if (result === 'no-file' || result === 'disabled') {
      ElMessage.error('文件不存在或 OnlyOffice 未启用')
      return
    }
    if (result !== 'initiated') {
      ElMessage.error(`保存失败${res?.ooError != null ? `（OO 错误码 ${res.ooError}）` : ''}`)
      return
    }

    // 已受理：OO 回调后端落盘需要数秒，轮询 updatedAt 变化确认完成
    const deadline = Date.now() + 20_000
    while (Date.now() < deadline) {
      await sleep(2_000)
      const now = await currentFileUpdatedAt()
      if (baseline && now && now !== baseline) {
        notifySaved()
        return
      }
    }
    ElMessage.warning('保存已提交但未在预期时间内确认完成，请稍后刷新文件列表查看')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push(`/knowledge/${kbId}`)
}
</script>

<template>
  <div class="office-edit-page">
    <!-- Breadcrumb -->
    <div class="office-edit-page__breadcrumb">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/knowledge' }">知识库</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: `/knowledge/${kbId}` }">{{ fileInfo.name }}</el-breadcrumb-item>
        <el-breadcrumb-item>在线编辑</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- Page header -->
    <div class="office-edit-page__header">
      <div class="office-edit-page__header-left">
        <el-button :icon="ArrowLeft" link @click="goBack">返回</el-button>
        <el-divider direction="vertical" />
        <el-icon :size="20" class="office-edit-page__icon">
          {{ FILE_CATEGORY_ICONS[fileInfo.category] || '📄' }}
        </el-icon>
        <div class="office-edit-page__title-group">
          <h2 class="office-edit-page__name">{{ fileInfo.name }}</h2>
          <span class="office-edit-page__meta">OnlyOffice 在线编辑</span>
        </div>
      </div>
      <div class="office-edit-page__header-right">
        <span class="office-edit-page__status" :class="{ 'is-modified': modified }">{{ statusText }}</span>
        <el-button type="primary" :icon="Check" :loading="saving" @click="handleSave">保存</el-button>
        <el-button :icon="Refresh" :disabled="loading" @click="handleReload">重新加载</el-button>
      </div>
    </div>

    <!-- Editor -->
    <div class="office-edit-page__content">
      <div :id="PLACEHOLDER_ID" class="office-edit-page__iframe" />
      <div v-if="loading" class="office-edit-page__loading">
        <el-progress :percentage="loadProgress" :stroke-width="14" />
        <p class="office-edit-page__loading-text">{{ statusText }}</p>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.office-edit-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;

  &__breadcrumb {
    padding: $spacing-sm $spacing-lg;
    border-bottom: 1px solid $border-lighter;
    background: $bg-hover;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: $spacing-sm $spacing-lg;
    border-bottom: 1px solid $border-lighter;
  }

  &__header-left {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__icon {
    font-size: 18px;
  }

  &__title-group {
    display: flex;
    align-items: baseline;
    gap: 8px;
    min-width: 0;
  }

  &__name {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__meta {
    font-size: 12px;
    color: $text-secondary;
    white-space: nowrap;
  }

  &__header-right {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-shrink: 0;
  }

  &__status {
    font-size: 13px;
    color: $color-success;

    &.is-modified {
      color: $color-warning;
    }
  }

  // 编辑器撑满剩余高度（之前弹窗实现高度计算错误导致过矮）
  &__content {
    position: relative;
    flex: 1;
    min-height: 0;
    padding: $spacing-base $spacing-lg $spacing-lg;
    box-sizing: border-box;
  }

  &__iframe {
    width: 100%;
    height: 100%;
    border: 1px solid $border-lighter;
    border-radius: $radius-base;
    overflow: hidden;
  }

  &__loading {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 18px;
    background: rgba(255, 255, 255, 0.92);
    pointer-events: none;

    .office-edit-page__loading-text {
      font-size: 14px;
      color: $text-secondary;
    }

    :deep(.el-progress) {
      width: 360px;
    }
  }
}
</style>
