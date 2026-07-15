<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import * as api from '@/api'

const router = useRouter()
const saving = ref(false)
const activeTab = ref('basic')

// ===== 基础信息 =====
const form = reactive({
  name: '',
  identifier: '',
  description: '',
  category: 'skill',
  trigger: '',
  enabled: true,
})

const categoryOptions = [
  { label: '工具', value: 'tool' },
  { label: '模型', value: 'model' },
  { label: 'MCP', value: 'mcp' },
  { label: '技能', value: 'skill' },
]

// ===== 代码管理：虚拟文件系统（创建前尚未入库） =====
// 创建阶段没有 slug，用一个虚拟文件列表在内存中管理
const virtualFiles = ref<{ path: string; isDir: boolean; content: string }[]>([
  { path: 'SKILL.md', isDir: false, content: '' },
])
const selectedFilePath = ref('SKILL.md')
const fileContent = ref('')
const showCreateFileDialog = ref(false)
const createFileName = ref('')
const createFileContent = ref('')

function selectFile(path: string) {
  selectedFilePath.value = path
  const file = virtualFiles.value.find((f) => f.path === path)
  if (file && !file.isDir) {
    fileContent.value = file.content
  } else {
    fileContent.value = ''
  }
}

function buildTree(flatFiles: typeof virtualFiles.value) {
  const root: any[] = []
  const map = new Map<string, any>()
  const sorted = [...flatFiles].sort((a, b) => a.path.localeCompare(b.path))
  for (const f of sorted) {
    const parts = f.path.split('/')
    let parentPath = ''
    for (let i = 0; i < parts.length; i++) {
      const currentPath = parts.slice(0, i + 1).join('/')
      const isLeaf = i === parts.length - 1
      const existingNode = map.get(currentPath)
      if (existingNode) {
        if (isLeaf) {
          existingNode.isDir = f.isDir
          existingNode.content = f.content
        }
        parentPath = currentPath
        continue
      }
      const node: any = {
        name: parts[i],
        path: currentPath,
        isDir: !isLeaf,
        content: isLeaf ? f.content : '',
        children: [],
      }
      map.set(currentPath, node)
      if (i === 0) {
        root.push(node)
      } else {
        const parent = map.get(parentPath)
        if (parent) {
          if (!parent.children.some((c: any) => c.path === currentPath)) {
            parent.children.push(node)
          }
        }
      }
      parentPath = currentPath
    }
  }
  return root
}

const fileTree = ref<any[]>([])

function refreshTree() {
  fileTree.value = buildTree(virtualFiles.value)
  // 默认选中 SKILL.md
  if (!selectedFilePath.value || !virtualFiles.value.find((f) => f.path === selectedFilePath.value)) {
    selectFile('SKILL.md')
  }
}

function openCreateFile() {
  createFileName.value = ''
  createFileContent.value = ''
  showCreateFileDialog.value = true
}

function handleCreateFile() {
  const name = createFileName.value.trim()
  if (!name) {
    ElMessage.warning('请输入文件名')
    return
  }
  const fullPath = name
  if (virtualFiles.value.some((f) => f.path === fullPath)) {
    ElMessage.warning('同名文件已存在')
    return
  }
  virtualFiles.value.push({
    path: fullPath,
    isDir: false,
    content: createFileContent.value,
  })
  showCreateFileDialog.value = false
  refreshTree()
  selectFile(fullPath)
}

function handleDeleteFile(path: string) {
  if (path === 'SKILL.md') {
    ElMessage.warning('SKILL.md 不能删除')
    return
  }
  virtualFiles.value = virtualFiles.value.filter((f) => !f.path.startsWith(path))
  refreshTree()
  if (selectedFilePath.value.startsWith(path)) {
    selectFile('SKILL.md')
  }
}

function handleTreeClick(data: any) {
  if (!data.isDir) {
    selectFile(data.path)
  }
}

// 保存当前编辑的文件到虚拟文件系统
function syncCurrentFileContent() {
  if (selectedFilePath.value && fileContent.value !== null) {
    const file = virtualFiles.value.find((f) => f.path === selectedFilePath.value)
    if (file) {
      file.content = fileContent.value
    }
  }
}

// ===== 依赖管理 =====
const depOptions = ref<{ tools: string[]; mcps: string[]; skills: string[] }>({ tools: [], mcps: [], skills: [] })
const selectedTools = ref<string[]>([])
const selectedMcps = ref<string[]>([])
const selectedSkills = ref<string[]>([])

