<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const availableTools = ref<any[]>([])
const bindings = ref<Array<{ id: string; toolId: string; toolName: string; enabled: number }>>([])

const selectedToolIds = ref<Set<string>>(new Set())

const loading = ref(false)
const saving = ref(false)

const selectedCount = computed(() => selectedToolIds.value.size)

async function loadData() {
  loading.value = true
  try {
    const [toolsRes, bindingsRes]: any[] = await Promise.all([
      api.getTools(),
      api.getAppToolBindings(appId()),
    ])
    availableTools.value = Array.isArray(toolsRes) ? toolsRes : (toolsRes?.list || toolsRes?.records || [])
    bindings.value = Array.isArray(bindingsRes) ? bindingsRes : (bindingsRes?.list || bindingsRes?.records || [])
    selectedToolIds.value = new Set(bindings.value.map(b => b.toolId))
  } catch (e) {
    availableTools.value = []
    bindings.value = []
  } finally {
    loading.value = false
  }
}

function toggleTool(toolId: string) {
  const id = String(toolId)
  if (selectedToolIds.value.has(id)) {
    selectedToolIds.value.delete(id)
  } else {
    selectedToolIds.value.add(id)
  }
  selectedToolIds.value = new Set(selectedToolIds.value)
}

function isSelected(toolId: string): boolean {
  return selectedToolIds.value.has(String(toolId))
}

async function handleSave() {
  saving.value = true
  try {
    const currentlyBound = new Set(bindings.value.map(b => b.toolId))
    const toAdd = [...selectedToolIds.value].filter(id => !currentlyBound.has(id))
    const toRemove = bindings.value.filter(b => !selectedToolIds.value.has(b.toolId))

    for (const b of toRemove) {
      await api.unbindAppTool(appId(), b.id)
    }
    for (const toolId of toAdd) {
      const tool = availableTools.value.find(s => String(s.id) === toolId)
      await api.bindAppTool(appId(), {
        toolId,
        toolName: tool?.name || tool?.identifier || '',
        enabled: 1,
      })
    }

    bindings.value = bindings.value.filter(b => selectedToolIds.value.has(b.toolId))
    for (const id of toAdd) {
      bindings.value.push({ id: '', toolId: id, toolName: '', enabled: 1 })
    }

    ElMessage.success(`已保存（新增 ${toAdd.length}，移除 ${toRemove.length}）`)
  } catch (e) {
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">工具配置</div>
        <span class="selected-count">已选 {{ selectedCount }} 个工具</span>
      </div>
      <p style="font-size:13px;color:var(--el-text-color-secondary);margin-bottom:12px">
        配置应用可用的 HTTP 工具，扩展智能体的执行能力
      </p>
      <div v-loading="loading" class="option-list">
        <el-empty v-if="!availableTools.length" description="暂无可用工具" :image-size="60" />
        <div v-for="tool in availableTools" :key="tool.id" class="option-item">
          <div class="option-info">
            <el-checkbox
              :model-value="isSelected(String(tool.id))"
              @change="toggleTool(String(tool.id))"
            >
              <span class="option-name">{{ tool.name }}</span>
              <span class="option-identifier">{{ tool.identifier || tool.id }}</span>
            </el-checkbox>
            <span class="option-desc">{{ tool.description || '暂无描述' }}</span>
          </div>
          <el-switch :model-value="tool.enabled" size="small" disabled @click.stop />
        </div>
      </div>
    </div>

    <div class="save-bar">
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section { padding-bottom: 72px; }

.card-panel {
  background: var(--el-bg-color-overlay);
  border-radius: $radius-base;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.section-title { font-size: 15px; font-weight: 600; color: $text-primary; }
.selected-count { font-size: 13px; color: $text-secondary; }

.option-list {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: $radius-base;
  overflow: hidden;
}

.option-item {
  padding: $spacing-base;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  display: flex;
  align-items: center;
  justify-content: space-between;

  &:last-child { border-bottom: none; }
  &:hover { background: var(--el-fill-color-light); }
}

.option-info { display: flex; flex-direction: column; gap: $spacing-xs; flex: 1; }
.option-name { font-weight: 500; }

.option-identifier {
  font-size: 12px; color: $text-secondary; margin-left: $spacing-sm;
}

.option-desc { font-size: 12px; color: $text-secondary; margin-left: 24px; }

.save-bar {
  position: sticky;
  bottom: 0;
  margin-top: $spacing-lg;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 4px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
  z-index: 10;
}
</style>
