<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const kbId = (route.params.id as string) || 'kb_sample'
const activeTab = ref('multiturn')
const loading = ref(false)

// 多轮问答
const mtList = ref<any[]>([])
async function loadMt() { mtList.value = ((await api.getMultiTurnQa(kbId)) as any) || [] }
const showMtDialog = ref(false)
const mtForm = ref({ id: '', title: '', turns: '', category: '', description: '' })
function handleAddMt() { mtForm.value = { id: '', title: '', turns: '', category: '', description: '' }; showMtDialog.value = true }
function handleEditMt(row: any) { mtForm.value = { ...row }; showMtDialog.value = true }
async function handleSaveMt() {
  if (!mtForm.value.title) { ElMessage.warning('请输入标题'); return }
  if (mtForm.value.id) await api.updateMultiTurnQa(kbId, mtForm.value.id, mtForm.value)
  else await api.createMultiTurnQa(kbId, mtForm.value)
  showMtDialog.value = false; await loadMt(); ElMessage.success('保存成功')
}
async function handleDeleteMt(row: any) { try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }); await api.deleteMultiTurnQa(kbId, row.id); await loadMt(); ElMessage.success('删除成功') } catch {} }

// 多模态问答
const mmList = ref<any[]>([])
async function loadMm() { mmList.value = ((await api.getMultimodalQa(kbId)) as any) || [] }
const showMmDialog = ref(false)
const mmForm = ref({ id: '', title: '', question: '', answer: '', modalType: 'text', category: '' })
function handleAddMm() { mmForm.value = { id: '', title: '', question: '', answer: '', modalType: 'text', category: '' }; showMmDialog.value = true }
function handleEditMm(row: any) { mmForm.value = { ...row }; showMmDialog.value = true }
async function handleSaveMm() {
  if (!mmForm.value.title) { ElMessage.warning('请输入标题'); return }
  if (mmForm.value.id) await api.updateMultimodalQa(kbId, mmForm.value.id, mmForm.value)
  else await api.createMultimodalQa(kbId, mmForm.value)
  showMmDialog.value = false; await loadMm(); ElMessage.success('保存成功')
}
async function handleDeleteMm(row: any) { try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }); await api.deleteMultimodalQa(kbId, row.id); await loadMm(); ElMessage.success('删除成功') } catch {} }

// 文档导读
const dgList = ref<any[]>([])
async function loadDg() { dgList.value = ((await api.getDocGuides(kbId)) as any) || [] }
const showDgDialog = ref(false)
const dgForm = ref({ id: '', fileId: '', title: '', category: '' })
function handleAddDg() { dgForm.value = { id: '', fileId: '', title: '', category: '' }; showDgDialog.value = true }
async function handleSaveDg() {
  if (!dgForm.value.title) { ElMessage.warning('请输入标题'); return }
  await api.createDocGuide(kbId, dgForm.value); showDgDialog.value = false; await loadDg(); ElMessage.success('创建成功')
}
async function handleIndexDg(row: any) { await api.indexDocGuide(kbId, row.id); await loadDg(); ElMessage.success('索引完成') }
async function handleDeleteDg(row: any) { try { await ElMessageBox.confirm('确认删除？', '删除确认', { type: 'warning' }); await api.deleteDocGuide(kbId, row.id); await loadDg(); ElMessage.success('删除成功') } catch {} }

onMounted(() => { loadMt(); loadMm(); loadDg() })
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="多轮问答" name="multiturn">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">多轮问答管理</div><el-button type="primary" @click="handleAddMt">新增</el-button></div>
          <el-table :data="mtList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="120" />
            <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><el-tag size="small">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" size="small" @click="handleEditMt(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDeleteMt(row)">删除</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
      <el-tab-pane label="多模态问答" name="multimodal">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">多模态问答管理</div><el-button type="primary" @click="handleAddMm">新增</el-button></div>
          <el-table :data="mmList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="modalType" label="模态" width="90" />
            <el-table-column prop="question" label="问题" show-overflow-tooltip />
            <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" size="small" @click="handleEditMm(row)">编辑</el-button><el-button link type="danger" size="small" @click="handleDeleteMm(row)">删除</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
      <el-tab-pane label="文档导读" name="docguide">
        <div class="card-panel">
          <div class="section-header"><div class="section-title">文档导读管理</div><el-button type="primary" @click="handleAddDg">新增导读</el-button></div>
          <el-table :data="dgList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="fileId" label="文件" width="140" />
            <el-table-column prop="indexStatus" label="索引状态" width="100"><template #default="{ row }"><el-tag :type="row.indexStatus==='completed'?'success':'warning'" size="small">{{ row.indexStatus }}</el-tag></template></el-table-column>
            <el-table-column prop="indexProgress" label="进度" width="80" />
            <el-table-column label="操作" width="140"><template #default="{ row }"><el-button v-if="row.indexStatus!=='completed'" link type="success" size="small" @click="handleIndexDg(row)">索引</el-button><el-button link type="danger" size="small" @click="handleDeleteDg(row)">删除</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showMtDialog" :title="mtForm.id?'编辑多轮问答':'新增多轮问答'" width="600px">
      <el-form label-width="70px">
        <el-form-item label="标题" required><el-input v-model="mtForm.title" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="mtForm.category" /></el-form-item>
        <el-form-item label="对话轮次"><el-input v-model="mtForm.turns" type="textarea" :rows="6" placeholder='JSON数组，如 [{"turnIndex":1,"question":"如何退款","answer":"..."}]' /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showMtDialog=false">取消</el-button><el-button type="primary" @click="handleSaveMt">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="showMmDialog" :title="mmForm.id?'编辑多模态问答':'新增多模态问答'" width="600px">
      <el-form label-width="70px">
        <el-form-item label="标题" required><el-input v-model="mmForm.title" /></el-form-item>
        <el-form-item label="模态"><el-select v-model="mmForm.modalType" style="width:140px"><el-option label="文本" value="text" /><el-option label="图片" value="image" /><el-option label="音频" value="audio" /><el-option label="视频" value="video" /><el-option label="混合" value="mixed" /></el-select></el-form-item>
        <el-form-item label="问题"><el-input v-model="mmForm.question" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="答案"><el-input v-model="mmForm.answer" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showMmDialog=false">取消</el-button><el-button type="primary" @click="handleSaveMm">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="showDgDialog" title="新增文档导读" width="500px">
      <el-form label-width="70px">
        <el-form-item label="标题" required><el-input v-model="dgForm.title" /></el-form-item>
        <el-form-item label="文件ID"><el-input v-model="dgForm.fileId" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="dgForm.category" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showDgDialog=false">取消</el-button><el-button type="primary" @click="handleSaveDg">创建</el-button></template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
</style>
