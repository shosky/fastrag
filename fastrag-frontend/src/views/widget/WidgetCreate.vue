<script setup lang="ts">
// Widget 新建页（PAGE-202）：WidgetForm mode=create，保存 → createWidget → 跳编辑页。
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createWidget, toWidgetSaveRequest, type WidgetFormDraft } from '@/api/widget'
import WidgetForm from './WidgetForm.vue'

const router = useRouter()
const saving = ref(false)

async function onSubmit(draft: WidgetFormDraft) {
  saving.value = true
  try {
    const res = await createWidget(toWidgetSaveRequest(draft))
    ElMessage.success('创建成功，可继续编辑')
    router.replace(`/widget/${res?.id ?? ''}`)
  } catch (err) {
    // 行级校验错误（ERR-WG-SCHEMA-INVALID）由 WidgetForm 内部落 tab；此处仅网络类兜底
    ElMessage.error((err as { message?: string })?.message ?? '创建失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="widget-create">
    <h2>新建 Widget</h2>
    <WidgetForm mode="create" :saving="saving" @submit="onSubmit" @cancel="router.back()" />
  </div>
</template>

<style scoped>
.widget-create { padding: 16px 20px; }
</style>
