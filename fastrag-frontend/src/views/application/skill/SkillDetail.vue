<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()
const slug = route.params.slug as string
const activeTab = ref('editor')

// 技能数据
const skill = ref<any>(null)
const skillId = ref('')

// 文件系统
const fileTree = ref<any[]>([])
const treeProps = { children: 'children', label: 'name' }
const currentFilePath = ref('')
const currentFileContent = ref<string | null>(null)
const originalContent = ref('')
const saving = ref(false)

// 范围
const skillEnabled = ref(true)
const shareAccessLevel = ref('user')

// 依赖
const depOptions = ref<any>(null)
const selectedTools = ref<string[]>([])
const selectedMcps = ref<string[]>([])
const selectedSkills = ref<string[]>([])

onMounted(async () => {
  await loadSkill()
  await loadFileTree()
  await loadDepOptions()
})

async function loadSkill() {
  try {
    const list = await api.getSkills()
    skill.value = list.find((s: any) => s.slug === slug)
    if (!skill.value) {
      // try detail API
      skill.value = await api.getSkillBySlug(slug)
    }
    if (skill.value) {
      skillId.value = skill.value.id
      skillEnabled.value = skill.value.enabled === 1 || skill.value.enabled === true
    }
  } catch (e) {
    ElMessage.error('加载技能失败')
    router.push('/application/skill-management')
  }
}

async function loadFileTree() {
  try {
    fileTree.value = [await api.getSkillTree(slug)]
  } catch {
    fileTree.value = []
  }
}

async function refreshTree() {
  await loadFileTree()
  currentFilePath.value = ''
  currentFileContent.value = null
}

async function handleFileClick(data: any, treeNode: any) {
  if (data.isDir) return
  const path = buildFilePath(data, treeNode)
  currentFilePath.value = path
  try {
    const res = await api.getSkillFile(slug, path)
    currentFileContent.value = res.content || ''
    originalContent.value = res.content || ''
  } catch {
    currentFileContent.value = '// 文件不可编辑'
  }
}

function buildFilePath(data: any, treeNode: any): string {
  // el-tree @node-click 第一个参数是 data，第二个是 TreeNode（有 parent 链）
  const parts: string[] = [data.name]
  let parent = treeNode?.parent
  // 沿 parent 链向上直到根（根节点的 parent.data.name === slug）
  while (parent && parent.data && parent.data.name !== slug) {
    parts.unshift(parent.data.name)
    parent = parent.parent
  }
  return parts.join('/')
}

