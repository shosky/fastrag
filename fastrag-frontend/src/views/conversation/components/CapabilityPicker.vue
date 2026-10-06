<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import * as api from '@/api'

// ===========================================================================
// 能力多选弹层（ChatComposer 直聊时消费；挂预设时由外层 v-if 隐藏——能力走应用绑定）
// 工具 / 技能 / MCP 三分组多选；弹层模式复用 KbScopePicker（popover + 左组右列 + 搜索）。
// 数据源为 api/index.ts 既有列表接口 getTools/getSkills/getMcpServices，零新增后端调用。
// 已选 ID 以 chip 内联显示在控制栏（同 KB chip 风格），可单个移除；
// 随 stream 请求体 toolIds/mcpIds/skillIds 透传（B02 演进，空数组不发送）。
// ===========================================================================

const toolIds = defineModel<string[]>('toolIds', { default: () => [] })
const skillIds = defineModel<string[]>('skillIds', { default: () => [] })
const mcpIds = defineModel<string[]>('mcpIds', { default: () => [] })

type CapGroup = 'tools' | 'skills' | 'mcps'

const groups: Array<{ key: CapGroup; label: string }> = [
  { key: 'tools', label: '工具' },
  { key: 'skills', label: '技能' },
  { key: 'mcps', label: 'MCP' },
]

const visible = ref(false)
const loading = ref(false)
const loaded = ref(false)
const activeGroup = ref<CapGroup>('tools')
const keyword = ref('')

const catalogs = ref<Record<CapGroup, Array<{ id: string; name: string }>>>({
  tools: [], skills: [], mcps: [],
})

const currentList = computed(() => catalogs.value[activeGroup.value])
const filteredList = computed(() =>
  keyword.value ? currentList.value.filter(item => item.name?.includes(keyword.value)) : currentList.value
)

/** 分组 → 对应已选 ID 模型（渲染与 toggle 共用的取值口） */
function idsOf(group: CapGroup): string[] {
  return group === 'tools' ? toolIds.value : group === 'skills' ? skillIds.value : mcpIds.value
}

const currentSelected = computed(() => idsOf(activeGroup.value))

function isSelected(id: string): boolean {
  return currentSelected.value.includes(id)
}

function toggle(id: string) {
  const next = isSelected(id)
    ? currentSelected.value.filter(v => v !== id)
    : [...currentSelected.value, id]
  if (activeGroup.value === 'tools') toolIds.value = next
  else if (activeGroup.value === 'skills') skillIds.value = next
  else mcpIds.value = next
}

function clearCurrentGroup() {
  if (activeGroup.value === 'tools') toolIds.value = []
  else if (activeGroup.value === 'skills') skillIds.value = []
  else mcpIds.value = []
}

/** 从对应分组移除单个已选项（chip × 内联移除） */
function removeFrom(group: CapGroup, id: string) {
  const next = idsOf(group).filter(v => v !== id)
  if (group === 'tools') toolIds.value = next
  else if (group === 'skills') skillIds.value = next
  else mcpIds.value = next
}

/** chip 显示名：目录已加载用名称，加载失败回退 id */
function nameOf(group: CapGroup, id: string): string {
  return catalogs.value[group].find(item => item.id === id)?.name || id
}

function pickList(res: any): Array<{ id: string; name: string }> {
  const raw = Array.isArray(res) ? res : (res?.list || res?.records || [])
  return (Array.isArray(raw) ? raw : []).map((item: any) => ({ id: String(item.id), name: item.name || item.id }))
}

