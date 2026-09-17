<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as api from '@/api'

/**
 * 知识库同步：源库 → 目标库的知识条目与问答对同步。
 * 支持手动执行（一键同步）、周期配置（分钟级，由后端定时调度执行）与同步记录查看。
 */
const loading = ref(false)
const configs = ref<any[]>([])
const kbs = ref<any[]>([])

const showConfigDialog = ref(false)
const configForm = ref<any>({ id: '', name: '', sourceKbId: '', targetKbId: '', syncMode: 'incremental', intervalMinutes: 60, enabled: 1 })

const showRecords = ref(false)
const records = ref<any[]>([])
const recordsTitle = ref('')

async function loadConfigs() {
  loading.value = true
  try {
    const res: any = await api.getKbSyncConfigs()
    configs.value = res || []
  } finally { loading.value = false }
}

async function loadKbs() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbs.value = res?.list || res || []
  } catch { kbs.value = [] }
}

onMounted(() => { loadConfigs(); loadKbs() })

function handleAdd() {
  configForm.value = { id: '', name: '', sourceKbId: '', targetKbId: '', syncMode: 'incremental', intervalMinutes: 60, enabled: 1 }
  showConfigDialog.value = true
}
function handleEdit(row: any) {
  configForm.value = { ...row }
  showConfigDialog.value = true
}
async function handleSave() {
  const f = configForm.value
  if (!f.name.trim() || !f.sourceKbId || !f.targetKbId) { ElMessage.warning('请完善配置信息'); return }
  if (f.sourceKbId === f.targetKbId) { ElMessage.warning('源库与目标库不能相同'); return }
  try {
    if (f.id) await api.updateKbSyncConfig(f.id, f)
    else await api.createKbSyncConfig(f)
    ElMessage.success('保存成功')
    showConfigDialog.value = false
    loadConfigs()
  } catch { ElMessage.error('保存失败') }
}
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除同步配置「${row.name}」？`, '提示', { type: 'warning' })
    await api.deleteKbSyncConfig(row.id)
    loadConfigs()
  } catch { }
}
async function handleToggle(row: any) {
  try {
    await api.updateKbSyncConfig(row.id, { enabled: row.enabled === 1 ? 0 : 1 })
    loadConfigs()
  } catch { ElMessage.error('操作失败') }
}
async function handleRun(row: any) {
  try {
    const res: any = await api.runKbSync(row.id)
    if (res?.status === 'success') {
      ElMessage.success(`同步完成：知识条目 ${res.syncedEntries} 条，问答对 ${res.syncedQaPairs} 条`)
    } else {
      ElMessage.error(`同步失败：${res?.message || '未知错误'}`)
    }
    loadConfigs()
  } catch { ElMessage.error('执行失败') }
}
async function handleViewRecords(row: any) {
  recordsTitle.value = `同步记录：${row.name}`
  try {
    records.value = ((await api.getKbSyncRecords(row.id)) as any) || []
  } catch { records.value = [] }
  showRecords.value = true
}
</script>

<template>
  <div class="kb-sync" v-loading="loading">
    <div class="kb-sync__header">
      <div>
        <h2 class="kb-sync__title">知识库同步</h2>
        <p class="kb-sync__sub">将源知识库的知识条目与问答对同步至目标库，支持手动执行与周期调度</p>
      </div>
      <el-button type="primary" @click="handleAdd">新增同步配置</el-button>
    </div>

    <el-table :data="configs" stripe>
      <el-table-column prop="name" label="配置名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="源知识库" min-width="140">
        <template #default="{ row }">{{ kbs.find((k) => k.id === row.sourceKbId)?.name || row.sourceKbId }}</template>
      </el-table-column>
      <el-table-column label="目标知识库" min-width="140">
        <template #default="{ row }">{{ kbs.find((k) => k.id === row.targetKbId)?.name || row.targetKbId }}</template>
      </el-table-column>
      <el-table-column prop="syncMode" label="模式" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.syncMode === 'full' ? 'warning' : 'primary'">
            {{ row.syncMode === 'full' ? '全量' : '增量' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="周期" width="90">
        <template #default="{ row }">{{ row.intervalMinutes }} 分钟</template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="handleToggle(row)" />
        </template>
      </el-table-column>
      <el-table-column prop="lastSyncAt" label="上次同步" width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" size="small" :icon="Refresh" @click="handleRun(row)">立即同步</el-button>
          <el-button link type="primary" size="small" @click="handleViewRecords(row)">记录</el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !configs.length" description="暂无同步配置" />

    <!-- 新增/编辑配置 -->
    <el-dialog v-model="showConfigDialog" :title="configForm.id ? '编辑同步配置' : '新增同步配置'" width="560px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="配置名称" required>
          <el-input v-model="configForm.name" placeholder="如：主库 → 客服库 同步" />
        </el-form-item>
        <el-form-item label="源知识库" required>
          <el-select v-model="configForm.sourceKbId" filterable style="width: 100%">
            <el-option v-for="k in kbs" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标知识库" required>
          <el-select v-model="configForm.targetKbId" filterable style="width: 100%">
            <el-option v-for="k in kbs" :key="k.id" :label="k.name" :value="k.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="同步模式">
          <el-radio-group v-model="configForm.syncMode">
            <el-radio value="incremental">增量（仅同步新增）</el-radio>
            <el-radio value="full">全量（含更新覆盖）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="同步周期">
          <el-input-number v-model="configForm.intervalMinutes" :min="5" :max="1440" :step="5" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px">分钟（后端定时调度自动执行）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showConfigDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 同步记录 -->
    <el-dialog v-model="showRecords" :title="recordsTitle" width="640px">
      <el-table :data="records" stripe size="small">
        <el-table-column prop="createdAt" label="时间" width="160" show-overflow-tooltip />
        <el-table-column prop="syncedEntries" label="同步条目" width="90" />
        <el-table-column prop="syncedQaPairs" label="同步问答对" width="100" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="140" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!records.length" description="暂无同步记录" :image-size="60" />
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.kb-sync { padding: 16px 20px; max-width: 1200px; margin: 0 auto; }
.kb-sync__header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;
}
.kb-sync__title { font-size: 20px; font-weight: 600; margin: 0; }
.kb-sync__sub { font-size: 13px; color: #909399; margin: 4px 0 0; }
</style>
