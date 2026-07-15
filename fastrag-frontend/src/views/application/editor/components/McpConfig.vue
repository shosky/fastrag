<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const availableMcpServices = ref<any[]>([])
const bindings = ref<Array<{ id: string; mcpServiceId: string; mcpServiceName: string; enabled: number }>>([])

const selectedMcpIds = ref<Set<string>>(new Set())

const loading = ref(false)
const saving = ref(false)

const selectedCount = computed(() => selectedMcpIds.value.size)

async function loadData() {
  loading.value = true
  try {
    const [mcpRes, bindingsRes]: any[] = await Promise.all([
      api.getMcpServices(),
      api.getAppMcpBindings(appId()),
    ])
    const list = Array.isArray(mcpRes) ? mcpRes : (mcpRes?.list || mcpRes?.records || [])
    availableMcpServices.value = (list || []).map((s: any) => ({
      ...s,
      enabled: s.enabled === 1 || s.enabled === true,
    }))
    bindings.value = Array.isArray(bindingsRes) ? bindingsRes : (bindingsRes?.list || bindingsRes?.records || [])
    selectedMcpIds.value = new Set(bindings.value.map(b => b.mcpServiceId))
  } catch (e) {
    availableMcpServices.value = []
    bindings.value = []
  } finally {
    loading.value = false
  }
}

function toggleMcp(mcpId: string) {
  const id = String(mcpId)
  if (selectedMcpIds.value.has(id)) {
    selectedMcpIds.value.delete(id)
  } else {
    selectedMcpIds.value.add(id)
  }
  selectedMcpIds.value = new Set(selectedMcpIds.value)
}

function isSelected(mcpId: string): boolean {
  return selectedMcpIds.value.has(String(mcpId))
}

async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

async function handleSave() {
  saving.value = true
  try {
    const currentlyBound = new Set(bindings.value.map(b => b.mcpServiceId))
    const toAdd = [...selectedMcpIds.value].filter(id => !currentlyBound.has(id))
    const toRemove = bindings.value.filter(b => !selectedMcpIds.value.has(b.mcpServiceId))

    for (const b of toRemove) {
      await api.unbindAppMcp(appId(), b.id)
    }
    for (const mcpId of toAdd) {
      const mcp = availableMcpServices.value.find(s => String(s.id) === mcpId)
      await api.bindAppMcp(appId(), {
        mcpServiceId: mcpId,
        mcpServiceName: mcp?.name || '',
        enabled: 1,
      })
    }

    bindings.value = bindings.value.filter(b => selectedMcpIds.value.has(b.mcpServiceId))
    for (const id of toAdd) {
      bindings.value.push({ id: '', mcpServiceId: id, mcpServiceName: '', enabled: 1 })
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
        <div class="section-title">MCP 配置</div>
        <span class="selected-count">已选 {{ selectedCount }} 个 MCP 服务</span>
      </div>
      <p style="font-size:13px;color:var(--el-text-color-secondary);margin-bottom:12px">
        配置应用可用的 MCP 服务，扩展智能体的外部能力
      </p>
      <div v-loading="loading" class="option-list">
        <el-empty v-if="!availableMcpServices.length" description="暂无可用 MCP 服务" :image-size="60" />
        <div v-for="mcp in availableMcpServices" :key="mcp.id" class="option-item">
          <div class="option-info">
            <el-checkbox
              :model-value="isSelected(String(mcp.id))"
              @change="toggleMcp(String(mcp.id))"
            >
              <span class="option-name">{{ mcp.name }}</span>
            </el-checkbox>
            <span class="option-url" :title="mcp.mcpUrl || mcp.command || ''">
              {{ mcp.mcpUrl || mcp.command || '-' }}
              <el-button v-if="mcp.mcpUrl || mcp.command" link type="primary" size="small" @click.stop="copyToClipboard(mcp.mcpUrl || mcp.command)">
                复制
              </el-button>
            </span>
            <div v-if="mcp.tools && mcp.tools.length" class="option-tools">
              <el-tag v-for="tool in mcp.tools" :key="tool" size="small" type="info">{{ tool }}</el-tag>
            </div>
          </div>
          <el-switch :model-value="mcp.enabled" size="small" disabled @click.stop />
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
  align-items: flex-start;
  justify-content: space-between;

  &:last-child { border-bottom: none; }
  &:hover { background: var(--el-fill-color-light); }
}

.option-info { display: flex; flex-direction: column; gap: $spacing-xs; flex: 1; }
.option-name { font-weight: 500; }

.option-url {
  font-size: 12px;
  color: $text-secondary;
  margin-left: 24px;
  word-break: break-all;
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-tools {
  display: flex;
  gap: $spacing-xs;
  margin-left: 24px;
  margin-top: $spacing-xs;
}

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
