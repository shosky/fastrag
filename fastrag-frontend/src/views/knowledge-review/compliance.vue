<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const dataList = ref<any[]>([])
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const ruleTypeOptions = [{ label: '内容检查', value: 'content' }, { label: '格式检查', value: 'format' }, { label: '关键词检查', value: 'keyword' }, { label: '长度检查', value: 'length' }]

// 分页
const page = ref(1)
const pageSize = 5

const kbList = ref<any[]>([])
const selectedKbId = ref('')
async function loadKbList() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbList.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
    if (kbList.value.length > 0 && !selectedKbId.value) selectedKbId.value = kbList.value[0].id
  } catch {
    kbList.value = [{ id: 'kb_sample', name: '示例知识库' }]
    selectedKbId.value = 'kb_sample'
  }
}

async function loadData() {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    dataList.value = (await api.getComplianceRules(selectedKbId.value)) as any[] || []
  } catch {
    dataList.value = []
  } finally {
    loading.value = false
  }
}
onMounted(async () => { await loadKbList(); loadData() })

function handleAdd() { editingId.value = null; formData.value = { ruleType: 'content', enabled: true }; dialogTitle.value = '新增合规规则'; showDialog.value = true }
function handleEdit(row: any) { editingId.value = row.id; formData.value = { name: row.ruleName || row.name, ruleType: row.ruleType, rule: row.pattern || row.rule, description: row.description, enabled: row.enabled === true || row.enabled === 1 }; dialogTitle.value = '编辑合规规则'; showDialog.value = true }
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除规则「${row.ruleName || row.name}」吗？`, '提示', { type: 'warning' })
    await api.deleteComplianceRule(selectedKbId.value, row.id); ElMessage.success('删除成功'); loadData()
  } catch {}
}
async function handleToggle(row: any) {
  try {
    const data = { ruleName: row.ruleName || row.name, ruleType: row.ruleType, pattern: row.pattern || row.rule, description: row.description, enabled: !row.enabled ? 1 : 0 }
    await api.updateComplianceRule(selectedKbId.value, row.id, data)
    row.enabled = !row.enabled; ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch {}
}
async function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入规则名称'); return }
  if (!formData.value.rule) { ElMessage.warning('请输入规则描述'); return }
  try {
    const data = { ruleName: formData.value.name, ruleType: formData.value.ruleType, pattern: formData.value.rule, description: formData.value.description, enabled: formData.value.enabled ? 1 : 0 }
    if (editingId.value) { await api.updateComplianceRule(selectedKbId.value, editingId.value, data); ElMessage.success('更新成功') }
    else { await api.createComplianceRule(selectedKbId.value, data); ElMessage.success('创建成功') }
    showDialog.value = false; loadData()
  } catch { ElMessage.error('操作失败') }
}

// ===== 执行合规检查 =====
const knowledgeList = ref<any[]>([])
const showCheckDialog = ref(false)
const checkResult = ref<any>(null)
const checkLoading = ref(false)
const selectedKnowledgeId = ref('')

async function loadKnowledgeList() {
  if (!selectedKbId.value) return
  try {
    const res: any = await api.getKnowledgeList(selectedKbId.value)
    knowledgeList.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
  } catch {
    knowledgeList.value = []
  }
}

async function handleExecuteCheck(row?: any) {
  // 如果传入了具体规则，则对该规则执行检查；否则打开全量检查弹窗
  if (row) {
    // 单规则快速检查：需要选择知识的弹窗
    await loadKnowledgeList()
    selectedKnowledgeId.value = ''
    checkResult.value = null
    showCheckDialog.value = true
    formData.value._singleRuleId = row.id
    formData.value._singleRuleName = row.ruleName || row.name
    return
  }
  // 全量检查
  await loadKnowledgeList()
  selectedKnowledgeId.value = ''
  checkResult.value = null
  formData.value._singleRuleId = null
  showCheckDialog.value = true
}

async function doCheck() {
  if (!selectedKnowledgeId.value) { ElMessage.warning('请选择要检查的知识'); return }
  checkLoading.value = true
  try {
    const ruleIds = formData.value._singleRuleId ? [formData.value._singleRuleId] : undefined
    const res: any = await api.executeComplianceCheck(selectedKbId.value, {
      knowledgeId: selectedKnowledgeId.value,
      ruleIds,
    })
    checkResult.value = res
    if (res?.results) {
      const total = res.results.length
      const passed = res.results.filter((r: any) => r.passed).length
      const failed = total - passed
      if (failed === 0) {
        ElMessage.success(`检查完成：${total}条规则全部通过`)
      } else {
        ElMessage.warning(`检查完成：${passed}条通过，${failed}条未通过`)
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '执行检查失败')
  } finally {
    checkLoading.value = false
  }
}

function getSeverityTag(severity: string) {
  const map: Record<string, string> = { high: 'danger', medium: 'warning', low: 'info' }
  return map[severity] || 'info'
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">合规性检查</div>
        <div style="display:flex;gap:12px;align-items:center">
          <el-select v-model="selectedKbId" @change="loadData" placeholder="选择知识库" style="width:200px">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button type="primary" @click="handleExecuteCheck()">执行检查</el-button>
          <el-button type="primary" @click="handleAdd">新增规则</el-button>
        </div>
      </div>
      <el-table :data="dataList.slice((page - 1) * pageSize, page * pageSize)" stripe>
        <el-table-column label="规则名称" min-width="150">
          <template #default="{ row }">{{ row.ruleName || row.name }}</template>
        </el-table-column>
        <el-table-column label="规则类型" width="100">
          <template #default="{ row }"><el-tag size="small">{{ ruleTypeOptions.find(o => o.value === row.ruleType)?.label || row.ruleType }}</el-tag></template>
        </el-table-column>
        <el-table-column label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="规则内容" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">{{ row.pattern || row.rule || '-' }}</template>
        </el-table-column>
        <el-table-column label="严重级别" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.severity" :type="getSeverityTag(row.severity)" size="small">{{ {high:'高',medium:'中',low:'低'}[row.severity] || row.severity }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80"><template #default="{ row }"><el-switch :model-value="row.enabled === true || row.enabled === 1" @change="handleToggle(row)" size="small" /></template></el-table-column>
        <el-table-column label="命中次数" width="100">
          <template #default="{ row }">{{ row.hitCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createdAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleExecuteCheck(row)">检查</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:center;margin-top:16px">
        <el-pagination
          v-if="dataList.length > pageSize"
          v-model:current-page="page"
          :page-size="pageSize"
          :total="dataList.length"
          layout="prev, pager, next"
          small
        />
      </div>
    </div>

    <!-- 新增/编辑规则弹窗 -->
    <el-dialog v-model="showDialog" :title="dialogTitle" width="600px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="规则名称" required><el-input v-model="formData.name" placeholder="请输入规则名称" /></el-form-item>
        <el-form-item label="规则类型" required>
          <el-select v-model="formData.ruleType" style="width: 100%"><el-option v-for="opt in ruleTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" /></el-select>
        </el-form-item>
        <el-form-item label="严重级别">
          <el-select v-model="formData.severity" style="width: 100%" placeholder="可选">
            <el-option label="高" value="high" />
            <el-option label="中" value="medium" />
            <el-option label="低" value="low" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="formData.description" placeholder="请输入规则描述" /></el-form-item>
        <el-form-item label="规则内容" required><el-input v-model="formData.rule" type="textarea" :rows="3" placeholder="关键词用逗号分隔；正则直接输入模式；长度格式如 min:100,max:5000；格式输入 html/markdown/plain" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="formData.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 合规检查执行弹窗 -->
    <el-dialog v-model="showCheckDialog" title="执行合规检查" width="700px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="选择知识">
          <el-select v-model="selectedKnowledgeId" placeholder="请选择要检查的知识条目" style="width:100%" filterable>
            <el-option v-for="k in knowledgeList" :key="k.id" :label="k.title" :value="k.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData._singleRuleId" label="检查规则">
          <el-tag type="primary">{{ formData._singleRuleName }}</el-tag>
        </el-form-item>
      </el-form>
      <div style="text-align:center;margin-bottom:16px">
        <el-button type="primary" :loading="checkLoading" @click="doCheck" :disabled="!selectedKnowledgeId">
          开始检查
        </el-button>
      </div>

      <!-- 检查结果 -->
      <div v-if="checkResult">
        <el-divider />
        <div class="section-title" style="margin-bottom:12px">检查结果</div>
        <el-table :data="checkResult.results || []" stripe size="small">
          <el-table-column prop="ruleName" label="规则名称" min-width="130" />
          <el-table-column label="规则类型" width="90">
            <template #default="{ row }">{{ ruleTypeOptions.find(o => o.value === row.ruleType)?.label || row.ruleType }}</template>
          </el-table-column>
          <el-table-column label="结果" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                {{ row.passed ? '通过' : '未通过' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="详情" min-width="250" show-overflow-tooltip>
            <template #default="{ row }">{{ row.details || '-' }}</template>
          </el-table-column>
        </el-table>

        <div style="margin-top:12px;font-size:12px;color:#909399;text-align:right">
          检查时间：{{ checkResult.executedAt || '-' }}
        </div>
      </div>

      <template #footer>
        <el-button @click="showCheckDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.section-title { font-size: 15px; font-weight: 600; }
.card-panel { background: #fff; border-radius: 8px; padding: 16px; }
.page-container { padding: 16px; }
</style>
