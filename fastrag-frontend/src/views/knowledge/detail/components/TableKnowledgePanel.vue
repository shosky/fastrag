<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ kbId: string }>()

const loading = ref(false)
const tables = ref<any[]>([])
const showEditor = ref(false)
const current = ref<any>(null) // { id, name, description, columns, rows }
const currentLoading = ref(false)

// 新建表格对话框
const showCreate = ref(false)
const createForm = ref<{ name: string; description: string }>({ name: '', description: '' })
const createColumns = ref<{ name: string; key: string; type: string }[]>([{ name: '名称', key: 'name', type: 'text' }, { name: '内容', key: 'content', type: 'text' }])

// 加列对话框
const showAddColumn = ref(false)
const columnForm = ref<{ name: string; key: string; type: string }>({ name: '', key: '', type: 'text' })

// 行编辑（简易行内编辑）
const editingRowId = ref<string | null>(null)
const editingRowContent = ref<Record<string, string>>({})

async function loadTables() {
  loading.value = true
  try {
    const res: any = await api.getAnswerTables(props.kbId)
    tables.value = res || []
  } finally { loading.value = false }
}
onMounted(loadTables)

async function openEditor(row: any) {
  currentLoading.value = true
  showEditor.value = true
  try {
    const res: any = await api.getAnswerTable(props.kbId, row.id)
    current.value = res
  } finally { currentLoading.value = false }
}

