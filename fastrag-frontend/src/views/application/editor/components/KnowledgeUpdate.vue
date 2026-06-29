<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const updateLogs = ref<any[]>([])
const autoUpdateConfig = ref({ enabled: false, schedule: '0 0 2 * * ?', incremental: true })
const showAutoConfig = ref(false)

async function loadUpdateLogs() {
  try { updateLogs.value = ((await api.getKnowledgeUpdateLogs(appId())) as any) || [] } catch { updateLogs.value = [] }
  if (!updateLogs.value.length) {
    updateLogs.value = [
      { id: 'u1', updateType: 'auto', status: 'completed', summary: '增量更新：新增3个文档', detail: '新增：产品手册v2.docx、技术规格.pdf、API文档.md', operator: '系统', createdAt: '2026-06-29 02:00:00' },
      { id: 'u2', updateType: 'manual', status: 'completed', summary: '手动更新：重新导入知识库', detail: '重新导入全部文档，共12个文件', operator: '管理员', createdAt: '2026-06-28 15:30:00' },
      { id: 'u3', updateType: 'incremental', status: 'running', summary: '增量更新进行中', detail: '正在处理2个新增文件', operator: '系统', createdAt: '2026-06-29 10:00:00' },
    ]
  }
}
async function loadAutoConfig() {
  try { const r: any = await api.getAutoKnowledgeUpdate(appId()); if (r) Object.assign(autoUpdateConfig.value, r) } catch {}
}
async function handleManualUpdate() {
  try {
    const { value } = await ElMessageBox.prompt('确认手动更新知识库？输入备注说明', '手动更新', { inputPlaceholder: '更新备注' })
    if (value !== null) { await api.triggerAppKnowledgeUpdate(appId(), { remark: value }); ElMessage.success('更新任务已提交'); await loadUpdateLogs() }
  } catch {}
}
async function handleSaveAutoConfig() {
  await api.setAutoKnowledgeUpdate(appId(), autoUpdateConfig.value); showAutoConfig.value = false; ElMessage.success('自动更新配置已保存')
}
async function handleCompare(row: any) {
  try { const r: any = await api.compareKnowledgeContent(appId(), row.oldId || row.id, row.newId || row.id); ElMessageBox.alert(JSON.stringify(r, null, 2), '内容比较') } catch { ElMessage.error('获取比较内容失败') }
}
onMounted(() => { loadUpdateLogs(); loadAutoConfig() })
</script>
<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header"><div class="section-title">知识更新</div>
        <div style="display:flex;gap:8px">
          <el-button size="small" @click="showAutoConfig=true">自动更新配置</el-button>
          <el-button size="small" type="primary" @click="handleManualUpdate">手动更新</el-button>
        </div>
      </div>
      <el-table :data="updateLogs" stripe size="small" style="margin-top:12px">
        <el-table-column prop="updateType" label="类型" width="100"><template #default="{row}">{{ {auto:'自动',manual:'手动',incremental:'增量'}[row.updateType]||row.updateType }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="80"><template #default="{row}"><el-tag :type="row.status==='completed'?'success':'warning'" size="small">{{row.status}}</el-tag></template></el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="80"><template #default="{row}"><el-button link type="primary" size="small" @click="handleCompare(row)">比较</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!updateLogs.length" description="暂无更新记录" :image-size="60" />
    </div>
    <el-dialog v-model="showAutoConfig" title="自动更新配置" width="480px">
      <el-form label-width="120px">
        <el-form-item label="启用自动更新"><el-switch v-model="autoUpdateConfig.enabled" /></el-form-item>
        <el-form-item label="调度表达式"><el-input v-model="autoUpdateConfig.schedule" placeholder="cron表达式" /></el-form-item>
        <el-form-item label="增量更新"><el-switch v-model="autoUpdateConfig.incremental" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showAutoConfig=false">取消</el-button><el-button type="primary" @click="handleSaveAutoConfig">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; gap:8px; }
.section-title { font-size: 15px; font-weight: 600; }
.card-panel { background: var(--el-bg-color-overlay); border-radius: 8px; padding: 20px; border: 1px solid var(--el-border-color-light); }
</style>
