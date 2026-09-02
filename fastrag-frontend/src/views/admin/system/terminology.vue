<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import { PERMISSIONS } from '@/types/auth'
import * as api from '@/api'

interface TermLibrary {
  id: string
  name: string
  description: string
  termCount: number
}

interface TermRecord {
  id: string
  term: string
  libraryId: string
  alias: string
  status: number
  definition: string
}

type DialogMode = 'library-create' | 'library-edit' | 'term-create' | 'term-edit'

const activeTab = ref('library')
const showDialog = ref(false)
const dialogMode = ref<DialogMode>('library-create')
const editingId = ref<string | null>(null)
const loading = ref(false)

const libraryForm = ref({ name: '', description: '' })
const termForm = ref({ term: '', alias: '', libraryId: '', status: 1 as 0 | 1, definition: '' })

const termLibraries = ref<TermLibrary[]>([])
const termList = ref<TermRecord[]>([])

// --- 术语筛选：库筛选走后端（getTerms libraryId），关键词前端过滤 ---
const searchKeyword = ref('')
const selectedLibraryId = ref<string | null>(null)

// --- 分页 ---
const { currentPage, pageSize, handleCurrentChange, handleSizeChange } = usePagination(10)

const libraryNameById = computed(() => {
  const map: Record<string, string> = {}
  for (const lib of termLibraries.value) map[lib.id] = lib.name
  return map
})

const filteredTerms = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return termList.value
  return termList.value.filter(
    t =>
      (t.term || '').toLowerCase().includes(kw) ||
      (t.alias || '').toLowerCase().includes(kw) ||
      (t.definition || '').toLowerCase().includes(kw)
  )
})

const paginatedTerms = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredTerms.value.slice(start, start + pageSize.value)
})

async function refreshData(selectedLibraryIdOverride?: string | null) {
  loading.value = true
  try {
    const libId = selectedLibraryIdOverride ?? selectedLibraryId.value
    const [libRes, termRes] = await Promise.all([
      api.getTermLibraries(),
      api.getTerms(libId ? { libraryId: libId } : undefined),
    ])
    termLibraries.value = (libRes as any) || []
    termList.value = (termRes as any)?.list || (termRes as any) || []
  } finally {
    loading.value = false
  }
}

async function handleLibraryFilterChange(libraryId: string | null) {
  selectedLibraryId.value = libraryId
  currentPage.value = 1
  await refreshData(libraryId)
}

onMounted(refreshData)

function openLibraryCreate() {
  dialogMode.value = 'library-create'
  editingId.value = null
  libraryForm.value = { name: '', description: '' }
  showDialog.value = true
}

function openLibraryEdit(lib: TermLibrary) {
  dialogMode.value = 'library-edit'
  editingId.value = lib.id
  libraryForm.value = { name: lib.name, description: lib.description }
  showDialog.value = true
}

async function handleDeleteLibrary(lib: TermLibrary) {
  try {
    await ElMessageBox.confirm('删除术语库后将同步删除其下所有词条，且不可恢复！', '删除确认', { type: 'warning' })
    await api.deleteTermLibrary(lib.id)
    await refreshData()
    ElMessage.success('删除成功')
  } catch {}
}

function openTermCreate() {
  dialogMode.value = 'term-create'
  editingId.value = null
  termForm.value = { term: '', alias: '', libraryId: selectedLibraryId.value || termLibraries.value[0]?.id || '', status: 1, definition: '' }
  showDialog.value = true
}

function openTermEdit(term: TermRecord) {
  dialogMode.value = 'term-edit'
  editingId.value = term.id
  termForm.value = {
    term: term.term,
    alias: term.alias || '',
    libraryId: term.libraryId,
    status: (term.status ?? 1) as 0 | 1,
    definition: term.definition || '',
  }
  showDialog.value = true
}

