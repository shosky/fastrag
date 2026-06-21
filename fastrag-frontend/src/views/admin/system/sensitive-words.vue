<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const showDialog = ref(false)
const dialogTitle = ref('添加敏感词')

const formData = ref({
  word: '',
  reply: '',
  blockInput: false,
  blockSearch: false,
  replaceAnswer: false,
})

const wordList = ref([
  { id: '1', word: '演示敏感词_01', reply: '该内容涉及敏感词，请修改后再提交。', blockInput: true, blockSearch: false, replaceAnswer: false },
  { id: '2', word: '测试屏蔽词', reply: '', blockInput: false, blockSearch: true, replaceAnswer: false },
  { id: '3', word: '替换词', reply: '', blockInput: false, blockSearch: false, replaceAnswer: true },
])

function handleAdd() {
  dialogTitle.value = '添加敏感词'
  formData.value = { word: '', reply: '', blockInput: false, blockSearch: false, replaceAnswer: false }
  showDialog.value = true
}

function handleEdit(row: any) {
  dialogTitle.value = '编辑敏感词'
  formData.value = { ...row }
  showDialog.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该敏感词？', '删除确认', { type: 'warning' })
    wordList.value = wordList.value.filter(w => w.id !== row.id)
    ElMessage.success('删除成功')
  } catch {}
}

function handleSave() {
  if (!formData.value.word) {
    ElMessage.warning('请输入敏感词')
    return
  }
  if (dialogTitle.value === '添加敏感词') {
    wordList.value.push({ ...formData.value, id: String(Date.now()) })
  }
  showDialog.value = false
  ElMessage.success('保存成功')
}

function handleDownloadTemplate() {
  ElMessage.info('模板下载中...')
}

function handleBatchImport() {
  ElMessage.info('批量导入功能')
}
</script>

<template>
  <div class="page-container">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">敏感词设置</div>
        <div>
          <el-button @click="handleDownloadTemplate">下载模板</el-button>
          <el-button @click="handleBatchImport">批量导入</el-button>
          <el-button type="primary" @click="handleAdd">添加敏感词</el-button>
        </div>
      </div>

      <el-table :data="wordList" stripe>
        <el-table-column prop="word" label="敏感词" />
        <el-table-column prop="reply" label="指定回复" show-overflow-tooltip />
        <el-table-column label="阻止用户输入" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.blockInput ? 'danger' : 'info'" size="small">{{ row.blockInput ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="联网检索屏蔽" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.blockSearch ? 'danger' : 'info'" size="small">{{ row.blockSearch ? '是' : '否' }}</el-tag>
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

    <el-dialog v-model="showDialog" :title="dialogTitle" width="500px">
      <el-form label-width="120px">
        <el-form-item label="敏感词" required>
          <el-input v-model="formData.word" placeholder="请输入敏感词" />
        </el-form-item>
        <el-form-item label="指定回复">
          <el-input v-model="formData.reply" type="textarea" :rows="3" placeholder="请输入指定回复内容" />
        </el-form-item>
        <el-form-item label="阻止用户输入">
          <el-switch v-model="formData.blockInput" />
        </el-form-item>
        <el-form-item label="联网检索屏蔽">
          <el-switch v-model="formData.blockSearch" />
        </el-form-item>
        <el-form-item label="模型生成答案时替换">
          <el-switch v-model="formData.replaceAnswer" />
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

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}
</style>
