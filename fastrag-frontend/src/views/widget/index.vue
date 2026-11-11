<script setup lang="ts">
// ===========================================================================
// Widget 列表页（PAGE-201，资源段第 9 项）：「我的 Widget / 官方模板」双 tab。
// 双 tab 统一 ADP 式预览卡片网格：进入时逐条拉取详情，toPreviewSnapshot →
// WidgetCardRenderer 实时渲染实际交互内容（非交互 preview 态，2026-10-31 联调裁决）。
// 我的卡头=编辑/删除；官方卡头=复制。
// STATE-201 骨架屏 / STATE-202 我的空态双引导 / STATE-203 官方空态兜底 / STATE-204 失败 toast+重试。
// ===========================================================================
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  copyWidget,
  deleteWidget,
  getWidget,
  getWidgets,
  toWidgetListItem,
  type WidgetDetail,
  type WidgetListItem,
} from '@/api/widget'
import { toPreviewSnapshot } from '@/utils/widget/snapshot'
import type { WidgetSnapshot } from '@/types/widget'
import WidgetCardRenderer from '@/views/conversation/components/widget/WidgetCardRenderer.vue'

const router = useRouter()
const activeTab = ref<'mine' | 'builtin'>('mine')
const loading = ref(false)
const mine = ref<WidgetListItem[]>([])
const builtin = ref<WidgetListItem[]>([])

// 预览详情缓存：id → 详情（双 tab 共享；进 tab / 增删后按需拉取）
const detailsById = ref<Map<string, WidgetDetail>>(new Map())
const previewLoading = ref(false)

const rows = computed(() => (activeTab.value === 'mine' ? mine.value : builtin.value))

const previews = computed(() =>
  rows.value.map((row) => {
    const detail = detailsById.value.get(row.id)
    const snapshot: WidgetSnapshot | null = detail
      ? toPreviewSnapshot(detail.template, detail.defaultJson)
      : null
    return { row, snapshot }
  })
)

async function load() {
  loading.value = true
  try {
    const [mineRes, builtinRes] = await Promise.all([getWidgets(false), getWidgets(true)])
    // request 拦截器已解包 {code,data,message}：getWidgets 返回的就是 WidgetSummary[]
    mine.value = (mineRes ?? []).map(toWidgetListItem)
    builtin.value = (builtinRes ?? []).map(toWidgetListItem)
    await ensureDetails(rows.value)
  } catch {
    ElMessage.error('Widget 列表加载失败，请重试')
  } finally {
    loading.value = false
  }
}

/** 详情拉取（预览数据源；单条失败仅该卡降级占位，不阻塞整版） */
async function ensureDetails(rowsToLoad: WidgetListItem[]) {
  const missing = rowsToLoad.filter((r) => !detailsById.value.has(r.id))
  if (missing.length === 0) return
  previewLoading.value = true
  try {
    await Promise.all(
      missing.map(async (row) => {
        try {
          const detail = await getWidget(row.id)
          if (detail) detailsById.value.set(row.id, detail)
        } catch {
          /* 单条失败 → 预览占位（STATE-208 语义） */
        }
      })
    )
  } finally {
    previewLoading.value = false
  }
}

// 增删改后失效对应详情缓存（下次进 tab 重拉最新三件套）
function invalidateDetail(id: string) {
  detailsById.value.delete(id)
}

watch(activeTab, (tab) => {
  const target = tab === 'mine' ? mine.value : builtin.value
  ensureDetails(target)
})

async function remove(row: WidgetListItem) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？被应用绑定的 Widget 需先解绑。`, '删除 Widget', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteWidget(row.id)
    invalidateDetail(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (err) {
    ElMessage.error((err as { message?: string })?.message ?? '删除失败')
  }
}

async function copy(row: WidgetListItem) {
  try {
    const res = await copyWidget(row.id)
    ElMessage.success('已复制，可开始编辑')
    router.push(`/widget/${res?.id ?? ''}`)
  } catch {
    ElMessage.error('复制失败，请重试')
  }
}

onMounted(load)
</script>

<template>
  <div class="widget-page">
    <div class="page-head">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="我的 Widget" name="mine" />
        <el-tab-pane label="官方模板" name="builtin" />
      </el-tabs>
      <el-button
        v-if="activeTab === 'mine'"
        data-test="widget-new"
        type="primary"
        @click="router.push('/widget/create')"
      >新建</el-button>
    </div>

    <!-- 双 tab 统一预览卡片网格：实际交互内容实时渲染（非交互 preview 态） -->
    <div
      v-if="!loading && rows.length"
      v-loading="previewLoading"
      class="preview-grid"
      data-test="widget-preview-grid"
    >
      <div v-for="p in previews" :key="p.row.id" class="preview-card">
        <div class="preview-card__head">
          <span class="preview-card__name">{{ p.row.name }}</span>
          <span class="preview-card__actions">
            <template v-if="activeTab === 'mine'">
              <el-button link type="primary" @click="router.push(`/widget/${p.row.id}`)">编辑</el-button>
              <el-button link type="danger" data-test="widget-delete" @click="remove(p.row)">删除</el-button>
            </template>
            <el-button v-else link type="primary" data-test="widget-copy" @click="copy(p.row)">复制</el-button>
          </span>
        </div>
        <div class="preview-card__body">
          <div class="preview-card__stage">
            <WidgetCardRenderer :snapshot="p.snapshot" :interactive="false" preview />
          </div>
        </div>
      </div>
    </div>

    <el-empty
      v-if="!loading && activeTab === 'mine' && rows.length === 0"
      data-test="widget-empty-mine"
      description="还没有自己的 Widget"
    >
      <el-button type="primary" @click="router.push('/widget/create')">新建</el-button>
      <el-button data-test="widget-empty-goto-builtin" @click="activeTab = 'builtin'">去官方模板复制</el-button>
    </el-empty>
    <el-empty
      v-if="!loading && activeTab === 'builtin' && rows.length === 0"
      data-test="widget-empty-builtin"
      description="官方模板为空"
    >
      <el-button @click="load">重试</el-button>
    </el-empty>

    <div v-if="loading" v-loading="true" class="skeleton" data-test="widget-loading" />
  </div>
</template>

<style scoped>
.widget-page { padding: 16px 20px; }
.page-head { display: flex; align-items: center; justify-content: space-between; }
.skeleton { min-height: 240px; }

/* ADP 式模板网格：浅灰舞台 + 白卡实时预览（双 tab 统一） */
.preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.preview-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  overflow: hidden;
  background: var(--el-fill-color-blank);
}
.preview-card__head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px 6px;
}
.preview-card__name { font-weight: 600; font-size: 14px; }
.preview-card__actions { display: inline-flex; align-items: center; }
.preview-card__body {
  background: var(--el-fill-color-light);
  padding: 16px 12px;
  min-height: 160px;
  display: flex; align-items: center; justify-content: center;
}
/* 预览舞台：指针穿透（非交互预览），窄卡内居中自适应 */
.preview-card__stage {
  width: 100%; max-width: 300px;
  pointer-events: none;
  font-size: 12px;
  transform-origin: center top;
}
.preview-card__stage :deep(.widget-card) { margin: 0 auto; }
</style>
