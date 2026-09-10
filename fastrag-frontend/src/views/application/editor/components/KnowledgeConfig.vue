<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as api from '@/api'

// ===========================================================================
// 应用管理 - 知识库配置
// 完全基于后端真实数据：
//   - 知识库分类/列表        => GET /api/kb/categories、GET /api/kb
//   - 应用已绑定的知识库      => GET /api/apps/{appId}/knowledge-bases
//   - 新建/编辑/删除知识库    => POST/PUT/DELETE /api/kb
//   - 绑定/解绑              => POST/DELETE /api/apps/{appId}/knowledge-bases
//   - 检索开关持久化          => GET/PUT /api/apps/{appId}/knowledge-bases/settings
//   - 导出/导入绑定配置      => GET/POST /api/apps/{appId}/knowledge-bases/export|import
// ===========================================================================
const props = defineProps<{ appInfo?: { id: string } }>()
const appId = () => props.appInfo?.id || ''
const hasApp = computed(() => !!props.appInfo?.id)

const loading = ref(false)
const saving = ref(false)

// 知识库配置开关
const specifyRetrieval = ref(true) // 指定知识库检索
const bindPersonalKB = ref('no') // 绑定个人知识库：yes / no

// 真实数据
const categories = ref<any[]>([]) // 分类列表
const knowledgeBases = ref<any[]>([]) // 全部知识库
const bindings = ref<any[]>([]) // 当前应用已绑定
const boundKbIds = ref<Set<string>>(new Set()) // 已绑定知识库 id 集合

// 搜索/筛选
const kbSearchKeyword = ref('')
const categorySearchKeyword = ref('')
const selectedCategory = ref('all')

// 新建/编辑知识库弹窗
const showKbDialog = ref(false)
const isEditKb = ref(false)
const editingKbId = ref('')
const tagInput = ref('')
const embeddingModels = ref<any[]>([])
function emptyKbForm() {
  return { name: '', category: '', description: '', embeddingModel: '', permission: 'team', tags: [] as string[] }
}
const kbForm = ref(emptyKbForm())

// ===== 数据加载 =====
async function loadCategories() {
  const res: any = await api.getKnowledgeBaseCategories().catch(() => [])
  categories.value = (Array.isArray(res) ? res : (res as any)?.list || []).map((c: any) => ({
    id: c.id || c.name,
    name: c.name,
    color: c.color || '',
  }))
}
async function loadKBs() {
  const res: any = await api.getKnowledgeBases().catch(() => [])
  knowledgeBases.value = (res as any)?.list || res || []
}
async function loadBindings() {
  bindings.value = hasApp.value ? ((await api.getAppKbBindings(appId()).catch(() => [])) as any) || [] : []
  boundKbIds.value = new Set(bindings.value.map((b: any) => b.kbId))
}
async function loadSettings() {
  if (!hasApp.value) return
  try {
    const r: any = await api.getAppKbSettings(appId())
    if (r && typeof r.specifyRetrieval === 'boolean') specifyRetrieval.value = r.specifyRetrieval
    if (r && (r.bindPersonalKB === 'yes' || r.bindPersonalKB === 'no')) bindPersonalKB.value = r.bindPersonalKB
  } catch { /* 保持默认值 */ }
}
async function loadEmbeddingModels() {
  try {
    const res: any = await api.getModels({ purpose: 'Embedding' })
    embeddingModels.value = (res as any)?.list || res || []
  } catch { embeddingModels.value = [] }
}
async function loadAll() {
  loading.value = true
  try { await Promise.all([loadCategories(), loadKBs(), loadBindings(), loadSettings(), loadEmbeddingModels()]) }
  finally { loading.value = false }
}
onMounted(loadAll)

