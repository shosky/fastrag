<script setup lang="ts">
/**
 * 问答知识管理：问答分类的 新建 / 查看列表 / 编辑 / 删除，以及按分类查看问答知识。
 * 分类走 /api/kb-categories（知识库分类树，问答知识按其归类）；
 * 问答知识走 /api/kb/{kbId}/qa-pairs。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

// ===== 分类管理 =====
const catLoading = ref(false)
const categories = ref<any[]>([])
const catKeyword = ref('')
const catDialog = ref(false)
const catEditing = ref(false)
const catForm = ref<any>({ id: '', name: '', sort: 1, description: '', color: '#409EFF' })
const activeCategory = ref('')

const filteredCategories = computed(() =>
  catKeyword.value
    ? categories.value.filter((c: any) => (c.name || '').includes(catKeyword.value))
    : categories.value,
)

async function loadCategories() {
  catLoading.value = true
  try {
    const res: any = await api.getKbCategories()
    categories.value = Array.isArray(res) ? res : res?.list || res?.records || []
  } catch {
    categories.value = []
    ElMessage.error('分类加载失败')
  } finally {
    catLoading.value = false
  }
}

function openCategory(row?: any) {
  catEditing.value = !!row
  catForm.value = row
    ? { id: row.id, name: row.name, sort: row.sort ?? 1, description: row.description || '', color: row.color || '#409EFF' }
    : { id: '', name: '', sort: (categories.value.length || 0) + 1, description: '', color: '#409EFF' }
  catDialog.value = true
}

async function saveCategory() {
  if (!catForm.value.name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  const payload = {
    name: catForm.value.name,
    sort: Number(catForm.value.sort) || 1,
    description: catForm.value.description,
    color: catForm.value.color,
  }
  try {
    if (catEditing.value) {
      await api.updateKbCategory(catForm.value.id, payload)
      ElMessage.success('分类已修改')
    } else {
      await api.createKbCategory(payload)
      ElMessage.success('分类已新建')
    }
    catDialog.value = false
    await loadCategories()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function removeCategory(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？该分类下的问答知识不会被删除。`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteKbCategory(row.id)
    ElMessage.success('分类已删除')
    if (activeCategory.value === row.name) activeCategory.value = ''
    await loadCategories()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function viewCategoryQa(row: any) {
  activeCategory.value = row.name
  loadQaList()
}

// ===== 问答知识（按分类查看） =====
const kbList = ref<any[]>([])
const kbId = ref('')
const qaLoading = ref(false)
const qaList = ref<any[]>([])
const qaKeyword = ref('')
const qaType = ref('')

const filteredQa = computed(() =>
  qaList.value.filter((q: any) => {
    const kwOk = !qaKeyword.value || (q.question || '').includes(qaKeyword.value)
    const typeOk = !qaType.value || q.faqType === qaType.value
    const catOk = !activeCategory.value || (q.category || q.faqCategory || '') === activeCategory.value || !q.category
    return kwOk && typeOk && catOk
  }),
)

async function loadKbList() {
  try {
    const res: any = await api.getKnowledgeBases()
    kbList.value = Array.isArray(res) ? res : res?.list || res?.records || []
    if (kbList.value.length && !kbId.value) kbId.value = kbList.value[0].id
  } catch {
    kbList.value = []
  }
}

async function loadQaList() {
  if (!kbId.value) return
  qaLoading.value = true
  try {
    const res: any = await api.getQaPairs(kbId.value, { page: 1, pageSize: 100 })
    qaList.value = res?.records || res?.list || res || []
  } catch {
    qaList.value = []
  } finally {
    qaLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadKbList()])
  await loadQaList()
})
</script>

<template>
  <div class="page-container">
    <div class="section-header">
      <h3>问答知识管理</h3>
      <div style="display: flex; gap: 8px">
        <el-button :loading="catLoading" @click="loadCategories">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="primary" @click="openCategory()">
          <el-icon><Plus /></el-icon>新建分类
        </el-button>
      </div>
    </div>

    <!-- 分类列表 -->
    <div class="card-panel" style="margin-bottom: 16px">
      <div class="section-header" style="margin-bottom: 12px">
        <div class="section-title" style="font-size: 14px">
          问答分类（共 {{ filteredCategories.length }} 个）
          <el-tag v-if="activeCategory" size="small" type="warning" closable style="margin-left: 8px" @close="activeCategory = ''">
            当前筛选：{{ activeCategory }}
          </el-tag>
        </div>
        <el-input v-model="catKeyword" placeholder="按分类名称查询" clearable size="small" style="width: 200px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <el-table :data="filteredCategories" stripe size="small" v-loading="catLoading">
        <el-table-column prop="name" label="分类名称" min-width="180">
          <template #default="{ row }">
            <span :style="{ display: 'inline-block', width: '8px', height: '8px', borderRadius: '50%', marginRight: '6px', background: row.color || '#409EFF' }" />
            {{ row.name }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column prop="count" label="知识库数" width="90" align="center" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewCategoryQa(row)">查看问答</el-button>
            <el-button link type="primary" size="small" @click="openCategory(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeCategory(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 问答知识列表 -->
    <div class="card-panel">
      <div class="section-header" style="margin-bottom: 12px">
        <div class="section-title" style="font-size: 14px">问答知识列表</div>
        <div style="display: flex; gap: 8px; align-items: center">
          <el-select v-model="kbId" placeholder="选择知识库" size="small" style="width: 220px" @change="loadQaList">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-input v-model="qaKeyword" placeholder="搜索问题" clearable size="small" style="width: 180px" />
          <el-select v-model="qaType" placeholder="问题类型" clearable size="small" style="width: 130px">
            <el-option label="常见问题" value="common" />
            <el-option label="非常见问题" value="uncommon" />
          </el-select>
          <span style="color: var(--el-text-color-secondary); font-size: 12px">共 {{ filteredQa.length }} 条</span>
        </div>
      </div>
      <el-table :data="filteredQa" stripe size="small" v-loading="qaLoading">
        <el-table-column prop="question" label="问题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="280" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ row.faqType === 'uncommon' ? '非常见问题' : '常见问题' }}</template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键词" width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </div>

    <!-- 新建/编辑分类弹窗 -->
    <el-dialog v-model="catDialog" :title="catEditing ? '编辑分类' : '新建分类'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="分类名称">
          <el-input v-model="catForm.name" placeholder="如：常见问题" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="catForm.color" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="catForm.sort" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="catForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
