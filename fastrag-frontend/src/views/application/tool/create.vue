<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import ToolForm from './ToolForm.vue'
import type { Tool } from '@/mock/tools'
import * as api from '@/api'

const router = useRouter()
const saving = ref(false)

async function handleSubmit(data: Tool) {
  saving.value = true
  try {
    const created: any = await api.createTool({
      name: data.name,
      identifier: data.identifier,
      description: data.description,
      type: data.type,
      tags: data.tags,
      icon: data.icon,
      httpConfig: data.httpConfig,
      inputs: data.inputs,
      inputSchema: data.inputSchema,
      outputs: data.outputSchema,
      outputMapping: data.outputMapping,
      enabled: data.enabled,
    })
    ElMessage.success(`工具「${created?.name || data.name}」创建成功`)
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
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </div>
</template>

<style lang="scss" scoped>
// 使用全局 .page-header 公共类
</style>
