<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const kbId = (route.params.id as string) || 'kb_sample'

const activeTab = ref('tagType')

// 标签类型
const tagTypeList = ref<any[]>([])
async function loadTagTypes() { tagTypeList.value = ((await api.getTagTypes(kbId)) as any) || [] }

const showTagTypeDialog = ref(false)
const tagTypeForm = ref({ id: '', name: '', description: '', color: '#1890ff', icon: 'folder' })

function handleAddTagType() { tagTypeForm.value = { id: '', name: '', description: '', color: '#1890ff', icon: 'folder' }; showTagTypeDialog.value = true }
function handleEditTagType(row: any) { tagTypeForm.value = { ...row }; showTagTypeDialog.value = true }
async function handleSaveTagType() {
  if (!tagTypeForm.value.name) { ElMessage.warning('请输入名称'); return }
  if (tagTypeForm.value.id) await api.updateTagType(kbId, tagTypeForm.value.id, tagTypeForm.value)
  else await api.createTagType(kbId, tagTypeForm.value)
  showTagTypeDialog.value = false; await loadTagTypes(); ElMessage.success('保存成功')
}
async function handleDeleteTagType(row: any) {
  try { await ElMessageBox.confirm('删除标签类型会解除其下标签的关联，确认？', '删除确认', { type: 'warning' })
    await api.deleteTagType(kbId, row.id); await loadTagTypes(); ElMessage.success('删除成功') } catch {}
}

// 标签
const tagList = ref<any[]>([])
const tagQuery = ref({ tagTypeId: '', keyword: '' })
async function loadTags() { tagList.value = ((await api.getTags(kbId, { tagTypeId: tagQuery.value.tagTypeId || undefined, keyword: tagQuery.value.keyword || undefined })) as any) || [] }

const showTagDialog = ref(false)
const tagForm = ref({ id: '', name: '', tagTypeId: '', color: '', description: '' })
function handleAddTag() { tagForm.value = { id: '', name: '', tagTypeId: '', color: '', description: '' }; showTagDialog.value = true }
function handleEditTag(row: any) { tagForm.value = { ...row }; showTagDialog.value = true }
async function handleSaveTag() {
  if (!tagForm.value.name) { ElMessage.warning('请输入标签名'); return }
  if (tagForm.value.id) await api.updateTag(kbId, tagForm.value.id, tagForm.value)
  else await api.createTag(kbId, tagForm.value)
  showTagDialog.value = false; await loadTags(); ElMessage.success('保存成功')
}
async function handleDeleteTag(row: any) {
  try { await ElMessageBox.confirm('确认删除该标签？', '删除确认', { type: 'warning' })
    await api.deleteTag(kbId, row.id); await loadTags(); ElMessage.success('删除成功') } catch {}
}

// 标签关联知识管理
const showTagKnowledgeDialog = ref(false)
const tagKnowledgeList = ref<any[]>([])
const currentTag = ref<any>(null)

async function handleShowTagKnowledge(row: any) {
  currentTag.value = row
  try {
    const res: any = await api.getTagKnowledge(kbId, row.id)
    tagKnowledgeList.value = Array.isArray(res) ? res : (res?.list || [])
    showTagKnowledgeDialog.value = true
  } catch {
    tagKnowledgeList.value = []
    showTagKnowledgeDialog.value = true
  }
}

async function handleDisassociateTag(knowledgeId: string, knowledgeTitle: string) {
  if (!currentTag.value) return
  try {
    await ElMessageBox.confirm(`确定取消「${knowledgeTitle}」与标签「${currentTag.value.name}」的关联？`, '取消关联确认', { type: 'warning' })
    await api.disassociateTag(kbId, currentTag.value.id, knowledgeId)
    await handleShowTagKnowledge(currentTag.value)
    await loadTags()
    ElMessage.success('已取消关联')
  } catch { /* cancelled */ }
}

// 分类辅助函数
function getTypeName(tagTypeId: string): string {
  const t = tagTypeList.value.find((t: any) => t.id === tagTypeId)
  return t?.name || ''
}
const TAG_TYPE_COLORS = ['', 'success', 'warning', 'danger', 'info', 'primary']
function getTypeTagType(tagTypeId: string): string {
  const idx = tagTypeList.value.findIndex((t: any) => t.id === tagTypeId)
  return idx >= 0 ? TAG_TYPE_COLORS[idx % TAG_TYPE_COLORS.length] : 'info'
}

// 笔记
const noteList = ref<any[]>([])
async function loadNotes() { noteList.value = ((await api.getNotes(kbId)) as any) || [] }

const showNoteDialog = ref(false)
const noteForm = ref({ id: '', title: '', content: '', targetType: '', targetId: '', tags: '' })
function handleAddNote() { noteForm.value = { id: '', title: '', content: '', targetType: '', targetId: '', tags: '' }; showNoteDialog.value = true }
function handleEditNote(row: any) { noteForm.value = { ...row }; showNoteDialog.value = true }
async function handleSaveNote() {
  if (!noteForm.value.title) { ElMessage.warning('请输入标题'); return }
  if (noteForm.value.id) await api.updateNote(kbId, noteForm.value.id, noteForm.value)
  else await api.createNote(kbId, noteForm.value)
  showNoteDialog.value = false; await loadNotes(); ElMessage.success('保存成功')
}
async function handleDeleteNote(row: any) {
  try { await ElMessageBox.confirm('确认删除该笔记？', '删除确认', { type: 'warning' })
    await api.deleteNote(kbId, row.id); await loadNotes(); ElMessage.success('删除成功') } catch {}
}
async function handleExportNotes() {
  const res: any = await api.exportNotes(kbId)
  ElMessage.success(`导出 ${res?.count || 0} 条笔记`)
}

