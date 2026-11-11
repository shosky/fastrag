<script setup lang="ts">
// ===========================================================================
// WidgetConfig —— 应用编辑器「配置 > Widget」绑定面板（PAGE-204，第六类绑定）。
// 照 SkillConfig 模式：候选（API-001 自建资产 /candidates 口径）与现绑定（API-007）求差，
// 本地勾选 + per-binding enabled 开关，保存 diff 增删（API-008/009/010）。
// ERR-WG-BINDING-LIMIT(409) → toast 上限提示（含 10）。
// ===========================================================================
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  bindAppWidget,
  deleteAppWidgetBinding,
  getAppWidgets,
  getWidgetCandidates,
  updateAppWidgetBinding,
} from '@/api/widget'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

interface Candidate { id: string; name: string; description: string | null }
interface Binding { id: string; widgetId: string; widgetName: string; enabled: boolean }

const candidates = ref<Candidate[]>([])
const bindings = ref<Binding[]>([])
const selectedIds = ref<Set<string>>(new Set())
const enabledById = ref<Map<string, boolean>>(new Map())
const loading = ref(false)
const saving = ref(false)

const selectedCount = computed(() => selectedIds.value.size)
const MAX_WIDGET_BINDINGS = 10

async function load() {
  loading.value = true
  try {
    const [candRes, bindRes] = await Promise.all([
      getWidgetCandidates(appId()),
      getAppWidgets(appId()),
    ])
    // request 拦截器已解包：getWidgetCandidates/getAppWidgets 返回的就是数组
    candidates.value = (candRes ?? []).map((c: Candidate) => ({ id: c.id, name: c.name, description: c.description }))
    bindings.value = bindRes ?? []
    const map = new Map<string, boolean>()
    const ids = new Set<string>()
    for (const b of bindings.value) {
      ids.add(b.widgetId)
      map.set(b.widgetId, b.enabled)
    }
    selectedIds.value = ids
    enabledById.value = map
  } catch {
    ElMessage.error('绑定数据加载失败，请重试')
  } finally {
    loading.value = false
  }
}

function setEnabled(id: string, v: boolean) {
  enabledById.value.set(id, v)
  enabledById.value = new Map(enabledById.value)
}

function toggle(id: string, checked: boolean) {
  if (checked) {
    if (selectedIds.value.size >= MAX_WIDGET_BINDINGS) {
      ElMessage.warning(`Widget 绑定数已达上限（${MAX_WIDGET_BINDINGS} 个），请先解绑部分 Widget`)
      selectedIds.value.delete(id)
      selectedIds.value = new Set(selectedIds.value)
      return
    }
    selectedIds.value.add(id)
    enabledById.value.set(id, true)
  } else {
    selectedIds.value.delete(id)
    enabledById.value.delete(id)
  }
  selectedIds.value = new Set(selectedIds.value)
  enabledById.value = new Map(enabledById.value)
}

async function save() {
  saving.value = true
  try {
    let toAdd = 0
    let toRemove = 0
    // 新增
    for (const id of selectedIds.value) {
      if (!bindings.value.some((b) => b.widgetId === id)) {
        await bindAppWidget(appId(), { widgetId: id, enabled: enabledById.value.get(id) ?? true })
        toAdd++
      }
    }
    // 移除 + 启用开关
    for (const b of bindings.value) {
      if (!selectedIds.value.has(b.widgetId)) {
        await deleteAppWidgetBinding(appId(), b.id)
        toRemove++
      } else {
        const want = enabledById.value.get(b.widgetId) ?? true
        if (b.enabled !== want) {
          await updateAppWidgetBinding(appId(), b.id, want)
        }
      }
    }
    ElMessage.success(`已保存（新增 ${toAdd}，移除 ${toRemove}）`)
    await load()
  } catch (err) {
    ElMessage.error((err as { message?: string })?.message ?? '保存失败，请重试')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="widget-config" v-loading="loading">
    <h3>Widget 绑定</h3>
    <p class="hint">勾选要挂到本应用的 Widget（仅自建资产；单应用上限 {{ MAX_WIDGET_BINDINGS }} 个）。启用开关控制该卡片是否参与对话。</p>

    <div class="candidate-list">
      <label
        v-for="c in candidates"
        :key="c.id"
        class="candidate"
        :data-test="`widget-candidate-${c.id}`"
      >
        <el-checkbox
          :model-value="selectedIds.has(c.id)"
          @update:model-value="(v: boolean) => toggle(c.id, v)"
        >
          {{ c.name }}
        </el-checkbox>
        <span class="desc">{{ c.description || '—' }}</span>
        <el-switch
          v-if="selectedIds.has(c.id)"
          :model-value="enabledById.get(c.id) ?? true"
          size="small"
          @update:model-value="(v: boolean) => setEnabled(c.id, v)"
        />
      </label>
    </div>

    <el-button type="primary" :loading="saving" data-test="widget-bind-save" @click="save">保存</el-button>
  </div>
</template>

<style scoped>
.widget-config { padding: 12px 4px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
.candidate-list { margin: 12px 0; }
.candidate { display: flex; align-items: center; gap: 12px; padding: 8px 4px; border-bottom: 1px solid var(--el-border-color-lighter); }
.desc { color: var(--el-text-color-secondary); font-size: 12px; flex: 1; }
</style>
