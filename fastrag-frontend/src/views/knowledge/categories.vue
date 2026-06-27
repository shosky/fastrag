<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

interface Category {
  id: string
  name: string
  description: string
  count: number
  createdAt: string
}

const loading = ref(false)
const dataList = ref<Category[]>([])
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})
const searchKeyword = ref('')

function loadData() {
  loading.value = true
  try {
    // 从知识库数据中聚合分类
    const stored = localStorage.getItem('kb_categories')
    if (stored) {
      dataList.value = JSON.parse(stored)
    } else {
      dataList.value = [
        { id: 'cat-1', name: '产品文档', description: '产品相关文档和说明', count: 12, createdAt: '2026-01-10T08:00:00Z' },
        { id: 'cat-2', name: '技术文档', description: '技术架构和API文档', count: 8, createdAt: '2026-01-10T08:00:00Z' },
        { id: 'cat-3', name: '客户案例', description: '客户成功案例和最佳实践', count: 5, createdAt: '2026-01-15T08:00:00Z' },
        { id: 'cat-4', name: '培训资料', description: '员工培训和技能提升资料', count: 6, createdAt: '2026-02-01T08:00:00Z' },
        { id: 'cat-5', name: '常见问题', description: 'FAQ和常见问题解答', count: 15, createdAt: '2026-02-10T08:00:00Z' },
      ]
    }
  } finally { loading.value = false }
}

function saveData() {
  localStorage.setItem('kb_categories', JSON.stringify(dataList.value))
}

onMounted(loadData)

const filteredList = ref<Category[]>([])
import { computed } from 'vue'
const filtered = computed(() => {
  if (!searchKeyword.value) return dataList.value
  return dataList.value.filter(c => c.name.includes(searchKeyword.value))
})

function handleAdd() {
  editingId.value = null
  formData.value = {}
  dialogTitle.value = '新增分类'
  showDialog.value = true
}
function handleEdit(row: Category) {
  editingId.value = row.id
  formData.value = { ...row }
  dialogTitle.value = '编辑分类'
  showDialog.value = true
}
async function handleDelete(row: Category) {
  try {
    await ElMessageBox.confirm(`确定要删除分类「${row.name}」吗？`, '提示', { type: 'warning' })
    dataList.value = dataList.value.filter(c => c.id !== row.id)
    saveData()
    ElMessage.success('删除成功')
  } catch {}
}
function handleSave() {
  if (!formData.value.name) { ElMessage.warning('请输入分类名称'); return }
  if (editingId.value) {
    const idx = dataList.value.findIndex(c => c.id === editingId.value)
    if (idx !== -1) dataList.value[idx] = { ...dataList.value[idx], ...formData.value }
    ElMessage.success('更新成功')
  } else {
    dataList.value.push({
      id: `cat-${Date.now()}`,
      name: formData.value.name,
      description: formData.value.description || '',
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
        <div class="section-title">知识库分类</div>
        <el-button type="primary" @click="handleAdd">新增分类</el-button>
      </div>
      <div class="filter-bar">
        <el-input v-model="searchKeyword" placeholder="搜索分类..." clearable style="width: 240px" />
      </div>
      <el-table :data="filtered" stripe>
        <el-table-column prop="name" label="分类名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column prop="count" label="知识库数" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="分类名称" required>
          <el-input v-model="formData.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入分类描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