// ===== 提交 =====
async function handleSubmit() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入技能名称')
    activeTab.value = 'basic'
    return
  }
  if (!form.value.identifier.trim()) {
    ElMessage.warning('请输入技能标识')
    activeTab.value = 'basic'
    return
  }
  // 同步当前编辑内容
  syncCurrentFileContent()

  saving.value = true
  try {
    const slug = form.value.identifier.trim().toLowerCase().replace(/\s+/g, '_').replace(/[^a-z0-9_]/g, '')
    // 第一步：创建技能
    const skillMd = virtualFiles.value.find((f) => f.path === 'SKILL.md')
    await api.createSkill({
      name: form.value.name.trim(),
      identifier: slug,
      slug,
      description: form.value.description.trim() || form.value.name.trim(),
      source: 'custom',
      category: form.value.category,
      trigger: form.value.trigger,
      content: skillMd?.content || '',
      codeType: 'markdown',
      code: skillMd?.content || '',
      enabled: form.value.enabled,
      version: '1.0.0',
    } as any)

    // 第二步：创建所有虚拟文件到技能目录
    for (const file of virtualFiles.value) {
      try {
        await api.createSkillFile(slug, file.path, file.isDir, file.content)
      } catch {
        // SKILL.md 内容已在 createSkill 中写入，跳过
      }
    }

    // 第三步：更新依赖
    const deps = [
      ...selectedTools.value.map((n) => ({ type: 'tool', name: n, required: false })),
      ...selectedMcps.value.map((n) => ({ type: 'mcp', name: n, required: false })),
      ...selectedSkills.value.map((n) => ({ type: 'skill', name: n, required: false })),
    ]
    if (deps.length > 0) {
      try {
        // 创建成功后通过列表获取 skill id 再更新依赖
        const skillList: any = await api.getSkills()
        const created = (skillList || []).find((s: any) => s.slug === slug || s.identifier === slug)
        if (created) {
          await api.updateSkillDependencies(created.id, deps)
        }
      } catch {
        // 依赖设置失败不阻断流程
      }
    }

    ElMessage.success(`技能「${form.value.name}」创建成功`)
    router.push('/application/skill-management')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '创建失败')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/application/skill-management')
}

