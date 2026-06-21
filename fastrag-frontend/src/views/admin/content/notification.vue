<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const searchTitle = ref('')
const filterTop = ref('')
const filterStatus = ref('')
const showEditor = ref(false)
const isEdit = ref(false)

const formData = ref({
  title: '',
  titleColor: '#303133',
  titleBold: false,
  content: '',
  publishTime: '',
  expiryType: 'long',
  expiryTime: '',
  isTop: false,
})

const notificationList = ref([
  { id: '1', title: '系统升级通知', isTop: true, publishTime: '2026-06-04 10:00', effectiveTime: '2026-06-04 10:00 - 长期', status: '已发布' },
  { id: '2', title: '新功能上线公告', isTop: false, publishTime: '2026-06-03 14:00', effectiveTime: '2026-06-03 14:00 - 2026-07-03', status: '已发布' },
  { id: '3', title: '维护公告', isTop: false, publishTime: '2026-06-01 09:00', effectiveTime: '2026-06-01 09:00 - 2026-06-02', status: '已过期' },
])

function handleAdd() {
  isEdit.value = false
  formData.value = { title: '', titleColor: '#303133', titleBold: false, content: '', publishTime: '', expiryType: 'long', expiryTime: '', isTop: false }
  showEditor.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  formData.value = { title: row.title, titleColor: '#303133', titleBold: false, content: '通知内容...', publishTime: row.publishTime, expiryType: 'long', expiryTime: '', isTop: row.isTop }
  showEditor.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('该操作将永久删除当前公告，请慎重操作！', '删除确认', { type: 'warning' })
    notificationList.value = notificationList.value.filter(n => n.id !== row.id)
    ElMessage.success('删除成功')
  } catch {}
}

function handleSave() {
  showEditor.value = false
  ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
}
</script>

<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">通知管理</div>
        <el-button type="primary" @click="handleAdd">新增通知</el-button>
      </div>
      <div class="filter-bar">
        <el-input v-model="searchTitle" placeholder="通知标题" clearable style="width: 200px" />
        <el-select v-model="filterTop" placeholder="是否置顶" clearable style="width: 120px">
          <el-option label="是" value="是" />
          <el-option label="否" value="否" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 100px">
          <el-option label="已发布" value="已发布" />
          <el-option label="已过期" value="已过期" />
        </el-select>
        <el-button type="primary">查询</el-button>
        <el-button>重置</el-button>
      </div>
      <el-table :data="notificationList" stripe>
        <el-table-column prop="title" label="标题" show-overflow-tooltip />
        <el-table-column label="置顶" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isTop ? 'danger' : 'info'" size="small">{{ row.isTop ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="160" />
        <el-table-column prop="effectiveTime" label="生效时间" width="240" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === '已发布' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showEditor" :title="isEdit ? '编辑通知' : '新增通知'" width="700px">
      <el-form label-width="80px">
        <el-form-item label="标题" required>
          <div class="title-input">
            <el-input v-model="formData.title" placeholder="请输入标题" />
            <el-color-picker v-model="formData.titleColor" />
            <el-checkbox v-model="formData.titleBold">加粗</el-checkbox>
          </div>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="formData.content" type="textarea" :rows="8" placeholder="请输入通知内容" />
        </el-form-item>
        <el-form-item label="发布时间" required>
          <el-date-picker v-model="formData.publishTime" type="datetime" placeholder="选择发布时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="到期时间">
          <el-radio-group v-model="formData.expiryType">
            <el-radio value="long">长期有效</el-radio>
            <el-radio value="custom">设定失效时间</el-radio>
          </el-radio-group>
          <el-date-picker v-if="formData.expiryType === 'custom'" v-model="formData.expiryTime" type="datetime" placeholder="选择失效时间" style="width: 100%; margin-top: 8px" />
        </el-form-item>
        <el-form-item label="是否置顶">
          <el-radio-group v-model="formData.isTop">
            <el-radio :value="false">否</el-radio>
            <el-radio :value="true">是</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditor = false">取消</el-button>
        <el-button type="primary" @click="handleSave">{{ isEdit ? '更新' : '新增' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.title-input {
  display: flex;
  gap: $spacing-sm;
  align-items: center;
  width: 100%;
}
</style>
