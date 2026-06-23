<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import McpForm from './McpForm.vue'
import type { McpService } from '@/mock/mcp'
import * as api from '@/api'

const router = useRouter()
const saving = ref(false)

async function handleSubmit(data: McpService) {
  saving.value = true
  try {
    const created: any = await api.createMcpService(data as any)
    ElMessage.success(`MCP 服务「${created?.name || data.name}」添加成功`)
    router.push('/application/mcp-management')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/application/mcp-management')
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="handleCancel">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h3>添加 MCP 服务</h3>
    </div>

    <McpForm
      mode="create"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </div>
</template>

<style lang="scss" scoped>
// 使用全局 .page-header 公共类
</style>