// ===== 初始化 =====
onMounted(async () => {
  refreshTree()
  selectFile('SKILL.md')
  // 加载依赖选项
  try {
    const opts = await api.getDependencyOptions()
    depOptions.value = opts || { tools: [], mcps: [], skills: [] }
  } catch {
    depOptions.value = { tools: [], mcps: [], skills: [] }
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <el-button @click="handleCancel">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <h3>新建技能</h3>
    </div>

    <el-tabs v-model="activeTab" class="create-tabs">
      <!-- Tab 1: 基础信息 -->
      <el-tab-pane label="基础信息" name="basic">
        <div class="create-card">
          <el-form label-width="100px" label-position="right">
            <el-form-item label="技能名称" required>
              <el-input
                v-model="form.name"
                placeholder="请输入技能名称，如：联网搜索"
                maxlength="30"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="技能标识" required>
              <el-input
                v-model="form.identifier"
                placeholder="唯一标识，如 web_search（仅英文/数字/下划线）"
              />
            </el-form-item>
            <el-form-item label="技能描述">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="2"
                placeholder="简要描述技能用途"
              />
            </el-form-item>
            <el-form-item label="技能分类">
              <el-select v-model="form.category" style="width: 200px">
                <el-option
                  v-for="c in categoryOptions"
                  :key="c.value"
                  :label="c.label"
                  :value="c.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="触发场景">
              <el-input
                v-model="form.trigger"
                type="textarea"
                :rows="2"
                placeholder="描述何时触发此技能（可选）"
              />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 代码管理 -->
      <el-tab-pane label="代码管理" name="code">
        <div class="editor-layout">
          <!-- 左侧文件树 -->
          <div class="tree-panel">
            <div class="tree-header">
              <span class="tree-label">项目结构</span>
              <div class="tree-actions">
                <el-tooltip content="新建文档"><el-button size="small" text @click="openCreateFile"><el-icon><Plus /></el-icon></el-button></el-tooltip>
                <el-tooltip content="刷新"><el-button size="small" text @click="refreshTree"><el-icon><Refresh /></el-icon></el-button></el-tooltip>
              </div>
            </div>
            <div class="tree-body">
              <el-tree
                :data="fileTree"
                :props="{ children: 'children', label: 'name' }"
                default-expand-all
                highlight-current
                @node-click="handleTreeClick"
              >
                <template #default="{ data }">
                  <span class="tree-node">
                    <el-icon v-if="data.isDir" :size="14"><Folder /></el-icon>
                    <el-icon v-else :size="14"><Document /></el-icon>
                    <span>{{ data.name }}</span>
                    <el-dropdown
                      v-if="data.path !== 'SKILL.md'"
                      trigger="click"
                      class="node-actions"
                      @command="(cmd: string) => cmd === 'delete' && handleDeleteFile(data.path)"
                    >
                      <el-icon :size="12" @click.stop><MoreFilled /></el-icon>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="delete" style="color: var(--el-color-danger)">删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </span>
                </template>
              </el-tree>
            </div>
          </div>

          <!-- 右侧编辑器 -->
          <div class="editor-panel">
            <div class="editor-toolbar">
              <span class="editor-path">{{ selectedFilePath || '选择文件' }}</span>
              <el-button size="small" type="primary" @click="syncCurrentFileContent(); ElMessage.success('已保存到草稿')">
                <el-icon><Check /></el-icon>保存
              </el-button>
            </div>
            <div class="editor-body">
              <el-input
                v-model="fileContent"
                type="textarea"
                :rows="22"
                :placeholder="selectedFilePath ? '编辑文件内容...' : '从左侧选择文件进行编辑'"
                class="code-textarea"
              />
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 3: 依赖管理 -->
      <el-tab-pane label="依赖管理" name="deps">
        <div class="create-card deps-card">
          <div class="deps-section">
            <h4>工具依赖</h4>
            <p class="deps-desc">声明此技能运行时需要调用的工具能力。</p>
            <el-select v-model="selectedTools" multiple placeholder="选择工具" filterable style="width: 100%">
              <el-option v-for="t in depOptions.tools" :key="t" :label="t" :value="t" />
            </el-select>
            <div v-if="selectedTools.length" class="deps-chips">
              <el-tag v-for="t in selectedTools" :key="t" closable @close="selectedTools = selectedTools.filter(x => x !== t)">{{ t }}</el-tag>
            </div>
          </div>
          <div class="deps-section">
            <h4>MCP 依赖</h4>
            <p class="deps-desc">声明此技能依赖的 MCP 服务。</p>
            <el-select v-model="selectedMcps" multiple placeholder="选择 MCP 服务" filterable style="width: 100%">
              <el-option v-for="m in depOptions.mcps" :key="m" :label="m" :value="m" />
            </el-select>
            <div v-if="selectedMcps.length" class="deps-chips">
              <el-tag v-for="m in selectedMcps" :key="m" closable @close="selectedMcps = selectedMcps.filter(x => x !== m)">{{ m }}</el-tag>
            </div>
          </div>
          <div class="deps-section">
            <h4>技能依赖</h4>
            <p class="deps-desc">声明需要一起加载的其他技能。</p>
            <el-select v-model="selectedSkills" multiple placeholder="选择技能" filterable style="width: 100%">
              <el-option v-for="s in depOptions.skills" :key="s" :label="s" :value="s" />
            </el-select>
            <div v-if="selectedSkills.length" class="deps-chips">
              <el-tag v-for="s in selectedSkills" :key="s" closable @close="selectedSkills = selectedSkills.filter(x => x !== s)">{{ s }}</el-tag>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 底部操作栏 -->
    <div class="create-footer">
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        <el-icon><Plus /></el-icon>创建技能
      </el-button>
    </div>

    <!-- 新建文件/目录弹窗 -->
    <el-dialog
      v-model="showCreateFileDialog"
      title="新建文档"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px">
        <el-form-item label="路径" required>
          <el-input v-model="createFileName" placeholder="例如：scripts/main.py" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="createFileContent" type="textarea" :rows="5" placeholder="文件初始内容（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateFileDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateFile">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.page-header {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-lg;

  h3 {
    margin: 0;
    font-size: 18px;
    color: $text-primary;
  }
}

.create-tabs {
  :deep(.el-tabs__content) {
    min-height: 480px;
  }
}

.create-card {
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  padding: $spacing-lg $spacing-xl;
  max-width: 800px;
}

// ===== 编辑器布局 =====
.editor-layout {
  display: flex;
  gap: 0;
  min-height: 500px;
  background: $bg-white;
  border-radius: $radius-base;
  border: 1px solid $border-lighter;
  overflow: hidden;
}

.tree-panel {
  width: 260px;
  border-right: 1px solid $border-lighter;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.tree-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px 0;

  .tree-label {
    font-size: 12px;
    font-weight: 600;
    color: $text-secondary;
  }

  .tree-actions {
    display: flex;
    gap: 2px;
  }
}

.tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
  font-size: 13px;

  .node-actions {
    margin-left: auto;
    opacity: 0;
    transition: opacity 0.15s;
  }

  &:hover .node-actions {
    opacity: 1;
  }
}

.editor-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid $border-lighter;
  background: $bg-hover;

  .editor-path {
    font-size: 12px;
    color: $text-secondary;
    font-family: 'Consolas', 'Monaco', monospace;
  }
}

.editor-body {
  flex: 1;
  padding: 0;
}

// ===== 依赖管理 =====
.deps-card {
  max-width: 800px;
}

.deps-section {
  margin-bottom: $spacing-lg;

  &:last-child {
    margin-bottom: 0;
  }

  h4 {
    margin: 0 0 4px;
    font-size: 15px;
    font-weight: 600;
    color: $text-primary;
  }

  .deps-desc {
    margin: 0 0 $spacing-sm;
    font-size: 13px;
    color: $text-secondary;
  }
}

.deps-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: $spacing-sm;
}

// ===== 底部操作栏 =====
.create-footer {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
  border-top: 1px solid $border-lighter;
}

// ===== 代码编辑器 =====
:deep(.code-textarea .el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  border: none;
  border-radius: 0;
  box-shadow: none;
}
</style>
