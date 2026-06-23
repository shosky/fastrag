<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCategoryLabel,
  getSourceMeta,
  SKILL_CATEGORIES,
} from '@/mock/skills'
import type { Skill, SkillDependency } from '@/mock/skills'
import * as api from '@/api'

const router = useRouter()

// --- 列表数据 ---
const skills = ref<Skill[]>([])

async function loadSkills() {
  skills.value = (await api.getSkills()) as any || []
}

onMounted(loadSkills)

const searchKeyword = ref('')
const selectedCategory = ref('')

const filteredSkills = computed(() => {
  let list = skills.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(
      (s) =>
        s.name.toLowerCase().includes(kw) ||
        s.identifier.toLowerCase().includes(kw) ||
        s.description.toLowerCase().includes(kw),
    )
  }
  if (selectedCategory.value) {
    list = list.filter((s) => s.category === selectedCategory.value)
  }
  return list
})

// --- 详情弹窗（只读） ---
const showDetailDialog = ref(false)
const detailSkill = ref<Skill | null>(null)
const detailTab = ref('info')

const DEP_TYPE_LABELS: Record<SkillDependency['type'], string> = {
  tool: '工具',
  model: '模型',
  mcp: 'MCP',
  skill: '技能',
}

function cloneSkill(s: Skill): Skill {
  return {
    ...s,
    dependencies: s.dependencies.map((d) => ({ ...d })),
    scopes: s.scopes.map((sc) => ({ ...sc })),
  }
}

function openDetail(skill: Skill) {
  detailSkill.value = cloneSkill(skill)
  detailTab.value = 'info'
  showDetailDialog.value = true
}

/** 从详情弹窗跳转到编辑页 */
function openEditFromDetail() {
  if (!detailSkill.value) return
  const id = detailSkill.value.id
  showDetailDialog.value = false
  router.push(`/application/skill-management/${id}/edit`)
}

// --- 路由跳转 ---
function openCreate() {
  router.push('/application/skill-management/create')
}

function openEdit(skill: Skill) {
  router.push(`/application/skill-management/${skill.id}/edit`)
}

async function handleDelete(skill: Skill) {
  try {
    await ElMessageBox.confirm(`确定删除技能「${skill.name}」吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await api.deleteSkill(skill.id)
    await loadSkills()
    ElMessage.success('删除成功')
  } catch {}
}

async function handleToggleEnabled(skill: Skill) {
  await api.toggleSkill(skill.id)
  await loadSkills()
  ElMessage.success(skill.enabled ? '已禁用' : '已启用')
}

// --- 上传技能 ---
const showUploadDialog = ref(false)
const uploadFileList = ref<any[]>([])
const uploadForm = ref({
  name: '',
  identifier: '',
  description: '',
  version: '1.0.0',
})
const uploadSubmitting = ref(false)

function openUpload() {
  showUploadDialog.value = true
  uploadFileList.value = []
  uploadForm.value = { name: '', identifier: '', description: '', version: '1.0.0' }
}

function handleUploadChange(file: any) {
  // 自动填充名称 / 标识（去掉扩展名）
  if (!uploadForm.value.name) {
    const base = (file.name || '').replace(/\.(zip|tar|gz|tgz|skill)$/i, '')
    uploadForm.value.name = base
    uploadForm.value.identifier = base.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '')
  }
}

function handleUploadRemove() {
  uploadFileList.value = []
}

async function handleUploadSubmit() {
  if (!uploadFileList.value.length) {
    ElMessage.warning('请先选择技能包文件')
    return
  }
  if (!uploadForm.value.name.trim() || !uploadForm.value.identifier.trim()) {
    ElMessage.warning('请填写技能名称和标识')
    return
  }
  uploadSubmitting.value = true
  try {
    const fileName = uploadFileList.value[0]?.name || 'skill.zip'
    const created: any = await api.createSkill({
      name: uploadForm.value.name.trim(),
      identifier: uploadForm.value.identifier.trim(),
      description: uploadForm.value.description.trim() || `从 ${fileName} 安装的技能`,
      icon: '#909399',
      source: 'plugin',
      category: 'tool',
      trigger: '',
      content: '',
      codeType: 'python',
      code: `# 从 ${fileName} 解析的技能代码\n# 安装来源：本地上传`,
      inputs: '',
      outputs: '',
      enabled: true,
      recommended: false,
      dependencies: [],
      scopes: [],
      author: '本地上传',
      version: uploadForm.value.version || '1.0.0',
    })
    await loadSkills()
    showUploadDialog.value = false
    ElMessage.success(`技能「${created?.name || uploadForm.value.name}」安装成功`)
  } finally {
    uploadSubmitting.value = false
  }
}

