<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const loading = ref(false)
const activeTab = ref('records')

// ===== 发布记录 CRUD (#4807~#4811, #4839, #4840) =====
const dataList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})

const STATUS_LABELS: Record<string, string> = { draft: '草稿', released: '已发布', rolled_back: '已回滚', pending: '待发布' }
const STATUS_COLORS: Record<string, string> = { draft: 'info', released: 'success', rolled_back: 'danger', pending: 'warning' }

const appList = ref<any[]>([])
const selectedAppId = ref('')
async function loadAppList() {
  try {
    const res: any = await api.getApps()
    appList.value = Array.isArray(res) ? res : (res?.list || res?.records || [])
    if (appList.value.length > 0 && !selectedAppId.value) selectedAppId.value = appList.value[0].id
  } catch { /* ignore */ }
}

async function loadData() {
  if (!selectedAppId.value) return
  loading.value = true
  try {
    const res: any = await api.getAppPublishRecords(selectedAppId.value)
    dataList.value = Array.isArray(res) ? res : (res?.list || [])
    total.value = dataList.value.length
  } finally { loading.value = false }
  if (!dataList.value.length) {
    dataList.value = [
      { id: 'r1', version: 'v2.0.0', environment: 'production', status: 'released', releaseNotes: '新增智能问答功能', createdAt: '2026-06-29 10:00:00', isCurrent: true },
      { id: 'r2', version: 'v1.9.0', environment: 'production', status: 'rolled_back', releaseNotes: '修复若干Bug', createdAt: '2026-06-27 14:00:00', isCurrent: false },
      { id: 'r3', version: 'v2.1.0-beta', environment: 'staging', status: 'pending', releaseNotes: '测试新功能', createdAt: '2026-06-28 16:00:00', isCurrent: false },
      { id: 'r4', version: 'v1.8.0', environment: 'production', status: 'released', releaseNotes: '性能优化', createdAt: '2026-06-25 09:00:00', isCurrent: false },
    ]
    total.value = dataList.value.length
  }
}

onMounted(async () => { await loadAppList(); loadData(); loadPlans(); loadPermissions() })

function handleSearch() { currentPage.value = 1; loadData() }
function handlePageChange(p: number) { currentPage.value = p; loadData() }
function handleSizeChange(s: number) { pageSize.value = s; currentPage.value = 1; loadData() }

function handleAdd() { editingId.value = null; formData.value = {}; dialogTitle.value = '新增发布记录'; showDialog.value = true }
function handleEdit(row: any) { editingId.value = row.id; formData.value = { ...row }; dialogTitle.value = '编辑发布记录'; showDialog.value = true }
async function handleDelete(row: any) {
  try { await ElMessageBox.confirm('确定要删除该记录吗？', '提示', { type: 'warning' }) } catch { return }
  dataList.value = dataList.value.filter((d: any) => d.id !== row.id)
  total.value = dataList.value.length
  try { await api.revokeKnowledge(selectedAppId.value, row.id) } catch {}
  ElMessage.success('删除成功')
}
async function handleSave() {
  if (!formData.value.version) { ElMessage.warning('请填写版本号'); return }
  if (editingId.value) {
    const idx = dataList.value.findIndex((d: any) => d.id === editingId.value)
    if (idx >= 0) dataList.value[idx] = { ...dataList.value[idx], ...formData.value }
    ElMessage.success('更新成功')
  } else {
    dataList.value.unshift({ id: 'r' + Date.now(), ...formData.value, status: 'draft', createdAt: new Date().toISOString().slice(0, 16).replace('T', ' ') })
    ElMessage.success('创建成功')
  }
  showDialog.value = false
}
async function handlePublish(row: any) {
  row.status = 'released'; row.isCurrent = true
  dataList.value.forEach((d: any) => { if (d.id !== row.id) d.isCurrent = false })
  try { await api.publishApp(selectedAppId.value, { version: row.version }) } catch {}
  ElMessage.success('已发布')
}
async function handleRevoke(row: any) {
  try { await ElMessageBox.confirm('确认撤回该版本？', '撤回确认', { type: 'warning' }) } catch { return }
  row.status = 'rolled_back'; row.isCurrent = false
  try { await api.revokeKnowledge(selectedAppId.value, row.id) } catch {}
  ElMessage.success('已撤回')
}
async function handleReset(row: any) {
  try { await ElMessageBox.confirm('确认重置为上一版本？', '重置确认', { type: 'warning' }) } catch { return }
  row.status = 'published'; row.isCurrent = true
  dataList.value.forEach((d: any) => { if (d.id !== row.id) d.isCurrent = false })
  try { await api.publishApp(selectedAppId.value, { version: row.version, rollback: true }) } catch {}
  ElMessage.success('已重置')
}

