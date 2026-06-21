<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import ToolForm from './ToolForm.vue'
import type { Tool } from '@/mock/tools'
import { getTool, updateTool, deleteTool } from '@/mock/tools'

const route = useRoute()
const router = useRouter()

const tool = ref<Tool | null>(null)
const loading = ref(false)
const saving = ref(false)

const id = route.params.id as string

async function loadTool() {
  loading.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 300))
    const data = getTool(id)
    if (!data) {
      ElMessage.error('工具不存在')
      router.push('/application/my-tools')
      return
    }
    tool.value = data
  } finally {
    loading.value = false
  }
}

async function handleSubmit(data: Tool) {
  saving.value = true
  try {
    const updated = updateTool(id, data)
    if (!updated) {
      ElMessage.error('保存失败：工具不存在')
      return
    }
    ElMessage.success('保存成功')
    router.push('/application/my-tools')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/application/my-tools')
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(
      `确定要删除工具「${tool.value?.name}」吗？`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    deleteTool(id)
    ElMessage.success('删除成功')
    router.push('/application/my-tools')
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadTool()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="handleCancel">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h3>编辑工具</h3>
      <el-button
        v-if="tool"
        type="danger"
        plain
        size="small"
        style="margin-left: auto"
        @click="handleDelete"
      >
        <el-icon><Delete /></el-icon>删除工具
      </el-button>
    </div>

    <div v-if="loading" v-loading="true" style="min-height: 400px" />

    <ToolForm
      v-else-if="tool"
      mode="edit"
      :initial-data="tool"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </div>
</template>

<style lang="scss" scoped>
// 使用全局 .page-header 公共类
</style>
