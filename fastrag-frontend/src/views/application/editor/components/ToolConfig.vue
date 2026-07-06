<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

// ===========================================================================
// 工具配置
// ===========================================================================

const availableTools = ref<any[]>([])
const selectedToolIds = ref<string[]>([])
const loading = ref(false)

async function loadTools() {
  loading.value = true
  try {
    const res: any = await api.getTools()
    availableTools.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
  } catch (e) {
    availableTools.value = []
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
      selectedToolIds.value = ids.filter((id: string) => !id.startsWith('skill_') && !id.startsWith('mcp_'))
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
    const mcpIds = toolIds.filter((id: string) => id.startsWith('mcp_') || id.startsWith('mcp:'))
    const merged = [...skillIds, ...selectedToolIds.value, ...mcpIds].join(',')
    await api.saveAppConfig(appId(), { toolIds: merged })
    ElMessage.success('工具配置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    loading.value = false
  }
}

function handleToggleTool(tool: any) {
  const id = String(tool.id)
  const idx = selectedToolIds.value.indexOf(id)
  if (idx >= 0) {
    selectedToolIds.value.splice(idx, 1)
  } else {
    selectedToolIds.value.push(id)
  }
}

onMounted(async () => {
  await Promise.all([loadTools(), loadAppConfig()])
})
</script>

<template>
  <div class="config-section">
    <h3>工具配置</h3>
    <p class="desc">配置应用可用的 HTTP 工具，扩展智能体的执行能力。</p>

    <div v-loading="loading" class="tool-list">
      <el-empty v-if="!availableTools.length" description="暂无可用工具" :image-size="60" />

      <div v-for="tool in availableTools" :key="tool.id" class="tool-option">
        <div class="tool-info">
          <el-checkbox
            :model-value="selectedToolIds.includes(String(tool.id))"
            @change="handleToggleTool(tool)"
          >
            <span class="tool-name">{{ tool.name }}</span>
            <span class="tool-identifier">{{ tool.identifier || tool.id }}</span>
          </el-checkbox>
          <span class="tool-desc">{{ tool.description || '暂无描述' }}</span>
        </div>
        <el-switch
          :model-value="tool.enabled"
          size="small"
          disabled
          @click.stop
        />
      </div>
    </div>

    <div v-if="availableTools.length" class="selected-count">
      已选 {{ selectedToolIds.length }} 个工具
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

.tool-list {
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  overflow: hidden;
}

.tool-option {
  padding: $spacing-base;
  border-bottom: 1px solid $border-extra-light;
  display: flex;
  align-items: center;
  justify-content: space-between;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: $bg-hover;
  }
}

.tool-info {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  flex: 1;
}

.tool-name {
  font-weight: 500;
}

.tool-identifier {
  font-size: 12px;
  color: $text-secondary;
  margin-left: $spacing-sm;
}

.tool-desc {
  font-size: 12px;
  color: $text-secondary;
  margin-left: 24px;
}

.selected-count {
  margin-top: $spacing-base;
  color: $text-secondary;
  font-size: 13px;
  display: flex;
  align-items: center;
}
</style>
