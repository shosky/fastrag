<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTermLibraries,
  getTerms,
  createTerm,
  updateTerm,
  deleteTerm,
  deleteTermLibrary,
  type TermLibrary,
  type TermRecord,
} from '@/mock/terminology'

const activeTab = ref('library')
const showDialog = ref(false)
const dialogTitle = ref('新建术语库')

const formData = ref({
  name: '',
  alias: '',
  library: '',
  status: '启用' as '启用' | '禁用',
  definition: '',
})

// 术语库 —— 来自统一 mock 层
const termLibraries = ref<TermLibrary[]>(getTermLibraries())

// 术语列表 —— 来自统一 mock 层
const termList = ref<TermRecord[]>(getTerms())

function refreshData() {
  termLibraries.value = getTermLibraries()
  termList.value = getTerms()
}

function handleAddLibrary() {
  dialogTitle.value = '新建术语库'
  formData.value = { name: '', alias: '', library: '', status: '启用', definition: '' }
  showDialog.value = true
}

function handleEditLibrary(lib: TermLibrary) {
  dialogTitle.value = '编辑术语库'
  formData.value = { name: lib.name, alias: '', library: '', status: '启用', definition: lib.desc }
  showDialog.value = true
}

async function handleDeleteLibrary(lib: TermLibrary) {
  try {
    await ElMessageBox.confirm('删除术语库后将同步删除其下所有词条，且不可恢复！', '删除确认', { type: 'warning' })
    deleteTermLibrary(lib.id)
    refreshData()
    ElMessage.success('删除成功')
  } catch {}
}

function handleAddTerm() {
  dialogTitle.value = '新建术语'
  formData.value = { name: '', alias: '', library: '', status: '启用', definition: '' }
  showDialog.value = true
}

function handleEditTerm(term: TermRecord) {
  dialogTitle.value = '编辑术语'
  formData.value = { name: term.name, alias: term.alias, library: term.library, status: term.status, definition: term.definition }
  showDialog.value = true
}

async function handleDeleteTerm(term: TermRecord) {
  try {
    await ElMessageBox.confirm('确定要删除这个术语吗？', '删除确认', { type: 'warning' })
    deleteTerm(term.id)
    refreshData()
    ElMessage.success('删除成功')
  } catch {}
}

function handleSave() {
  if (!formData.value.name) {
    ElMessage.warning('请输入名称')
    return
  }
  if (dialogTitle.value === '新建术语') {
    createTerm({
      name: formData.value.name,
      library: formData.value.library || termLibraries.value[0]?.name || '',
      alias: formData.value.alias,
      status: formData.value.status,
      definition: formData.value.definition,
    })
  }
  refreshData()
  showDialog.value = false
  ElMessage.success('保存成功')
}

function handleBatchImport() {
  ElMessage.info('批量导入功能')
}
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="术语库" name="library">
        <div class="section-header">
          <div />
          <el-button type="primary" @click="handleAddLibrary">新建术语库</el-button>
        </div>
        <div class="library-grid">
          <div v-for="lib in termLibraries" :key="lib.id" class="library-card">
            <div class="card-header">
              <h4>{{ lib.name }}</h4>
              <div class="card-actions">
                <el-button link size="small" @click="handleEditLibrary(lib)"><el-icon><Edit /></el-icon></el-button>
                <el-button link type="danger" size="small" @click="handleDeleteLibrary(lib)"><el-icon><Delete /></el-icon></el-button>
              </div>
            </div>
            <p>{{ lib.desc }}</p>
            <div class="card-footer">{{ lib.count }} 个词条</div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="术语" name="terms">
        <div class="section-header">
          <div class="filter-bar">
            <el-input placeholder="搜索术语" clearable style="width: 200px" />
            <el-select placeholder="术语库筛选" clearable style="width: 150px">
              <el-option v-for="lib in termLibraries" :key="lib.id" :label="lib.name" :value="lib.id" />
            </el-select>
            <el-button type="primary">搜索</el-button>
          </div>
          <div>
            <el-button @click="handleBatchImport">批量导入</el-button>
            <el-button type="primary" @click="handleAddTerm">新建词条</el-button>
          </div>
        </div>
        <el-table :data="termList" stripe>
          <el-table-column type="selection" width="50" />
          <el-table-column prop="name" label="术语名称" />
          <el-table-column prop="library" label="所属术语库" width="150" />
          <el-table-column prop="alias" label="别名" width="120" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="definition" label="释义" show-overflow-tooltip />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleEditTerm(row as TermRecord)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDeleteTerm(row as TermRecord)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px">
      <el-form label-width="100px">
        <el-form-item :label="dialogTitle.includes('术语库') ? '术语库标题' : '术语名称'" required>
          <el-input v-model="formData.name" placeholder="请输入" />
        </el-form-item>
        <el-form-item v-if="dialogTitle.includes('术语')" label="所属术语库">
          <el-select v-model="formData.library" style="width: 100%">
            <el-option v-for="lib in termLibraries" :key="lib.id" :label="lib.name" :value="lib.name" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dialogTitle.includes('术语')" label="别名">
          <el-input v-model="formData.alias" placeholder="请输入别名" />
        </el-form-item>
        <el-form-item v-if="dialogTitle.includes('术语')" label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio label="启用">启用</el-radio>
            <el-radio label="禁用">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="dialogTitle.includes('术语库') ? '简介描述' : '释义内容'">
          <el-input v-model="formData.definition" type="textarea" :rows="4" placeholder="请输入" />
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
</style>