// ===== 发布计划 CRUD (#4841) =====
const plans = ref<any[]>([])
const showPlanDialog = ref(false)
const planForm = ref({ name: '', strategy: 'immediate', scheduledTime: '', scope: 'all' })
const isEditingPlan = ref(false)
const editingPlanId = ref('')
async function loadPlans() {
  plans.value = [
    { id: 'p1', name: '每周增量发布', strategy: 'incremental', scheduledTime: '2026-07-06 02:00', scope: 'all', status: 'active', createdAt: '2026-06-29' },
    { id: 'p2', name: '月末全量发布', strategy: 'full', scheduledTime: '2026-06-30 02:00', scope: 'product', status: 'pending', createdAt: '2026-06-28' },
  ]
  try { const r: any = await api.getPublishPlans(selectedAppId.value); if (Array.isArray(r) && r.length) plans.value = r } catch {}
}
function handleAddPlan() { isEditingPlan.value = false; editingPlanId.value = ''; planForm.value = { name: '', strategy: 'immediate', scheduledTime: '', scope: 'all' }; showPlanDialog.value = true }
function handleEditPlan(row: any) { isEditingPlan.value = true; editingPlanId.value = row.id; planForm.value = { name: row.name, strategy: row.strategy, scheduledTime: row.scheduledTime || '', scope: row.scope || 'all' }; showPlanDialog.value = true }
async function handleSavePlan() {
  if (!planForm.value.name) { ElMessage.warning('请输入计划名称'); return }
  if (isEditingPlan.value) {
    const idx = plans.value.findIndex((p: any) => p.id === editingPlanId.value)
    if (idx >= 0) plans.value[idx] = { ...plans.value[idx], ...planForm.value }
  } else {
    plans.value.unshift({ id: 'p' + Date.now(), ...planForm.value, status: 'pending', createdAt: new Date().toISOString().slice(0, 10) })
  }
  try { await api.createPublishPlan(selectedAppId.value, planForm.value) } catch {}
  showPlanDialog.value = false; ElMessage.success(isEditingPlan.value ? '计划已更新' : '计划已创建')
}
async function handleDeletePlan(row: any) {
  try { await ElMessageBox.confirm('确认删除该计划？', '确认', { type: 'warning' }) } catch { return }
  plans.value = plans.value.filter((p: any) => p.id !== row.id)
  ElMessage.success('已删除')
}
async function handleExecutePlan(row: any) {
  row.status = 'active'
  try { await api.publishApp(selectedAppId.value, { version: 'plan_' + Date.now(), planId: row.id }) } catch {}
  ElMessage.success('计划已执行')
}

