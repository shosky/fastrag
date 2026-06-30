<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const kbId = (route.params.id as string) || 'kb_sample'
const activeTab = ref('association')
const loading = ref(false)

// 搜索联想
const DIMENSION_OPTIONS = [
  { label: '主题词/别名', value: 'content' },
  { label: '搜索规则', value: 'rule' },
  { label: '附件', value: 'attachment' },
  { label: '发布时间', value: 'publishTime' },
  { label: '知识类型', value: 'knowledgeType' },
] as const
const DIMENSION_LABEL_MAP: Record<string, string> = Object.fromEntries(DIMENSION_OPTIONS.map(d => [d.value, d.label]))
const assocList = ref<any[]>([])
const assocTotal = ref(0)
const assocPage = ref(1)
const assocPageSize = ref(5)
const assocQuery = ref({ dimension: '', keyword: '' })
async function loadAssoc() {
  loading.value = true
  try {
    const res: any = await api.getSearchAssociations(kbId, { ...assocQuery.value, page: assocPage.value, pageSize: assocPageSize.value })
    assocList.value = res?.list || []
    assocTotal.value = res?.total || assocList.value.length
  } finally { loading.value = false }
}
function handleAssocPageChange(page: number) { assocPage.value = page; loadAssoc() }
const showAssocDialog = ref(false)
const assocForm = ref({ id: '', dimension: 'content', name: '', description: '', pattern: '', suggestions: '', priority: 0 })
function handleAddAssoc() { assocForm.value = { id: '', dimension: 'content', name: '', description: '', pattern: '', suggestions: '', priority: 0 }; showAssocDialog.value = true }
function handleEditAssoc(row: any) { assocForm.value = { ...row }; showAssocDialog.value = true }
async function handleSaveAssoc() {
  if (!assocForm.value.name) { ElMessage.warning('请输入名称'); return }
  if (assocForm.value.id) await api.updateSearchAssociation(kbId, assocForm.value.id, assocForm.value)
  else await api.createSearchAssociation(kbId, assocForm.value)
  showAssocDialog.value = false; await loadAssoc(); ElMessage.success('保存成功')
}
async function handleDeleteAssoc(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' })
    await api.deleteSearchAssociation(kbId, row.id); await loadAssoc(); ElMessage.success('删除成功') } catch {}
}

// 联想效果验证
const testQuery = ref('')
const testDimension = ref('') // 空=全部维度（多条件组合），指定值=单维度验证
const testResult = ref<any>(null)
// 按维度分组，每个维度合并所有匹配规则的 suggestions
const groupedTestResult = computed(() => {
  if (!testResult.value?.associations?.length) return []
  const map = new Map<string, string[]>()
  for (const a of testResult.value.associations) {
    const dim = a.dimension || 'content'
    const list = map.get(dim) || []
    if (a.suggestions?.length) list.push(...a.suggestions)
    map.set(dim, list)
  }
  return Array.from(map.entries()).map(([dimension, suggestions]) => ({ dimension, suggestions }))
})
const DIMENSION_TAG_TYPES: Record<string, string> = { content: '', rule: 'warning', attachment: 'success', publishTime: 'info', knowledgeType: 'danger' }
function dimensionTagType(dim: string) { return DIMENSION_TAG_TYPES[dim] || '' }
async function handleTest() {
  if (!testQuery.value) { ElMessage.warning('请输入搜索词'); return }
  testResult.value = await api.searchAssociations(kbId, testQuery.value, testDimension.value || undefined)
}

