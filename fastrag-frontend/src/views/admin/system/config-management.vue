<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const activeTab = ref('security')

// ============================================================================
// 1. 安全策略（列表式 CRUD）
// ============================================================================
const policyList = ref<any[]>([])
const showPolicyDialog = ref(false)
const policyForm = ref<any>({})
const editingPolicyId = ref<string | null>(null)

const policyTypeOptions = [
  { label: 'IP 黑名单', value: 'ip_blacklist' },
  { label: 'IP 白名单', value: 'ip_whitelist' },
  { label: '访问频率限制', value: 'rate_limit' },
  { label: '敏感内容过滤', value: 'sensitive_filter' },
  { label: '登录安全策略', value: 'login_policy' },
  { label: '数据脱敏规则', value: 'data_masking' },
]
const policyActionOptions = [
  { label: '允许', value: 'allow' },
  { label: '拒绝', value: 'block' },
  { label: '告警', value: 'alert' },
  { label: '记录日志', value: 'log' },
]

async function loadPolicies() {
  try {
    const res = await api.getSecurityPolicies()
    policyList.value = Array.isArray(res) ? res : (res as any)?.list || []
  } catch {
    policyList.value = []
  }
}

function handleAddPolicy() {
  editingPolicyId.value = null
  policyForm.value = { policyType: 'ip_blacklist', action: 'block', enabled: 1, priority: 0 }
  showPolicyDialog.value = true
}
function handleEditPolicy(row: any) {
  editingPolicyId.value = row.id
  policyForm.value = { ...row, enabled: row.enabled === 1 || row.enabled === true ? 1 : 0 }
  showPolicyDialog.value = true
}
async function handleSavePolicy() {
  if (!policyForm.value.name) { ElMessage.warning('请输入策略名称'); return }
  try {
    if (editingPolicyId.value) {
      await api.updateSecurityPolicy(editingPolicyId.value, policyForm.value)
      ElMessage.success('更新成功')
    } else {
      await api.createSecurityPolicy(policyForm.value)
      ElMessage.success('创建成功')
    }
    showPolicyDialog.value = false
    loadPolicies()
  } catch { ElMessage.error('操作失败') }
}
async function handleDeletePolicy(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除安全策略「${row.name}」？`, '提示', { type: 'warning' })
    await api.deleteSecurityPolicy(row.id)
    ElMessage.success('删除成功')
    loadPolicies()
  } catch {}
}
async function handleTogglePolicy(row: any) {
  try {
    await api.updateSecurityPolicy(row.id, { ...row, enabled: row.enabled ? 0 : 1 })
    row.enabled = row.enabled ? 0 : 1
    ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch {}
}

// ============================================================================
// 2. 发布策略（列表式 CRUD）
// ============================================================================
const strategyList = ref<any[]>([])
const showStrategyDialog = ref(false)
const strategyForm = ref<any>({})
const editingStrategyId = ref<string | null>(null)

const strategyTypeOptions = [
  { label: '自动发布', value: 'auto_publish' },
  { label: '定时发布', value: 'scheduled_publish' },
  { label: '审核后发布', value: 'review_required' },
  { label: '增量发布', value: 'incremental' },
  { label: '全量发布', value: 'full_publish' },
]

async function loadStrategies() {
  try {
    const res = await api.getPublishStrategies()
    strategyList.value = Array.isArray(res) ? res : (res as any)?.list || []
  } catch {
    strategyList.value = []
  }
}

function handleAddStrategy() {
  editingStrategyId.value = null
  strategyForm.value = { strategyType: 'review_required', enabled: 1, priority: 0, config: '{}' }
  showStrategyDialog.value = true
}
function handleEditStrategy(row: any) {
  editingStrategyId.value = row.id
  strategyForm.value = { ...row, enabled: row.enabled === 1 || row.enabled === true ? 1 : 0 }
  showStrategyDialog.value = true
}
async function handleSaveStrategy() {
  if (!strategyForm.value.name) { ElMessage.warning('请输入策略名称'); return }
  try {
    if (editingStrategyId.value) {
      await api.updatePublishStrategy(editingStrategyId.value, strategyForm.value)
      ElMessage.success('更新成功')
    } else {
      await api.createPublishStrategy(strategyForm.value)
      ElMessage.success('创建成功')
    }
    showStrategyDialog.value = false
    loadStrategies()
  } catch { ElMessage.error('操作失败') }
}
async function handleDeleteStrategy(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除发布策略「${row.name}」？`, '提示', { type: 'warning' })
    await api.deletePublishStrategy(row.id)
    ElMessage.success('删除成功')
    loadStrategies()
  } catch {}
}
async function handleToggleStrategy(row: any) {
  try {
    await api.updatePublishStrategy(row.id, { ...row, enabled: row.enabled ? 0 : 1 })
    row.enabled = row.enabled ? 0 : 1
    ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch {}
}

onMounted(() => { loadPolicies(); loadStrategies() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <!-- ===== Tab 1: 安全策略（列表式 CRUD） ===== -->
      <el-tab-pane label="安全策略" name="security">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">安全策略列表（共 {{ policyList.length }} 条）</div>
            <el-button type="primary" @click="handleAddPolicy">新增策略</el-button>
          </div>
          <el-table :data="policyList" stripe size="small">
            <el-table-column prop="name" label="策略名称" min-width="150" />
            <el-table-column label="策略类型" width="130">
              <template #default="{ row }">{{ policyTypeOptions.find(o => o.value === row.policyType)?.label || row.policyType }}</template>
            </el-table-column>
            <el-table-column prop="pattern" label="匹配规则" min-width="200" show-overflow-tooltip />
            <el-table-column label="执行动作" width="80">
              <template #default="{ row }">{{ policyActionOptions.find(o => o.value === row.action)?.label || row.action }}</template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="70" align="center" />
            <el-table-column label="启用" width="70">
              <template #default="{ row }"><el-switch :model-value="row.enabled === 1 || row.enabled === true" @change="handleTogglePolicy(row)" size="small" /></template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditPolicy(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeletePolicy(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!policyList.length" description="暂无安全策略，点击上方「新增策略」添加" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- ===== Tab 2: 发布策略（列表式 CRUD） ===== -->
      <el-tab-pane label="发布策略" name="publish">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">发布策略列表（共 {{ strategyList.length }} 条）</div>
            <el-button type="primary" @click="handleAddStrategy">新增策略</el-button>
          </div>
          <el-table :data="strategyList" stripe size="small">
            <el-table-column prop="name" label="策略名称" min-width="150" />
            <el-table-column label="策略类型" width="130">
              <template #default="{ row }">{{ strategyTypeOptions.find(o => o.value === row.strategyType)?.label || row.strategyType }}</template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column prop="priority" label="优先级" width="70" align="center" />
            <el-table-column label="启用" width="70">
              <template #default="{ row }"><el-switch :model-value="row.enabled === 1 || row.enabled === true" @change="handleToggleStrategy(row)" size="small" /></template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditStrategy(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteStrategy(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!strategyList.length" description="暂无发布策略，点击上方「新增策略」添加" :image-size="60" />
        </div>
      </el-tab-pane>

      <!-- ===== Tab 3: 配置项管理（保留原有） ===== -->
      <el-tab-pane label="配置项管理" name="config">
        <div class="card-panel">
          <div class="section-title">知识库系统配置项</div>
          <p class="desc-text">配置管理通过系统字典与 Config API 管理，详见「字典管理」页面</p>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 4: 审核流程选择（保留原有） ===== -->
      <el-tab-pane label="审核流程" name="review">
        <div class="card-panel">
          <div class="section-title">审核流程选择</div>
          <p class="desc-text">审核流程配置详见「知识审核」模块的流程设计页面</p>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 安全策略编辑弹窗 -->
    <el-dialog v-model="showPolicyDialog" :title="editingPolicyId ? '编辑安全策略' : '新增安全策略'" width="560px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="策略名称" required><el-input v-model="policyForm.name" placeholder="如：内网IP白名单" /></el-form-item>
        <el-form-item label="策略类型">
          <el-select v-model="policyForm.policyType" style="width:100%">
            <el-option v-for="o in policyTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配规则"><el-input v-model="policyForm.pattern" type="textarea" :rows="2" placeholder="IP 地址 / 正则表达式 / 关键词" /></el-form-item>
        <el-form-item label="执行动作">
          <el-select v-model="policyForm.action" style="width:100%">
            <el-option v-for="o in policyActionOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="policyForm.priority" :min="0" :max="999" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="policyForm.description" type="textarea" :rows="2" placeholder="策略说明" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="policyForm.enabled" :true-value="1" :false-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPolicyDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSavePolicy">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发布策略编辑弹窗 -->
    <el-dialog v-model="showStrategyDialog" :title="editingStrategyId ? '编辑发布策略' : '新增发布策略'" width="560px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="策略名称" required><el-input v-model="strategyForm.name" placeholder="如：自动发布策略" /></el-form-item>
        <el-form-item label="策略类型">
          <el-select v-model="strategyForm.strategyType" style="width:100%">
            <el-option v-for="o in strategyTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置参数"><el-input v-model="strategyForm.config" type="textarea" :rows="3" placeholder='JSON 格式，如 {"maxPublish":10,"timeWindow":"08:00-20:00"}' /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="strategyForm.priority" :min="0" :max="999" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="strategyForm.description" type="textarea" :rows="2" placeholder="策略说明" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="strategyForm.enabled" :true-value="1" :false-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showStrategyDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveStrategy">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.section-title { font-size: 15px; font-weight: 600; }
.desc-text { font-size: 13px; color: #909399; }
.card-panel { background: #fff; border-radius: 8px; padding: 16px; }
.page-container { padding: 16px; }
</style>
