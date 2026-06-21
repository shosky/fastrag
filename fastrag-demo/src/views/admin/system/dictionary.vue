<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeType = ref('系统信息')
const searchKeyword = ref('')
const showDialog = ref(false)
const dialogTitle = ref('新建字典条目')

const dictTypes = ['系统信息', '系统登录配置', '业务配置']

const formData = ref({
  key: '',
  label: '',
  value: '',
  enabled: true,
  remark: '',
})

const dictData: Record<string, any[]> = {
  '系统信息': [
    { key: 'system_name', label: '系统名称', value: 'AIS 智能知识服务平台', enabled: true, remark: '系统展示名称', updatedAt: '2026-06-04' },
    { key: 'system_slogan', label: '宣传语', value: '让知识触手可及', enabled: true, remark: '首页展示', updatedAt: '2026-06-04' },
    { key: 'copyright', label: '版权信息', value: '© 2026 TorchV', enabled: true, remark: '页脚版权', updatedAt: '2026-06-03' },
  ],
  '系统登录配置': [
    { key: 'login_title', label: '登录标题', value: '欢迎登录', enabled: true, remark: '登录页标题', updatedAt: '2026-06-01' },
    { key: 'max_retry', label: '最大重试次数', value: '5', enabled: true, remark: '登录失败锁定', updatedAt: '2026-05-20' },
  ],
  '业务配置': [
    { key: 'default_model', label: '默认模型', value: 'qwen3-32b', enabled: true, remark: '默认大模型', updatedAt: '2026-06-02' },
  ],
}

const currentList = computed(() => {
  let list = dictData[activeType.value] || []
  if (searchKeyword.value) {
    list = list.filter(item =>
      item.key.includes(searchKeyword.value) ||
      item.label.includes(searchKeyword.value) ||
      item.value.includes(searchKeyword.value)
    )
  }
  return list
})

function handleAdd() {
  dialogTitle.value = '新建字典条目'
  formData.value = { key: '', label: '', value: '', enabled: true, remark: '' }
  showDialog.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑字典条目'
  formData.value = { ...row }
  showDialog.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除条目 "${row.label}" 吗？`, '删除确认', { type: 'warning' })
    ElMessage.success('删除成功')
  } catch {}
}

function handleSave() {
  if (!formData.value.key) {
    ElMessage.warning('请输入条目键')
    return
  }
  showDialog.value = false
  ElMessage.success('保存成功')
}
</script>

<template>
  <div class="page-container">
    <div class="dict-layout">
      <div class="dict-types">
        <div class="types-title">字典类型</div>
        <div
          v-for="t in dictTypes"
          :key="t"
          class="type-item"
          :class="{ active: activeType === t }"
          @click="activeType = t"
        >
          {{ t }}
        </div>
      </div>
      <div class="dict-content">
        <div class="section-header">
          <div class="section-title">{{ activeType }}</div>
          <el-button type="primary" @click="handleAdd">新建条目</el-button>
        </div>
        <div class="filter-bar">
          <el-input v-model="searchKeyword" placeholder="搜索键、名称、值或备注" clearable style="width: 300px" />
          <el-button type="primary">搜索</el-button>
          <el-button @click="searchKeyword = ''">重置</el-button>
        </div>
        <el-table :data="currentList" stripe>
          <el-table-column prop="key" label="键(Key)" width="180" />
          <el-table-column prop="label" label="名称(Label)" width="150" />
          <el-table-column prop="value" label="值(Value)" show-overflow-tooltip />
          <el-table-column label="启用" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" width="150" />
          <el-table-column prop="updatedAt" label="更新时间" width="120" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px">
      <el-form label-width="80px">
        <el-form-item label="条目键" required>
          <el-input v-model="formData.key" placeholder="请输入条目键" :disabled="dialogTitle === '编辑字典条目'" />
        </el-form-item>
        <el-form-item label="显示名称" required>
          <el-input v-model="formData.label" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="条目值">
          <el-input v-model="formData.value" type="textarea" :rows="3" placeholder="请输入条目值" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="formData.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.dict-layout {
  display: flex;
  gap: $spacing-base;
  height: calc(100vh - 120px);
}

.dict-types {
  width: 200px;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-base;
  flex-shrink: 0;

  .types-title { font-weight: 600; margin-bottom: $spacing-base; }
}

.type-item {
  padding: $spacing-sm $spacing-base;
  border-radius: $radius-sm;
  cursor: pointer;
  font-size: 13px;
  &:hover { background: $bg-hover; }
  &.active { background: #ecf5ff; color: $color-primary; }
}

.dict-content {
  flex: 1;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}
</style>
