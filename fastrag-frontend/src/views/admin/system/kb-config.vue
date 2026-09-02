<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePagination } from '@/composables/usePagination'
import * as api from '@/api'

const searchName = ref('')
const filterType = ref('')
const showDefaultConfig = ref(false)
const showBatchEdit = ref(false)
const loading = ref(false)

const defaultConfig = ref({
  teamSpace: 10,
  teamUnit: 'GB',
  personalSpace: 5,
  personalUnit: 'GB',
})

const batchForm = ref({ space: 10, unit: 'GB' })
const selectedKBs = ref<string[]>([])

const kbList = ref<any[]>([])

// --- 分页 ---
const { currentPage, pageSize, handleCurrentChange, handleSizeChange } = usePagination(10)

const paginatedKBs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

async function loadKBs() {
  loading.value = true
  try {
    const res = await api.getKnowledgeBases()
    kbList.value = ((res as any)?.list || (res as any) || []).map((kb: any) => ({
      ...kb,
      usedSize: formatSize(kb.usedSize || 0),
      spaceLimit: formatSize(kb.totalSize || 10737418240),
      editing: false,
    }))
  } finally {
    loading.value = false
  }
}

onMounted(loadKBs)

function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const gb = bytes / (1024 * 1024 * 1024)
  if (gb >= 1) return gb.toFixed(1) + ' GB'
  const mb = bytes / (1024 * 1024)
  return mb.toFixed(1) + ' MB'
}

const filteredList = computed(() => {
  return kbList.value.filter((kb: any) => {
    const nameMatch = !searchName.value || kb.name?.includes(searchName.value)
    const typeMatch = !filterType.value || kb.type === filterType.value
    return nameMatch && typeMatch
  })
})

function handleSearch() {
  // 使用 computed 自动过滤
}

function handleReset() {
  searchName.value = ''
  filterType.value = ''
}

function handleSaveDefaultConfig() {
  showDefaultConfig.value = false
  ElMessage.success('保存成功')
}

function handleBatchEdit() {
  if (selectedKBs.value.length === 0) {
    ElMessage.warning('请先勾选知识库')
    return
  }
  showBatchEdit.value = true
}

function handleBatchSave() {
  showBatchEdit.value = false
  ElMessage.success('批量修改成功')
}

function handleInlineEdit(kb: any) {
  kb.editing = true
}

async function handleInlineSave(kb: any) {
  kb.editing = false
  try {
    await api.updateKnowledgeBase(kb.id, { totalSize: kb.totalSize })
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">知识库配置</div>
        <div class="header-actions">
          <el-button @click="showDefaultConfig = true">默认配置</el-button>
          <el-button :disabled="selectedKBs.length === 0" @click="handleBatchEdit">批量修改</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-input v-model="searchName" placeholder="搜索知识库名称" clearable style="width: 200px" />
        <el-select v-model="filterType" placeholder="类型" clearable style="width: 120px">
          <el-option label="团队" value="团队" />
          <el-option label="个人" value="个人" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="paginatedKBs" stripe @selection-change="(rows: any) => selectedKBs = rows.map((r: any) => r.id)">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="name" label="知识库名称" />
        <el-table-column prop="creator" label="创建者" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="120" />
        <el-table-column prop="type" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === '团队' ? 'info' : 'warning'" size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="usedSize" label="已使用大小" width="100" />
        <el-table-column label="空间限制" width="200">
          <template #default="{ row }">
            <div v-if="row.editing" class="inline-edit">
              <el-input v-model="row.spaceLimit" size="small" style="width: 80px" />
              <el-button type="primary" size="small" @click="handleInlineSave(row)">确认</el-button>
              <el-button size="small" @click="row.editing = false">取消</el-button>
            </div>
            <span v-else>{{ row.spaceLimit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleInlineEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="kb-config__pagination">
        <el-pagination
          v-if="filteredList.length > pageSize"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="filteredList.length"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 默认配置弹窗 -->
    <el-dialog v-model="showDefaultConfig" title="默认知识库空间配置" width="400px">
      <el-form label-width="120px">
        <el-form-item label="团队知识库空间">
          <div class="space-input">
            <el-input-number v-model="defaultConfig.teamSpace" :min="1" />
            <el-select v-model="defaultConfig.teamUnit" style="width: 80px">
              <el-option label="GB" value="GB" />
              <el-option label="MB" value="MB" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="个人知识库空间">
          <div class="space-input">
            <el-input-number v-model="defaultConfig.personalSpace" :min="1" />
            <el-select v-model="defaultConfig.personalUnit" style="width: 80px">
              <el-option label="GB" value="GB" />
              <el-option label="MB" value="MB" />
            </el-select>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDefaultConfig = false">取消</el-button>
        <el-button type="primary" @click="handleSaveDefaultConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量修改弹窗 -->
    <el-dialog v-model="showBatchEdit" title="批量修改空间限制" width="400px">
      <el-form label-width="100px">
        <el-form-item label="新空间限制">
          <div class="space-input">
            <el-input-number v-model="batchForm.space" :min="1" />
            <el-select v-model="batchForm.unit" style="width: 80px">
              <el-option label="GB" value="GB" />
              <el-option label="MB" value="MB" />
            </el-select>
          </div>
        </el-form-item>
        <el-alert :title="`将影响 ${selectedKBs.length} 个知识库`" type="info" show-icon :closable="false" />
      </el-form>
      <template #footer>
        <el-button @click="showBatchEdit = false">取消</el-button>
        <el-button type="primary" @click="handleBatchSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-base;
  .header-actions { display: flex; gap: $spacing-sm; }
}

.table-footer {
  margin-top: $spacing-base;
  font-size: 13px;
  color: $text-secondary;
}

.inline-edit {
  display: flex;
  gap: $spacing-xs;
  align-items: center;
}

.space-input {
  display: flex;
  gap: $spacing-sm;
}

.kb-config__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
