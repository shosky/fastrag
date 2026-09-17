<script setup lang="ts">
/**
 * 实体管理（对话知识-实体）：在应用管理内维护实体库。
 * 实体按知识库维度存储（kb_entity，接口 /api/kb/{kbId}/entities）；
 * 知识库来源优先取应用已绑定的知识库（GET /apps/{appId}/knowledge-bases），未绑定时回退全部知识库。
 * 覆盖功能点：新增实体 / 修改实体 / 删除实体 / 实体库
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api'

const props = defineProps<{ appInfo: any }>()

const loading = ref(false)
const saving = ref(false)
const kbOptions = ref<any[]>([])
const kbId = ref('')
const entities = ref<any[]>([])
const keyword = ref('')
const typeFilter = ref('')

const ENTITY_TYPES = ['字典', '业务', '正则', '枚举']

const filtered = computed(() =>
  entities.value.filter((e: any) => {
    const kwOk = !keyword.value || (e.name || '').includes(keyword.value) || (e.description || '').includes(keyword.value)
    const tpOk = !typeFilter.value || e.entityType === typeFilter.value
    return kwOk && tpOk
  }),
)

/** 实体值：库里存 valuesJson（JSON 数组字符串），这里统一成数组便于编辑 */
function valuesOf(row: any): string[] {
  if (!row?.valuesJson) return []
  try {
    const v = typeof row.valuesJson === 'string' ? JSON.parse(row.valuesJson) : row.valuesJson
    return Array.isArray(v) ? v.map((x) => String(x)) : []
  } catch {
    return String(row.valuesJson).split(',').map((s) => s.trim()).filter(Boolean)
  }
}

async function loadKbOptions() {
  try {
    const bindings: any = await api.getAppKbBindings(props.appInfo?.id)
    const list = Array.isArray(bindings) ? bindings : bindings?.list || bindings?.records || []
    if (list.length) {
      kbOptions.value = list.map((b: any) => ({ id: b.kbId || b.id, name: b.kbName || b.name || b.kbId }))
    } else {
      const all: any = await api.getKnowledgeBases()
      kbOptions.value = (Array.isArray(all) ? all : all?.list || all?.records || []).map((k: any) => ({ id: k.id, name: k.name }))
    }
    if (!kbId.value && kbOptions.value.length) kbId.value = kbOptions.value[0].id
  } catch {
    kbOptions.value = []
  }
}

async function loadEntities() {
  if (!kbId.value) return
  loading.value = true
  try {
    entities.value = ((await api.getEntities(kbId.value)) as any) || []
  } catch {
    entities.value = []
  } finally {
    loading.value = false
  }
}

// ===== 新增 / 修改 =====
const dialogVisible = ref(false)
const editingId = ref('')
const form = ref<any>({ name: '', entityType: '字典', description: '', valuesText: '' })
function openCreate() {
  editingId.value = ''
  form.value = { name: '', entityType: '字典', description: '', valuesText: '' }
  dialogVisible.value = true
}
function openEdit(row: any) {
  editingId.value = row.id
  form.value = { name: row.name, entityType: row.entityType || '字典', description: row.description || '', valuesText: valuesOf(row).join('\n') }
  dialogVisible.value = true
}
async function save() {
  if (!form.value.name) {
    ElMessage.warning('请输入实体名称')
    return
  }
  const payload = {
    name: form.value.name,
    entityType: form.value.entityType,
    description: form.value.description,
    valuesJson: JSON.stringify(
      String(form.value.valuesText || '')
        .split(/[\n,，]/)
        .map((s) => s.trim())
        .filter(Boolean),
    ),
  }
  saving.value = true
  try {
    if (editingId.value) {
      await api.updateEntity(kbId.value, editingId.value, payload)
      ElMessage.success('实体已修改')
    } else {
      await api.createEntity(kbId.value, payload)
      ElMessage.success('实体已新增')
    }
    dialogVisible.value = false
    await loadEntities()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || ''))
  } finally {
    saving.value = false
  }
}

// ===== 删除 =====
async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除实体「${row.name}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.deleteEntity(kbId.value, row.id)
    ElMessage.success('实体已删除')
    await loadEntities()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.message || ''))
  }
}

// ===== 查看 =====
const viewVisible = ref(false)
const viewRow = ref<any>({})
function openView(row: any) {
  viewRow.value = row
  viewVisible.value = true
}

onMounted(async () => {
  await loadKbOptions()
  await loadEntities()
})
</script>

<template>
  <div class="entity-config config-section">
    <h3>实体管理</h3>
    <p class="desc">维护应用问答用到的实体（字典/业务/正则/枚举），检索与语义理解时按实体做归一化与召回。</p>

    <div class="entity-toolbar">
      <el-select v-model="kbId" placeholder="选择知识库" size="small" style="width: 220px" @change="loadEntities">
        <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <el-input v-model="keyword" placeholder="按实体名称/描述查询" clearable size="small" style="width: 200px" />
      <el-select v-model="typeFilter" placeholder="实体类型" clearable size="small" style="width: 130px">
        <el-option v-for="t in ENTITY_TYPES" :key="t" :label="t" :value="t" />
      </el-select>
      <span class="entity-count">实体库共 {{ filtered.length }} 个实体</span>
      <el-button size="small" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新增实体
      </el-button>
    </div>

    <el-table :data="filtered" stripe size="small" v-loading="loading">
      <el-table-column prop="name" label="实体名称" min-width="150" />
      <el-table-column prop="entityType" label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.entityType || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="实体值" min-width="260">
        <template #default="{ row }">
          <el-tag v-for="v in valuesOf(row).slice(0, 6)" :key="v" size="small" style="margin: 2px 4px 2px 0">{{ v }}</el-tag>
          <span v-if="valuesOf(row).length > 6" style="color: #909399; font-size: 12px">等 {{ valuesOf(row).length }} 项</span>
          <span v-if="!valuesOf(row).length" style="color: #909399">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openView(row)">查看</el-button>
          <el-button link type="primary" size="small" @click="openEdit(row)">修改</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!filtered.length && !loading" description="暂无实体，点击「新增实体」创建" :image-size="60" />

    <!-- 新增/修改实体 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '修改实体' : '新增实体'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="实体名称">
          <el-input v-model="form.name" placeholder="如：城市 / 套餐档位" />
        </el-form-item>
        <el-form-item label="实体类型">
          <el-select v-model="form.entityType" style="width: 100%">
            <el-option v-for="t in ENTITY_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="用途说明" />
        </el-form-item>
        <el-form-item label="实体值">
          <el-input v-model="form.valuesText" type="textarea" :rows="5" placeholder="每行一个值（也支持逗号分隔）&#10;北京&#10;上海&#10;广州" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看实体 -->
    <el-dialog v-model="viewVisible" title="查看实体" width="480px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="实体名称">{{ viewRow.name }}</el-descriptions-item>
        <el-descriptions-item label="实体类型">{{ viewRow.entityType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ viewRow.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实体值数量">{{ valuesOf(viewRow).length }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewRow.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top: 10px">
        <el-tag v-for="v in valuesOf(viewRow)" :key="v" size="small" style="margin: 2px 4px 2px 0">{{ v }}</el-tag>
      </div>
      <template #footer>
        <el-button @click="viewVisible = false">关闭</el-button>
        <el-button type="primary" @click="viewVisible = false; openEdit(viewRow)">修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.entity-config {
  .entity-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 12px 0;
  }
  .entity-count {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}
</style>
