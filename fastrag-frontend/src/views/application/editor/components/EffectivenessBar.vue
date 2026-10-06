<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

// ===========================================================================
// 绑定有效性 chip 条（app-chat-optimize Step 3a，对齐 WeKnora
// AgentEditorModal:1186-1208「有效工具预览」）
//
// 数据源 GET /apps/{appId}/bindings/effectiveness，按 type 过滤本面板关注项；
// 面板经 v-if 挂载（即打开面板时）触发拉取。展示：有效 n（绿）/ 失效项逐条
// （红，hover title 显示原因）+「解绑」小按钮（收尾批 2026-09-26）。拉取失败
// 静默隐藏整条。
//
// 解绑参数形状：effectiveness 返回的是资源 id（kbId/toolId/…），而 unbind 端点
// 按绑定行 id 删（后端 AppConfigServiceImpl#unbindKb 等 deleteById 绑定表主键）——
// 与 KnowledgeConfig#findBindingByKbId 同法：先查本类型绑定列表按资源 id 换行 id。
// ===========================================================================

/** 后端契约：AppConfigService#bindingsEffectiveness（name 失效时为 null） */
interface EffectivenessItem {
  type: 'kb' | 'tool' | 'skill' | 'mcp' | 'db' | string
  id: string
  name: string | null
  exists: boolean
  enabled: boolean
  reason?: string
}

const props = defineProps<{
  appInfo: { id: string }
  /** 本面板关注的绑定类型（其余类型忽略） */
  type: 'kb' | 'tool' | 'skill' | 'mcp' | 'db'
}>()

const items = ref<EffectivenessItem[]>([])
const loaded = ref(false)
/** 正在解绑的资源 id（按钮 busy 态，防重复点击） */
const unbindingId = ref('')

const validCount = computed(() => items.value.filter(i => i.exists).length)
const invalidItems = computed(() => items.value.filter(i => !i.exists))

const invalidTitle = computed(() =>
  invalidItems.value.map(i => `${i.name || i.id}：${i.reason || '资源已删除'}`).join('\n')
)

/** 各类型解绑路由：绑定列表查询 + 绑定行资源 id 字段名 + 解绑封装（api/index.ts 签名为准） */
interface UnbindRoute {
  listBindings: (appId: string) => Promise<unknown>
  /** 绑定行上资源 id 字段名（对齐后端 AppXxxBinding 实体） */
  resourceKey: 'kbId' | 'toolId' | 'skillId' | 'mcpServiceId' | 'dbId'
  unbind: (appId: string, id: string) => Promise<unknown>
}

const UNBIND_ROUTES: Record<'kb' | 'tool' | 'skill' | 'mcp' | 'db', UnbindRoute> = {
  kb: { listBindings: api.getAppKbBindings, resourceKey: 'kbId', unbind: api.unbindAppKb },
  tool: { listBindings: api.getAppToolBindings, resourceKey: 'toolId', unbind: api.unbindAppTool },
  skill: { listBindings: api.getAppSkillBindings, resourceKey: 'skillId', unbind: api.unbindAppSkill },
  mcp: { listBindings: api.getAppMcpBindings, resourceKey: 'mcpServiceId', unbind: api.unbindAppMcp },
  db: { listBindings: api.getAppDbBindings, resourceKey: 'dbId', unbind: api.deleteAppDbBinding },
}

async function loadEffectiveness() {
  try {
    const res: any = await api.getBindingsEffectiveness(props.appInfo.id)
    const list: EffectivenessItem[] = Array.isArray(res) ? res : (res?.list || res?.records || [])
    items.value = list.filter(i => i.type === props.type)
    loaded.value = true
  } catch {
    // 拉取失败静默：不展示 chip 条（面板主功能不受影响）
  }
}

/** 一键解绑失效绑定：资源 id → 绑定行 id 换算后调解绑端点，成功刷新有效性 */
async function handleUnbind(item: EffectivenessItem) {
  const route = UNBIND_ROUTES[props.type]
  unbindingId.value = item.id
  try {
    const res: any = await route.listBindings(props.appInfo.id)
    const rows: any[] = Array.isArray(res) ? res : (res?.list || res?.records || [])
    const row = rows.find(b => String(b?.[route.resourceKey]) === String(item.id))
    if (!row?.id) {
      ElMessage.error('未找到对应绑定记录，请刷新后重试')
      return
    }
    await route.unbind(props.appInfo.id, String(row.id))
    ElMessage.success('已解绑')
    await loadEffectiveness()
  } catch {
    ElMessage.error('解绑失败')
  } finally {
    unbindingId.value = ''
  }
}

onMounted(loadEffectiveness)
</script>

<template>
  <div v-if="loaded && items.length" class="effectiveness-bar">
    <span class="eff-label">有效性</span>
    <span class="eff-chip eff-chip--ok" title="绑定资源存在且可用">有效 {{ validCount }}</span>
    <span v-if="invalidItems.length" class="eff-chip eff-chip--bad" :title="invalidTitle">
      失效 {{ invalidItems.length }}
    </span>
    <!-- 失效项逐条：名称 + 解绑按钮（点击换算绑定行 id 后调解绑端点） -->
    <span
      v-for="item in invalidItems"
      :key="item.id"
      class="eff-chip eff-chip--bad"
      :title="`${item.name || item.id}：${item.reason || '资源已删除'}`"
    >
      {{ item.name || item.id }}
      <button
        class="eff-unbind"
        :disabled="unbindingId === item.id"
        @click="handleUnbind(item)"
      >解绑</button>
    </span>
  </div>
</template>

<style lang="scss" scoped>
.effectiveness-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.eff-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.eff-chip {
  font-size: 12px;
  line-height: 20px;
  padding: 0 10px;
  border-radius: 10px;
  cursor: default;
  &--ok {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
    border: 1px solid var(--el-color-success-light-5);
  }
  &--bad {
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
    border: 1px solid var(--el-color-danger-light-5);
  }
}

// 解绑小按钮（chip 内嵌；配色沿 MessageBubble 操作钮语义：透明底 + hover 提亮）
.eff-unbind {
  margin-left: 4px;
  font-size: 11px;
  line-height: 1.5;
  padding: 0 6px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--el-color-danger);
  cursor: pointer;
  &:hover { background: #fff; text-decoration: underline; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}
</style>
