<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

/** tags 与数据库 JSON 列互转工具 */
function parseTag(tags: any): string {
  if (!tags) return ''
  if (typeof tags !== 'string') return String(tags)
  try { const arr = JSON.parse(tags); return Array.isArray(arr) ? arr.join(', ') : String(arr) } catch { return tags }
}
function toTagJson(str: string): string {
  if (!str || !str.trim()) return ''
  try { JSON.parse(str); return str } catch { /* 不是 JSON，包装为数组 */ }
  return JSON.stringify(str.split(/[,，]/).map(s => s.trim()).filter(Boolean))
}

const testList = ref<any[]>([])
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const formDefault = { name: '', query: '', expectedAnswer: '', tags: '' }
const form = ref({ ...formDefault })

// 分页
const page = ref(1)
const pageSize = 5

async function loadData() {
  try {
    const res: any = await api.getAppDialogTests(appId())
    testList.value = Array.isArray(res) ? res : []
  } catch { testList.value = [] }
}

/** 录制测试案例：打开对话界面与机器人真实对话 */
const showRecorder = ref(false)
const recorderLoading = ref(false)
const chatMessages = ref<{ role: 'user' | 'assistant'; content: string }[]>([])
const chatInput = ref('')
const recordedQA = ref<{ query: string; answer: string } | null>(null)

function openRecorder() {
  chatMessages.value = []
  chatInput.value = ''
  recordedQA.value = null
  showRecorder.value = true
}

async function handleSendMessage() {
  if (!chatInput.value.trim()) return
  const question = chatInput.value.trim()
  chatMessages.value.push({ role: 'user', content: question })
  chatInput.value = ''
  recorderLoading.value = true
  try {
    const res: any = await api.runApp(appId(), question)
    const answer = typeof res === 'string' ? res : (res?.answer || res?.content || res?.text || JSON.stringify(res))
    chatMessages.value.push({ role: 'assistant', content: answer })
    recordedQA.value = { query: question, answer }
  } catch (e: any) {
    chatMessages.value.push({ role: 'assistant', content: '（调用失败：' + (e?.message || '未知错误') + '）' })
  } finally {
    recorderLoading.value = false
  }
}

async function handleSaveRecorded() {
  if (!recordedQA.value) { ElMessage.warning('请先发送问题获取回答'); return }
  try {
    await api.createAppDialogTest(appId(), {
      name: '录制-' + recordedQA.value.query.substring(0, 16) + '...',
      query: recordedQA.value.query,
      expectedAnswer: recordedQA.value.answer,
      tags: JSON.stringify(['录制']),
    })
    ElMessage.success('测试案例已录制保存')
    showRecorder.value = false
    await loadData()
  } catch { ElMessage.error('录制保存失败') }
}

/** 批量导入 */
const showImportDialog = ref(false)
const importText = ref('')
async function handleBatchImport() {
  if (!importText.value.trim()) { ElMessage.warning('请输入测试数据'); return }
  try {
    const lines = importText.value.trim().split('\n')
    let count = 0
    for (const line of lines) {
      const parts = line.split(',')
      if (parts.length >= 2) {
        await api.createAppDialogTest(appId(), {
          name: parts[0].trim(),
          query: parts[1].trim(),
          expectedAnswer: parts.length >= 3 ? parts[2].trim() : '',
          tags: JSON.stringify(['批量导入']),
        })
        count++
      }
    }
    ElMessage.success(`成功导入 ${count} 条测试案例`)
    showImportDialog.value = false
    await loadData()
  } catch { ElMessage.error('导入失败，请检查格式（每行：名称,问题,期望答案）') }
}

