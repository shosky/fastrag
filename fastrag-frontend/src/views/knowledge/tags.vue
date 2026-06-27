<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

interface TagItem {
  id: string
  name: string
  color: string
  count: number
  createdAt: string
}

const loading = ref(false)
const dataList = ref<TagItem[]>([])
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const searchKeyword = ref('')

const colorOptions = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6', '#1ABC9C', '#3498DB']

function loadData() {
  loading.value = true
  try {
    const stored = localStorage.getItem('kb_tags')
    if (stored) {
      dataList.value = JSON.parse(stored)
    } else {
      dataList.value = [
        { id: 'tag-1', name: '产品', color: '#409EFF', count: 12, createdAt: '2026-01-10T08:00:00Z' },
        { id: 'tag-2', name: '技术', color: '#67C23A', count: 8, createdAt: '2026-01-10T08:00:00Z' },
        { id: 'tag-3', name: '案例', color: '#E6A23C', count: 5, createdAt: '2026-01-15T08:00:00Z' },
        { id: 'tag-4', name: '培训', color: '#F56C6C', count: 6, createdAt: '2026-02-01T08:00:00Z' },
        { id: 'tag-5', name: 'API', color: '#9B59B6', count: 4, createdAt: '2026-02-10T08:00:00Z' },
        { id: 'tag-6', name: '文档', color: '#1ABC9C', count: 10, createdAt: '2026-02-15T08:00:00Z' },
        { id: 'tag-7', name: '客户', color: '#3498DB', count: 3, createdAt: '2026-03-01T08:00:00Z' },
      ]
    }
  } finally { loading.value = false }
}

function saveData() {
  localStorage.setItem('kb_tags', JSON.stringify(dataList.value))
}

onMounted(loadData)

const filtered = computed(() => {
  if (!searchKeyword.value) return dataList.value
  return dataList.value.filter(t => t.name.includes(searchKeyword.value))
})

function handleAdd() {
  editingId.value = null
  formData.value = { color: colorOptions[Math.floor(Math.random() * colorOptions.length)] }
  dialogTitle.value = '新增标签'
  showDialog.value = true
}
function handleEdit(row: TagItem) {
  editingId.value = row.id
  formData.value = { ...row }
  dialogTitle.value = '编辑标签'
  showDialog.value = true
}
async function handleDelete(row: TagItem) {
  try {
    await ElMessageBox.confirm(`确定要删除标签「${row.name}」吗？`, '提示', { type: 'warning' })
    dataList.value = dataList.value.filter(t => t.id !== row.id)
    saveData()
    ElMessage.success('删除成功')
  } catch {}
}
function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入标签名称'); return }
  if (editingId.value) {
    const idx = dataList.value.findIndex(t => t.id === editingId.value)
    if (idx !== -1) dataList.value[idx] = { ...dataList.value[idx], ...formData.value }
    ElMessage.success('更新成功')
  } else {
    dataList.value.push({
      id: `tag-${Date.now()}`,
      name: formData.value.name,
      color: formData.value.color || '#409EFF',
      count: 0,
      createdAt: new Date().toISOString(),
    })
    ElMessage.success('创建成功')
  }
  saveData()
  showDialog.value = false
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">知识库标签</div>
        <el-button type="primary" @click="handleAdd">新增标签</el-button>
      </div>
      <div class="filter-bar">
        <el-input v-model="searchKeyword" placeholder="搜索标签..." clearable style="width: 240px" />
      </div>
      <div style="display: flex; flex-wrap: wrap; gap: 12px; padding: 16px 0">
        <div v-for="tag in filtered" :key="tag.id" class="tag-card">
          <div class="tag-card__header">
            <el-tag :color="tag.color" effect="dark" size="large" style="border: none; color: #fff">{{ tag.name }}</el-tag>
            <span class="tag-card__count">{{ tag.count }} 个知识库</span>
          </div>
          <div class="tag-card__actions">
            <el-button link type="primary" size="small" @click="handleEdit(tag)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(tag)">删除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-if="filtered.length === 0 && !loading" description="暂无标签" />
    </div>
    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="标签名称" required>
          <el-input v-model="formData.name" placeholder="请输入标签名称" />
        </el-form-item>
        <el-form-item label="标签颜色">
          <div style="display: flex; gap: 8px; flex-wrap: wrap">
            <div
              v-for="c in colorOptions" :key="c"
              :style="{ width: '28px', height: '28px', borderRadius: '4px', background: c, cursor: 'pointer', border: formData.color === c ? '2px solid #303133' : '2px solid transparent' }"
              @click="formData.color = c"
            />
          </div>
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

.tag-card {
  background: $bg-white;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  padding: 16px;
  min-width: 200px;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
  }

  &__count {
    font-size: 13px;
    color: $text-secondary;
  }

  &__actions {
    display: flex;
    gap: 8px;
  }
}
</style>