// --- 远程安装 ---
const showRemoteDialog = ref(false)
const remoteForm = ref({
  url: '',
  name: '',
  version: '',
})
const remoteInstalling = ref(false)

function openRemote() {
  showRemoteDialog.value = true
  remoteForm.value = { url: '', name: '', version: '' }
}

async function handleRemoteInstall() {
  if (!remoteForm.value.url.trim()) {
    ElMessage.warning('请输入技能包地址')
    return
  }
  remoteInstalling.value = true
  try {
    const url = remoteForm.value.url.trim()
    const lastSegment = url.split('/').pop()?.split('?')[0] || 'remote_skill'
    const base = lastSegment.replace(/\.(zip|tar|gz|tgz|skill)$/i, '')
    const created: any = await api.createSkill({
      name: remoteForm.value.name.trim() || base,
      identifier: base.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, ''),
      description: `从远程地址安装：${url}`,
      icon: '#909399',
      source: 'plugin',
      category: 'tool',
      trigger: '',
      content: '',
      codeType: 'python',
      code: `# 从远程地址拉取的技能代码\n# 来源：${url}`,
      inputs: '',
      outputs: '',
      enabled: true,
      recommended: false,
      dependencies: [],
      scopes: [],
      author: '远程安装',
      version: remoteForm.value.version.trim() || '1.0.0',
    })
    await loadSkills()
    showRemoteDialog.value = false
    ElMessage.success(`技能「${created?.name || base}」安装成功`)
  } finally {
    remoteInstalling.value = false
  }
}