function openAdd() { isEditing.value = false; editingId.value = ''; form.value = { ...formDefault }; showDialog.value = true }
function openEdit(row: any) { isEditing.value = true; editingId.value = row.id; form.value = { name: row.name, query: row.query, expectedAnswer: row.expectedAnswer, tags: parseTag(row.tags) }; showDialog.value = true }
async function handleSave() {
  if (!form.value.name) { ElMessage.warning('请输入名称'); return }
  const data = { ...form.value, tags: toTagJson(form.value.tags) }
  if (isEditing.value) { await api.updateAppDialogTest(appId(), editingId.value, data); ElMessage.success('已更新') }
  else { await api.createAppDialogTest(appId(), data); ElMessage.success('已创建') }
  showDialog.value = false; await loadData()
}
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '确认', { type: 'warning' }); await api.deleteAppDialogTest(appId(), row.id); await loadData(); ElMessage.success('已删除') } catch {}
}
async function handleExport() {
  try {
    const blob = await api.exportAppDialogTests(appId()) as Blob
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `test_report_${Date.now()}.csv`; a.click(); URL.revokeObjectURL(url); ElMessage.success('已导出')
  } catch { ElMessage.error('导出失败') }
}
onMounted(loadData)
</script>
<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">对话测试</div>
        <div style="display:flex;gap:8px">
          <el-button size="small" @click="openRecorder">录制测试</el-button>
          <el-button size="small" @click="showImportDialog = true">批量导入</el-button>
          <el-button size="small" @click="handleExport">导出CSV</el-button>
          <el-button type="primary" size="small" @click="openAdd">新增测试</el-button>
        </div>
      </div>
      <el-table :data="testList.slice((page - 1) * pageSize, page * pageSize)" stripe size="small" style="margin-top:12px">
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="query" label="问题" show-overflow-tooltip min-width="180" />
        <el-table-column prop="expectedAnswer" label="期望答案" show-overflow-tooltip min-width="180" />
        <el-table-column prop="tags" label="标签" width="100"><template #default="{row}"><el-tag v-if="row.tags" size="small">{{ parseTag(row.tags) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="130"><template #default="{row}"><el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button></template></el-table-column>
      </el-table>
      <div style="display:flex;justify-content:center;margin-top:12px">
        <el-pagination v-if="testList.length > pageSize" v-model:current-page="page" :page-size="pageSize" :total="testList.length" layout="prev, pager, next" small />
      </div>
      <el-empty v-if="!testList.length" description="暂无测试案例" :image-size="60" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="showDialog" :title="isEditing?'编辑测试案例':'新增测试案例'" width="500px">
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="测试问题"><el-input v-model="form.query" /></el-form-item>
        <el-form-item label="期望答案"><el-input v-model="form.expectedAnswer" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="用于分类筛选" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog=false">取消</el-button><el-button type="primary" @click="handleSave">{{ isEditing?'保存':'创建' }}</el-button></template>
    </el-dialog>

    <!-- 录制弹窗：真实对话界面 -->
    <el-dialog v-model="showRecorder" title="录制测试案例 - 与机器人对话" width="560px" :close-on-click-modal="false">
      <div style="border:1px solid #ebeef5;border-radius:8px;padding:16px;height:360px;overflow-y:auto;background:#fafafa;margin-bottom:12px">
        <div v-if="!chatMessages.length" style="text-align:center;color:#c0c4cc;padding-top:120px;font-size:14px">
          在下方输入问题，与机器人对话后保存为测试案例
        </div>
        <div v-for="(msg, idx) in chatMessages" :key="idx" :style="{display:'flex',marginBottom:'12px',justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start'}">
          <div :style="{maxWidth:'80%',padding:'8px 14px',borderRadius:'12px',fontSize:'13px',lineHeight:'1.6',
            background: msg.role === 'user' ? '#409eff' : '#fff',
            color: msg.role === 'user' ? '#fff' : '#333',
            border: msg.role === 'user' ? 'none' : '1px solid #e0e0e0'}">
            <div style="font-size:11px;opacity:0.7;margin-bottom:4px">{{ msg.role === 'user' ? '我' : '机器人' }}</div>
            <div style="white-space:pre-wrap">{{ msg.content }}</div>
          </div>
        </div>
        <div v-if="recorderLoading" style="text-align:center;color:#909399;font-size:13px;padding:8px">机器人正在思考...</div>
      </div>
      <div style="display:flex;gap:8px">
        <el-input v-model="chatInput" placeholder="输入测试问题，按 Enter 发送" @keyup.enter="handleSendMessage" :disabled="recorderLoading" />
        <el-button type="primary" :loading="recorderLoading" @click="handleSendMessage" :disabled="!chatInput.trim()">发送</el-button>
      </div>
      <div v-if="recordedQA" style="margin-top:8px;font-size:12px;color:#67c23a">✓ 已获取回答，点击下方「保存为测试案例」完成录制</div>
      <template #footer>
        <el-button @click="showRecorder=false">取消</el-button>
        <el-button type="success" @click="handleSaveRecorded" :disabled="!recordedQA">保存为测试案例</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="showImportDialog" title="批量导入测试案例" width="520px">
      <p style="font-size:13px;color:#909399;margin-bottom:8px">每行一条，格式：名称,问题,期望答案（可选）</p>
      <el-input v-model="importText" type="textarea" :rows="8" placeholder="示例：
登录测试,用户能否正常登录？,用户应能使用账号密码成功登录
搜索测试,搜索功能是否正常？,系统应返回相关搜索结果" />
      <template #footer>
        <el-button @click="showImportDialog=false">取消</el-button>
        <el-button type="primary" @click="handleBatchImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom:$spacing-base; gap:8px; }
.section-title { font-size:15px; font-weight:600; }
.card-panel { background:var(--el-bg-color-overlay); border-radius:8px; padding:20px; border:1px solid var(--el-border-color-light); }
</style>
