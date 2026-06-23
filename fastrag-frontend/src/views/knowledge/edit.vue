<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import KnowledgeForm from './form.vue'
import type { KnowledgeBase, KnowledgeBaseForm } from '@/types/knowledge'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()

const knowledgeBase = ref<KnowledgeBase | null>(null)
const loading = ref(false)
const saving = ref(false)

/** Get the knowledge base ID from route params */
const id = route.params.id as string

/** Load knowledge base data by ID */
async function loadKnowledgeBase() {
  loading.value = true
  try {
    const data: any = await api.getKnowledgeBaseDetail(id)
    if (!data) {
      ElMessage.error('知识库不存在')
      router.push('/knowledge')
      return
    }
    knowledgeBase.value = data
  } catch {
    ElMessage.error('加载知识库数据失败')
  } finally {
    loading.value = false
  }
}

/** Handle form submit */
async function handleSubmit(formData: KnowledgeBaseForm) {
  saving.value = true
  try {
    await api.updateKnowledgeBase(id, formData as any)
    ElMessage.success('保存成功')
    router.push('/knowledge')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

/** Handle cancel */
function handleCancel() {
  router.push('/knowledge')
}

/** Handle delete with confirmation dialog */
async function handleDelete() {
  try {
    await ElMessageBox.confirm(
      '确定要删除该知识库吗？删除后数据将无法恢复。',
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await api.deleteKnowledgeBase(id)
    ElMessage.success('删除成功')
    router.push('/knowledge')
  } catch {
    // User cancelled – do nothing
  }
}

onMounted(() => {
  loadKnowledgeBase()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="router.push('/knowledge')">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h3>编辑知识库</h3>
    </div>

    <!-- Loading state -->
    <div v-if="loading" v-loading="true" style="min-height: 400px" />

    <!-- Form -->
    <KnowledgeForm
      v-else-if="knowledgeBase"
      mode="edit"
      :initial-data="knowledgeBase"
      @submit="handleSubmit"
      @cancel="handleCancel"
      @delete="handleDelete"
    />
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

// 使用全局 .page-header 公共类，不再自定义 .edit-header
</style>
