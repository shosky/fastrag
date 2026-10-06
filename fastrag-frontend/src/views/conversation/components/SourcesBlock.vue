<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { SourceItem } from '@/composables/useConversationStream'
import type { MessageReference } from '@/types/conversation'

// ===========================================================================
// 知识来源折叠块（双形态）+ 全部来源抽屉（app-chat-optimize Step 2）
//
// - 流内形态（variant='stream'）：SourceItem（含 content）——全文展开 + 切片图片预览
// - 历史形态（variant='history'）：MessageReference（投影子集，无 content）——仅来源行 + 跳原文
// - 分组：展开区按 fileName 分组展示；块尾「查看全部 N 条来源」→ 右抽屉全量列表
//   （文件/得分/跳 chunks），流内与历史双形态同抽屉
// - 抽屉开关受控（三批）：父级传 drawerVisible 时受控（引用角标点击开抽屉），
//   未传时内部 drawerOpen 自管（块尾入口/历史直看不受影响）
//
// 分组交互平移 WeKnora docInfo.vue:14-53；跳转沿 jumpToChunk（runtime.vue:208-212）。
// ===========================================================================

const props = withDefaults(defineProps<{
  sources: (SourceItem | MessageReference)[]
  /** stream=流内 SourceItem（含 content）；history=历史 references（无 content） */
  variant?: 'stream' | 'history'
  /** 抽屉受控开关：传入即受控（角标点击开抽屉），关闭须经 update:drawerVisible 回写父级 */
  drawerVisible?: boolean
}>(), {
  variant: 'stream',
})

const emit = defineEmits<{
  (e: 'update:drawerVisible', visible: boolean): void
}>()

const router = useRouter()

/** 本地 UI 状态：折叠块整体展开与否 */
const open = ref(false)

/** 本地 UI 状态：全部来源抽屉（非受控兜底；受控时经 drawerModel 透传） */
const drawerOpen = ref(false)

/** 抽屉开关：父级传了 drawerVisible 即受控，否则走本地状态 */
const drawerModel = computed<boolean>({
  get: () => (props.drawerVisible !== undefined ? props.drawerVisible : drawerOpen.value),
  set: (v: boolean) => {
    drawerOpen.value = v
    if (props.drawerVisible !== undefined) emit('update:drawerVisible', v)
  },
})

/** 本地 UI 状态：展开全文的条目（按对象引用，分组渲染不改变原对象） */
const expandedItems = ref<Set<SourceItem | MessageReference>>(new Set())

/** 来源按 fileName 分组（保序；未知文件名单独一组） */
interface SourceGroup {
  fileName: string
  items: (SourceItem | MessageReference)[]
}

const groupedSources = computed<SourceGroup[]>(() => {
  const groups: SourceGroup[] = []
  const index = new Map<string, number>()
  for (const src of props.sources) {
    const name = src.fileName || '未知文件'
    const gi = index.get(name)
    if (gi === undefined) {
      index.set(name, groups.length)
      groups.push({ fileName: name, items: [src] })
    } else {
      groups[gi].items.push(src)
    }
  }
  return groups
})

function toggleOpen() {
  open.value = !open.value
}

function toggleExpand(src: SourceItem | MessageReference) {
  const next = new Set(expandedItems.value)
  if (next.has(src)) next.delete(src)
  else next.add(src)
  expandedItems.value = next
}

/** 跳转知识库分片页并定位到命中的分片（抽屉内跳转后收起抽屉，受控/非受控同路） */
function jumpToChunk(src: SourceItem | MessageReference) {
  if (!src.kbId || !src.fileId) return
  drawerModel.value = false
  const chunkId = `${src.fileId}_chunk_${src.chunkIndex ?? 0}`
  router.push({ path: `/knowledge/${src.kbId}/chunks/${src.fileId}`, query: { chunkId } })
}

// ── 来源图片加载（仅流内形态；经鉴权接口取 blob 生成 objectURL）──
const sourceImages = ref<Record<string, string>>({})

function isStreamItem(src: SourceItem | MessageReference): src is SourceItem {
  return props.variant === 'stream'
}

