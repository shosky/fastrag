<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Search, Plus, CopyDocument, Delete, Cpu, User, Setting, MagicStick, Collection, Star, VideoPlay, DataLine, Tools } from '@element-plus/icons-vue'
import * as api from '@/api'

const router = useRouter()
const activeTab = ref('my')
const searchKeyword = ref('')
const selectedTag = ref('')
const showAddDialog = ref(false)
const loading = ref(false)

const newApp = ref({
  type: 'ChatBot 智能问答',
  name: '',
  description: '',
  icon: '',
  tags: [] as string[],
})

// 应用列表
const apps = ref<any[]>([])

/** 图标映射：根据 icon 字段值返回对应的 Element Plus 图标组件 */
function getAppIcon(icon: string) {
  const map: Record<string, any> = {
    robot: Cpu,
    user: User,
    setting: Setting,
    magic: MagicStick,
    kb: Collection,
    star: Star,
    video: VideoPlay,
    data: DataLine,
    tools: Tools,
  }
  return map[icon] || ChatDotRound
}

async function loadApps() {
  loading.value = true
  try {
    const appRes = await api.getApps()
    apps.value = (appRes as any)?.list || (appRes as any) || []
  } finally {
    loading.value = false
  }
}

onMounted(loadApps)

const filteredApps = computed(() => {
  let list = apps.value
  if (searchKeyword.value) {
    list = list.filter((app: any) => app.name?.includes(searchKeyword.value))
  }
  if (selectedTag.value) {
    list = list.filter((app: any) => (app.tags || []).includes(selectedTag.value))
  }
  return list
})

const allTags = computed(() => {
  const set = new Set<string>()
  apps.value.forEach((app: any) => (app.tags || []).forEach((t: string) => set.add(t)))
  return Array.from(set)
})

async function handleAddApp() {
  if (!newApp.value.name) {
    ElMessage.warning('请输入应用名称')
    return
  }
  try {
    await api.createApp({
      name: newApp.value.name,
      type: newApp.value.type.split(' ')[0],
      description: newApp.value.description,
      tags: newApp.value.tags,
    })
    showAddDialog.value = false
    newApp.value = { type: 'ChatBot 智能问答', name: '', description: '', icon: '', tags: [] }
    ElMessage.success('创建成功')
    await loadApps()
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  }
}

async function handleDeleteApp(id: string) {
  try {
    await ElMessageBox.confirm('删除应用会同步清理该应用产生的历史记录，确认删除？', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await api.deleteApp(id)
    ElMessage.success('删除成功')
    await loadApps()
  } catch {}
}

function handleCopyId(id: string) {
  navigator.clipboard.writeText(id)
  ElMessage.success('复制成功')
}

function goToEditor(id: string) {
  router.push(`/application/${id}/editor`)
}

function goToRuntime(id: string) {
  router.push(`/application/runtime?appId=${id}`)
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="我的应用" name="my">
        <div class="filter-bar">
          <el-input v-model="searchKeyword" placeholder="搜索应用" clearable style="width: 300px">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="showAddDialog = true">
            <el-icon><Plus /></el-icon>添加应用
          </el-button>
        </div>
        <div class="app-grid">
          <div v-for="app in filteredApps" :key="app.id" class="app-card">
            <div class="card-actions">
              <el-button link size="small" type="primary" @click.stop="goToRuntime(app.id)">
                <el-icon><VideoPlay /></el-icon>
              </el-button>
              <el-button link size="small" @click.stop="handleCopyId(app.id)">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
              <el-button link type="danger" size="small" @click.stop="handleDeleteApp(app.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <div class="app-card-header" @click="goToEditor(app.id)">
              <div class="app-icon" :style="{ background: app.icon }">
                <el-icon :size="24" color="#fff" :component="getAppIcon(app.icon)"></el-icon>
              </div>
              <div class="app-type">{{ app.type }}</div>
            </div>
            <h4 @click="goToEditor(app.id)">{{ app.name }}</h4>
            <p>{{ app.description }}</p>
            <div class="app-tags">
              <el-tag v-for="tag in app.tags" :key="tag" size="small" type="info">{{ tag }}</el-tag>
            </div>
          </div>
        </div>
        <el-empty v-if="!filteredApps.length" description="暂无应用" />
      </el-tab-pane>
    </el-tabs>

    <!-- 添加应用弹窗 -->
    <el-dialog v-model="showAddDialog" title="添加应用" width="500px">
      <el-form label-width="80px">
        <el-form-item label="应用类型" required>
          <el-select v-model="newApp.type" style="width: 100%">
            <el-option label="ChatBot 智能问答" value="ChatBot 智能问答" />
            <el-option label="Editor 写作助手" value="Editor 写作助手" />
            <el-option label="LiteAgent 智能体" value="LiteAgent 智能体" />
          </el-select>
        </el-form-item>
        <el-form-item label="应用名称" required>
          <el-input v-model="newApp.name" placeholder="请输入应用名称" />
        </el-form-item>
        <el-form-item label="应用简介">
          <el-input v-model="newApp.description" type="textarea" :rows="3" placeholder="请输入应用简介" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="newApp.tags" multiple placeholder="请选择标签" style="width: 100%">
            <el-option v-for="tag in allTags" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddApp">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: $spacing-base;
  margin-top: $spacing-base;
}

.app-card {
  background: $bg-white;
  border-radius: $radius-base;
  padding: $spacing-lg;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid $border-lighter;
  position: relative;

  &:hover {
    box-shadow: $shadow-base;
    border-color: $color-primary;
  }

  h4 { margin: $spacing-sm 0 $spacing-xs; font-size: 15px; }
  p { font-size: 12px; color: $text-secondary; margin-bottom: $spacing-sm; }
}

.card-actions {
  position: absolute;
  top: $spacing-sm;
  right: $spacing-sm;
  display: flex;
  gap: $spacing-xs;
  opacity: 0;
  transition: opacity 0.2s;
}

.app-card:hover .card-actions {
  opacity: 1;
}

.app-card-header, .template-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-sm;
}

.app-icon {
  width: 48px;
  height: 48px;
  border-radius: $radius-base;
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-type {
  font-size: 12px;
  color: $text-secondary;
}

.app-tags {
  display: flex;
  gap: $spacing-xs;
}
</style>
