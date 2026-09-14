<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const updateLogs = ref<any[]>([])
const autoUpdateConfig = ref({ enabled: false, schedule: '0 0 2 * * ?', incremental: true })
const showAutoConfig = ref(false)

// 知识库选择：更新日志/比较走真实 kb 级端点，需要选定知识库
const kbList = ref<any[]>([])
const selectedKbId = ref('')
async function loadKbList() {
  try {
    const res: any = await api.getAppKbBindings(appId())
    const bindings = Array.isArray(res) ? res : res?.list || []
    // 绑定记录只有 kbId，需要换名称时直接展示 kbId
    kbList.value = bindings.map((b: any) => ({ id: b.kbId, name: b.kbName || b.kbId }))
    if (kbList.value.length && !selectedKbId.value) selectedKbId.value = kbList.value[0].id
  } catch { kbList.value = [] }
}

async function loadUpdateLogs() {
  if (!selectedKbId.value) { updateLogs.value = []; return }
  loading.value = true
  try {
    const res: any = await api.getKnowledgeUpdateLogs(selectedKbId.value, 1, 50)
    updateLogs.value = res?.list || []
  } catch { updateLogs.value = [] } finally { loading.value = false }
}
const loading = ref(false)

async function loadAutoConfig() {
  try {
    const r: any = await api.getAutoKnowledgeUpdate(appId())
    if (r) Object.assign(autoUpdateConfig.value, { schedule: r.schedule || '0 0 2 * * ?', incremental: r.incremental !== false, enabled: !!r.enabled })
  } catch {}
}
async function handleManualUpdate() {
  try {
    const { value } = await ElMessageBox.prompt('确认手动更新知识库？输入备注说明', '手动更新', { inputPlaceholder: '更新备注' })
    if (value !== null) {
      const r: any = await api.triggerAppKnowledgeUpdate(appId(), { remark: value })
      ElMessage.success(`更新已完成（${r?.updated ?? 0} 个知识库已记录）`)
      await loadUpdateLogs()
    }
  } catch {}
}
async function handleSaveAutoConfig() {
  await api.setAutoKnowledgeUpdate(appId(), autoUpdateConfig.value)
  showAutoConfig.value = false; ElMessage.success('自动更新配置已保存')
}
async function handleKbChange() { await loadUpdateLogs() }
async function handleCompare(row: any) {
  if (!row.oldKnowledgeId && !row.newKnowledgeId) { ElMessage.info('该记录无新旧版本信息'); return }
  try {
    const r: any = await api.compareKnowledgeContent(selectedKbId.value, row.oldKnowledgeId, row.newKnowledgeId)
    const diff = `旧版：${r?.oldVersion?.title || '-'}（${r?.oldLength ?? 0} 字）\n新版：${r?.newVersion?.title || '-'}（${r?.newLength ?? 0} 字）\n标题变更：${r?.titleChanged ? '是' : '否'}；内容变更：${r?.contentChanged ? '是' : '否'}`
    ElMessageBox.alert(diff, '内容比较')
  } catch { ElMessage.error('获取比较内容失败') }
}
onMounted(async () => { await loadKbList(); loadUpdateLogs(); loadAutoConfig() })
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
      <div class="filter-bar">
        <el-select v-model="selectedKbId" placeholder="选择知识库" style="width:240px" @change="handleKbChange">
          <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <span style="font-size:12px;color:#909399">更新日志按知识库记录，请先选择知识库</span>
      </div>
      <el-table :data="updateLogs" stripe size="small" style="margin-top:12px" v-loading="loading">
        <el-table-column prop="updateType" label="类型" width="100"><template #default="{row}">{{ ({auto:'自动',manual:'手动',incremental:'增量'} as Record<string,string>)[row.updateType]||row.updateType }}</template></el-table-column>
        <el-table-column prop="target" label="目标" width="120" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="timestamp" label="时间" width="160" />
        <el-table-column label="操作" width="80"><template #default="{row}"><el-button link type="primary" size="small" @click="handleCompare(row)">比较</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!updateLogs.length && !loading" description="暂无更新记录（手动更新后可见）" :image-size="60" />
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
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom:$spacing-base; gap:8px; }
.section-title { font-size:15px; font-weight:600; }
.filter-bar { display: flex; align-items: center; gap: 12px; margin-top: 8px; }
.card-panel { background: var(--el-bg-color-overlay); border-radius: 8px; padding: 20px; border: 1px solid var(--el-border-color-light); }
</style>