// 判断（按维度校验联想规则）
const showJudgeDialog = ref(false)
const judgeForm = ref({ dimension: 'content', query: '', targetText: '' })
const judgeResult = ref<any>(null)
const judgeLoading = ref(false)
function handleShowJudge(row: any) {
  judgeForm.value = { dimension: row.dimension || 'content', query: '', targetText: row.name || '' }
  judgeResult.value = null
  showJudgeDialog.value = true
}
async function handleJudge() {
  if (!judgeForm.value.query) { ElMessage.warning('请输入测试查询内容'); return }
  judgeLoading.value = true
  try {
    judgeResult.value = await api.judgeSearchAssociation(kbId, judgeForm.value.dimension, judgeForm.value.query, judgeForm.value.targetText)
  } catch {
    ElMessage.error('判断请求失败')
  } finally {
    judgeLoading.value = false
  }
}

// 自动纠错
const correctionList = ref<any[]>([])
async function loadCorrections() { correctionList.value = ((await api.getAutoCorrections(kbId)) as any) || [] }
const showCorrectionDialog = ref(false)
const correctionForm = ref({ id: '', wrongText: '', correctText: '', matchType: 'exact', priority: 0 })
function handleAddCorrection() { correctionForm.value = { id: '', wrongText: '', correctText: '', matchType: 'exact', priority: 0 }; showCorrectionDialog.value = true }
function handleEditCorrection(row: any) { correctionForm.value = { ...row }; showCorrectionDialog.value = true }
async function handleSaveCorrection() {
  if (!correctionForm.value.wrongText || !correctionForm.value.correctText) { ElMessage.warning('请输入错误和正确文本'); return }
  if (correctionForm.value.id) await api.updateAutoCorrection(kbId, correctionForm.value.id, correctionForm.value)
  else await api.createAutoCorrection(kbId, correctionForm.value)
  showCorrectionDialog.value = false; await loadCorrections(); ElMessage.success('保存成功')
}
async function handleDeleteCorrection(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' })
    await api.deleteAutoCorrection(kbId, row.id); await loadCorrections(); ElMessage.success('删除成功') } catch {}
}

// ===========================================================================
// 知识更新提醒
// ===========================================================================
const updateLogs = ref<any[]>([])
const updateLogTotal = ref(0)
const updateLogPage = ref(1)
const updateLogPageSize = ref(20)
const updateRemindConfig = ref({
  enabled: false,
  notifyChannels: ['in_app'],
  checkInterval: 60,
})
const showRemindConfigDialog = ref(false)
const unreadUpdateCount = ref(0)

async function loadUpdateLogs() {
  try {
    const res: any = await api.getKnowledgeUpdateLogs(kbId, updateLogPage.value, updateLogPageSize.value)
    const list = Array.isArray(res) ? res : (res?.list || [])
    // 保持已有 read 状态，新数据默认为未读
    const readMap = new Map(updateLogs.value.filter(l => l.read).map(l => [l.id, true]))
    updateLogs.value = list.map((item: any) => ({ ...item, read: readMap.has(item.id) || false }))
    updateLogTotal.value = res?.total || list.length
  } catch {
    updateLogs.value = []
    updateLogTotal.value = 0
  }
}

async function loadUpdateRemindConfig() {
  try {
    const r: any = await api.getKbUpdateRemind(kbId)
    if (r) Object.assign(updateRemindConfig.value, r)
  } catch { /* ignore */ }
}

// 计算未读更新数（取最近7天且用户未确认的更新）
const unreadCount = computed(() => {
  return updateLogs.value.filter((log: any) => {
    if (!log.read) return true
    return false
  }).length
})

async function handleShowRemindConfig() {
  await loadUpdateRemindConfig()
  showRemindConfigDialog.value = true
}