// ===== 列表筛选 =====
const filteredCategories = computed(() =>
  categorySearchKeyword.value ? categories.value.filter((c: any) => (c.name || '').includes(categorySearchKeyword.value)) : categories.value
)
const filteredKBs = computed(() => {
  let list = knowledgeBases.value
  if (selectedCategory.value !== 'all') list = list.filter((kb: any) => kb.category === selectedCategory.value)
  if (kbSearchKeyword.value) list = list.filter((kb: any) => (kb.name || '').includes(kbSearchKeyword.value))
  return list
})
const selectedKBCount = computed(() => knowledgeBases.value.filter((kb: any) => boundKbIds.value.has(kb.id)).length)
const allSelected = computed(() => filteredKBs.value.length > 0 && filteredKBs.value.every((kb: any) => boundKbIds.value.has(kb.id)))

function isBound(id: string) {
  return boundKbIds.value.has(id)
}
function handleToggleBound(kb: any) {
  const s = new Set(boundKbIds.value)
  if (s.has(kb.id)) s.delete(kb.id)
  else s.add(kb.id)
  boundKbIds.value = s
}
function handleSelectAll() {
  const next = !allSelected.value
  const s = new Set(boundKbIds.value)
  filteredKBs.value.forEach((kb: any) => { if (next) s.add(kb.id); else s.delete(kb.id) })
  boundKbIds.value = s
}

// ===== 保存绑定（真实增删 app_kb_binding）+ 检索开关 =====
async function handleSaveKB() {
  if (!hasApp.value) { ElMessage.warning('当前页面缺少应用上下文，无法保存绑定'); return }
  if (boundKbIds.value.size === 0) { ElMessage.warning('请先选择要绑定的知识库'); return }
  saving.value = true
  try {
    const want = boundKbIds.value
    const toBind = knowledgeBases.value.filter((kb: any) => want.has(kb.id) && !bindings.value.some((b: any) => b.kbId === kb.id))
    const toUnbind = bindings.value.filter((b: any) => b.kbId && !want.has(b.kbId))
    for (const kb of toBind) await api.bindAppKb(appId(), { kbId: kb.id, priority: 0 })
    for (const b of toUnbind) await api.unbindAppKb(appId(), b.id)
    await api.saveAppKbSettings(appId(), { specifyRetrieval: specifyRetrieval.value, bindPersonalKB: bindPersonalKB.value })
    await loadBindings()
    ElMessage.success('知识库配置保存成功')
  } catch {
    ElMessage.error('保存知识库配置失败')
  } finally { saving.value = false }
}

