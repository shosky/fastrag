<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import KnowledgeForm from './form.vue'
import type { KnowledgeBaseForm } from '@/types/knowledge'
import * as api from '@/api'

const router = useRouter()
const saving = ref(false)

// 策略模板缓存
let strategyTemplates: any[] = []

// 加载策略模板
async function loadTemplates() {
  try {
    const res: any = await api.fetchParseStrategyTemplates()
    strategyTemplates = res || []
  } catch {
    strategyTemplates = []
  }
}

async function handleSubmit(data: KnowledgeBaseForm) {
  saving.value = true
  try {
    // 确保模板已加载
    if (strategyTemplates.length === 0) {
      await loadTemplates()
    }

    const kb: any = await api.createKnowledgeBase(data as any)
    const kbId = kb?.id

    // 创建成功后，自动创建默认解析策略
    if (kbId) {
      const template = strategyTemplates.find(t => t.key === (data.parseMode || 'auto')) || strategyTemplates[0]
      if (template) {
        try {
          await api.createStrategyApi(kbId, {
            name: template.name,
            description: template.description,
            extensions: template.extensions,
            parseMethod: template.parseMethod,
          })
        } catch {
          // 策略创建失败不阻断主流程
        }
      }
    }

    ElMessage.success(`知识库「${kb?.name || data.name}」创建成功`)
    router.push('/knowledge')
  } catch {
    ElMessage.error('创建知识库失败')
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
