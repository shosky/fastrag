<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import KnowledgeForm from './form.vue'
import type { KnowledgeBaseForm } from '@/types/knowledge'
import { getKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase } from '@/mock/knowledge-bases'
import { setKBAcl } from '@/mock/auth-acl'

const route = useRoute()
const router = useRouter()

const knowledgeBase = ref<ReturnType<typeof getKnowledgeBase>>(null)
const loading = ref(false)
const saving = ref(false)

/** Get the knowledge base ID from route params */
const id = route.params.id as string

/** Load knowledge base data by ID（来自统一 mock 层） */
async function loadKnowledgeBase() {
  loading.value = true
  try {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 300))

    const data = getKnowledgeBase(id)
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

/** Handle form submit —— 写回 mock 数据层 */
function handleSubmit(formData: KnowledgeBaseForm) {
  saving.value = true
  try {
    const updated = updateKnowledgeBase(id, formData)
    if (!updated) {
      ElMessage.error('保存失败：知识库不存在')
      return
    }
    ElMessage.success('保存成功')
    router.push('/knowledge')
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
    deleteKnowledgeBase(id)
    // 同步清理 ACL 条目：重置为空
    setKBAcl(id, [])
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
