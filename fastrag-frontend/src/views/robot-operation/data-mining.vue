<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const taskList = ref<any[]>([])
const keyword = ref('')

async function loadData() {
  loading.value = true
  try {
    const res = await api.getDataMiningTasks({ keyword: keyword.value || undefined })
    taskList.value = (res as any) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

const showDialog = ref(false)
const formData = ref({
  name: '',
  kbId: '',
  ruleType: 'keyword',
  ruleConfig: '',
})

function handleAdd() {
  formData.value = { name: '', kbId: '', ruleType: 'keyword', ruleConfig: '' }
  showDialog.value = true
}

async function handleSave() {
  if (!formData.value.name) {
    ElMessage.warning('请输入任务名称')
    return
  }
  await api.createDataMiningTask({
    name: formData.value.name,
    kbId: formData.value.kbId,
    ruleType: formData.value.ruleType,
    ruleConfig: formData.value.ruleConfig,
  })
  showDialog.value = false
  await loadData()
  ElMessage.success('创建成功')
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除任务「${row.name}」？`, '删除确认', { type: 'warning' })
    await api.deleteDataMiningTask(row.id)
    await loadData()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleRun(row: any) {
  await api.runDataMiningTask(row.id)
  await loadData()
  ElMessage.success('执行完成')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">数据挖掘任务</div>
        <el-button type="primary" @click="handleAdd">新增任务</el-button>
      </div>

      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索任务名称" clearable style="width: 200px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">查询</el-button>
      </div>

      <el-table :data="taskList" stripe>
        <el-table-column prop="name" label="任务名称" show-overflow-tooltip />
        <el-table-column prop="kbId" label="知识库" width="140" show-overflow-tooltip />
        <el-table-column prop="ruleType" label="规则类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.ruleType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'info'" size="small">
              {{ row.status === 'enabled' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastRunAt" label="最后执行" width="160" />
        <el-table-column prop="resultSummary" label="结果摘要" show-overflow-tooltip />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleRun(row)">执行</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!taskList.length && !loading" description="暂无数据挖掘任务" />
    </div>

    <el-dialog v-model="showDialog" title="新增数据挖掘任务" width="520px">
      <el-form label-width="90px">
        <el-form-item label="任务名称" required>
          <el-input v-model="formData.name" placeholder="如：高退款率商品挖掘" />
        </el-form-item>
        <el-form-item label="知识库ID">
          <el-input v-model="formData.kbId" placeholder="kb_sample" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="formData.ruleType" style="width: 160px">
            <el-option label="关键词" value="keyword" />
            <el-option label="频率统计" value="frequency" />
            <el-option label="聚类" value="cluster" />
            <el-option label="阈值" value="threshold" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则配置">
          <el-input v-model="formData.ruleConfig" type="textarea" :rows="4" placeholder='{"threshold": 0.15, "days": 30}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}
.section-title { font-size: 15px; font-weight: 600; }
</style>