/** 复制代码到剪贴板 */
function copyCode(code: string) {
  try {
    navigator.clipboard?.writeText(code)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败')
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-title">
        <h2>技能管理</h2>
        <p>管理智能体的技能配置，扩展其能力边界</p>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索技能名称 / 标识 / 描述"
          clearable
          style="width: 320px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="selectedCategory" placeholder="全部分类" clearable style="width: 160px">
          <el-option
            v-for="c in SKILL_CATEGORIES.filter((x) => x.value)"
            :key="c.value"
            :label="c.label"
            :value="c.value"
          />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button @click="openUpload">
          <el-icon><Upload /></el-icon>上传技能
        </el-button>
        <el-button @click="openRemote">
          <el-icon><Download /></el-icon>远程安装
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>创建技能
        </el-button>
      </div>
    </div>

    <!-- 技能列表 -->
    <div class="skills-grid" v-if="filteredSkills.length">
      <div v-for="skill in filteredSkills" :key="skill.id" class="skill-card">
        <div class="skill-card-body" @click="openDetail(skill)">
          <div class="skill-title-row">
            <div class="skill-icon">
              <el-icon :size="16" color="#909399"><MagicStick /></el-icon>
            </div>
            <h4 :title="skill.name">{{ skill.name }}</h4>
            <span class="source-text">{{ getSourceMeta(skill.source).label }}</span>
          </div>
          <p class="skill-desc">{{ skill.description }}</p>
          <div class="skill-tags">
            <span class="tag-text">{{ getCategoryLabel(skill.category) }}</span>
            <span class="tag-text tag-mono">{{ skill.identifier }}</span>
          </div>
        </div>
        <div class="skill-card-footer">
          <div class="usage">
            <el-icon><DataLine /></el-icon>
            <span>调用 {{ skill.usageCount }} 次</span>
          </div>
          <div class="actions">
            <el-switch :model-value="skill.enabled" size="small" @change="handleToggleEnabled(skill)" />
            <el-button link type="primary" size="small" @click="openEdit(skill)">编辑</el-button>
            <el-dropdown trigger="click">
              <el-icon class="more-icon"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openDetail(skill)">查看详情</el-dropdown-item>
                  <el-dropdown-item divided @click="handleDelete(skill)">
                    <span style="color: var(--el-color-danger)">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无技能，点击右上角创建" />

    <!-- ========== 技能详情弹窗 ========== -->
    <el-dialog
      v-model="showDetailDialog"
      :title="detailSkill?.name || '技能详情'"
      width="780px"
      top="6vh"
      class="detail-dialog"
    >
      <template v-if="detailSkill">
        <!-- 元信息区 -->
        <div class="detail-meta">
          <div class="detail-meta-icon">
            <el-icon :size="24" color="#fff"><MagicStick /></el-icon>
          </div>
          <div class="detail-meta-main">
            <div class="detail-meta-title">
              <span class="title-name">{{ detailSkill.name }}</span>
              <span class="title-meta">{{ getSourceMeta(detailSkill.source).label }}</span>
              <span class="title-meta">v{{ detailSkill.version }}</span>
            </div>
            <p class="detail-meta-desc">{{ detailSkill.description }}</p>
            <div class="detail-meta-info">
              <span><el-icon><User /></el-icon>{{ detailSkill.author }}</span>
              <span><el-icon><DataLine /></el-icon>调用 {{ detailSkill.usageCount }} 次</span>
              <span><el-icon><Clock /></el-icon>{{ detailSkill.updatedAt }}</span>
            </div>
          </div>
          <div class="detail-meta-actions">
            <div class="meta-switch">
              <span>启用</span>
              <el-switch :model-value="detailSkill.enabled" @change="handleToggleEnabled(detailSkill); detailSkill.enabled = !detailSkill.enabled" />
            </div>
            <el-button type="primary" plain size="small" @click="openEditFromDetail">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
          </div>
        </div>

        <el-tabs v-model="detailTab" class="detail-tabs">
          <el-tab-pane label="基础信息" name="info">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="技能标识">{{ detailSkill.identifier }}</el-descriptions-item>
              <el-descriptions-item label="分类">{{ getCategoryLabel(detailSkill.category) }}</el-descriptions-item>
              <el-descriptions-item label="触发场景" :span="2">{{ detailSkill.trigger || '-' }}</el-descriptions-item>
              <el-descriptions-item label="技能说明" :span="2">{{ detailSkill.content || '-' }}</el-descriptions-item>
              <el-descriptions-item label="入参">{{ detailSkill.inputs || '-' }}</el-descriptions-item>
              <el-descriptions-item label="出参">{{ detailSkill.outputs || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane label="代码管理" name="code">
            <div class="code-header">
              <span class="code-type-text">{{ detailSkill.codeType }}</span>
              <el-button size="small" text @click="copyCode(detailSkill.code)">
                <el-icon><CopyDocument /></el-icon>复制
              </el-button>
            </div>
            <pre class="code-block"><code>{{ detailSkill.code }}</code></pre>
          </el-tab-pane>

          <el-tab-pane label="生效范围" name="scope">
            <el-empty v-if="!detailSkill.scopes.length" description="未配置生效范围" :image-size="60" />
            <el-table v-else :data="detailSkill.scopes" size="small" border>
              <el-table-column prop="name" label="应用 / 知识库" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <span :class="['status-text', row.enabled ? 'is-on' : 'is-off']">
                    {{ row.enabled ? '已启用' : '未启用' }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="依赖管理" name="deps">
            <el-empty v-if="!detailSkill.dependencies.length" description="无依赖项" :image-size="60" />
            <el-table v-else :data="detailSkill.dependencies" size="small" border>
              <el-table-column prop="name" label="依赖名称" />
              <el-table-column label="类型" width="100" align="center">
                <template #default="{ row }">
                  <span class="dep-type-text">{{ DEP_TYPE_LABELS[row.type as SkillDependency['type']] }}</span>
                </template>
              </el-table-column>
              <el-table-column label="是否必选" width="100" align="center">
                <template #default="{ row }">
                  <span :class="['status-text', row.required ? 'is-on' : 'is-off']">
                    {{ row.required ? '必选' : '可选' }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-dialog>

    <!-- ========== 上传技能弹窗 ========== -->
    <el-dialog
      v-model="showUploadDialog"
      title="上传技能"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px" label-position="right">
        <el-form-item label="技能包" required>
          <el-upload
            v-model:file-list="uploadFileList"
            drag
            :auto-upload="false"
            :limit="1"
            accept=".zip,.tar,.gz,.tgz,.skill"
            :on-change="handleUploadChange"
            :on-remove="handleUploadRemove"
            style="width: 100%"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              将技能包拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">支持 .zip / .tar.gz / .skill 格式</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="技能名称" required>
          <el-input v-model="uploadForm.name" placeholder="上传后自动填充，可修改" />
        </el-form-item>
        <el-form-item label="技能标识" required>
          <el-input v-model="uploadForm.identifier" placeholder="如 web_search" />
        </el-form-item>
        <el-form-item label="技能描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="2" placeholder="简要描述技能用途" />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="uploadForm.version" placeholder="如 1.0.0" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploadSubmitting" @click="handleUploadSubmit">
          安装
        </el-button>
      </template>
    </el-dialog>

    <!-- ========== 远程安装弹窗 ========== -->
    <el-dialog
      v-model="showRemoteDialog"
      title="远程安装技能"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="输入技能包的远程地址，系统将拉取并自动安装"
        style="margin-bottom: 16px"
      />
      <el-form label-width="100px" label-position="right">
        <el-form-item label="包地址" required>
          <el-input v-model="remoteForm.url" placeholder="https://example.com/skills/web_search.skill">
            <template #prefix><el-icon><Link /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="技能名称">
          <el-input v-model="remoteForm.name" placeholder="留空则使用包名" />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="remoteForm.version" placeholder="留空则使用包内版本" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRemoteDialog = false">取消</el-button>
        <el-button type="primary" :loading="remoteInstalling" @click="handleRemoteInstall">
          安装
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-header {
  margin-bottom: $spacing-lg;

  h2 {
    margin: 0 0 $spacing-xs;
    font-size: 20px;
    color: $text-primary;
  }

  p {
    margin: 0;
    font-size: 14px;
    color: $text-secondary;
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
}

.toolbar-left {
  display: flex;
  gap: $spacing-sm;
}

.toolbar-right {
  display: flex;
  gap: $spacing-sm;
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: $spacing-base;
}

.skill-card {
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  overflow: hidden;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;

  &:hover {
    box-shadow: $shadow-base;
    border-color: $border-base;
    transform: translateY(-2px);
  }
}

.skill-card-body {
  padding: $spacing-base;
  flex: 1;
  cursor: pointer;
}

.skill-title-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-xs;

  .skill-icon {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  h4 {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    min-width: 0;
  }

  .source-text {
    flex-shrink: 0;
    font-size: 12px;
    color: $text-secondary;
  }
}

.skill-desc {
  margin: 0 0 $spacing-sm;
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 36px;
}

.skill-tags {
  display: flex;
  gap: $spacing-xs;
  flex-wrap: wrap;

  .tag-text {
    font-size: 12px;
    color: $text-secondary;
    background: $bg-hover;
    border-radius: $radius-sm;
    padding: 2px 8px;
  }

  .tag-mono {
    font-family: 'Consolas', 'Monaco', monospace;
  }
}

.skill-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-sm $spacing-base;
  border-top: 1px solid $border-lighter;

  .usage {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: $text-secondary;
  }

  .actions {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
  }

  .more-icon {
    cursor: pointer;
    color: $text-secondary;
    padding: 2px;

    &:hover {
      color: $color-primary;
    }
  }
}

// 详情弹窗
.detail-meta {
  display: flex;
  gap: $spacing-base;
  padding-bottom: $spacing-base;
  margin-bottom: $spacing-base;
  border-bottom: 1px solid $border-lighter;
}

.detail-meta-icon {
  width: 56px;
  height: 56px;
  border-radius: $radius-base;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: $bg-active;
}

.detail-meta-main {
  flex: 1;
  min-width: 0;
}

.detail-meta-title {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-xs;

  .title-name {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }

  .title-meta {
    font-size: 12px;
    color: $text-secondary;
  }
}

.detail-meta-desc {
  margin: 0 0 $spacing-xs;
  font-size: 13px;
  color: $text-secondary;
  line-height: 1.5;
}

.detail-meta-info {
  display: flex;
  gap: $spacing-base;
  font-size: 12px;
  color: $text-secondary;

  span {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.detail-meta-actions {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  align-items: flex-end;
  flex-shrink: 0;

  .meta-switch {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    font-size: 12px;
    color: $text-secondary;
  }
}

.detail-tabs {
  :deep(.el-tabs__content) {
    max-height: 420px;
    overflow-y: auto;
  }
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-sm;

  .code-type-text {
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 12px;
    color: $text-secondary;
    background: $bg-hover;
    border-radius: $radius-sm;
    padding: 2px 8px;
  }
}

.code-block {
  margin: 0;
  padding: $spacing-base;
  background: #1e1e1e;
  border-radius: $radius-base;
  max-height: 360px;
  overflow: auto;

  code {
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 13px;
    line-height: 1.6;
    color: #d4d4d4;
    white-space: pre;
  }
}

// 表格内的中性状态文本（替代多色 el-tag）
.status-text {
  font-size: 12px;

  &.is-on {
    color: $text-primary;
  }

  &.is-off {
    color: $text-placeholder;
  }
}

.dep-type-text {
  font-size: 12px;
  color: $text-secondary;
}

.tab-section-tip {
  margin-bottom: $spacing-base;
  padding: $spacing-sm $spacing-base;
  font-size: 12px;
  color: $text-secondary;
  background: $bg-hover;
  border-radius: $radius-sm;
}

.scope-add,
.dep-add {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-base;
  align-items: center;
}

:deep(.code-textarea .el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
