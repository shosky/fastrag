<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getListenerList, createListener, updateListener, deleteListener, LISTENER_STATUS_LABELS, LISTENER_STATUS_COLORS } from '@/mock/knowledge-review'

const loading = ref(false)
const dataList = ref<any[]>([])
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const eventsInput = ref('')
const eventOptions = ['review.submitted', 'review.approved', 'review.rejected', 'review.timeout', 'publish.online', 'publish.offline', 'listener.error']

async function loadData() { loading.value = true; try { dataList.value = getListenerList() } finally { loading.value = false } }
onMounted(loadData)

function handleAdd() { editingId.value = null; formData.value = { enabled: true }; eventsInput.value = ''; dialogTitle.value = '新增监听器'; showDialog.value = true }
function handleEdit(row: any) { editingId.value = row.id; formData.value = { ...row }; eventsInput.value = (row.events || []).join(', '); dialogTitle.value = '编辑监听器'; showDialog.value = true }
async function handleDelete(row: any) { try { await ElMessageBox.confirm(`确定要删除「${row.name}」吗？`, '提示', { type: 'warning' }); deleteListener(row.id); ElMessage.success('删除成功'); loadData() } catch {} }
function handleToggle(row: any) { updateListener(row.id, { enabled: !row.enabled }); row.enabled = !row.enabled; ElMessage.success(row.enabled ? '已启用' : '已停用') }
function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入名称'); return }
  if (!formData.value.url) { ElMessage.warning('请输入回调URL'); return }
  const data = { ...formData.value, events: typeof eventsInput.value === 'string' ? eventsInput.value.split(',').map((s: string) => s.trim()).filter(Boolean) : eventsInput.value }
  if (editingId.value) { updateListener(editingId.value, data); ElMessage.success('更新成功') }
  else { createListener(data); ElMessage.success('创建成功') }
  showDialog.value = false; loadData()
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">监听管理</div>
        <el-button type="primary" @click="handleAdd">新增监听器</el-button>
      </div>
      <el-table :data="dataList" stripe>
        <el-table-column prop="name" label="监听器名称" min-width="150" />
        <el-table-column prop="url" label="回调URL" min-width="250" show-overflow-tooltip />
        <el-table-column label="监听事件" min-width="220">
          <template #default="{ row }"><el-tag v-for="e in (row.events || [])" :key="e" size="small" style="margin: 2px">{{ e }}</el-tag></template>
        </el-table-column>
        <el-table-column label="启用" width="80"><template #default="{ row }"><el-switch :model-value="row.enabled" @change="handleToggle(row)" size="small" /></template></el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }"><el-tag :type="LISTENER_STATUS_COLORS[row.status] as any" size="small">{{ LISTENER_STATUS_LABELS[row.status] || row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="triggerCount" label="触发次数" width="100" />
        <el-table-column prop="lastTriggeredAt" label="最后触发" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="showDialog" :title="dialogTitle" width="600px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="监听器名称" required><el-input v-model="formData.name" placeholder="请输入名称" /></el-form-item>
        <el-form-item label="回调URL" required><el-input v-model="formData.url" placeholder="https://example.com/webhook" /></el-form-item>
        <el-form-item label="监听事件">
          <el-select v-model="eventsInput" multiple filterable allow-create placeholder="选择或输入事件" style="width: 100%"><el-option v-for="e in eventOptions" :key="e" :label="e" :value="e" /></el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="formData.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