async function handleCreate() {
  if (!createForm.value.name.trim()) { ElMessage.warning('请输入表格名称'); return }
  const cols = createColumns.value.filter((c) => c.name.trim())
  if (cols.length === 0) { ElMessage.warning('请至少定义一列'); return }
  try {
    await api.createAnswerTable(props.kbId, {
      name: createForm.value.name,
      description: createForm.value.description,
      columns: cols.map((c, i) => ({ name: c.name, key: c.key || `col_${i}`, type: c.type })),
    })
    ElMessage.success('表格创建成功')
    showCreate.value = false
    createForm.value = { name: '', description: '' }
    createColumns.value = [{ name: '名称', key: 'name', type: 'text' }, { name: '内容', key: 'content', type: 'text' }]
    loadTables()
  } catch { ElMessage.error('创建失败') }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除表格「${row.name}」？其列定义与内容将一并删除。`, '提示', { type: 'warning' })
    await api.deleteAnswerTable(props.kbId, row.id)
    ElMessage.success('删除成功')
    loadTables()
  } catch {}
}

// ---- 表格增加列 ----
async function handleAddColumn() {
  if (!columnForm.value.name.trim()) { ElMessage.warning('请输入列名'); return }
  try {
    const res: any = await api.addAnswerTableColumn(props.kbId, current.value.id, {
      name: columnForm.value.name,
      key: columnForm.value.key || undefined,
      type: columnForm.value.type,
    })
    current.value = res
    ElMessage.success('列已添加')
    showAddColumn.value = false
    columnForm.value = { name: '', key: '', type: 'text' }
  } catch { ElMessage.error('添加列失败') }
}

async function handleDeleteColumn(col: any) {
  try {
    await ElMessageBox.confirm(`确定删除列「${col.name}」？`, '提示', { type: 'warning' })
    const res: any = await api.deleteAnswerTableColumn(props.kbId, current.value.id, col.id)
    current.value = res
    loadTables()
  } catch {}
}

// ---- 表格内容（行） ----
function startEditRow(row: any) {
  editingRowId.value = row.id
  editingRowContent.value = { ...(row.contentMap || row.content || {}) }
}
function newRowTemplate(): Record<string, string> {
  const t: Record<string, string> = {}
  for (const c of current.value.columns || []) t[c.colKey] = ''
  return t
}
async function handleAddRow() {
  try {
    const res: any = await api.addAnswerTableRow(props.kbId, current.value.id, newRowTemplate())
    current.value = res
    loadTables()
  } catch { ElMessage.error('添加行失败') }
}
async function saveEditRow(row: any) {
  try {
    const res: any = await api.updateAnswerTableRow(props.kbId, current.value.id, row.id, editingRowContent.value)
    current.value = res
    editingRowId.value = null
    ElMessage.success('已保存')
  } catch { ElMessage.error('保存失败') }
}
async function handleDeleteRow(row: any) {
  try {
    await ElMessageBox.confirm('确定删除该行？', '提示', { type: 'warning' })
    const res: any = await api.deleteAnswerTableRow(props.kbId, current.value.id, row.id)
    current.value = res
    loadTables()
  } catch {}
}
function colName(colKey: string) {
  const c = (current.value?.columns || []).find((x: any) => x.colKey === colKey)
  return c ? c.name : colKey
}
</script>

<template>
  <div v-loading="loading">
    <div class="section-header">
      <div class="section-title">表格知识</div>
      <el-button type="primary" size="small" @click="showCreate = true">新增表格</el-button>
    </div>

    <el-table :data="tables" stripe size="small">
      <el-table-column type="index" width="50" />
      <el-table-column prop="name" label="表格名称" min-width="180" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="columnCount" label="列数" width="80" />
      <el-table-column prop="rowCount" label="行数" width="80" />
      <el-table-column prop="updatedAt" label="更新时间" width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEditor(row)">编辑内容</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建表格 -->
    <el-dialog v-model="showCreate" title="新增表格" width="640px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="createForm.name" placeholder="如：产品规格表" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="createForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="列定义">
          <div style="width: 100%">
            <div v-for="(c, i) in createColumns" :key="i" style="display: flex; gap: 8px; margin-bottom: 8px">
              <el-input v-model="c.name" placeholder="列名" style="flex: 1" />
              <el-input v-model="c.key" placeholder="键名(可选)" style="flex: 1" />
              <el-select v-model="c.type" style="width: 100px">
                <el-option label="文本" value="text" /><el-option label="数字" value="number" />
                <el-option label="日期" value="date" /><el-option label="链接" value="link" />
              </el-select>
              <el-button link type="danger" @click="createColumns.splice(i, 1)">删除</el-button>
            </div>
            <el-button size="small" @click="createColumns.push({ name: '', key: '', type: 'text' })">添加列</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 表格编辑器：增加列 + 行内容 -->
    <el-dialog v-model="showEditor" :title="`编辑表格：${current?.name || ''}`" width="900px" top="6vh" :close-on-click-modal="false">
      <div v-loading="currentLoading">
        <div style="display: flex; justify-content: space-between; margin-bottom: 12px">
          <div>
            <el-button size="small" @click="showAddColumn = true">表格增加列</el-button>
            <el-button size="small" type="primary" plain @click="handleAddRow">添加行</el-button>
          </div>
          <span style="color:#909399;font-size:12px">{{ (current?.columns || []).length }} 列 × {{ (current?.rows || []).length }} 行</span>
        </div>

        <el-table :data="current?.rows || []" size="small" border max-height="420">
          <el-table-column type="index" width="46" />
          <el-table-column v-for="c in (current?.columns || [])" :key="c.id" :label="c.name" min-width="140">
            <template #default="{ row }">
              <template v-if="editingRowId === row.id">
                <el-input v-model="editingRowContent[c.colKey]" size="small" />
              </template>
              <template v-else>{{ (row.contentMap || row.content || {})[c.colKey] }}</template>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="editingRowId === row.id">
                <el-button link type="success" size="small" @click="saveEditRow(row)">保存</el-button>
                <el-button link size="small" @click="editingRowId = null">取消</el-button>
              </template>
              <template v-else>
                <el-button link type="primary" size="small" @click="startEditRow(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="handleDeleteRow(row)">删除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top: 12px">
          <div style="font-size: 13px; color: #606266; margin-bottom: 6px">列定义</div>
          <el-tag v-for="c in (current?.columns || [])" :key="c.id" closable style="margin: 0 6px 6px 0"
            @close="handleDeleteColumn(c)">{{ c.name }}（{{ c.colKey }}）</el-tag>
        </div>
      </div>
    </el-dialog>

    <!-- 表格增加列 -->
    <el-dialog v-model="showAddColumn" title="表格增加列" width="460px" :close-on-click-modal="false">
      <el-form label-width="70px">
        <el-form-item label="列名" required><el-input v-model="columnForm.name" /></el-form-item>
        <el-form-item label="键名"><el-input v-model="columnForm.key" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="columnForm.type" style="width: 100%">
            <el-option label="文本" value="text" /><el-option label="数字" value="number" />
            <el-option label="日期" value="date" /><el-option label="链接" value="link" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddColumn = false">取消</el-button>
        <el-button type="primary" @click="handleAddColumn">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>
