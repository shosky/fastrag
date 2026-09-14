<script setup lang="ts">
import { Plus, Delete, Edit, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

// 后端 query_rule 实体：action=替换/扩写内容，enabled=1/0，ruleType=rewrite|expand
interface QueryRule {
  id: string
  name: string
  pattern: string
  action: string
  priority: number
  enabled: number
  ruleType: string
  description?: string
  createdAt?: string
}

const activeTab = ref('rewrite')
const rules = ref<QueryRule[]>([])
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const loading = ref(false)
const form = ref({ name: '', pattern: '', replacement: '', priority: 5, status: 'enabled' })

async function refresh() {
  loading.value = true
  try {
    const res = await api.getQueryRules({ type: activeTab.value })
    rules.value = (res as any)?.list || (res as any) || []
  } finally {
    loading.value = false
  }
}

watch(activeTab, refresh)
onMounted(refresh)

function handleAdd() {
  isEditing.value = false
  editingId.value = ''
  form.value = { name: '', pattern: '', replacement: '', priority: 5, status: 'enabled' }
  showDialog.value = true
}

function handleEdit(rule: QueryRule) {
  isEditing.value = true
  editingId.value = rule.id
  form.value = { name: rule.name, pattern: rule.pattern, replacement: rule.action, priority: rule.priority ?? 5, status: rule.enabled === 1 ? 'enabled' : 'disabled' }
  showDialog.value = true
}

async function handleSave() {
  if (!form.value.name || !form.value.pattern || !form.value.replacement) {
    ElMessage.warning('请填写完整信息')
    return
  }
  const payload = { ...form.value, type: activeTab.value }
  if (isEditing.value) {
    await api.updateQueryRule(editingId.value, payload)
  } else {
    await api.createQueryRule(payload)
  }
  showDialog.value = false
  await refresh()
  ElMessage.success('保存成功')
}

// 查看规则详情
const showViewDialog = ref(false)
const viewingRule = ref<QueryRule | null>(null)
function handleView(rule: QueryRule) {
  viewingRule.value = rule
  showViewDialog.value = true
}

async function handleDelete(rule: QueryRule) {
  try {
    await ElMessageBox.confirm(`确定删除规则「${rule.name}」？`, '删除确认', { type: 'warning' })
    await api.deleteQueryRule(rule.id)
    await refresh()
    ElMessage.success('已删除')
  } catch {}
}

async function handleToggleStatus(rule: QueryRule) {
  await api.toggleQueryRule(rule.id)
  await refresh()
}

// 测试规则：调用真实规则引擎
const testQuery = ref('')
const testResult = ref<{ rewritten: string; appliedRules: string[] } | null>(null)
const testLoading = ref(false)

async function handleTest() {
  if (!testQuery.value.trim()) {
    ElMessage.warning('请输入测试查询')
    return
  }
  testLoading.value = true
  try {
    testResult.value = await api.applyQueryRules(testQuery.value)
  } catch {
    ElMessage.error('测试失败')
  } finally {
    testLoading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="section-header">
      <h3>查询规则管理</h3>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>新建规则
      </el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="重写规则" name="rewrite" />
      <el-tab-pane label="扩写规则" name="expand" />
    </el-tabs>

    <el-table :data="rules" stripe v-loading="loading">
      <el-table-column prop="name" label="规则名称" min-width="150" />
      <el-table-column prop="pattern" label="匹配模式" width="150" />
      <el-table-column prop="action" label="替换/扩写" min-width="200" show-overflow-tooltip />
      <el-table-column prop="priority" label="优先级" width="80" align="center" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled === 1"
            size="small"
            @change="handleToggleStatus(row as QueryRule)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :icon="View" @click="handleView(row as QueryRule)">查看</el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row as QueryRule)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row as QueryRule)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="rules.length === 0 && !loading" description="暂无规则" />

    <!-- 测试区 -->
    <div class="qr-test">
      <h4>规则测试</h4>
      <div class="qr-test__input">
        <el-input v-model="testQuery" placeholder="输入查询内容测试规则效果" clearable @keyup.enter="handleTest" />
        <el-button type="primary" :loading="testLoading" @click="handleTest">测试</el-button>
      </div>
      <div v-if="testResult" class="qr-test__result">
        <div>改写结果：<b>{{ testResult.rewritten }}</b></div>
        <div v-if="testResult.appliedRules?.length" style="margin-top:6px;color:#909399">
          命中规则：{{ testResult.appliedRules.join('；') }}
        </div>
        <div v-else style="margin-top:6px;color:#909399">未命中任何规则</div>
      </div>
    </div>

    <!-- 查看规则详情对话框 -->
    <el-dialog v-model="showViewDialog" title="规则详情" width="520px">
      <el-descriptions v-if="viewingRule" :column="1" border>
        <el-descriptions-item label="规则名称">{{ viewingRule.name }}</el-descriptions-item>
        <el-descriptions-item label="规则类型">{{ viewingRule.ruleType === 'rewrite' ? '查询重写' : '查询扩写' }}</el-descriptions-item>
        <el-descriptions-item label="匹配模式">{{ viewingRule.pattern }}</el-descriptions-item>
        <el-descriptions-item label="替换/扩写内容">{{ viewingRule.action }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ viewingRule.priority }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="viewingRule.enabled === 1 ? 'success' : 'info'" size="small">{{ viewingRule.enabled === 1 ? '启用' : '禁用' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述">{{ viewingRule.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewingRule.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="showViewDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="showDialog" :title="isEditing ? '编辑规则' : '新建规则'" width="480px">
      <el-form label-position="top" size="default">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.name" placeholder="如：wifi→WiFi组网" />
        </el-form-item>
        <el-form-item :label="activeTab === 'rewrite' ? '匹配关键词' : '触发词'" required>
          <el-input v-model="form.pattern" placeholder="如：wifi" />
        </el-form-item>
        <el-form-item :label="activeTab === 'rewrite' ? '替换为' : '扩写词（空格分隔）'" required>
          <el-input v-model="form.replacement" :placeholder="activeTab === 'rewrite' ? 'WiFi 组网' : '价格 报价 费用'" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" :max="10" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="enabled">启用</el-radio>
            <el-radio value="disabled">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.qr-test {
  margin-top: $spacing-lg;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;

  h4 { margin: 0 0 $spacing-sm; }

  &__input {
    display: flex;
    gap: $spacing-sm;
  }

  &__result {
    margin-top: $spacing-sm;
    padding: $spacing-sm;
    background: var(--el-fill-color-light);
    border-radius: $radius-base;
    font-size: 13px;
    line-height: 1.6;
    word-break: break-all;
  }
}
</style>
