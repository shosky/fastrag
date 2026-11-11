<script setup lang="ts">
// Widget 编辑页（PAGE-203）：详情回显 → WidgetForm mode=edit + 预览面板（showPreview）。
// 保存 → updateWidget（保存即生效提示）；校验失败行级错误由 WidgetForm 落 tab（STATE-205）。
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getWidget, toWidgetDraft, toWidgetSaveRequest, updateWidget, type WidgetFormDraft } from '@/api/widget'
import WidgetForm from './WidgetForm.vue'

const route = useRoute()
const widgetId = computed(() => String(route.params.id ?? ''))
const loading = ref(true)
const saving = ref(false)
const initial = ref<WidgetFormDraft | null>(null)

onMounted(async () => {
  try {
    const detail = await getWidget(widgetId.value)
    initial.value = detail ? toWidgetDraft(detail) : null
  } catch {
    ElMessage.error('Widget 加载失败')
  } finally {
    loading.value = false
  }
})

async function onSubmit(draft: WidgetFormDraft) {
  saving.value = true
  try {
    await updateWidget(widgetId.value, toWidgetSaveRequest(draft))
    ElMessage.success('已保存（保存即生效，新对话使用最新内容；已发出消息卡片不受影响）')
  } catch (err) {
    ElMessage.error((err as { message?: string })?.message ?? '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="widget-edit" v-loading="loading">
    <h2>编辑 Widget</h2>
    <WidgetForm
      v-if="initial"
      mode="edit"
      :initial="initial"
      :saving="saving"
      show-preview
      @submit="onSubmit"
      @cancel="() => $router.back()"
    />
  </div>
</template>

<style scoped>
.widget-edit { padding: 16px 20px; }
</style>
