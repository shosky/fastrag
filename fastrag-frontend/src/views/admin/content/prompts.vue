<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeCategory = ref('')
const searchKeyword = ref('')
const showPromptDialog = ref(false)
const showCategoryDialog = ref(false)
const dialogTitle = ref('添加提示词')

const promptForm = ref({ title: '', description: '', category: '', tags: '', content: '' })
const categoryForm = ref({ name: '', description: '', color: '#409eff' })

const categories = ref([
  { id: '1', name: 'TorchV KB产品线', color: '#409eff', count: 5 },
  { id: '2', name: '通用模板', color: '#67c23a', count: 8 },
])

const promptList = ref([
  { id: '1', title: 'KB问答助手', description: '基于知识库的问答提示词', category: 'TorchV KB产品线', tags: ['KB', '问答'], content: '你是一个专业的知识库问答助手...' },
  { id: '2', title: '文档摘要', description: '生成文档摘要的提示词', category: '通用模板', tags: ['摘要', '文档'], content: '请为以下文档生成简洁的摘要...' },
  { id: '3', title: '代码审查', description: '代码审查提示词', category: '通用模板', tags: ['代码', '审查'], content: '请审查以下代码并提供改进建议...' },
])

function handleAddPrompt() {
  dialogTitle.value = '添加提示词'
  promptForm.value = { title: '', description: '', category: '', tags: '', content: '' }
  showPromptDialog.value = true
}

function handleEditPrompt(prompt: any) {
  dialogTitle.value = '编辑提示词'
  promptForm.value = { ...prompt, tags: prompt.tags.join(',') }
  showPromptDialog.value = true
}

function handleCopyPrompt(prompt: any) {
  dialogTitle.value = '复制提示词'
  promptForm.value = { ...prompt, title: prompt.title + '_副本', tags: prompt.tags.join(',') }
  showPromptDialog.value = true
}

async function handleDeletePrompt(prompt: any) {
  try {
    await ElMessageBox.confirm(`确定要删除提示词「${prompt.title}」吗？`, '删除确认', { type: 'warning' })
    promptList.value = promptList.value.filter(p => p.id !== prompt.id)
    ElMessage.success('删除成功')
  } catch {}
}

function handleAddCategory() {
  categoryForm.value = { name: '', description: '', color: '#409eff' }
  showCategoryDialog.value = true
}

function handleSavePrompt() {
  showPromptDialog.value = false
  ElMessage.success('保存成功')
}

function handleSaveCategory() {
  showCategoryDialog.value = false
  ElMessage.success('保存成功')
}
</script>

<template>
  <div class="page-container">
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
          {{ cat.name }}
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
          <h4>{{ prompt.title }}</h4>
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
        <p>{{ prompt.description }}</p>
        <div class="card-tags">
          <el-tag v-for="tag in prompt.tags" :key="tag" size="small" type="info">{{ tag }}</el-tag>
        </div>
      </div>
    </div>

    <el-dialog v-model="showPromptDialog" :title="dialogTitle" width="600px">
      <el-form label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="promptForm.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="promptForm.description" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="promptForm.category" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="promptForm.tags" placeholder="输入标签后回车" />
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
        <el-form-item label="颜色">
          <el-color-picker v-model="categoryForm.color" />
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

  .card-tags { display: flex; gap: $spacing-xs; }
}
</style>