// ===== 新建 / 编辑 / 删除知识库 =====
function openCreate() {
  isEditKb.value = false
  editingKbId.value = ''
  kbForm.value = emptyKbForm()
  if (categories.value.length) kbForm.value.category = categories.value[0].id
  if (embeddingModels.value.length) kbForm.value.embeddingModel = embeddingModels.value[0].code || embeddingModels.value[0].name
  showKbDialog.value = true
}
function openEdit(kb: any) {
  isEditKb.value = true
  editingKbId.value = kb.id
  kbForm.value = {
    name: kb.name || '',
    category: kb.category || '',
    description: kb.description || '',
    embeddingModel: kb.embeddingModel || (embeddingModels.value[0]?.code || embeddingModels.value[0]?.name) || '',
    permission: kb.permission || 'team',
    tags: Array.isArray(kb.tags) ? kb.tags.slice() : [],
  }
  showKbDialog.value = true
}
function handleAddTag() {
  const t = tagInput.value.trim()
  if (t && !kbForm.value.tags.includes(t)) kbForm.value.tags.push(t)
  tagInput.value = ''
}
function handleRemoveTag(t: string) {
  kbForm.value.tags = kbForm.value.tags.filter(x => x !== t)
}
async function handleSaveKbForm() {
  if (!kbForm.value.name.trim()) { ElMessage.warning('请输入知识库名称'); return }
  const payload: Record<string, unknown> = {
    name: kbForm.value.name.trim(),
    category: kbForm.value.category || null,
    description: kbForm.value.description,
    permission: kbForm.value.permission,
    embeddingModel: kbForm.value.embeddingModel || null,
    tags: kbForm.value.tags,
    parseMode: 'auto',
    splitMode: 'auto',
  }
  saving.value = true
  try {
    if (isEditKb.value) {
      await api.updateKnowledgeBase(editingKbId.value, payload)
      ElMessage.success('知识库已更新')
    } else {
      await api.createKnowledgeBase(payload)
      ElMessage.success('知识库创建成功')
    }
    showKbDialog.value = false
    await Promise.all([loadKBs(), loadCategories()])
  } catch {
    ElMessage.error(isEditKb.value ? '更新知识库失败' : '创建知识库失败')
  } finally { saving.value = false }
}
async function handleDeleteKb(kb: any) {
  try {
    await ElMessageBox.confirm(`确定要删除知识库「${kb.name}」吗？删除后数据将无法恢复。`, '删除确认', { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' })
    await api.deleteKnowledgeBase(kb.id)
    ElMessage.success('知识库已删除')
    // 同步刷新列表与绑定（被删知识库的绑定记录一并处理）
    await Promise.all([loadKBs(), loadBindings()])
  } catch { /* 用户取消 */ }
}

// ===== 导出 / 导入知识库（含元数据与文档全文，走后端真实接口）=====
async function handleExportKbContent(kb: any) {
  try {
    const data: any = await api.exportKnowledgeBase(kb.id)
    if (!data) throw new Error('empty')
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `知识库_${kb.name || kb.id}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`知识库「${kb.name}」已导出`)
  } catch {
    ElMessage.error('导出知识库失败')
  }
}
function handleImportKbContent() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e: any) => {
    const file = e.target.files?.[0]
    if (!file) return
    let data: any
    try { data = JSON.parse(await file.text()) } catch { ElMessage.error('文件解析失败，请选择合法的 JSON 文件'); return }
    if (!data?.knowledgeBase) { ElMessage.error('导入文件格式错误：缺少 knowledgeBase'); return }
    saving.value = true
    try {
      await api.importKnowledgeBase(data)
      await Promise.all([loadKBs(), loadCategories()])
      ElMessage.success(`知识库「${data.knowledgeBase.name || file.name}」导入成功，文档将自动解析并建立索引`)
    } catch {
      ElMessage.error('导入知识库失败')
    } finally {
      saving.value = false
      input.value = ''
    }
  }
  input.click()
}

// ===== 导出 / 导入（走后端真实接口）=====
async function handleExportKB() {
  let exportData: any = null
  if (hasApp.value) {
    try { exportData = await api.exportAppKbBindings(appId()) } catch { exportData = null }
  }
  if (!exportData) {
    exportData = {
      appId: appId(),
      settings: { specifyRetrieval: specifyRetrieval.value, bindPersonalKB: bindPersonalKB.value },
      bindings: bindings.value,
    }
  }
  // 附加知识库名称便于阅读
  const names = new Map(knowledgeBases.value.map((kb: any) => [kb.id, kb.name]))
  exportData.bindings = (exportData.bindings || []).map((b: any) => ({ ...b, kbName: names.get(b.kbId) || '' }))
  const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `kb_config_${exportData.appId || 'app'}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('知识库配置已导出')
}
async function handleImportKB() {
  if (!hasApp.value) { ElMessage.warning('当前页面缺少应用上下文，无法导入'); return }
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e: any) => {
    const file = e.target.files?.[0]
    if (!file) return
    try {
      const data = JSON.parse(await file.text())
      if (!Array.isArray(data?.bindings)) { ElMessage.error('导入文件格式错误：缺少 bindings'); return }
      await api.importAppKbBindings(appId(), data)
      await Promise.all([loadBindings(), loadSettings()])
      ElMessage.success('知识库配置已导入')
    } catch {
      ElMessage.error('导入失败，请检查文件格式')
    } finally {
      input.value = ''
    }
  }
  input.click()
}
</script>

<template>
  <div class="config-section" v-loading="loading">
    <h3>知识库配置</h3>
    <p class="desc">为应用绑定知识库，使其能够基于知识库内容回答问题；也可在此新建、编辑、删除、导出、导入知识库。</p>

    <div class="kb-config-options">
      <div class="kb-option-item">
        <span class="option-label">指定知识库检索</span>
        <el-tooltip content="开启后将仅在绑定的知识库中检索" placement="top">
          <el-icon><QuestionFilled /></el-icon>
        </el-tooltip>
        <el-switch v-model="specifyRetrieval" />
      </div>

      <div class="kb-option-item">
        <span class="option-label">绑定个人知识库</span>
        <el-tooltip content="是否允许使用个人知识库" placement="top">
          <el-icon><QuestionFilled /></el-icon>
        </el-tooltip>
        <el-radio-group v-model="bindPersonalKB">
          <el-radio value="yes">是</el-radio>
          <el-radio value="no">否</el-radio>
        </el-radio-group>
      </div>
    </div>

    <div class="kb-binding-section">
      <div class="kb-binding-header">
        <span class="section-label">绑定团队知识库</span>
        <el-tooltip content="选择要绑定到此应用的团队知识库" placement="top">
          <el-icon><QuestionFilled /></el-icon>
        </el-tooltip>
        <div class="header-actions">
          <el-button type="primary" size="small" @click="openCreate">
            <el-icon><Plus /></el-icon>新建知识库
          </el-button>
          <el-button size="small" @click="handleImportKbContent">
            <el-icon><Upload /></el-icon>导入知识库
          </el-button>
          <el-button size="small" :icon="Refresh" @click="loadAll">刷新</el-button>
        </div>
      </div>

      <div class="kb-binding-content">
        <!-- 左侧分类 -->
        <div class="kb-categories">
          <el-input v-model="categorySearchKeyword" placeholder="搜索分类" clearable size="small">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <div class="category-list">
            <div class="category-item" :class="{ active: selectedCategory === 'all' }" @click="selectedCategory = 'all'">
              <span>全部</span>
            </div>
            <div
              v-for="cat in filteredCategories"
              :key="cat.id"
              class="category-item"
              :class="{ active: selectedCategory === cat.id }"
              @click="selectedCategory = cat.id"
            >
              <span v-if="cat.color" class="category-color-dot" :style="{ background: cat.color }" />
              <span>{{ cat.name }}</span>
            </div>
          </div>
        </div>

        <!-- 右侧知识库列表 -->
        <div class="kb-list">
          <div class="kb-list-header">
            <el-input v-model="kbSearchKeyword" placeholder="搜索知识库" clearable size="small" style="width: 300px">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <div class="kb-list-actions">
              <el-button text size="small" @click="handleSelectAll">{{ allSelected ? '取消全选' : '全选' }}</el-button>
              <span class="selected-info">已选 {{ selectedKBCount }} 个知识库</span>
            </div>
          </div>

          <div class="kb-list-body">
            <div v-for="kb in filteredKBs" :key="kb.id" class="kb-item">
              <el-checkbox :model-value="isBound(kb.id)" @change="handleToggleBound(kb)" />
              <span class="kb-name">{{ kb.name }}</span>
              <span class="kb-meta">嵌入模型:{{ kb.embeddingModel || '-' }}<template v-if="kb.dimension"> | 维度:{{ kb.dimension }}</template></span>
              <span class="kb-ops">
                <el-button link type="primary" size="small" @click="handleExportKbContent(kb)">导出</el-button>
                <el-button link type="primary" size="small" @click="openEdit(kb)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteKb(kb)">删除</el-button>
              </span>
            </div>
            <el-empty
              v-if="!filteredKBs.length"
              :description="knowledgeBases.length ? '无匹配知识库' : '暂无知识库，点击右上角「新建知识库」创建'"
            />
          </div>
        </div>
      </div>
    </div>

    <div class="kb-footer">
      <el-button type="primary" :loading="saving" @click="handleSaveKB">保 存</el-button>
      <el-button @click="handleExportKB">导出绑定配置</el-button>
      <el-button @click="handleImportKB">导入绑定配置</el-button>
    </div>

    <!-- 新建 / 编辑知识库弹窗 -->
    <el-dialog v-model="showKbDialog" :title="isEditKb ? '编辑知识库' : '新建知识库'" width="540px">
      <el-form label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="kbForm.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="kbForm.category" placeholder="请选择分类" clearable style="width: 100%">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="kbForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="嵌入模型">
          <el-select v-model="kbForm.embeddingModel" placeholder="请选择嵌入模型" clearable filterable style="width: 100%">
            <el-option v-for="m in embeddingModels" :key="m.code || m.name" :label="m.name" :value="m.code || m.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="权限">
          <el-radio-group v-model="kbForm.permission">
            <el-radio value="private">私有</el-radio>
            <el-radio value="team">团队</el-radio>
            <el-radio value="public">公开</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标签">
          <div class="tag-input-area">
            <el-tag v-for="tag in kbForm.tags" :key="tag" closable size="small" @close="handleRemoveTag(tag)">{{ tag }}</el-tag>
            <el-input
              v-model="tagInput"
              size="small"
              style="width: 120px"
              placeholder="回车添加"
              @keyup.enter="handleAddTag"
              @blur="handleAddTag"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showKbDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveKbForm">{{ isEditKb ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.config-section {
  h3 { margin: 0 0 $spacing-lg; }
  .desc { color: $text-secondary; margin-bottom: $spacing-base; }
}

.kb-config-options {
  margin-bottom: $spacing-lg;
  padding: $spacing-base;
  background: $bg-hover;
  border-radius: $radius-base;
}

.kb-option-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;

  &:last-child {
    margin-bottom: 0;
  }

  .option-label {
    font-size: 14px;
    color: $text-primary;
  }
}