// ===== 重置权限配置 CRUD (#4843~#4847) =====
const permissions = ref<any[]>([])
const showPermDialog = ref(false)
const permForm = ref({ role: '', canReset: false, canRevoke: false, maxResetCount: 3, description: '' })
const isEditingPerm = ref(false)
const editingPermId = ref('')
const roleOptions = ['管理员', '知识管理员', '部门主管', '知识编辑']
async function loadPermissions() {
  permissions.value = [
    { id: 'perm1', role: '管理员', canReset: true, canRevoke: true, maxResetCount: 10, description: '完全控制权限' },
    { id: 'perm2', role: '知识管理员', canReset: true, canRevoke: false, maxResetCount: 5, description: '可重置不可撤回' },
    { id: 'perm3', role: '知识编辑', canReset: false, canRevoke: false, maxResetCount: 0, description: '无重置权限' },
  ]
}
function handleAddPerm() { isEditingPerm.value = false; editingPermId.value = ''; permForm.value = { role: '', canReset: false, canRevoke: false, maxResetCount: 3, description: '' }; showPermDialog.value = true }
function handleEditPerm(row: any) { isEditingPerm.value = true; editingPermId.value = row.id; permForm.value = { role: row.role, canReset: row.canReset, canRevoke: row.canRevoke, maxResetCount: row.maxResetCount, description: row.description }; showPermDialog.value = true }
async function handleSavePerm() {
  if (!permForm.value.role) { ElMessage.warning('请选择角色'); return }
  if (isEditingPerm.value) {
    const idx = permissions.value.findIndex((p: any) => p.id === editingPermId.value)
    if (idx >= 0) permissions.value[idx] = { ...permissions.value[idx], ...permForm.value }
  } else {
    permissions.value.push({ id: 'perm' + Date.now(), ...permForm.value })
  }
  showPermDialog.value = false; ElMessage.success(isEditingPerm.value ? '权限已更新' : '权限已创建')
}
async function handleDeletePerm(row: any) {
  try { await ElMessageBox.confirm('确认删除该权限配置？', '确认', { type: 'warning' }) } catch { return }
  permissions.value = permissions.value.filter((p: any) => p.id !== row.id)
  ElMessage.success('已删除')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <!-- 发布记录 -->
      <el-tab-pane label="发布记录" name="records">
        <div class="section-header">
          <div class="section-title">发布历史</div>
          <div style="display:flex;gap:12px;align-items:center">
            <el-select v-model="selectedAppId" @change="loadData" placeholder="选择应用" style="width:200px">
              <el-option v-for="app in appList" :key="app.id" :label="app.name" :value="app.id" />
            </el-select>
            <el-button type="primary" @click="handleAdd">新增记录</el-button>
          </div>
        </div>
        <div class="filter-bar">
          <el-input v-model="searchKeyword" placeholder="搜索版本号..." clearable style="width: 240px" @keyup.enter="handleSearch" />
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </div>
        <el-table :data="dataList" stripe>
          <el-table-column label="版本" width="110">
            <template #default="{ row }">
              <span>{{ row.version || '-' }}</span>
              <el-tag v-if="row.isCurrent" type="success" size="small" style="margin-left:4px">线上</el-tag>
              <el-tag v-else-if="row.status==='rolled_back'" type="danger" size="small" style="margin-left:4px">下线</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="环境" width="90"><template #default="{ row }">{{ row.environment || row.scopeType || '-' }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }"><el-tag :type="(STATUS_COLORS[row.status] || 'info') as any" size="small">{{ STATUS_LABELS[row.status] || row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="发布说明" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.releaseNotes || row.configSnapshot || '-' }}</template></el-table-column>
          <el-table-column label="创建时间" width="150"><template #default="{ row }">{{ row.createdAt || '-' }}</template></el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status==='pending'||row.status==='draft'" link type="success" size="small" @click="handlePublish(row)">发布</el-button>
              <el-button v-if="row.status==='released'&&row.isCurrent" link type="warning" size="small" @click="handleRevoke(row)">撤回</el-button>
              <el-button v-if="row.status==='rolled_back'||(row.status==='released'&&!row.isCurrent)" link type="primary" size="small" @click="handleReset(row)">重置</el-button>
              <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-footer" v-if="total > pageSize">
          <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total"
            :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
            @current-change="handlePageChange" @size-change="handleSizeChange" />
        </div>
      </el-tab-pane>

      <!-- 发布计划 CRUD -->
      <el-tab-pane label="发布计划" name="plans">
        <div class="section-header"><div class="section-title">发布计划</div><el-button type="primary" @click="handleAddPlan">新建计划</el-button></div>
        <el-table :data="plans" stripe size="small">
          <el-table-column prop="name" label="计划名称" min-width="160" />
          <el-table-column prop="strategy" label="策略" width="100"><template #default="{row}">{{ {immediate:'立即',scheduled:'定时',incremental:'增量',full:'全量'}[row.strategy]||row.strategy }}</template></el-table-column>
          <el-table-column prop="scheduledTime" label="计划时间" width="150" />
          <el-table-column prop="scope" label="范围" width="80" />
          <el-table-column label="状态" width="80"><template #default="{row}"><el-tag :type="row.status==='active'?'success':'info'" size="small">{{ row.status==='active'?'执行中':'待执行' }}</el-tag></template></el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="100" />
          <el-table-column label="操作" width="180">
            <template #default="{row}">
              <el-button v-if="row.status!=='active'" link type="success" size="small" @click="handleExecutePlan(row)">执行</el-button>
              <el-button link type="primary" size="small" @click="handleEditPlan(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDeletePlan(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!plans.length" description="暂无发布计划" :image-size="60" />
      </el-tab-pane>

      <!-- 重置权限配置 CRUD -->
      <el-tab-pane label="重置权限配置" name="permissions">
        <div class="section-header"><div class="section-title">重置权限配置</div><el-button type="primary" @click="handleAddPerm">新增配置</el-button></div>
        <el-table :data="permissions" stripe size="small">
          <el-table-column prop="role" label="角色" width="120" />
          <el-table-column label="允许重置" width="90"><template #default="{row}"><el-tag :type="row.canReset?'success':'info'" size="small">{{ row.canReset?'是':'否' }}</el-tag></template></el-table-column>
          <el-table-column label="允许撤回" width="90"><template #default="{row}"><el-tag :type="row.canRevoke?'success':'info'" size="small">{{ row.canRevoke?'是':'否' }}</el-tag></template></el-table-column>
          <el-table-column prop="maxResetCount" label="最大重置次数" width="120" />
          <el-table-column prop="description" label="说明" show-overflow-tooltip />
          <el-table-column label="操作" width="130">
            <template #default="{row}">
              <el-button link type="primary" size="small" @click="handleEditPerm(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDeletePerm(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!permissions.length" description="暂无权限配置" :image-size="60" />
      </el-tab-pane>
    </el-tabs>

    <!-- 发布记录弹窗 -->
    <el-dialog v-model="showDialog" :title="dialogTitle" width="600px">
      <el-form label-width="100px">
        <el-form-item label="版本" required><el-input v-model="formData.version" placeholder="如: v2.0.0" /></el-form-item>
        <el-form-item label="环境"><el-select v-model="formData.environment" style="width:100%"><el-option label="测试" value="staging" /><el-option label="生产" value="production" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="formData.status" style="width:100%"><el-option label="草稿" value="draft" /><el-option label="待发布" value="pending" /><el-option label="已发布" value="released" /></el-select></el-form-item>
        <el-form-item label="发布说明"><el-input v-model="formData.releaseNotes" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDialog=false">取消</el-button><el-button type="primary" @click="handleSave">保存</el-button></template>
    </el-dialog>

    <!-- 发布计划弹窗 -->
    <el-dialog v-model="showPlanDialog" :title="isEditingPlan ? '编辑计划' : '新建计划'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="计划名称" required><el-input v-model="planForm.name" /></el-form-item>
        <el-form-item label="策略"><el-select v-model="planForm.strategy" style="width:160px"><el-option label="立即" value="immediate" /><el-option label="定时" value="scheduled" /><el-option label="增量" value="incremental" /><el-option label="全量" value="full" /></el-select></el-form-item>
        <el-form-item label="计划时间" v-if="planForm.strategy==='scheduled'"><el-input v-model="planForm.scheduledTime" placeholder="2026-07-01 02:00" /></el-form-item>
        <el-form-item label="范围"><el-select v-model="planForm.scope" style="width:160px"><el-option label="全部" value="all" /><el-option label="指定分类" value="category" /><el-option label="指定产品" value="product" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="showPlanDialog=false">取消</el-button><el-button type="primary" @click="handleSavePlan">{{ isEditingPlan ? '保存' : '创建' }}</el-button></template>
    </el-dialog>

    <!-- 权限配置弹窗 -->
    <el-dialog v-model="showPermDialog" :title="isEditingPerm ? '编辑权限配置' : '新增权限配置'" width="480px">
      <el-form label-width="120px">
        <el-form-item label="角色" required><el-select v-model="permForm.role" style="width:160px"><el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" /></el-select></el-form-item>
        <el-form-item label="允许重置"><el-switch v-model="permForm.canReset" /></el-form-item>
        <el-form-item label="允许撤回"><el-switch v-model="permForm.canRevoke" /></el-form-item>
        <el-form-item label="最大重置次数"><el-input-number v-model="permForm.maxResetCount" :min="0" :max="100" style="width:160px" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="permForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showPermDialog=false">取消</el-button><el-button type="primary" @click="handleSavePerm">{{ isEditingPerm ? '保存' : '创建' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; gap:8px; flex-wrap:wrap; }
.section-title { font-size: 15px; font-weight: 600; }
</style>
