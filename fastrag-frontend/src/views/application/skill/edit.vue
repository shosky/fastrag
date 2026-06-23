<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import SkillForm from './SkillForm.vue'
import type { Skill } from '@/mock/skills'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()

const skill = ref<Skill | null>(null)
const loading = ref(false)
const saving = ref(false)

const id = route.params.id as string

async function loadSkill() {
  loading.value = true
  try {
    const data = (await api.getSkillDetail(id)) as any
    if (!data) {
      ElMessage.error('技能不存在')
      router.push('/application/skill-management')
      return
    }
    skill.value = data
  } finally {
    loading.value = false
  }
}

async function handleSubmit(data: Skill) {
  saving.value = true
  try {
    await api.updateSkill(id, data as any)
    ElMessage.success('保存成功')
    router.push('/application/skill-management')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/application/skill-management')
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(
      `确定要删除技能「${skill.value?.name}」吗？`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await api.deleteSkill(id)
    ElMessage.success('删除成功')
    router.push('/application/skill-management')
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadSkill()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="handleCancel">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h3>编辑技能</h3>
      <el-button
        v-if="skill"
        type="danger"
        plain
        size="small"
        style="margin-left: auto"
        @click="handleDelete"
      >
        <el-icon><Delete /></el-icon>删除技能
      </el-button>
    </div>

    <div v-if="loading" v-loading="true" style="min-height: 400px" />

    <SkillForm
      v-else-if="skill"
      mode="edit"
      :initial-data="skill"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </div>
</template>

<style lang="scss" scoped>
// 使用全局 .page-header 公共类
</style>
