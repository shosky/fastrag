<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import ToolForm from './ToolForm.vue'
import type { Tool } from '@/mock/tools'
import { createTool } from '@/mock/tools'

const router = useRouter()
const saving = ref(false)

async function handleSubmit(data: Tool) {
  saving.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 300))
    const created = createTool({
      name: data.name,
      identifier: data.identifier,
      description: data.description,
      type: 'http',
      tags: data.tags,
      icon: data.icon,
      httpConfig: data.httpConfig,
      inputs: data.inputs,
      enabled: data.enabled,
    })
    ElMessage.success(`HTTP 工具「${created.name}」创建成功`)
    router.push('/application/my-tools')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/application/my-tools')
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="handleCancel">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h3>创建工具</h3>
    </div>

    <ToolForm
      mode="create"
      http-only
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </div>
</template>

<style lang="scss" scoped>
// 使用全局 .page-header 公共类
</style>