async function load() {
  if (loaded.value) return
  loading.value = true
  try {
    const [t, s, m] = await Promise.all([
      api.getTools().catch(() => null) as Promise<any>,
      api.getSkills().catch(() => null) as Promise<any>,
      api.getMcpServices().catch(() => null) as Promise<any>,
    ])
    catalogs.value = { tools: pickList(t), skills: pickList(s), mcps: pickList(m) }
    loaded.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(visible, (v) => { if (v && !loaded.value) load() })
</script>

<template>
  <div class="capability-picker">
    <el-popover v-model:visible="visible" :width="420" trigger="click" placement="top-start">
      <template #reference>
        <button class="cap-picker-btn" title="选择直聊可用的工具 / 技能 / MCP">
          <el-icon :size="14"><MagicStick /></el-icon>
          <span>能力</span>
          <span v-if="toolIds.length + skillIds.length + mcpIds.length" class="cap-count">
            {{ toolIds.length + skillIds.length + mcpIds.length }}
          </span>
        </button>
      </template>
      <div class="cap-scope-picker">
        <div class="cap-groups">
          <div
            v-for="g in groups"
            :key="g.key"
            class="group-item"
            :class="{ active: activeGroup === g.key }"
            @click="activeGroup = g.key; keyword = ''"
          >
            <span>{{ g.label }}</span>
            <span v-if="idsOf(g.key).length" class="group-count">{{ idsOf(g.key).length }}</span>
          </div>
        </div>
        <div class="cap-list">
          <el-input v-model="keyword" placeholder="搜索" clearable size="small">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-scrollbar class="cap-list__scroll" v-loading="loading">
            <template v-if="!loading">
              <div v-for="item in filteredList" :key="item.id" class="cap-item" @click="toggle(item.id)">
                <el-checkbox :model-value="isSelected(item.id)" @click.prevent />
                <span class="cap-name">{{ item.name }}</span>
              </div>
              <el-empty
                v-if="!filteredList.length"
                :description="`暂无可选${groups.find(g => g.key === activeGroup)?.label || '能力'}，请先在应用编排中配置`"
                :image-size="48"
              />
            </template>
          </el-scrollbar>
          <div class="cap-list__footer">
            <span class="selected-info">已选 {{ currentSelected.length }} 个</span>
            <el-button link size="small" :disabled="!currentSelected.length" @click="clearCurrentGroup">清空</el-button>
          </div>
        </div>
      </div>
    </el-popover>

    <!-- 已选能力 chip（内联控制栏，同 KB chip 风格，可单个移除） -->
    <el-tag
      v-for="id in toolIds"
      :key="`tool-${id}`"
      closable
      size="small"
      @close="removeFrom('tools', id)"
    >工具：{{ nameOf('tools', id) }}</el-tag>
    <el-tag
      v-for="id in skillIds"
      :key="`skill-${id}`"
      closable
      type="success"
      size="small"
      @close="removeFrom('skills', id)"
    >技能：{{ nameOf('skills', id) }}</el-tag>
    <el-tag
      v-for="id in mcpIds"
      :key="`mcp-${id}`"
      closable
      type="warning"
      size="small"
      @close="removeFrom('mcps', id)"
    >MCP：{{ nameOf('mcps', id) }}</el-tag>
  </div>
</template>

<style lang="scss" scoped>
.capability-picker {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.cap-picker-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid #eef0f0;
  border-radius: 6px;
  background: transparent;
  color: #4c4d4d;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  &:hover { border-color: #3996ae; color: #046a82; }
  .cap-count {
    min-width: 16px;
    height: 16px;
    line-height: 16px;
    padding: 0 4px;
    border-radius: 8px;
    background: #046a82;
    color: #fff;
    font-size: 11px;
    text-align: center;
  }
}

.cap-scope-picker {
  display: flex;
  gap: 0;
  height: 300px;
  border: 1px solid #f0f2f2;
  border-radius: 6px;
  overflow: hidden;
}

.cap-groups {
  width: 90px;
  border-right: 1px solid #f0f2f2;
  .group-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    font-size: 13px;
    color: #4c4d4d;
    cursor: pointer;
    &:hover { background: #f5f7f7; }
    &.active {
      background: #f7fbfd;
      color: #046a82;
      font-weight: 500;
    }
    .group-count {
      min-width: 15px;
      height: 15px;
      line-height: 15px;
      padding: 0 4px;
      border-radius: 8px;
      background: #046a82;
      color: #fff;
      font-size: 10px;
      text-align: center;
    }
  }
}

.cap-list {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 8px;
  &__scroll {
    flex: 1;
    margin-top: 6px;
  }
  .cap-item {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 8px;
    border-radius: 4px;
    cursor: pointer;
    &:hover { background: #f5f7f7; }
    .cap-name {
      flex: 1;
      min-width: 0;
      font-size: 13px;
      color: #151616;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: 6px;
    border-top: 1px solid #f0f2f2;
    .selected-info { font-size: 12px; color: #979999; }
  }
}
</style>
