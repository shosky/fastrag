<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import McpForm from './McpForm.vue'
import type { McpService } from '@/mock/mcp'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()

const service = ref<McpService | null>(null)
const loading = ref(false)
const saving = ref(false)

const id = route.params.id as string

async function loadService() {
  loading.value = true
  try {
    const data = (await api.getMcpServiceDetail(id)) as any
    if (!data) {
      ElMessage.error('MCP 服务不存在')
      router.push('/application/mcp-management')
      return
    }
    service.value = data
  } finally {
    loading.value = false
  }
}

async function handleSubmit(data: McpService) {
  saving.value = true
  try {
    await api.updateMcpService(id, data as any)
    ElMessage.success('保存成功')
    router.push('/application/mcp-management')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/application/mcp-management')
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(
      `确定要删除 MCP 服务「${service.value?.name}」吗？`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await api.deleteMcpService(id)
    ElMessage.success('删除成功')
    router.push('/application/mcp-management')
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadService()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="handleCancel">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h3>编辑 MCP 服务</h3>
      <el-button
        v-if="service"
        type="danger"
        plain
        size="small"
        style="margin-left: auto"
        @click="handleDelete"
      >
        <el-icon><Delete /></el-icon>删除服务
      </el-button>
    </div>

    <div v-if="loading" v-loading="true" style="min-height: 400px" />

    <McpForm
      v-else-if="service"
      mode="edit"
      :initial-data="service"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </div>
</template>

<style lang="scss" scoped>
// 使用全局 .page-header 公共类
</style>
