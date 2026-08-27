<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'
import type { KbTag } from '@/types/knowledge'

const activeGroup = ref('')
const searchKeyword = ref('')
const showTagDialog = ref(false)
const showGroupDialog = ref(false)
const dialogTitle = ref('新增标签')
const loading = ref(false)
const editingId = ref<string | null>(null)

const tagForm = ref({ type: '知识库', name: '', group: '' })
const groupForm = ref({ name: '' })

const tagGroups = ref<any[]>([])
const tagList = ref<any[]>([])

// ===== 标签维度：通用标签(dict) / 知识库标签(kb_tag) 分册四 =====
const dimension = ref<'dict' | 'kb'>('dict')
const kbTags = ref<KbTag[]>([])
const kbLoading = ref(false)
const showKbTagDialog = ref(false)
const kbTagEditingId = ref<string | null>(null)
const kbTagForm = ref({ kbId: '', name: '', color: '#409EFF', tagTypeId: '', description: '' })
const kbOptions = ref<any[]>([])
const tagTypeOptions = [
  { label: '文档分类', value: 'T1' },
  { label: '优先级', value: 'T2' },
  { label: '状态', value: 'T3' },
]

async function loadTags() {
  loading.value = true
  try {
    const [groupsRes, tagsRes] = await Promise.all([
      api.getDictionaries({ type: '标签组' }),
      api.getDictionaries({ type: '标签' }),
    ])
    tagGroups.value = (groupsRes as any)?.['标签组'] || []
    tagList.value = (tagsRes as any)?.['标签'] || []
  } finally {
    loading.value = false
  }
}

async function loadKbTags() {
  kbLoading.value = true
  try {
    const res: any = await api.getAllKbTags()
    kbTags.value = res || []
  } finally {
    kbLoading.value = false
  }
}

async function loadKbOptions() {
  try {
    const res: any = await api.getKnowledgeBases({ page: 1, pageSize: 100 })
    kbOptions.value = res?.list || res || []
  } catch {
    kbOptions.value = []
  }
}

onMounted(() => { loadTags(); loadKbTags(); loadKbOptions() })

function handleAddTag() {
  dialogTitle.value = '新增标签'
  editingId.value = null
  tagForm.value = { type: '知识库', name: '', group: '' }
  showTagDialog.value = true
}

function handleEditTag(tag: any) {
  dialogTitle.value = '编辑标签'
  editingId.value = tag.id
  tagForm.value = { type: tag.key || '知识库', name: tag.label || '', group: tag.value || '' }
  showTagDialog.value = true
}

async function handleDeleteTag(tag: any) {
  try {
    await ElMessageBox.confirm('是否删除该标签？', '删除确认', { type: 'warning' })
    await api.deleteDictionary(tag.id)
    await loadTags()
    ElMessage.success('删除成功')
  } catch {}
}

function handleAddGroup() {
  groupForm.value = { name: '' }
  showGroupDialog.value = true
}

async function handleSaveTag() {
  if (!tagForm.value.name) {
    ElMessage.warning('请输入标签名称')
    return
  }
  const data = { type: '标签', key: tagForm.value.type, value: tagForm.value.group, label: tagForm.value.name }
  if (editingId.value) {
    await api.updateDictionary(editingId.value, data)
  } else {
    await api.createDictionary(data)
  }
  showTagDialog.value = false
  await loadTags()
  ElMessage.success('保存成功')
}

async function handleSaveGroup() {
  if (!groupForm.value.name) {
    ElMessage.warning('请输入标签组名称')
    return
  }
  await api.createDictionary({ type: '标签组', key: groupForm.value.name, value: '0' })
  showGroupDialog.value = false
  await loadTags()
  ElMessage.success('保存成功')
}

// ===== 知识库标签 CRUD =====
function handleAddKbTag() {
  kbTagEditingId.value = null
  kbTagForm.value = { kbId: '', name: '', color: '#409EFF', tagTypeId: '', description: '' }
  showKbTagDialog.value = true
}

function handleEditKbTag(tag: KbTag) {
  kbTagEditingId.value = tag.id
  kbTagForm.value = {
    kbId: tag.kbId,
    name: tag.name,
    color: tag.color || '#409EFF',
    tagTypeId: tag.tagTypeId || '',
    description: tag.description || '',
  }
  showKbTagDialog.value = true
}

async function handleSaveKbTag() {
  if (!kbTagForm.value.name) {
    ElMessage.warning('请输入标签名称')
    return
  }
  const payload = {
    name: kbTagForm.value.name,
    color: kbTagForm.value.color,
    tagTypeId: kbTagForm.value.tagTypeId || undefined,
    description: kbTagForm.value.description,
  }
  if (kbTagEditingId.value) {
    await api.updateKbTag(kbTagEditingId.value, payload)
  } else {
    if (!kbTagForm.value.kbId) {
      ElMessage.warning('请选择归属知识库')
      return
    }
    await api.createKbTag({ kbId: kbTagForm.value.kbId, ...payload })
  }
  showKbTagDialog.value = false
  await loadKbTags()
  ElMessage.success('保存成功')
}