async function handleDeleteTerm(term: TermRecord) {
  try {
    await ElMessageBox.confirm('确定要删除这个术语吗？', '删除确认', { type: 'warning' })
    await api.deleteTerm(term.id)
    await refreshData()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleSave() {
  if (dialogMode.value === 'library-create' || dialogMode.value === 'library-edit') {
    if (!libraryForm.value.name) {
      ElMessage.warning('请输入术语库名称')
      return
    }
    if (dialogMode.value === 'library-create') {
      await api.createTermLibrary(libraryForm.value)
    } else if (editingId.value) {
      await api.updateTermLibrary(editingId.value, libraryForm.value)
    }
  } else {
    if (!termForm.value.term) {
      ElMessage.warning('请输入术语名称')
      return
    }
    if (!termForm.value.libraryId) {
      ElMessage.warning('请选择所属术语库')
      return
    }
    if (dialogMode.value === 'term-create') {
      await api.createTerm({ ...termForm.value })
    } else if (editingId.value) {
      await api.updateTerm(editingId.value, { ...termForm.value })
    }
  }
  await refreshData()
  showDialog.value = false
  ElMessage.success('保存成功')
}

// TODO: 批量导入（CSV 模板 + 上传解析）待后续迭代
function handleBatchImport() {
  ElMessage.info('批量导入功能开发中')
}
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="术语库" name="library">
        <div class="section-header">
          <div />
          <el-button type="primary" v-permission="PERMISSIONS.TERMINOLOGY_LIBRARY_CREATE" @click="openLibraryCreate">新建术语库</el-button>
        </div>
        <el-empty v-if="!termLibraries.length && !loading" description="暂无术语库" :image-size="60" />
        <div v-else class="library-grid">
          <div v-for="lib in termLibraries" :key="lib.id" class="library-card">
            <div class="card-header">
              <h4>{{ lib.name }}</h4>
              <div class="card-actions">
                <el-button link size="small" @click="openLibraryEdit(lib)"><el-icon><Edit /></el-icon></el-button>
                <el-button link type="danger" size="small" v-permission="PERMISSIONS.TERMINOLOGY_LIBRARY_DELETE" @click="handleDeleteLibrary(lib)"><el-icon><Delete /></el-icon></el-button>
              </div>
            </div>
            <p>{{ lib.description }}</p>
            <div class="card-footer">{{ lib.termCount ?? 0 }} 个词条</div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="术语" name="terms">
        <div class="section-header">
          <div class="filter-bar">
            <el-input v-model="searchKeyword" placeholder="搜索术语" clearable style="width: 200px" @clear="currentPage = 1" />
            <el-select
              :model-value="selectedLibraryId"
              placeholder="术语库筛选"
              clearable
              style="width: 150px"
              @change="handleLibraryFilterChange"
            >
              <el-option v-for="lib in termLibraries" :key="lib.id" :label="lib.name" :value="lib.id" />
            </el-select>
          </div>
          <div>
            <el-button v-permission="PERMISSIONS.TERMINOLOGY_IMPORT" @click="handleBatchImport">批量导入</el-button>
            <el-button type="primary" v-permission="PERMISSIONS.TERMINOLOGY_TERM_CREATE" @click="openTermCreate">新建词条</el-button>
          </div>
        </div>
        <el-table v-loading="loading" :data="paginatedTerms" stripe>
          <el-table-column type="selection" width="50" />
          <el-table-column prop="term" label="术语名称" />
          <el-table-column label="所属术语库" width="150">
            <template #default="{ row }">{{ libraryNameById[row.libraryId] || row.libraryId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="alias" label="别名" width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="definition" label="释义" show-overflow-tooltip />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" v-permission="PERMISSIONS.TERMINOLOGY_TERM_EDIT" @click="openTermEdit(row as TermRecord)">编辑</el-button>
              <el-button link type="danger" size="small" v-permission="PERMISSIONS.TERMINOLOGY_TERM_DELETE" @click="handleDeleteTerm(row as TermRecord)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!filteredTerms.length && !loading" description="暂无术语" :image-size="60" />

        <div class="terminology__pagination">
          <el-pagination
            v-if="filteredTerms.length > pageSize"
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="filteredTerms.length"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="handleCurrentChange"
            @size-change="handleSizeChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showDialog" :title="dialogMode === 'library-create' ? '新建术语库' : dialogMode === 'library-edit' ? '编辑术语库' : dialogMode === 'term-create' ? '新建术语' : '编辑术语'" width="500px">
      <el-form v-if="dialogMode.startsWith('library')" label-width="100px">
        <el-form-item label="术语库名称" required>
          <el-input v-model="libraryForm.name" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="简介描述">
          <el-input v-model="libraryForm.description" type="textarea" :rows="4" placeholder="请输入" />
        </el-form-item>
      </el-form>
      <el-form v-else label-width="100px">
        <el-form-item label="术语名称" required>
          <el-input v-model="termForm.term" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="所属术语库">
          <el-select v-model="termForm.libraryId" style="width: 100%">
            <el-option v-for="lib in termLibraries" :key="lib.id" :label="lib.name" :value="lib.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="别名">
          <el-input v-model="termForm.alias" placeholder="多个别名用逗号分隔" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="termForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="释义内容">
          <el-input v-model="termForm.definition" type="textarea" :rows="4" placeholder="请输入" />
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

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.library-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: $spacing-base;
}

.library-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  border: 1px solid $border-lighter;

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    h4 { margin: 0; }
  }

  p { font-size: 13px; color: $text-secondary; margin: $spacing-sm 0; }

  .card-footer { font-size: 12px; color: $text-secondary; }
}

.filter-bar {
  display: flex;
  gap: $spacing-sm;
}

.terminology__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
