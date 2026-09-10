<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: { id: string } }>()
const appId = () => props.appInfo.id

const optList = ref<any[]>([])
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const formDefault = { name: '', type: 'prompt', description: '', config: '' }
const form = ref({ ...formDefault })
// 对话分析（数据对话优化）
const analyzeVisible = ref(false)
const analyzing = ref(false)
const analyzeResult = ref<any>(null)
// 优化效果测试（效果对话优化）
const testVisible = ref(false)
const testing = ref(false)
const testResult = ref<any>(null)

async function loadData() {
  try {
    const res: any = await api.getAppOptimizations(appId())
    optList.value = Array.isArray(res) ? res : []
  } catch { optList.value = [] }
}
function openAdd() { isEditing.value = false; editingId.value = ''; form.value = { ...formDefault }; showDialog.value = true }
function openEdit(row: any) { isEditing.value = true; editingId.value = row.id; form.value = { name: row.title || row.name, type: row.suggestionType || row.type, description: row.description, config: row.config || '' }; showDialog.value = true }
async function handleSave() {
  if (!form.value.name) { ElMessage.warning('请输入名称'); return }
  // 映射前后端字段名: 前端 name/type → 后端 title/suggestionType
  const data = { title: form.value.name, suggestionType: form.value.type, description: form.value.description, config: form.value.config }
  if (isEditing.value) { await api.updateAppOptimization(appId(), editingId.value, data); ElMessage.success('已更新') }
  else { await api.createAppOptimization(appId(), data); ElMessage.success('已创建') }
  showDialog.value = false; await loadData()
}
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '确认', { type: 'warning' }); await api.deleteAppOptimization(appId(), row.id); await loadData(); ElMessage.success('已删除') } catch {}
}
async function handleApply(row: any) { await api.applyAppOptimization(appId(), row.id); ElMessage.success('优化建议已应用'); await loadData() }
// 分析对话数据
async function handleAnalyze() {
  analyzing.value = true
  try { analyzeResult.value = await api.analyzeAppOptimization(appId()); analyzeVisible.value = true }
  catch { ElMessage.error('分析失败') } finally { analyzing.value = false }
}
// 根据分析结果一键生成优化建议（流程/参数优化）
async function genFromAnalyze() {
  const r = analyzeResult.value || {}
  const unmatched = Number(r.unmatchedRate || 0)
  const suggestions: any[] = []
  if (unmatched >= 0.3) suggestions.push({ title: '补充知识库命中策略', suggestionType: 'flow', description: `当前未匹配率 ${(unmatched * 100).toFixed(1)}% 偏高，建议优化召回流程并补充兜底话术`, impactScore: 85 })
  if (unmatched > 0 && unmatched < 0.3) suggestions.push({ title: '调整召回参数', suggestionType: 'param', description: `未匹配率 ${(unmatched * 100).toFixed(1)}%，建议微调相似度阈值与 top-k 提升命中`, impactScore: 70 })
  if (!suggestions.length) suggestions.push({ title: '优化 Prompt 表述', suggestionType: 'prompt', description: '整体对话指标良好，建议持续优化 Prompt 提升回答质量', impactScore: 60 })
  for (const s of suggestions) await api.createAppOptimization(appId(), s)
  analyzeVisible.value = false
  ElMessage.success(`已根据分析结果生成 ${suggestions.length} 条优化建议`)
  await loadData()
}
// 测试优化效果
async function handleTest(row: any) {
  testing.value = true; testVisible.value = true; testResult.value = null
  try { testResult.value = await api.testAppOptimization(appId(), row.id); await loadData() }
  catch { ElMessage.error('测试失败'); testVisible.value = false } finally { testing.value = false }
}
function pct(v: any) { const n = Number(v); return isNaN(n) ? '-' : (n * 100).toFixed(1) + '%' }
async function handleExport() {
  try {
    const blob = await api.exportAppOptimizations(appId()) as unknown as Blob
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `optimization_report_${Date.now()}.csv`; a.click(); URL.revokeObjectURL(url); ElMessage.success('优化报告已导出')
  } catch { ElMessage.error('导出失败') }
}
function getTypeLabel(type: string) {
  const map: Record<string, string> = { prompt: 'Prompt优化', param: '参数优化', flow: '流程优化' }
  return map[type] || type
}
onMounted(loadData)
</script>
<template>
  <div class="config-section">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">对话优化</div>
        <div style="display:flex;gap:8px">
          <el-button size="small" type="warning" plain :loading="analyzing" @click="handleAnalyze">分析对话</el-button>
          <el-button size="small" @click="handleExport">导出优化报告</el-button>
          <el-button type="primary" size="small" @click="openAdd">新增优化</el-button>
        </div>
      </div>
      <el-table :data="optList" stripe size="small" style="margin-top:12px">
        <el-table-column label="名称" min-width="120"><template #default="{row}">{{ row.title || row.name }}</template></el-table-column>
        <el-table-column label="类型" width="100"><template #default="{row}">{{ getTypeLabel(row.suggestionType || row.type) }}</template></el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80"><template #default="{row}"><el-tag :type="row.status==='applied'?'success':'info'" size="small">{{ row.status==='applied'?'已应用':'待应用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="handleTest(row)">测试效果</el-button>
            <el-button v-if="row.status!=='applied'" link type="success" size="small" @click="handleApply(row)">应用</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!optList.length" description="暂无优化建议" :image-size="60" />
    </div>
    <el-dialog v-model="showDialog" :title="isEditing?'编辑优化':'新增优化'" width="500px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="form.type" style="width:160px"><el-option label="Prompt优化" value="prompt" /><el-option label="参数优化" value="param" /><el-option label="流程优化" value="flow" /></el-select></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="配置"><el-input v-model="form.config" type="textarea" :rows="3" placeholder="JSON格式配置" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog=false">取消</el-button><el-button type="primary" @click="handleSave">{{ isEditing?'保存':'创建' }}</el-button></template>
    </el-dialog>
    <!-- 分析对话结果 -->
    <el-dialog v-model="analyzeVisible" title="对话数据分析" width="520px">
      <el-descriptions v-if="analyzeResult" :column="2" border size="small">
        <el-descriptions-item label="对话总数">{{ analyzeResult.totalDialogs ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="发布次数">{{ analyzeResult.totalPublish ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="平均轮次">{{ analyzeResult.avgTurns ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="平均满意度">{{ analyzeResult.avgSatisfaction ?? '-' }} / 5</el-descriptions-item>
        <el-descriptions-item label="未匹配率" :span="2">{{ pct(analyzeResult.unmatchedRate) }}</el-descriptions-item>
      </el-descriptions>
      <el-alert type="info" :closable="false" show-icon style="margin-top:12px"
        title="可根据以上分析结果一键生成优化建议（流程/参数/Prompt 优化）" />
      <template #footer>
        <el-button @click="analyzeVisible=false">关闭</el-button>
        <el-button type="primary" @click="genFromAnalyze">生成优化建议</el-button>
      </template>
    </el-dialog>
    <!-- 优化效果测试对比 -->
    <el-dialog v-model="testVisible" title="优化效果测试" width="560px">
      <div v-loading="testing" style="min-height:120px">
        <template v-if="testResult">
          <el-alert type="success" :closable="false" show-icon style="margin-bottom:12px"
            :title="`「${testResult.title}」效果测试完成（影响分 ${testResult.impactScore ?? '-'}）`" />
          <el-table :data="[
            { metric: '未匹配率', before: pct(testResult.before?.unmatchedRate), after: pct(testResult.after?.unmatchedRate) },
            { metric: '平均满意度', before: (testResult.before?.avgSatisfaction ?? '-') + ' / 5', after: (testResult.after?.avgSatisfaction ?? '-') + ' / 5' },
            { metric: '平均响应耗时(ms)', before: testResult.before?.avgResponseMs ?? '-', after: testResult.after?.avgResponseMs ?? '-' }
          ]" size="small" border>
            <el-table-column prop="metric" label="指标" width="160" />
            <el-table-column prop="before" label="优化前" />
            <el-table-column prop="after" label="优化后" />
          </el-table>
        </template>
      </div>
      <template #footer><el-button type="primary" @click="testVisible=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:$spacing-base; gap:8px; }
.section-title { font-size:15px; font-weight:600; }
.card-panel { background:var(--el-bg-color-overlay); border-radius:8px; padding:20px; border:1px solid var(--el-border-color-light); }
</style>