async function loadSourceImage(kbId: string, fileId: string, imageKey: string) {
  const cacheKey = `${fileId}_${imageKey}`
  if (sourceImages.value[cacheKey]) return
  try {
    const raw = localStorage.getItem('ais_token') || ''
    const token = raw.startsWith('"') ? JSON.parse(raw) : raw
    const resp = await fetch(`/api/kb/${kbId}/files/${fileId}/images/${imageKey}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (resp.ok) {
      const blob = await resp.blob()
      sourceImages.value[cacheKey] = URL.createObjectURL(blob)
    }
  } catch { /* ignore */ }
}

function sourceImageUrl(src: SourceItem | MessageReference, imageKey: string): string | undefined {
  if (!src.fileId) return undefined
  return sourceImages.value[`${src.fileId}_${imageKey}`]
}

onMounted(() => {
  if (props.variant !== 'stream') return
  for (const src of props.sources) {
    if (src.kbId && src.fileId && src.imageKeys?.length) {
      src.imageKeys.forEach(key => loadSourceImage(src.kbId!, src.fileId!, key))
    }
  }
})
</script>

<template>
  <div v-if="sources.length" class="msg-sources">
    <div class="sources-header" @click="toggleOpen">
      <span class="sources-title">📚 知识来源（{{ sources.length }}）</span>
      <span class="sources-toggle">{{ open ? '收起' : '展开' }}</span>
    </div>
    <div v-if="open" class="sources-list">
      <div v-for="group in groupedSources" :key="group.fileName" class="source-group">
        <div class="source-group-name">📄 {{ group.fileName }}（{{ group.items.length }}）</div>
        <div v-for="src in group.items" :key="src.fileId + '_' + src.chunkIndex" class="source-item">
          <div class="source-item-head">
            <span class="source-name">{{ group.fileName }}</span>
            <div class="source-item-actions">
              <span v-if="src.score !== undefined" class="source-score">{{ Math.round(src.score * 100) }}%</span>
              <button
                v-if="isStreamItem(src) && src.content && src.content.length > 200"
                class="source-btn"
                title="查看全文"
                @click="toggleExpand(src)"
              >
                {{ expandedItems.has(src) ? '收起' : '全文' }}
              </button>
              <button
                v-if="src.kbId && src.fileId"
                class="source-btn"
                title="查看原文切片"
                @click="jumpToChunk(src)"
              >
                原文
              </button>
            </div>
          </div>
          <div
            v-if="isStreamItem(src) && src.content"
            class="source-content"
            :class="{ expanded: expandedItems.has(src) }"
          >{{ src.content }}</div>
          <!-- 切片关联图片（仅流内形态） -->
          <div v-if="isStreamItem(src) && src.imageKeys?.length" class="source-images">
            <el-image
              v-for="(key, ki) in src.imageKeys"
              :key="ki"
              class="source-image"
              :src="sourceImageUrl(src, key) || ''"
              :alt="key"
              :preview-src-list="[sourceImageUrl(src, key) || '']"
              preview-teleported
              fit="cover"
              loading="lazy"
            />
          </div>
        </div>
      </div>
    </div>
    <!-- 全部来源入口：右抽屉（流内/历史同抽屉） -->
    <div class="sources-footer">
      <button class="sources-all-btn" @click="drawerModel = true">
        查看全部 {{ sources.length }} 条来源
      </button>
    </div>

    <el-drawer v-model="drawerModel" :title="`全部来源（${sources.length}）`" direction="rtl" size="420px">
      <div class="drawer-list">
        <div v-for="(src, si) in sources" :key="si" class="drawer-item">
          <div class="drawer-item-head">
            <span class="source-name">{{ src.fileName || '未知文件' }}</span>
            <span v-if="src.score !== undefined" class="source-score">{{ Math.round(src.score * 100) }}%</span>
          </div>
          <div class="drawer-item-meta">
            <span v-if="src.chunkType" class="source-chunk-type">{{ src.chunkType }}</span>
            <button
              v-if="src.kbId && src.fileId"
              class="source-btn"
              title="跳转分片页定位"
              @click="jumpToChunk(src)"
            >
              跳 chunks
            </button>
          </div>
          <div
            v-if="isStreamItem(src) && src.content"
            class="source-content"
          >{{ src.content }}</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
$primary: #046a82;
$primary-light: #3996ae;
$text-primary: #151616;
$text-secondary: #4c4d4d;
$text-muted: #979999;
$border: #eef0f0;
$border-light: #f0f2f2;
$bg-card: #ffffff;
$bg-hover: #f5f7f7;

// ── 知识来源 ──
.msg-sources {
  margin-top: 8px;
  border: 1px solid $border;
  border-radius: 8px;
  background: $bg-hover;
  overflow: hidden;
}
.sources-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
  &:hover { background: rgba(0,0,0,0.03); }
}
.sources-title { font-weight: 600; color: $text-primary; }
.sources-toggle { color: $text-muted; font-size: 11px; }
.sources-list {
  border-top: 1px solid $border;
  padding: 6px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 200px;
  overflow-y: auto;
}
.source-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.source-group-name {
  font-size: 12px;
  font-weight: 600;
  color: $text-secondary;
  padding: 2px 0;
}
.sources-footer {
  border-top: 1px solid $border;
  padding: 4px 10px;
}
.sources-all-btn {
  width: 100%;
  text-align: center;
  font-size: 12px;
  color: $primary;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 3px 0;
  border-radius: 4px;
  &:hover { background: $bg-hover; }
}

// ── 全部来源抽屉 ──
.drawer-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.drawer-item {
  background: $bg-hover;
  border: 1px solid $border;
  border-radius: 6px;
  padding: 8px 10px;
}
.drawer-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.drawer-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}
.source-chunk-type {
  font-size: 11px;
  color: $text-muted;
}
.drawer-item .source-content {
  margin-top: 6px;
  max-height: 120px;
  overflow-y: auto;
}

.source-item {
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: 6px;
  padding: 6px 8px;
}
.source-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.source-name {
  font-size: 12px;
  font-weight: 500;
  color: $primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.source-item-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.source-score {
  font-size: 11px;
  color: $text-muted;
  flex-shrink: 0;
}
.source-btn {
  font-size: 11px;
  padding: 0 6px;
  border: 1px solid $border;
  border-radius: 4px;
  background: $bg-card;
  color: $text-secondary;
  cursor: pointer;
  line-height: 1.6;
  &:hover { border-color: $primary-light; color: $primary; }
}
.source-content {
  margin-top: 3px;
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
  &.expanded {
    display: block;
    -webkit-line-clamp: unset;
    max-height: 240px;
    overflow-y: auto;
  }
}
.source-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.source-image {
  width: 120px;
  height: 90px;
  border-radius: 6px;
  border: 1px solid $border;
  cursor: zoom-in;
  overflow: hidden;
  transition: transform 0.15s ease;
  &:hover { transform: scale(1.03); }
  :deep(img) { width: 100%; height: 100%; }
}
</style>