async function saveFile() {
  if (!currentFilePath.value || currentFileContent.value === null) return
  saving.value = true
  try {
    await api.updateSkillFile(slug, currentFilePath.value, currentFileContent.value)
    ElMessage.success('保存成功')
    originalContent.value = currentFileContent.value
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function loadDepOptions() {
  try {
    const opts = await api.getDependencyOptions(skillId.value)
    depOptions.value = opts
  } catch {
    depOptions.value = { tools: [], mcps: [], skills: [] }
  }
}

async function saveDependencies() {
  const deps = [
    ...selectedTools.value.map((n: string) => ({ type: 'tool', name: n, required: false })),
    ...selectedMcps.value.map((n: string) => ({ type: 'mcp', name: n, required: false })),
    ...selectedSkills.value.map((n: string) => ({ type: 'skill', name: n, required: false })),
  ]
  try {
    await api.updateSkillDependencies(skillId.value, deps)
    ElMessage.success('依赖已更新')
  } catch {
    ElMessage.error('更新依赖失败')
  }
}

function removeTool(t: string) {
  selectedTools.value = selectedTools.value.filter((x) => x !== t)
}
function removeMcp(m: string) {
  selectedMcps.value = selectedMcps.value.filter((x) => x !== m)
}
function removeSkill(s: string) {
  selectedSkills.value = selectedSkills.value.filter((x) => x !== s)
}

async function handleToggleEnabled(val: boolean) {
  try {
    await api.toggleSkill(skillId.value)
  } catch {
    skillEnabled.value = !val
  }
}

async function saveShareConfig() {
  try {
    await api.updateSkillShareConfig(skillId.value, {
      accessLevel: shareAccessLevel.value,
    })
    ElMessage.success('设置已保存')
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handleExport() {
  try {
    const blob = await api.exportSkillZip(slug)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${slug}.zip`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定删除此技能？', '确认删除')
    await api.deleteSkill(skillId.value)
    ElMessage.success('已删除')
    router.push('/application/skill-management')
  } catch {
    // cancelled or failed
  }
}

function goBack() {
  router.push('/application/skill-management')
}
</script>

<template>
  <div class="skill-detail">
    <!-- 顶部导航 -->
    <div class="detail-header">
      <el-button @click="goBack" text>← 返回</el-button>
      <h2>{{ skill?.name }}</h2>
      <el-tag>{{ skill?.slug }}</el-tag>
      <el-tag :type="skill?.enabled ? 'success' : 'info'">
        {{ skill?.enabled ? '已启用' : '已禁用' }}
      </el-tag>
      <div class="header-actions">
        <el-button @click="handleExport">导出 ZIP</el-button>
        <el-button type="danger" @click="handleDelete">删除</el-button>
      </div>
    </div>

    <!-- 三个 Tab -->
    <el-tabs v-model="activeTab">
      <!-- Tab 1: 代码管理 -->
      <el-tab-pane label="代码管理" name="editor">
        <div class="editor-layout">
          <div class="file-tree">
            <div class="tree-header">
              <span>文件</span>
              <el-button size="small" text @click="refreshTree">刷新</el-button>
            </div>
            <el-tree :data="fileTree" :props="treeProps" @node-click="handleFileClick" />
          </div>
          <div class="file-editor">
            <div class="editor-toolbar">
              <span>{{ currentFilePath || '选择文件' }}</span>
              <el-button
                v-if="currentFilePath"
                size="small"
                type="primary"
                @click="saveFile"
                :loading="saving"
              >保存</el-button>
            </div>
            <el-input
              v-if="currentFileContent !== null"
              v-model="currentFileContent"
              type="textarea"
              :rows="20"
              class="code-editor"
            />
            <div v-else class="editor-placeholder">
              <p>从左侧文件树选择文件编辑</p>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 生效范围 -->
      <el-tab-pane label="生效范围" name="settings">
        <div class="settings-panel">
          <el-card>
            <template #header>启用状态</template>
            <el-switch v-model="skillEnabled" @change="handleToggleEnabled" />
            <span class="ml-2">{{ skillEnabled ? '已启用' : '已禁用' }}</span>
          </el-card>
          <el-card>
            <template #header>分享配置</template>
            <el-form label-width="120px">
              <el-form-item label="访问级别">
                <el-select v-model="shareAccessLevel">
                  <el-option label="全局可见" value="global" />
                  <el-option label="仅创建者" value="user" />
                </el-select>
              </el-form-item>
              <el-button type="primary" @click="saveShareConfig">保存设置</el-button>
            </el-form>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- Tab 3: 依赖管理 -->
      <el-tab-pane label="依赖管理" name="dependencies">
        <div class="deps-panel">
          <el-card>
            <template #header>工具依赖</template>
            <el-select v-model="selectedTools" multiple placeholder="选择工具" style="width: 100%">
              <el-option v-for="t in depOptions?.tools || []" :key="t" :label="t" :value="t" />
            </el-select>
            <div class="selected-tags">
              <el-tag v-for="t in selectedTools" :key="t" closable @close="removeTool(t)">{{ t }}</el-tag>
            </div>
          </el-card>
          <el-card>
            <template #header>MCP 依赖</template>
            <el-select v-model="selectedMcps" multiple placeholder="选择 MCP 服务" style="width: 100%">
              <el-option v-for="m in depOptions?.mcps || []" :key="m" :label="m" :value="m" />
            </el-select>
            <div class="selected-tags">
              <el-tag v-for="m in selectedMcps" :key="m" closable @close="removeMcp(m)">{{ m }}</el-tag>
            </div>
          </el-card>
          <el-card>
            <template #header>技能依赖</template>
            <el-select v-model="selectedSkills" multiple placeholder="选择技能" style="width: 100%">
              <el-option v-for="s in depOptions?.skills || []" :key="s" :label="s" :value="s" />
            </el-select>
            <div class="selected-tags">
              <el-tag v-for="s in selectedSkills" :key="s" closable @close="removeSkill(s)">{{ s }}</el-tag>
            </div>
          </el-card>
          <el-button type="primary" @click="saveDependencies" class="save-deps-btn">更新依赖</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.skill-detail {
  padding: 20px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.detail-header h2 {
  margin: 0;
  font-size: 20px;
}

.header-actions {
  margin-left: auto;
}

.editor-layout {
  display: flex;
  gap: 16px;
  min-height: 500px;
}

.file-tree {
  width: 240px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px;
}

.tree-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.file-editor {
  flex: 1;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
}

.editor-toolbar {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
}

.code-editor {
  font-family: 'Courier New', monospace;
  border: none;
}

.code-editor :deep(.el-textarea__inner) {
  font-family: monospace;
  border: none;
}

.editor-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
}

.settings-panel {
  max-width: 600px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.deps-panel {
  max-width: 800px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.selected-tags {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.save-deps-btn {
  align-self: flex-start;
  margin-top: 8px;
}

.ml-2 {
  margin-left: 8px;
}
</style>
