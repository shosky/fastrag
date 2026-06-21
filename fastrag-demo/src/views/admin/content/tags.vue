<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeGroup = ref('')
const searchKeyword = ref('')
const showTagDialog = ref(false)
const showGroupDialog = ref(false)
const dialogTitle = ref('新增标签')

const tagForm = ref({ type: '知识库', name: '', group: '' })
const groupForm = ref({ name: '' })

const tagGroups = ref([
  { id: '1', name: '产品标签', count: 5 },
  { id: '2', name: '技术标签', count: 8 },
  { id: '3', name: '业务标签', count: 3 },
])

const tagList = ref([
  { id: '1', name: '产品', type: '知识库', group: '产品标签' },
  { id: '2', name: '文档', type: '页面文档', group: '产品标签' },
  { id: '3', name: 'API', type: '知识库', group: '技术标签' },
  { id: '4', name: '测试', type: '应用中心', group: '业务标签' },
  { id: '5', name: '演示', type: '提示词', group: '业务标签' },
])

function handleAddTag() {
  dialogTitle.value = '新增标签'
  tagForm.value = { type: '知识库', name: '', group: '' }
  showTagDialog.value = true
}

function handleEditTag(tag: any) {
  dialogTitle.value = '编辑标签'
  tagForm.value = { ...tag }
  showTagDialog.value = true
}

async function handleDeleteTag(tag: any) {
  try {
    await ElMessageBox.confirm('是否删除该标签？', '删除确认', { type: 'warning' })
    tagList.value = tagList.value.filter(t => t.id !== tag.id)
    ElMessage.success('删除成功')
  } catch {}
}

function handleAddGroup() {
  groupForm.value = { name: '' }
  showGroupDialog.value = true
}

function handleSaveTag() {
  showTagDialog.value = false
  ElMessage.success('保存成功')
}

function handleSaveGroup() {
  showGroupDialog.value = false
  ElMessage.success('保存成功')
}
</script>

<template>
  <div class="page-container">
    <div class="tag-layout">
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
          <span>{{ g.name }} ({{ g.count }})</span>
          <el-dropdown trigger="click">
            <el-button link size="small"><el-icon><MoreFilled /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>编辑</el-dropdown-item>
                <el-dropdown-item>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div class="tag-content">
        <div class="section-header">
          <div class="section-title">标签管理</div>
          <el-button type="primary" @click="handleAddTag">新增标签</el-button>
        </div>
        <div class="filter-bar">
          <el-input v-model="searchKeyword" placeholder="搜索标签" clearable style="width: 200px" />
          <el-select placeholder="标签类型" clearable style="width: 120px">
            <el-option label="知识库" value="知识库" />
            <el-option label="页面文档" value="页面文档" />
            <el-option label="应用中心" value="应用中心" />
            <el-option label="提示词" value="提示词" />
          </el-select>
          <el-button type="primary">查询</el-button>
          <el-button>重置</el-button>
        </div>
        <el-table :data="tagList" stripe>
          <el-table-column prop="name" label="标签名称" />
          <el-table-column prop="type" label="标签类型" width="120" />
          <el-table-column prop="group" label="所属标签组" width="120" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleEditTag(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDeleteTag(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
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
            <el-option v-for="g in tagGroups" :key="g.id" :label="g.name" :value="g.name" />
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
</style>
