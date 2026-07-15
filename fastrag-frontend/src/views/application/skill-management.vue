<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const router = useRouter()

// --- 列表数据 ---
const skills = ref<any[]>([])

async function loadSkills() {
  skills.value = (await api.getSkills()) as any || []
}

onMounted(loadSkills)

const searchKeyword = ref('')
const selectedCategory = ref('')

// 分类筛选选项
const categoryOptions = [
  { label: '全部', value: '' },
  { label: '工具', value: 'tool' },
  { label: '模型', value: 'model' },
  { label: 'MCP', value: 'mcp' },
  { label: '技能', value: 'skill' },
]

const filteredSkills = computed(() => {
  let list = skills.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(
      (s: any) =>
        s.name.toLowerCase().includes(kw) ||
        (s.slug || '').toLowerCase().includes(kw) ||
        s.description.toLowerCase().includes(kw),
    )
  }
  if (selectedCategory.value) {
    list = list.filter((s: any) => s.category === selectedCategory.value)
  }
  return list
})

// --- 路由跳转 ---
const goToDetail = (skill: any) => {
  router.push('/application/skill-management/' + skill.slug)
}

const goToEdit = (skill: any) => {
  router.push('/application/skill-management/' + skill.slug)
}

async function handleDelete(skill: any) {
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

async function handleToggleEnabled(skill: any) {
  // 切换到具体状态而不是取反
  const newEnabled = skill.enabled === 1 || skill.enabled === true ? 0 : 1
  await api.setSkillEnabled(skill.id, newEnabled === 1)
  skill.enabled = newEnabled
  ElMessage.success(newEnabled ? '已启用' : '已禁用')
}

// --- 上传技能 ---
const showUploadDialog = ref(false)
const uploadFileList = ref<any[]>([])
const uploadSubmitting = ref(false)

function openUpload() {
  showUploadDialog.value = true
  uploadFileList.value = []
}

function handleUploadRemove() {
  uploadFileList.value = []
}

async function handleUploadSubmit() {
  if (!uploadFileList.value.length) {
    ElMessage.warning('请先选择技能包文件')
    return
  }
  uploadSubmitting.value = true
  try {
    const file = uploadFileList.value[0].raw
    // 后端一次性完成：上传 → 创建草稿 → 确认安装 → 清理草稿
    await api.prepareSkillImport(file)
    await loadSkills()
    showUploadDialog.value = false
    ElMessage.success('技能上传成功')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '上传失败')
  } finally {
    uploadSubmitting.value = false
  }
}

// --- 新建技能 ---
function goToCreate() {
  router.push('/application/skill-management/create')
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
            v-for="c in categoryOptions.filter((x) => x.value)"
            :key="c.value"
            :label="c.label"
            :value="c.value"
          />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button @click="goToCreate">
          <el-icon><Plus /></el-icon>新建技能
        </el-button>
        <el-button type="primary" @click="openUpload">
          <el-icon><UploadFilled /></el-icon>上传技能
        </el-button>
      </div>
    </div>

    <!-- 技能列表 -->
    <div class="skills-grid" v-if="filteredSkills.length">
      <div v-for="skill in filteredSkills" :key="skill.id" class="skill-card">
        <div class="skill-card-body" @click="goToDetail(skill)">
          <div class="skill-title-row">
            <div class="skill-icon">
              <el-icon :size="16" color="#909399"><MagicStick /></el-icon>
            </div>
            <h4 :title="skill.name">{{ skill.name }}</h4>
            <span class="source-text">{{ skill.source }}</span>
          </div>
          <p class="skill-desc">{{ skill.description }}</p>
          <div class="skill-tags">
            <span class="tag-text">{{ skill.category }}</span>
            <span class="tag-text tag-mono">{{ skill.slug || skill.identifier }}</span>
          </div>
        </div>
        <div class="skill-card-footer">
          <div class="usage">
            <el-icon><DataLine /></el-icon>
            <span>调用 {{ skill.usageCount }} 次</span>
          </div>
          <div class="actions">
            <el-switch :model-value="skill.enabled === 1 || skill.enabled === true" size="small" style="pointer-events: none;" />
            <el-button link size="small" @click.stop="handleToggleEnabled(skill)">
              {{ skill.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="primary" size="small" @click.stop="goToDetail(skill)">详情</el-button>
            <el-button link type="primary" size="small" @click.stop="goToEdit(skill)">编辑</el-button>
            <el-dropdown trigger="click">
              <el-icon class="more-icon"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleDelete(skill)">
                    <span style="color: var(--el-color-danger)">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无技能，点击右上角新建或上传技能" />

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
            :on-remove="handleUploadRemove"
            style="width: 100%"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              将技能包拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">支持 .zip / .tar.gz / .skill 格式，上传后将自动创建草稿</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploadSubmitting" :disabled="!uploadFileList.length" @click="handleUploadSubmit">
          上传
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
</style>
