<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const activeCategory = ref('')
const searchKeyword = ref('')
const showPromptDialog = ref(false)
const showCategoryDialog = ref(false)
const dialogTitle = ref('添加提示词')
const loading = ref(false)
const editingId = ref<string | null>(null)

const promptForm = ref({ title: '', description: '', category: '', tags: '', content: '' })
const categoryForm = ref({ name: '', description: '', color: '#409eff' })

const categories = ref<any[]>([])
const promptList = ref<any[]>([])

async function loadPrompts() {
  loading.value = true
  try {
    const [catsRes, promptsRes] = await Promise.all([
      api.getDictionaries({ type: '提示词分类' }),
      api.getDictionaries({ type: '提示词' }),
    ])
    categories.value = (catsRes as any)?.['提示词分类'] || []
    promptList.value = (promptsRes as any)?.['提示词'] || []
  } finally {
    loading.value = false
  }
}

onMounted(loadPrompts)

function handleAddPrompt() {
  dialogTitle.value = '添加提示词'
  editingId.value = null
  promptForm.value = { title: '', description: '', category: '', tags: '', content: '' }
  showPromptDialog.value = true
}

function handleEditPrompt(prompt: any) {
  dialogTitle.value = '编辑提示词'
  editingId.value = prompt.id
  promptForm.value = { title: prompt.label || prompt.key, description: '', category: '', tags: '', content: prompt.value || '' }
  showPromptDialog.value = true
}

function handleCopyPrompt(prompt: any) {
  dialogTitle.value = '复制提示词'
  editingId.value = null
  promptForm.value = { title: (prompt.label || prompt.key) + '_副本', description: '', category: '', tags: '', content: prompt.value || '' }
  showPromptDialog.value = true
}

async function handleDeletePrompt(prompt: any) {
  try {
    await ElMessageBox.confirm(`确定要删除提示词吗？`, '删除确认', { type: 'warning' })
    await api.deleteDictionary(prompt.id)
    await loadPrompts()
    ElMessage.success('删除成功')
  } catch {}
}

function handleAddCategory() {
  categoryForm.value = { name: '', description: '', color: '#409eff' }
  showCategoryDialog.value = true
}

async function handleSavePrompt() {
  if (!promptForm.value.title) {
    ElMessage.warning('请输入标题')
    return
  }
  const data = { type: '提示词', key: promptForm.value.title, value: promptForm.value.content }
  if (editingId.value) {
    await api.updateDictionary(editingId.value, data)
  } else {
    await api.createDictionary(data)
  }
  showPromptDialog.value = false
  await loadPrompts()
  ElMessage.success('保存成功')
}

async function handleSaveCategory() {
  if (!categoryForm.value.name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  await api.createDictionary({ type: '提示词分类', key: categoryForm.value.name, value: categoryForm.value.description })
  showCategoryDialog.value = false
  await loadPrompts()
  ElMessage.success('保存成功')
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="prompt-header">
      <div class="category-tabs">
        <el-button :type="activeCategory === '' ? 'primary' : ''" @click="activeCategory = ''">
          全部 ({{ promptList.length }})
        </el-button>
        <el-button
          v-for="cat in categories"
          :key="cat.id"
          :type="activeCategory === cat.id ? 'primary' : ''"
          @click="activeCategory = cat.id"
        >
          {{ cat.key || cat.label }}
        </el-button>
        <el-button @click="handleAddCategory"><el-icon><Plus /></el-icon>添加分类</el-button>
      </div>
      <div>
        <el-input v-model="searchKeyword" placeholder="搜索提示词..." clearable style="width: 200px; margin-right: 8px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="handleAddPrompt">添加提示词</el-button>
      </div>
    </div>

    <div class="prompt-grid">
      <div v-for="prompt in promptList" :key="prompt.id" class="prompt-card">
        <div class="card-header">
          <h4>{{ prompt.label || prompt.key }}</h4>
          <el-dropdown trigger="click">
            <el-button link size="small"><el-icon><MoreFilled /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleEditPrompt(prompt)">编辑</el-dropdown-item>
                <el-dropdown-item @click="handleCopyPrompt(prompt)">复制</el-dropdown-item>
                <el-dropdown-item @click="handleDeletePrompt(prompt)">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <p>{{ prompt.value?.substring(0, 100) }}{{ prompt.value?.length > 100 ? '...' : '' }}</p>
      </div>
    </div>
    <el-empty v-if="!promptList.length && !loading" description="暂无提示词" />

    <el-dialog v-model="showPromptDialog" :title="dialogTitle" width="600px">
      <el-form label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="promptForm.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="promptForm.category" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.key || cat.label" :value="cat.key || cat.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="提示词内容">
          <el-input v-model="promptForm.content" type="textarea" :rows="8" placeholder="请输入提示词内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPromptDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSavePrompt">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCategoryDialog" title="添加分类" width="400px">
      <el-form label-width="80px">
        <el-form-item label="分类名称" required>
          <el-input v-model="categoryForm.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="categoryForm.description" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCategoryDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.prompt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-lg;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.category-tabs {
  display: flex;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

.prompt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: $spacing-base;
}

.prompt-card {
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
}
</style>
