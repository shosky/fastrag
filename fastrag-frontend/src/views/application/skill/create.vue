<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import SkillForm from './SkillForm.vue'
import type { Skill } from '@/mock/skills'
import { createSkill } from '@/mock/skills'

const router = useRouter()
const saving = ref(false)

async function handleSubmit(data: Skill) {
  saving.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 300))
    const created = createSkill({
      name: data.name,
      identifier: data.identifier,
      description: data.description,
      icon: data.icon,
      source: data.source,
      category: data.category,
      trigger: data.trigger,
      content: data.content,
      codeType: data.codeType,
      code: data.code,
      inputs: data.inputs,
      outputs: data.outputs,
      enabled: data.enabled,
      recommended: data.recommended,
      dependencies: data.dependencies,
      scopes: data.scopes,
      author: data.author,
      version: data.version,
    })
    ElMessage.success(`技能「${created.name}」创建成功`)
    router.push('/application/skill-management')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/application/skill-management')
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="handleCancel">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h3>创建技能</h3>
    </div>

    <SkillForm
      mode="create"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </div>
</template>

<style lang="scss" scoped>
// 使用全局 .page-header 公共类
</style>
