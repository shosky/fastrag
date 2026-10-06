<script setup lang="ts">
import { ref, computed } from 'vue'
import { Plus, Star, MoreFilled, RefreshRight, Close, ChatLineRound } from '@element-plus/icons-vue'
import { useConfirm } from '@/composables/useConfirm'
import { formatDate } from '@/utils/format'
import { groupSessionsByDate } from '@/utils/sessionGrouping'
import type { ConversationSummary } from '@/types/conversation'

// ===========================================================================
// 会话列表（左栏）：置顶图标/来源标记/⋯项操作菜单（起标题·置顶·清空·删除）/
// checkbox 多选批量删（ElMessageBox 确认）/ 分页（usePagination，仓规）/
// 日期分节（app-chat-optimize Step 4：置顶 / 今天 / 近 7 天 / 更早）
//
// 分页状态（currentPage/pageSize/total）由页面持有（页面负责拉取），
// 本组件渲染分页容器并上报变更。
// ===========================================================================

const props = defineProps<{
  sessions: ConversationSummary[]
  activeId: string
  loading?: boolean
  error?: string
  currentPage: number
  pageSize: number
  total: number
}>()

const emit = defineEmits<{
  (e: 'select', session: ConversationSummary): void
  (e: 'create'): void
  (e: 'retry'): void
  (e: 'generate-title', session: ConversationSummary): void
  (e: 'pin', session: ConversationSummary): void
  (e: 'clear', session: ConversationSummary): void
  (e: 'delete-selected', ids: string[]): void
  (e: 'page-change', page: number): void
  (e: 'size-change', size: number): void
}>()

const { confirm } = useConfirm()

// ── 多选模式（INFERRED-5：checkbox 多选 + 底部浮出「删除所选」+ 确认）──
const multiSelectMode = ref(false)
const selection = ref<string[]>([])

function toggleMultiSelect() {
  multiSelectMode.value = !multiSelectMode.value
  selection.value = []
}

function isSelected(id: string): boolean {
  return selection.value.includes(id)
}

function toggleSelect(id: string) {
  selection.value = isSelected(id)
    ? selection.value.filter(v => v !== id)
    : [...selection.value, id]
}

async function handleDeleteSelected() {
  if (!selection.value.length) return
  const ok = await confirm(`确定删除所选 ${selection.value.length} 个会话？会话内消息将一并删除。`, '删除确认')
  if (!ok) return
  emit('delete-selected', [...selection.value])
  selection.value = []
}

// ── ⋯ 项操作菜单（INFERRED-4：智能起标题在菜单内）──
async function handleItemCommand(command: string, session: ConversationSummary) {
  switch (command) {
    case 'title': emit('generate-title', session); break
    case 'pin': emit('pin', session); break
    case 'clear': emit('clear', session); break
    case 'delete': {
      const ok = await confirm(`确定删除会话「${session.title || '新对话'}」？会话内消息将一并删除。`, '删除确认')
      if (ok) emit('delete-selected', [session.id])
      break
    }
  }
}

function formatTime(value?: string): string {
  if (!value) return '-'
  return formatDate(value, 'MM-DD HH:mm')
}

// ── 日期分节（纯函数分桶；置顶桶恒在最前，空桶不输出）──
const groupedSessions = computed(() => groupSessionsByDate(props.sessions))

const activeCount = computed(() => selection.value.length)
</script>

