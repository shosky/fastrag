<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const kbId = (route.params.id as string) || 'kb_sample'
const activeTab = ref('knowledge')
const loading = ref(false)

// 知识管理
const knowledgeList = ref<any[]>([])
const knowledgeQuery = ref({ keyword: '', category: '' })
async function loadKnowledge() {
  loading.value = true
  try { knowledgeList.value = ((await api.getKnowledgeList(kbId, knowledgeQuery.value)) as any) || [] } finally { loading.value = false }
}
const showKnowledgeDialog = ref(false)
const knowledgeForm = ref({ id: '', title: '', content: '', summary: '', category: '', tags: '', status: 'draft' })
function handleAddKnowledge() { knowledgeForm.value = { id: '', title: '', content: '', summary: '', category: '', tags: '', status: 'draft' }; showKnowledgeDialog.value = true }
function handleEditKnowledge(row: any) { knowledgeForm.value = { ...row }; showKnowledgeDialog.value = true }
async function handleSaveKnowledge() {
  if (!knowledgeForm.value.title) { ElMessage.warning('请输入标题'); return }
  if (knowledgeForm.value.id) await api.updateKnowledge(kbId, knowledgeForm.value.id, knowledgeForm.value)
  else await api.createKnowledge(kbId, knowledgeForm.value)
  showKnowledgeDialog.value = false; await loadKnowledge(); ElMessage.success('保存成功')
}
async function handleDeleteKnowledge(row: any) {
  try { await ElMessageBox.confirm('确认删除该知识？', '删除确认', { type: 'warning' })
    await api.deleteKnowledge(kbId, row.id); await loadKnowledge(); ElMessage.success('删除成功') } catch {}
}

// 知识更新
const updateList = ref<any[]>([])
const updateQuery = ref({ status: '', page: 1, pageSize: 10 })
const updateTotal = ref(0)
async function loadUpdates() {
  const res: any = await api.getKnowledgeUpdates(kbId, { status: updateQuery.value.status || undefined, page: updateQuery.value.page, pageSize: updateQuery.value.pageSize })
  updateList.value = res?.list || []; updateTotal.value = res?.total || 0
}
const showUpdateDialog = ref(false)
const updateForm = ref({ id: '', knowledgeId: '', updateType: 'update', title: '', oldValue: '', newValue: '', changeSummary: '' })
function handleAddUpdate() { updateForm.value = { id: '', knowledgeId: '', updateType: 'update', title: '', oldValue: '', newValue: '', changeSummary: '' }; showUpdateDialog.value = true }
async function handleSaveUpdate() {
  if (!updateForm.value.title) { ElMessage.warning('请输入标题'); return }
  if (updateForm.value.id) await api.updateKnowledgeUpdate(kbId, updateForm.value.id, updateForm.value)
  else await api.createKnowledgeUpdate(kbId, updateForm.value)
  showUpdateDialog.value = false; await loadUpdates(); ElMessage.success('保存成功')
}
async function handleApplyUpdate(row: any) { await api.applyKnowledgeUpdate(kbId, row.id); await loadUpdates(); ElMessage.success('已应用') }
async function handleRollbackUpdate(row: any) { await api.rollbackKnowledgeUpdate(kbId, row.id); await loadUpdates(); ElMessage.success('已回滚') }
async function handleDeleteUpdate(row: any) {
  try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' })
    await api.deleteKnowledgeUpdate(kbId, row.id); await loadUpdates(); ElMessage.success('删除成功') } catch {}
}

onMounted(() => { loadKnowledge(); loadUpdates() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="知识管理" name="knowledge">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识条目管理</div>
            <el-button type="primary" @click="handleAddKnowledge">新增知识</el-button>
          </div>
          <div class="filter-bar">
            <el-input v-model="knowledgeQuery.keyword" placeholder="搜索标题" clearable style="width:200px" @keyup.enter="loadKnowledge" />
            <el-input v-model="knowledgeQuery.category" placeholder="分类" clearable style="width:150px" @keyup.enter="loadKnowledge" />
            <el-button type="primary" @click="loadKnowledge">查询</el-button>
          </div>
          <el-table :data="knowledgeList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="120" />
            <el-table-column prop="source" label="来源" width="90" />
            <el-table-column prop="version" label="版本" width="70" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }"><el-tag :type="row.status==='published'?'success':(row.status==='archived'?'info':'warning')" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="viewCount" label="查看数" width="80" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditKnowledge(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteKnowledge(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="知识更新" name="update">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">知识更新管理</div>
            <el-button type="primary" @click="handleAddUpdate">新增更新</el-button>
          </div>
          <div class="filter-bar">
            <el-select v-model="updateQuery.status" placeholder="状态" clearable style="width:120px" @change="updateQuery.page=1; loadUpdates()">
              <el-option label="待处理" value="pending" /><el-option label="已应用" value="applied" /><el-option label="已回滚" value="rolled_back" />
            </el-select>
          </div>
          <el-table :data="updateList" stripe>
            <el-table-column prop="title" label="更新标题" show-overflow-tooltip />
            <el-table-column prop="updateType" label="类型" width="80" />
            <el-table-column prop="changeSummary" label="变更摘要" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }"><el-tag :type="row.status==='applied'?'success':(row.status==='rolled_back'?'info':'warning')" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button v-if="row.status==='pending'" link type="success" size="small" @click="handleApplyUpdate(row)">应用</el-button>
                <el-button v-if="row.status==='applied'" link type="warning" size="small" @click="handleRollbackUpdate(row)">回滚</el-button>
                <el-button v-if="row.status==='pending'" link type="danger" size="small" @click="handleDeleteUpdate(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="updateTotal>0" class="table-footer" background layout="total,prev,pager,next" :total="updateTotal" :current-page="updateQuery.page" :page-size="updateQuery.pageSize" @current-change="(p:number)=>{updateQuery.page=p;loadUpdates()}" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showKnowledgeDialog" :title="knowledgeForm.id?'编辑知识':'新增知识'" width="600px">
      <el-form label-width="70px">
        <el-form-item label="标题" required><el-input v-model="knowledgeForm.title" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="knowledgeForm.category" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="knowledgeForm.summary" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="knowledgeForm.content" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="knowledgeForm.tags" placeholder="JSON数组" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showKnowledgeDialog=false">取消</el-button><el-button type="primary" @click="handleSaveKnowledge">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="showUpdateDialog" :title="updateForm.id?'编辑更新':'新增更新'" width="600px">
      <el-form label-width="80px">
        <el-form-item label="标题" required><el-input v-model="updateForm.title" /></el-form-item>
        <el-form-item label="知识ID"><el-input v-model="updateForm.knowledgeId" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="updateForm.updateType" style="width:140px"><el-option label="新建" value="create" /><el-option label="更新" value="update" /><el-option label="删除" value="delete" /><el-option label="归档" value="archive" /></el-select>
        </el-form-item>
        <el-form-item label="变更摘要"><el-input v-model="updateForm.changeSummary" /></el-form-item>
        <el-form-item label="新值"><el-input v-model="updateForm.newValue" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showUpdateDialog=false">取消</el-button><el-button type="primary" @click="handleSaveUpdate">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.table-footer { margin-top: $spacing-base; display: flex; justify-content: flex-end; }
</style>
