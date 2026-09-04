<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as bpm from '@/api/bpm'
import { type TestCaseVO, type TestCaseRequest } from '@/types/bpm'

const route = useRoute()
const router = useRouter()
const flowDefId = computed(() => String(route.params.flowDefId || ''))

const loading = ref(false)
const list = ref<TestCaseVO[]>([])
const showCreate = ref(false)
const showRunResult = ref(false)
const createForm = reactive<TestCaseRequest>({ name: '', inputs: {}, expectedOutput: '' })
const inputsText = ref('{}')
const runResult = ref<any>(null)

async function loadList() {
  if (!flowDefId.value) return
  loading.value = true
  try {
    list.value = await bpm.listTestCases(flowDefId.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
    list.value = []
  } finally { loading.value = false }
}

function openCreate() {
  createForm.name = ''
  createForm.inputs = {}
  createForm.expectedOutput = ''
  inputsText.value = '{}'
  showCreate.value = true
}

async function submitCreate() {
  if (!createForm.name) { ElMessage.warning('请输入用例名称'); return }
  try {
    let inputs: any = {}
    try { inputs = JSON.parse(inputsText.value) } catch { ElMessage.warning('输入参数 JSON 格式错误'); return }
    createForm.inputs = inputs
    await bpm.createTestCase(flowDefId.value, createForm)
    ElMessage.success('已创建')
    showCreate.value = false
    loadList()
  } catch (e: any) { ElMessage.error(e?.message || '创建失败') }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除测试用例「${row.name}」?`, '确认', { type: 'warning' })
    await bpm.deleteTestCase(flowDefId.value, row.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e: any) { if (e !== 'cancel' && e?.message) ElMessage.error(e.message) }
}

async function handleRun(row: any) {
  loading.value = true
  try {
    const res = await bpm.runTestCase(flowDefId.value, row.id)
    runResult.value = { testCase: row, ...res }
    showRunResult.value = true
    ElMessage.success('已执行')
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '执行失败')
  } finally { loading.value = false }
}

function formatDate(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').substring(0, 19)
}

function parseJSON(s?: string) {
  if (!s) return {}
  try { return JSON.parse(s) } catch { return {} }
}

onMounted(loadList)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="toolbar">
      <div class="left">
        <el-button text @click="router.push({ name: 'BpmFlowList' })">📂 流程列表</el-button>
        <span class="title">测试用例</span>
      </div>
      <el-button type="primary" @click="openCreate">＋ 新建测试用例</el-button>
    </div>

    <el-table :data="list" stripe border empty-text="暂无测试用例">
      <el-table-column prop="name" label="用例名称" min-width="200" show-overflow-tooltip />
      <el-table-column label="最后执行" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.lastRunStatus === 'pass'" size="small" type="success">通过</el-tag>
          <el-tag v-else-if="row.lastRunStatus === 'fail'" size="small" type="danger">失败</el-tag>
          <el-tag v-else size="small" type="info">未运行</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后运行时间" width="180">
        <template #default="{ row }">{{ formatDate(row.lastRunAt) }}</template>
      </el-table-column>
      <el-table-column prop="createdBy" label="创建人" width="120" />
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleRun(row)">▶ 执行</el-button>
          <el-button link type="primary" size="small" @click="openCreate">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建测试用例" width="640px">
      <el-form label-width="100px">
        <el-form-item label="用例名称" required>
          <el-input v-model="createForm.name" placeholder="如:问候场景" />
        </el-form-item>
        <el-form-item label="输入参数">
          <el-input v-model="inputsText" type="textarea" :rows="8" placeholder='{"query":"你好"}' />
        </el-form-item>
        <el-form-item label="期望输出">
          <el-input v-model="createForm.expectedOutput" type="textarea" :rows="4" placeholder="期望的关键文本片段或 JSON" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRunResult" title="用例执行结果" width="800px">
      <div v-if="runResult">
        <div class="run-summary">
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="用例">{{ runResult.testCase?.name }}</el-descriptions-item>
            <el-descriptions-item label="实例ID">{{ runResult.instanceId }}</el-descriptions-item>
            <el-descriptions-item v-if="runResult.outputs" label="输出" :span="2">
              <pre class="json-block">{{ JSON.stringify(runResult.outputs, null, 2) }}</pre>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
      <template #footer>
        <el-button @click="showRunResult = false">关闭</el-button>
        <el-button type="primary" @click="runResult?.instanceId && router.push({ name: 'BpmInstance', query: { instanceId: runResult.instanceId } })">查看实例详情</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-container { padding: $spacing-base; }
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 8px; flex-wrap: wrap; }
.toolbar .left { display: flex; align-items: center; gap: 8px; }
.toolbar .title { font-weight: 600; font-size: 15px; }

.json-block {
  background: #f5f7fa; padding: 8px 12px; border-radius: 4px;
  font-size: 11px; max-height: 300px; overflow: auto; margin: 0;
}
</style>