<template>
  <aside class="session-panel">
    <div class="panel-header">
      <span class="panel-title">会话</span>
      <button class="icon-btn small" :class="{ active: multiSelectMode }" title="多选管理" @click="toggleMultiSelect">
        <el-icon :size="14"><Close v-if="multiSelectMode" /><MoreFilled v-else /></el-icon>
      </button>
    </div>

    <div class="panel-body">
      <button class="new-chat-btn" @click="emit('create')">
        <el-icon :size="14"><Plus /></el-icon> 新建对话
      </button>

      <!-- loading：骨架条 ×5 -->
      <template v-if="loading">
        <div v-for="i in 5" :key="i" class="session-skeleton">
          <el-skeleton :rows="1" animated />
        </div>
      </template>

      <!-- error：错误行 + 重试 -->
      <div v-else-if="error" class="panel-state panel-error">
        <span>会话列表加载失败</span>
        <el-button link type="primary" size="small" @click="emit('retry')">
          <el-icon :size="12"><RefreshRight /></el-icon> 重试
        </el-button>
      </div>

      <!-- empty：引导文案 + 新建按钮 -->
      <div v-else-if="!sessions.length" class="panel-state">
        <div class="panel-empty">开始你的第一段对话</div>
        <el-button type="primary" size="small" plain @click="emit('create')">
          <el-icon :size="12"><Plus /></el-icon>&nbsp;新建对话
        </el-button>
      </div>

      <!-- 列表：日期分节（置顶优先） + 节内 updatedAt 倒序（服务端排序为准） -->
      <template v-else>
        <template v-for="group in groupedSessions" :key="group.key">
          <div class="session-group-head">{{ group.label }}</div>
          <div
            v-for="s in group.sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === activeId, selected: isSelected(s.id) }"
            @click="multiSelectMode ? toggleSelect(s.id) : emit('select', s)"
          >
          <el-checkbox
            v-if="multiSelectMode"
            :model-value="isSelected(s.id)"
            @click.stop
            @change="toggleSelect(s.id)"
          />
          <el-icon v-else :size="14" class="session-item-icon"><ChatLineRound /></el-icon>

          <div class="session-item-info">
            <div class="session-item-title">
              {{ s.title || '新对话' }}
            </div>
            <div class="session-item-meta">
              <el-tag
                size="small"
                :type="s.appId ? 'primary' : 'info'"
                class="source-tag"
              >
                {{ s.sourceLabel || (s.appId ? '应用' : '直聊') }}
              </el-tag>
              <span>{{ s.messageCount ?? 0 }} 条</span>
              <span>{{ formatTime(s.updatedAt) }}</span>
            </div>
          </div>

          <!-- 置顶小图钉（常驻，激活着色） -->
          <button
            class="pin-btn"
            :class="{ active: s.isPinned }"
            :title="s.isPinned ? '取消置顶' : '置顶'"
            @click.stop="emit('pin', s)"
          >
            <el-icon :size="12"><Star /></el-icon>
          </button>

          <!-- ⋯ 项操作菜单 -->
          <el-dropdown trigger="click" @command="(cmd: string) => handleItemCommand(cmd, s)">
            <button class="more-btn" @click.stop><el-icon :size="14"><MoreFilled /></el-icon></button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="title">✨ 智能起标题</el-dropdown-item>
                <el-dropdown-item command="pin">{{ s.isPinned ? '取消置顶' : '置顶' }}</el-dropdown-item>
                <el-dropdown-item command="clear">清空消息</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除会话</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          </div>
        </template>

        <!-- 分页（BEM：conversation-list__pagination） -->
        <div v-if="total > pageSize" class="session-list__pagination">
          <el-pagination
            small
            background
            layout="total, prev, pager, next"
            :total="total"
            :page-size="pageSize"
            :current-page="currentPage"
            :pager-count="5"
            @current-change="(p: number) => emit('page-change', p)"
          />
        </div>
        <div v-else class="session-list__pagination session-list__pagination--single">
          <span class="total-hint">共 {{ total }} 个会话</span>
        </div>
      </template>
    </div>

    <!-- 多选模式底部浮出操作条 -->
    <transition name="bar">
      <div v-if="multiSelectMode" class="bulk-bar">
        <span class="bulk-count">已选 {{ activeCount }} 项</span>
        <el-button size="small" type="danger" :disabled="!activeCount" @click="handleDeleteSelected">
          删除所选
        </el-button>
      </div>
    </transition>
  </aside>
