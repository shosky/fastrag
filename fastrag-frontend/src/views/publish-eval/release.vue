<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/mock/publish-eval'


const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
const showDialog = ref(false)
const dialogTitle = ref('')
const editingId = ref<string | null>(null)
const formData = ref<any>({})

const _SL: Record<string, string> = {}
const _SC: Record<string, string> = {}
function statusText(v: string) { return _SL[v] || v }
function statusColor(v: string) { return (_SC[v] || 'info') as any }

async function loadData() {
  loading.value = true
  try {
    const res = (api as any).getRobotReleaseList({ page: currentPage.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined })
    if (res && typeof res === 'object' && 'list' in res) {
      dataList.value = res.list; total.value = res.total
    } else if (Array.isArray(res)) {
      dataList.value = res; total.value = res.length
    } else {
      dataList.value = res ? [res] : []; total.value = dataList.value.length
    }
  } finally { loading.value = false }
}
onMounted(loadData)

function handleSearch() { currentPage.value = 1; loadData() }
function handlePageChange(p: number) { currentPage.value = p; loadData() }
function handleSizeChange(s: number) { pageSize.value = s; currentPage.value = 1; loadData() }

function handleAdd() { editingId.value = null; formData.value = {}; dialogTitle.value = '新增'; showDialog.value = true }
function handleEdit(row: any) { editingId.value = row.id; formData.value = { ...row }; dialogTitle.value = '编辑'; showDialog.value = true }
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定要删除该记录吗？', '提示', { type: 'warning' })
    const fn = (api as any)['deleteRelease'] || (api as any).deleteFaq
    if (fn) await fn(row.id)
    ElMessage.success('删除成功'); loadData()
  } catch {}
}
async function handleSave() {
    if (!formData.value.robotName) { ElMessage.warning('请输入机器人名称'); return }
    if (!formData.value.version) { ElMessage.warning('请输入版本'); return }
    if (!formData.value.environment) { ElMessage.warning('请输入环境'); return }
  try {
    const cfn = (api as any)['createRelease'] || (api as any).createFaq
    const ufn = (api as any)['updateRelease'] || (api as any).updateFaq
    if (editingId.value) { if (ufn) await ufn(editingId.value, formData.value); ElMessage.success('更新成功') }
    else { if (cfn) await cfn(formData.value); ElMessage.success('创建成功') }
    showDialog.value = false; loadData()
  } catch {}
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">机器人发布</div>
        <el-button type="primary" @click="handleAdd">新增</el-button>
      </div>
      <div class="filter-bar">
        <el-input v-model="searchKeyword" placeholder="搜索..." clearable style="width: 240px" @keyup.enter="handleSearch" />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
      <el-table :data="dataList" stripe>
          <el-table-column prop="robotName" label="机器人" min-width="150" show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="120" show-overflow-tooltip />
          <el-table-column prop="environment" label="环境" width="100" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusColor(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="releaseNotes" label="发布说明" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="创建时间" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange" @size-change="handleSizeChange" />
      </div>
    </div>
    <el-dialog v-model="showDialog" :title="dialogTitle" width="600px" :close-on-click-modal="false">
      <el-form label-width="100px">
            <el-form-item label="机器人名称" required>
              <el-input v-model="formData.robotName" placeholder="请输入机器人名称" />
            </el-form-item>
            <el-form-item label="版本" required>
              <el-input v-model="formData.version" placeholder="请输入版本" />
            </el-form-item>
            <el-form-item label="环境" required>
              <el-select v-model="formData.environment" placeholder="请选择环境" style="width: 100%">
                <el-option v-for="opt in [{label:'测试',value:'staging'},{label:'生产',value:'production'}]" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="发布说明" >
              <el-input v-model="formData.releaseNotes" type="textarea" :rows="3" placeholder="请输入发布说明" />
            </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
