<script setup lang="ts">
import type { QueryRule, RuleType, RuleStatus } from '@/mock/query-rules'
import { Plus, Delete, Edit } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getQueryRules,
  createQueryRule,
  updateQueryRule,
  deleteQueryRule,
  toggleRuleStatus,
} from '@/mock/query-rules'

const activeTab = ref<RuleType>('rewrite')
const rules = ref<QueryRule[]>([])
const showDialog = ref(false)
const isEditing = ref(false)
const editingId = ref('')
const form = ref({ name: '', pattern: '', replacement: '', priority: 5, status: 'enabled' as RuleStatus })

function refresh() {
  rules.value = getQueryRules(activeTab.value)
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
  form.value = { name: rule.name, pattern: rule.pattern, replacement: rule.replacement, priority: rule.priority, status: rule.status }
  showDialog.value = true
}

function handleSave() {
  if (!form.value.name || !form.value.pattern || !form.value.replacement) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (isEditing.value) {
    updateQueryRule(editingId.value, form.value)
  } else {
    createQueryRule({ ...form.value, type: activeTab.value })
  }
  showDialog.value = false
  refresh()
  ElMessage.success('保存成功')
}

async function handleDelete(rule: QueryRule) {
  try {
    await ElMessageBox.confirm(`确定删除规则「${rule.name}」？`, '删除确认', { type: 'warning' })
    deleteQueryRule(rule.id)
    refresh()
    ElMessage.success('已删除')
  } catch {}
}

function handleToggleStatus(rule: QueryRule) {
  toggleRuleStatus(rule.id)
  refresh()
}

// 测试规则
const testQuery = ref('')
const testResult = ref('')

function handleTest() {
  // 测试逻辑通过 mock 层的 applyQueryRules 实现
  if (!testQuery.value.trim()) {
    ElMessage.warning('请输入测试查询')
    return
  }
  ElMessage.info('测试结果请在检索调试中查看')
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

    <el-table :data="rules" stripe>
      <el-table-column prop="name" label="规则名称" min-width="150" />
      <el-table-column prop="pattern" label="匹配模式" width="150" />
      <el-table-column prop="replacement" label="替换/扩写" min-width="200" show-overflow-tooltip />
      <el-table-column prop="priority" label="优先级" width="80" align="center" />
      <el-table-column prop="status" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 'enabled'"
            size="small"
            @change="handleToggleStatus(row as QueryRule)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row as QueryRule)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row as QueryRule)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="rules.length === 0" description="暂无规则" />

    <!-- 测试区 -->
    <div class="qr-test">
      <h4>规则测试</h4>
      <div class="qr-test__input">
        <el-input v-model="testQuery" placeholder="输入查询内容测试规则效果" clearable />
        <el-button type="primary" @click="handleTest">测试</el-button>
      </div>
    </div>

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
}
</style>