</template>

<style lang="scss" scoped>
$primary: #046a82;
$primary-light: #3996ae;
$primary-bg: #f7fbfd;
$text-primary: #151616;
$text-secondary: #4c4d4d;
$text-muted: #979999;
$border: #eef0f0;
$border-light: #f0f2f2;
$bg-card: #ffffff;
$bg-hover: #f5f7f7;

.session-panel {
  width: 280px;
  background: $bg-card;
  border-right: 1px solid $border;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid $border-light;
}
.panel-title { font-size: 14px; font-weight: 600; color: $text-primary; }

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: $text-secondary;
  cursor: pointer;
  transition: all 0.15s ease;
  &:hover { background: $bg-hover; color: $text-primary; }
  &.active { background: $primary-bg; color: $primary; }
  &.small { width: 24px; height: 24px; }
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.new-chat-btn {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  width: 100%; padding: 8px; margin-bottom: 4px;
  border: 1px dashed $border; border-radius: 8px;
  background: transparent; font-size: 13px; color: $text-muted; cursor: pointer;
  &:hover { background: $bg-hover; color: $primary; border-color: $primary-light; }
}

// ── 日期分节头（小字灰色）──
.session-group-head {
  padding: 6px 8px 2px;
  font-size: 11px;
  color: $text-muted;
  user-select: none;
  &:not(:first-child) { margin-top: 4px; }
}

.session-skeleton { padding: 8px 6px; }

.panel-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 32px 0;
  color: $text-muted;
  font-size: 13px;
}
.panel-error {
  flex-direction: row;
  justify-content: center;
}

.session-item {
  position: relative;
  display: flex; align-items: center; gap: 8px;
  padding: 8px; padding-right: 52px;
  border-radius: 8px; cursor: pointer;
  &:hover { background: $bg-hover; }
  &.active { background: $primary-bg; }
  &.selected { background: $primary-bg; outline: 1px dashed $primary-light; }

  .session-item-icon { color: $primary; flex-shrink: 0; }

  .session-item-info { flex: 1; min-width: 0; }

  .session-item-title {
    font-size: 13px;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    color: $text-primary;
  }

  .session-item-meta {
    display: flex; align-items: center; gap: 6px;
    margin-top: 2px;
    font-size: 11px; color: $text-muted;
    .source-tag { flex-shrink: 0; transform: scale(0.9); transform-origin: left center; }
  }

  .pin-btn {
    position: absolute;
    top: 6px;
    right: 30px;
    display: inline-flex; align-items: center; justify-content: center;
    width: 20px; height: 20px;
    border: none; border-radius: 4px;
    background: transparent; color: $text-muted; cursor: pointer;
    opacity: 0.35; transition: all 0.15s;
    &:hover { background: #fff7e0; color: #e6a23c; opacity: 1; }
    &.active { color: #e6a23c; opacity: 1; }
  }
  &:hover .pin-btn { opacity: 0.8; }

  .more-btn {
    position: absolute;
    top: 6px;
    right: 6px;
    display: inline-flex; align-items: center; justify-content: center;
    width: 20px; height: 20px;
    border: none; border-radius: 4px;
    background: transparent; color: $text-muted; cursor: pointer;
    opacity: 0; transition: opacity 0.15s;
    &:hover { background: #eef0f0; color: $text-primary; }
  }
  &:hover .more-btn { opacity: 1; }
}

.session-list__pagination {
  display: flex;
  justify-content: center;
  padding: 8px 0 4px;
  &--single { justify-content: center; .total-hint { font-size: 11px; color: $text-muted; } }
}

.bulk-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid $border-light;
  background: $bg-card;
  .bulk-count { font-size: 12px; color: $text-secondary; }
}

.bar-enter-active, .bar-leave-active { transition: all 0.2s ease; }
.bar-enter-from, .bar-leave-to { transform: translateY(100%); opacity: 0; }
</style>