.kb-binding-section {
  margin-bottom: $spacing-lg;
}

.kb-binding-header {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-base;

  .section-label {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }

  .header-actions {
    margin-left: auto;
    display: flex;
    gap: $spacing-xs;
  }
}

.kb-binding-content {
  display: flex;
  gap: $spacing-base;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  min-height: 400px;
}

.kb-categories {
  width: 200px;
  border-right: 1px solid $border-lighter;
  padding: $spacing-base;

  .category-list {
    margin-top: $spacing-sm;
  }

  .category-item {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    padding: $spacing-sm $spacing-base;
    cursor: pointer;
    border-radius: $radius-sm;
    font-size: 13px;
    color: $text-regular;
    overflow: hidden;

    &:hover {
      background: $bg-hover;
    }

    &.active {
      background: $bg-active;
      color: $color-primary;
      font-weight: 500;
    }

    .category-color-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      flex-shrink: 0;
    }
  }
}

.kb-list {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.kb-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-base;
  border-bottom: 1px solid $border-lighter;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

.kb-list-actions {
  display: flex;
  align-items: center;
  gap: $spacing-base;

  .selected-info {
    font-size: 13px;
    color: $text-secondary;
  }
}

.kb-list-body {
  flex: 1;
  overflow-y: auto;
}

.kb-item {
  display: flex;
  align-items: center;
  padding: $spacing-base;
  border-bottom: 1px solid $border-extra-light;

  &:hover {
    background: $bg-hover;
  }

  .kb-name {
    flex: 1;
    font-size: 14px;
    color: $text-primary;
    margin-left: $spacing-sm;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .kb-meta {
    font-size: 12px;
    color: $text-secondary;
    flex-shrink: 0;
  }

  .kb-ops {
    flex-shrink: 0;
    margin-left: $spacing-sm;
  }
}

.kb-footer {
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
  display: flex;
  gap: $spacing-sm;
}

.tag-input-area {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  flex-wrap: wrap;
  width: 100%;
}
</style>