async function handleSaveRemindConfig() {
  try {
    if (updateRemindConfig.value.enabled) {
      await api.saveUpdateRemind({ kbId, ...updateRemindConfig.value })
    } else {
      await api.saveUpdateRemind({ kbId, enabled: false })
    }
    ElMessage.success('更新提醒配置已保存')
    showRemindConfigDialog.value = false
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handleMarkAllRead() {
  try {
    // 批量标记所有未读更新日志为已读
    const unreadIds = updateLogs.value.filter(l => !l.read).map(l => l.id)
    for (const id of unreadIds) {
      await api.markUpdateLogRead(kbId, id)
    }
    await loadUpdateLogs()
    unreadUpdateCount.value = 0
    ElMessage.success('已全部标为已读')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleMarkRead(row: any) {
  if (row.read) return
  try {
    await api.markUpdateLogRead(kbId, row.id)
    row.read = true
    unreadUpdateCount.value = Math.max(0, unreadUpdateCount.value - 1)
  } catch { /* ignore */ }
}

function handleUpdatePageChange(page: number) {
  updateLogPage.value = page
  loadUpdateLogs()
}

onMounted(() => {
  loadAssoc()
  loadCorrections()
  loadUpdateLogs()
  loadUpdateRemindConfig()
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="搜索联想" name="association">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">查询联想规则</div><el-button type="primary" @click="handleAddAssoc">新增联想</el-button></div>
          <div class="filter-bar">
            <el-select v-model="assocQuery.dimension" placeholder="维度" clearable style="width:140px" @change="loadAssoc">
              <el-option v-for="d in DIMENSION_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
            <el-input v-model="assocQuery.keyword" placeholder="名称搜索" clearable style="width:180px" @keyup.enter="loadAssoc" />
            <el-button type="primary" @click="loadAssoc">查询</el-button>
          </div>
          <el-table :data="assocList" stripe>
            <el-table-column prop="name" label="规则名称" show-overflow-tooltip />
            <el-table-column prop="dimension" label="维度" width="110"><template #default="{ row }">{{ DIMENSION_LABEL_MAP[row.dimension] || row.dimension }}</template></el-table-column>
            <el-table-column prop="pattern" label="匹配模式" show-overflow-tooltip />
            <el-table-column prop="priority" label="优先级" width="80" />
            <el-table-column prop="enabled" label="启用" width="70"><template #default="{ row }"><el-tag :type="row.enabled?'success':'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="180"><template #default="{ row }"><el-button link type="primary" size="small" @click="handleEditAssoc(row)">编辑</el-button><el-button link type="primary" size="small" @click="handleShowJudge(row)">判断</el-button><el-button link type="danger" size="small" @click="handleDeleteAssoc(row)">删除</el-button></template></el-table-column>
          </el-table>
          <div class="table-footer" v-if="assocTotal > assocPageSize">
            <el-pagination
              v-model:current-page="assocPage"
              v-model:page-size="assocPageSize"
              :total="assocTotal"
              :page-sizes="[5, 10, 20]"
              layout="total, sizes, prev, pager, next"
              @current-change="handleAssocPageChange"
              @size-change="handleAssocPageChange"
            />
          </div>
        </div>
        <div class="card-panel" style="margin-top:16px">
          <div class="section-title">联想效果验证</div>
          <div class="filter-bar">
            <el-input v-model="testQuery" placeholder="模拟用户输入的搜索词，如：宽带、专线、云电脑、2026" style="width:300px" @keyup.enter="handleTest" />
            <el-select v-model="testDimension" placeholder="验证维度" clearable style="width:140px">
              <el-option label="全部维度" value="" />
              <el-option v-for="d in DIMENSION_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
            <el-button type="primary" @click="handleTest">验证联想效果</el-button>
          </div>
          <el-empty v-if="testQuery && testResult && (!testResult.associations?.length) && (!testResult.corrections?.length)" description="未匹配到联想规则" :image-size="60" />
          <div v-if="testResult && (testResult.associations?.length || testResult.corrections?.length)" class="result-box">
            <!-- 纠错提示 -->
            <div v-if="testResult.corrections?.length" style="margin-bottom:12px;padding:8px 12px;background:#fdf6ec;border-radius:6px;border:1px solid #faecd8">
              <span style="font-size:13px;color:#e6a23c">💡 是否要搜索：</span>
              <span v-for="(c,i) in testResult.corrections" :key="'c'+i" style="margin-right:12px;font-weight:600;color:#e6a23c">{{ c.corrected }}</span>
              <span style="font-size:12px;color:#909399">（原输入：{{ testResult.corrections.map((c: any) => c.original).join('、') }}）</span>
            </div>
            <!-- 按维度分组展示联想建议 -->
            <div v-for="group in groupedTestResult" :key="group.dimension" style="margin-bottom:12px">
              <div style="font-size:13px;color:#606266;margin-bottom:6px">
                <el-tag size="small" :type="dimensionTagType(group.dimension)">{{ DIMENSION_LABEL_MAP[group.dimension] || group.dimension }}</el-tag>
                <span style="margin-left:8px">为您推荐以下内容：</span>
              </div>
              <div style="display:flex;flex-wrap:wrap;gap:6px">
                <el-tag v-for="(s, si) in group.suggestions" :key="si" effect="plain" style="max-width:300px" show-overflow-tooltip>{{ s }}</el-tag>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="自动纠错" name="correction">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">纠错规则管理</div><el-button type="primary" @click="handleAddCorrection">新增规则</el-button></div>
          <el-table :data="correctionList" stripe>
            <el-table-column prop="wrongText" label="错误文本" width="160" />
            <el-table-column prop="correctText" label="正确文本" width="160" />
            <el-table-column prop="matchType" label="匹配类型" width="100" />
            <el-table-column prop="priority" label="优先级" width="80" />
            <el-table-column prop="hitCount" label="命中次数" width="90" />
            <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" size="small" @click="handleEditCorrection(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDeleteCorrection(row)">删除</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 知识更新提醒 -->
      <el-tab-pane name="update-remind">
        <template #label>
          <span>
            更新提醒
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" type="danger" style="margin-left:4px" />
          </span>
        </template>
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">
              知识更新日志
              <el-tag v-if="updateRemindConfig.enabled" type="success" size="small" style="margin-left:8px">提醒已开启</el-tag>
              <el-tag v-else type="info" size="small" style="margin-left:8px">提醒已关闭</el-tag>
            </div>
            <div style="display:flex;gap:8px">
              <el-button size="small" @click="handleShowRemindConfig">更新提醒设置</el-button>
              <el-button size="small" @click="handleMarkAllRead" :disabled="unreadCount === 0">全部标为已读</el-button>
              <el-button size="small" @click="loadUpdateLogs">刷新</el-button>
            </div>
          </div>
          <el-table :data="updateLogs" stripe size="small" @row-click="handleMarkRead">
            <el-table-column label="状态" width="60">
              <template #default="{ row }">
                <el-badge v-if="!row.read" is-dot type="danger" />
              </template>
            </el-table-column>
            <el-table-column prop="updateType" label="更新类型" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="row.updateType === 'file_added' ? 'success' : row.updateType === 'file_removed' ? 'danger' : 'warning'">
                  {{ { file_added: '新增文件', file_removed: '删除文件', file_updated: '更新文件', chunk_added: '新增切片', chunk_removed: '删除切片', chunk_updated: '更新切片', config_changed: '配置变更' }[row.updateType as string] || row.updateType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="target" label="更新目标" min-width="200" show-overflow-tooltip />
            <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="timestamp" label="更新时间" width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.timestamp || row.createdAt || '-' }}</template>
            </el-table-column>
          </el-table>
          <div class="table-footer" v-if="updateLogTotal > updateLogPageSize">
            <el-pagination
              v-model:current-page="updateLogPage"
              v-model:page-size="updateLogPageSize"
              :total="updateLogTotal"
              layout="total, prev, pager, next"
              @current-change="handleUpdatePageChange"
            />
          </div>
          <el-empty v-if="!updateLogs.length" description="暂无更新记录" :image-size="60" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showAssocDialog" :title="assocForm.id?'编辑联想':'新增联想'" width="520px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="assocForm.name" /></el-form-item>
        <el-form-item label="维度">
          <el-select v-model="assocForm.dimension" style="width:180px"><el-option v-for="d in DIMENSION_OPTIONS" :key="d.value" :label="d.label" :value="d.value" /></el-select>
        </el-form-item>
        <el-form-item label="匹配模式"><el-input v-model="assocForm.pattern" placeholder="正则表达式，如：退款|退货" /></el-form-item>
        <el-form-item label="联想建议"><el-input v-model="assocForm.suggestions" type="textarea" :rows="3" placeholder='JSON数组' /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="assocForm.priority" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showAssocDialog=false">取消</el-button><el-button type="primary" @click="handleSaveAssoc">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="showCorrectionDialog" :title="correctionForm.id?'编辑纠错':'新增纠错'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="错误文本" required><el-input v-model="correctionForm.wrongText" /></el-form-item>
        <el-form-item label="正确文本" required><el-input v-model="correctionForm.correctText" /></el-form-item>
        <el-form-item label="匹配类型"><el-select v-model="correctionForm.matchType" style="width:140px"><el-option label="精确" value="exact" /><el-option label="模糊" value="fuzzy" /><el-option label="正则" value="regex" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="correctionForm.priority" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCorrectionDialog=false">取消</el-button><el-button type="primary" @click="handleSaveCorrection">保存</el-button></template>
    </el-dialog>

    <!-- 判断对话框 -->
    <el-dialog v-model="showJudgeDialog" title="搜索联想判断" width="500px">
      <el-form label-width="110px">
        <el-form-item label="判断维度">
          <el-select v-model="judgeForm.dimension" style="width:180px">
            <el-option v-for="d in DIMENSION_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="测试查询" required>
          <el-input v-model="judgeForm.query" placeholder="输入用户查询内容" />
        </el-form-item>
        <el-form-item label="目标文本">
          <el-input v-model="judgeForm.targetText" placeholder="可选，预期匹配的规则名称" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleJudge" :loading="judgeLoading">执行判断</el-button>
        </el-form-item>
      </el-form>
      <div v-if="judgeResult" class="judge-result">
        <p><b>判断结果：</b>
          <el-tag :type="judgeResult.matched ? 'success' : 'info'" size="small">
            {{ judgeResult.matched ? '匹配' : '不匹配' }}
          </el-tag>
        </p>
        <p><b>置信度：</b>{{ (judgeResult.confidence * 100).toFixed(0) }}%</p>
        <p v-if="judgeResult.matchedRule"><b>匹配规则：</b>{{ judgeResult.matchedRule.name }}（{{ judgeResult.matchedRule.dimension }}）</p>
      </div>
    </el-dialog>

    <!-- 更新提醒设置对话框 -->
    <el-dialog v-model="showRemindConfigDialog" title="知识更新提醒设置" width="480px">
      <el-form label-width="120px">
        <el-form-item label="启用更新提醒">
          <el-switch v-model="updateRemindConfig.enabled" />
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-checkbox-group v-model="updateRemindConfig.notifyChannels">
            <el-checkbox label="in_app">应用内通知</el-checkbox>
            <el-checkbox label="email">邮件通知</el-checkbox>
            <el-checkbox label="sms">短信通知</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="检查间隔(分钟)">
          <el-input-number v-model="updateRemindConfig.checkInterval" :min="10" :max="1440" :step="10" style="width:160px" />
          <div style="font-size:12px;color:#909399;margin-top:4px">最短10分钟，最长1440分钟（24小时）</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSaveRemindConfig">保存设置</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.result-box { margin-top: $spacing-base; padding: $spacing-base; background: $bg-white; border-radius: $radius-base; p { margin: 4px 0; } }
.judge-result { margin-top: $spacing-base; padding: $spacing-base; background: #fafafa; border-radius: $radius-base; border: 1px solid $border-lighter; p { margin: 6px 0; } }
</style>