onMounted(() => { loadTagTypes(); loadTags(); loadNotes() })
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="标签类型" name="tagType">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">标签类型管理</div>
            <el-button type="primary" @click="handleAddTagType">新建类型</el-button>
          </div>
          <el-table :data="tagTypeList" stripe>
            <el-table-column prop="name" label="类型名称" width="160" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column label="颜色" width="80">
              <template #default="{ row }"><span class="color-dot" :style="{ background: row.color || '#ccc' }"></span></template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditTagType(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteTagType(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="标签" name="tag">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">标签管理</div>
            <el-button type="primary" @click="handleAddTag">新建标签</el-button>
          </div>
          <div class="filter-bar">
            <el-select v-model="tagQuery.tagTypeId" placeholder="标签类型" clearable style="width: 160px" @change="loadTags">
              <el-option v-for="t in tagTypeList" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
            <el-input v-model="tagQuery.keyword" placeholder="搜索标签" clearable style="width: 180px" @keyup.enter="loadTags" />
            <el-button type="primary" @click="loadTags">查询</el-button>
          </div>
          <el-table :data="tagList" stripe>
            <el-table-column prop="name" label="标签名" width="160" />
            <el-table-column label="分类" width="140">
              <template #default="{ row }">
                <el-tag v-if="row.tagTypeId" size="small" :type="getTypeTagType(row.tagTypeId)">
                  {{ getTypeName(row.tagTypeId) }}
                </el-tag>
                <span v-else style="color:#c0c4cc">未分类</span>
              </template>
            </el-table-column>
            <el-table-column label="颜色" width="80">
              <template #default="{ row }"><span class="color-dot" :style="{ background: row.color || '#ccc' }"></span></template>
            </el-table-column>
            <el-table-column prop="usageCount" label="使用次数" width="100" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleShowTagKnowledge(row)">关联知识</el-button>
                <el-button link type="primary" size="small" @click="handleEditTag(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteTag(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="笔记" name="note">
        <div class="card-panel">
          <div class="section-header">
            <div class="section-title">笔记管理</div>
            <div>
              <el-button @click="handleExportNotes">导出</el-button>
              <el-button type="primary" @click="handleAddNote">新增笔记</el-button>
            </div>
          </div>
          <el-table :data="noteList" stripe>
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="targetType" label="关联类型" width="100" />
            <el-table-column prop="createdBy" label="创建者" width="100" />
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleEditNote(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteNote(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 标签类型弹窗 -->
    <el-dialog v-model="showTagTypeDialog" :title="tagTypeForm.id ? '编辑类型' : '新建类型'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="tagTypeForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="tagTypeForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="颜色"><el-color-picker v-model="tagTypeForm.color" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showTagTypeDialog = false">取消</el-button><el-button type="primary" @click="handleSaveTagType">保存</el-button></template>
    </el-dialog>

    <!-- 标签弹窗 -->
    <el-dialog v-model="showTagDialog" :title="tagForm.id ? '编辑标签' : '新建标签'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="tagForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="tagForm.tagTypeId" clearable style="width: 200px">
            <el-option v-for="t in tagTypeList" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="颜色"><el-color-picker v-model="tagForm.color" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="tagForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showTagDialog = false">取消</el-button><el-button type="primary" @click="handleSaveTag">保存</el-button></template>
    </el-dialog>

    <!-- 标签关联知识弹窗 -->
    <el-dialog v-model="showTagKnowledgeDialog" :title="'关联知识 - ' + (currentTag?.name || '')" width="650px">
      <div v-if="tagKnowledgeList.length === 0" style="text-align:center;padding:40px;color:#909399">暂无关联知识</div>
      <el-table v-else :data="tagKnowledgeList" stripe size="small">
        <el-table-column prop="title" label="知识标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status==='published'?'success':(row.status==='archived'?'info':'warning')" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="handleDisassociateTag(row.id, row.title)">取消关联</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="showTagKnowledgeDialog = false">关闭</el-button></template>
    </el-dialog>

    <!-- 笔记弹窗 -->
    <el-dialog v-model="showNoteDialog" :title="noteForm.id ? '编辑笔记' : '新增笔记'" width="600px">
      <el-form label-width="80px">
        <el-form-item label="标题" required><el-input v-model="noteForm.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="noteForm.content" type="textarea" :rows="6" placeholder="支持 Markdown" /></el-form-item>
        <el-form-item label="关联类型"><el-input v-model="noteForm.targetType" placeholder="document/image..." /></el-form-item>
        <el-form-item label="关联ID"><el-input v-model="noteForm.targetId" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="noteForm.tags" placeholder="逗号分隔" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showNoteDialog = false">取消</el-button><el-button type="primary" @click="handleSaveNote">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: $spacing-base; }
.section-title { font-size: 15px; font-weight: 600; }
.color-dot { display: inline-block; width: 16px; height: 16px; border-radius: 50%; }
</style>
