<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import SkillForm from './SkillForm.vue'
import type { Skill } from '@/mock/skills'
import * as api from '@/api'

const router = useRouter()
const saving = ref(false)

async function handleSubmit(data: Skill) {
  saving.value = true
  try {
    const created: any = await api.createSkill(data as any)
    ElMessage.success(`技能「${created?.name || data.name}」创建成功`)
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
