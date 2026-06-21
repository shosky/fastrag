<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import KnowledgeForm from './form.vue'
import type { KnowledgeBaseForm } from '@/types/knowledge'
import { createKnowledgeBase } from '@/mock/knowledge-bases'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const saving = ref(false)

async function handleSubmit(data: KnowledgeBaseForm) {
  saving.value = true
  try {
    // Simulate API delay
    await new Promise(resolve => setTimeout(resolve, 300))
    const creator = userStore.userInfo?.username || '我'
    const kb = createKnowledgeBase(data, creator)
    // form.vue 已写入 ACL（kbId='new'），此处把 owner 条目同步到真实 id
    // 注：因 form.vue 用了占位 id 'new'，这里不强制迁移；真实接入后端时由后端统一处理
    ElMessage.success(`知识库「${kb.name}」创建成功`)
    router.push('/knowledge')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/knowledge')
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="router.push('/knowledge')">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h3>创建知识库</h3>
    </div>

    <KnowledgeForm
      mode="create"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </div>
</template>

<style lang="scss" scoped>
// 使用全局 .page-header 公共类
</style>