async function handleDeleteKbTag(tag: KbTag) {
  const tip = (tag.usageCount || 0) > 0
    ? `该标签当前被 ${tag.usageCount} 个目标引用，删除需先移除这些引用。确定继续？`
    : '是否删除该知识库标签？'
  try {
    await ElMessageBox.confirm(tip, '删除确认', { type: 'warning' })
    await api.deleteKbTag(tag.id)
    await loadKbTags()
    ElMessage.success('删除成功')
  } catch {}
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="tag-dimension">
      <el-radio-group v-model="dimension">
        <el-radio-button value="dict">通用标签</el-radio-button>
        <el-radio-button value="kb">知识库标签</el-radio-button>
      </el-radio-group>
      <span class="tag-dimension__desc">知识库标签用于文件打标与检索过滤（分册四），归属各知识库。</span>
    </div>

    <!-- ============ 通用标签（dict 维度） ============ -->
    <div v-if="dimension === 'dict'" class="tag-layout">
      <div class="tag-sidebar">
        <div class="sidebar-header">
          <span>标签组</span>
          <el-button link size="small" @click="handleAddGroup"><el-icon><Plus /></el-icon></el-button>
        </div>
        <div
          class="group-item"
          :class="{ active: activeGroup === '' }"
          @click="activeGroup = ''"
        >
          <span>全部</span>
        </div>
        <div
          v-for="g in tagGroups"
          :key="g.id"
          class="group-item"
          :class="{ active: activeGroup === g.id }"
          @click="activeGroup = g.id"
        >
          <span>{{ g.key || g.label }}</span>
        </div>
      </div>
      <div class="tag-content">
        <div class="section-header">
          <div class="section-title">标签管理</div>
          <el-button type="primary" @click="handleAddTag">新增标签</el-button>
        </div>
        <el-table :data="tagList" stripe>
          <el-table-column prop="label" label="标签名称" />
          <el-table-column prop="key" label="标签类型" width="120" />
          <el-table-column prop="value" label="所属标签组" width="120" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleEditTag(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDeleteTag(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!tagList.length && !loading" description="暂无标签" />
      </div>
    </div>

    <!-- ============ 知识库标签（kb_tag 维度, 分册四） ============ -->
    <div v-else class="kb-tag-content" v-loading="kbLoading">
      <div class="section-header">
        <div class="section-title">知识库标签管理</div>
        <el-button type="primary" @click="handleAddKbTag">新增标签</el-button>
      </div>
      <el-table :data="kbTags" stripe>
        <el-table-column label="标签名称" min-width="140">
          <template #default="{ row }">
            <el-tag :color="row.color || '#409EFF'" style="color: #fff; border: none">{{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归属知识库" prop="kbId" width="120" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ ({ T1: '文档分类', T2: '优先级', T3: '状态' } as Record<string, string>)[row.tagTypeId] || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column prop="usageCount" label="引用数" width="80" align="center" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEditKbTag(row as KbTag)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteKbTag(row as KbTag)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!kbTags.length && !kbLoading" description="暂无知识库标签" />
    </div>

    <el-dialog v-model="showTagDialog" :title="dialogTitle" width="400px">
      <el-form label-width="80px">
        <el-form-item label="标签类型">
          <el-select v-model="tagForm.type" style="width: 100%">
            <el-option label="知识库" value="知识库" />
            <el-option label="页面文档" value="页面文档" />
            <el-option label="应用中心" value="应用中心" />
            <el-option label="提示词" value="提示词" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签名称" required>
          <el-input v-model="tagForm.name" placeholder="请输入标签名称" />
        </el-form-item>
        <el-form-item label="标签组">
          <el-select v-model="tagForm.group" style="width: 100%">
            <el-option v-for="g in tagGroups" :key="g.id" :label="g.key || g.label" :value="g.key || g.label" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTagDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTag">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showGroupDialog" title="添加标签组" width="400px">
      <el-form label-width="80px">
        <el-form-item label="标签组名称" required>
          <el-input v-model="groupForm.name" placeholder="请输入标签组名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGroupDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveGroup">确定</el-button>
      </template>
    </el-dialog>

    <!-- 知识库标签新增/编辑（分册四） -->
    <el-dialog v-model="showKbTagDialog" :title="kbTagEditingId ? '编辑知识库标签' : '新增知识库标签'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="归属知识库" required>
          <el-select v-model="kbTagForm.kbId" style="width: 100%" :disabled="!!kbTagEditingId" placeholder="选择知识库">
            <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签名称" required>
          <el-input v-model="kbTagForm.name" placeholder="请输入标签名称" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="kbTagForm.color" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="kbTagForm.tagTypeId" clearable placeholder="选择标签类型" style="width: 100%">
            <el-option v-for="t in tagTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="kbTagForm.description" placeholder="标签描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showKbTagDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveKbTag">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.tag-layout {
  display: flex;
  gap: $spacing-base;
  height: calc(100vh - 120px);
}

.tag-sidebar {
  width: 200px;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-base;
  flex-shrink: 0;

  .sidebar-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
    margin-bottom: $spacing-base;
  }
}

.group-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm $spacing-base;
  border-radius: $radius-sm;
  cursor: pointer;
  font-size: 13px;
  &:hover { background: $bg-hover; }
  &.active { background: #ecf5ff; color: $color-primary; }
}

.tag-content {
  flex: 1;
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
}

.tag-dimension {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  margin-bottom: $spacing-base;

  &__desc {
    font-size: 12px;
    color: $text-secondary;
  }
}

.kb-tag-content {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
}
</style>
