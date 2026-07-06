<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// MCP 配置
// ===========================================================================

const availableMcpServices = ref<any[]>([])
const selectedMcpIds = ref<string[]>([])
const loading = ref(false)

async function loadMcpServices() {
  loading.value = true
  try {
    const res: any = await api.getMcpServices()
    availableMcpServices.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
  } catch (e) {
    availableMcpServices.value = []
  } finally {
    loading.value = false
  }
}

async function loadAppConfig() {
  try {
    const config: any = await api.getAppConfig(appId())
    const toolIds = config?.toolIds || ''
    if (toolIds) {
      const ids = String(toolIds).split(',').filter(Boolean)
      selectedMcpIds.value = ids.filter((id: string) => id.startsWith('mcp_') || id.startsWith('mcp:'))
    }
  } catch (e) {
    // 静默处理
  }
}

async function handleSave() {
  loading.value = true
  try {
    const config: any = await api.getAppConfig(appId())
    const toolIds = String(config?.toolIds || '').split(',').filter(Boolean)
    const skillIds = toolIds.filter((id: string) => id.startsWith('skill_') || id.startsWith('skill:'))
    const otherToolIds = toolIds.filter((id: string) => !id.startsWith('mcp_') && !id.startsWith('mcp:'))
    const merged = [...skillIds, ...otherToolIds, ...selectedMcpIds.value].join(',')
    await api.saveAppConfig(appId(), { toolIds: merged })
    ElMessage.success('MCP 配置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    loading.value = false
  }
}

function handleToggleMcp(mcp: any) {
  const id = String(mcp.id)
  const idx = selectedMcpIds.value.indexOf(id)
  if (idx >= 0) {
    selectedMcpIds.value.splice(idx, 1)
  } else {
    selectedMcpIds.value.push(id)
  }
}

async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

onMounted(async () => {
  await Promise.all([loadMcpServices(), loadAppConfig()])
})
</script>

<template>
  <div class="config-section">
    <h3>MCP 配置</h3>
    <p class="desc">配置应用可用的 MCP 服务，扩展智能体的外部能力。</p>

    <div v-loading="loading" class="mcp-list">
      <el-empty v-if="!availableMcpServices.length" description="暂无可用 MCP 服务" :image-size="60" />

      <div v-for="mcp in availableMcpServices" :key="mcp.id" class="mcp-option">
        <div class="mcp-info">
          <el-checkbox
            :model-value="selectedMcpIds.includes(String(mcp.id))"
            @change="handleToggleMcp(mcp)"
          >
            <span class="mcp-name">{{ mcp.name }}</span>
          </el-checkbox>
          <span class="mcp-url" :title="mcp.mcpUrl || mcp.command || ''">
            {{ mcp.mcpUrl || mcp.command || '-' }}
            <el-button v-if="mcp.mcpUrl || mcp.command" link type="primary" size="small" @click.stop="copyToClipboard(mcp.mcpUrl || mcp.command)">
              复制
            </el-button>
          </span>
          <div class="mcp-tools" v-if="mcp.tools && mcp.tools.length">
            <el-tag v-for="tool in mcp.tools" :key="tool" size="small" type="info">{{ tool }}</el-tag>
          </div>
        </div>
        <el-switch
          :model-value="mcp.enabled"
          size="small"
          disabled
          @click.stop
        />
      </div>
    </div>

    <div v-if="availableMcpServices.length" class="selected-count">
      已选 {{ selectedMcpIds.length }} 个 MCP 服务
      <el-button type="primary" size="small" style="margin-left: 16px" :loading="loading" @click="handleSave">
        保 存
      </el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section {
  h3 { margin: 0 0 $spacing-lg; }
  .desc { color: $text-secondary; margin-bottom: $spacing-base; }
}

.mcp-list {
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  overflow: hidden;
}

.mcp-option {
  padding: $spacing-base;
  border-bottom: 1px solid $border-extra-light;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: $bg-hover;
  }
}

.mcp-info {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  flex: 1;
}

.mcp-name {
  font-weight: 500;
}

.mcp-url {
  font-size: 12px;
  color: $text-secondary;
  margin-left: 24px;
  word-break: break-all;
  display: flex;
  align-items: center;
  gap: 8px;
}

.mcp-tools {
  display: flex;
  gap: $spacing-xs;
  margin-left: 24px;
  margin-top: $spacing-xs;
}

.selected-count {
  margin-top: $spacing-base;
  color: $text-secondary;
  font-size: 13px;
  display: flex;
  align-items: center;
}
</style>
