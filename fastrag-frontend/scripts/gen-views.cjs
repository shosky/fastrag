const fs = require('fs');
const path = require('path');

const base = 'D:/Workspace/java/github/rag/fastrag/fastrag-frontend/src/views';

function ensureDir(dirPath) {
  if (!fs.existsSync(dirPath)) fs.mkdirSync(dirPath, { recursive: true });
}

function genPage(dir, filename, title, mockImport, mockFn, columns, formFields) {
  const dirPath = path.join(base, dir);
  ensureDir(dirPath);

  const hasForm = formFields.length > 0;

  const tableCols = columns.map(c => {
    const w = c.width ? `width="${c.width}"` : '';
    const mw = c.minWidth ? `min-width="${c.minWidth}"` : '';
    const dim = w || mw;
    if (c.tag) {
      return `          <el-table-column prop="${c.prop}" label="${c.label}" ${dim}>
            <template #default="{ row }">
              <el-tag :type="statusColor(row.${c.prop})" size="small">{{ statusText(row.${c.prop}) }}</el-tag>
            </template>
          </el-table-column>`;
    }
    if (c.switch) {
      return `          <el-table-column prop="${c.prop}" label="${c.label}" ${dim}>
            <template #default="{ row }"><el-switch :model-value="row.${c.prop}" disabled size="small" /></template>
          </el-table-column>`;
    }
    if (c.progress) {
      return `          <el-table-column prop="${c.prop}" label="${c.label}" ${dim}>
            <template #default="{ row }"><el-progress :percentage="row.${c.prop}" :stroke-width="6" style="width: 80px" /></template>
          </el-table-column>`;
    }
    if (c.tags) {
      return `          <el-table-column prop="${c.prop}" label="${c.label}" ${dim}>
            <template #default="{ row }">
              <el-tag v-for="t in (row.${c.prop} || [])" :key="t" size="small" style="margin: 2px">{{ t }}</el-tag>
            </template>
          </el-table-column>`;
    }
    return `          <el-table-column prop="${c.prop}" label="${c.label}" ${dim} show-overflow-tooltip />`;
  }).join('\n');

  const formItems = formFields.map(f => {
    const req = f.required ? 'required' : '';
    if (f.type === 'textarea') {
      return `            <el-form-item label="${f.label}" ${req}>
              <el-input v-model="formData.${f.field}" type="textarea" :rows="3" placeholder="请输入${f.label}" />
            </el-form-item>`;
    }
    if (f.type === 'select') {
      const opts = f.options.startsWith('[') ? f.options : `${f.options}`;
      return `            <el-form-item label="${f.label}" ${req}>
              <el-select v-model="formData.${f.field}" placeholder="请选择${f.label}" style="width: 100%">
                <el-option v-for="opt in ${opts}" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>`;
    }
    if (f.type === 'number') {
      return `            <el-form-item label="${f.label}" ${req}>
              <el-input-number v-model="formData.${f.field}" :min="0" style="width: 100%" />
            </el-form-item>`;
    }
    if (f.type === 'switch') {
      return `            <el-form-item label="${f.label}">
              <el-switch v-model="formData.${f.field}" />
            </el-form-item>`;
    }
    return `            <el-form-item label="${f.label}" ${req}>
              <el-input v-model="formData.${f.field}" placeholder="请输入${f.label}" />
            </el-form-item>`;
  }).join('\n');

  const optImports = formFields.filter(f => f.options && !f.options.startsWith('[')).map(f => f.options);
  const importLine = optImports.length > 0 ? `import { ${optImports.join(', ')} } from '${mockImport}'` : '';

  const tagCols = columns.filter(c => c.tag);
  const statusHelpers = tagCols.length > 0 ? `
const _SL: Record<string, string> = {}
const _SC: Record<string, string> = {}
function statusText(v: string) { return _SL[v] || v }
function statusColor(v: string) { return (_SC[v] || 'info') as any }
` : '';

  const requiredChecks = formFields.filter(f => f.required).map(f =>
    `    if (!formData.value.${f.field}) { ElMessage.warning('请输入${f.label}'); return }`
  ).join('\n');

  const tpl = `<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '${mockImport}'
${importLine}

const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchKeyword = ref('')
${hasForm ? "const showDialog = ref(false)\nconst dialogTitle = ref('')\nconst editingId = ref<string | null>(null)\nconst formData = ref<any>({})" : ''}
${statusHelpers}
async function loadData() {
  loading.value = true
  try {
    const res = (api as any).${mockFn}({ page: currentPage.value, pageSize: pageSize.value, keyword: searchKeyword.value || undefined })
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
${hasForm ? `
function handleAdd() { editingId.value = null; formData.value = {}; dialogTitle.value = '新增'; showDialog.value = true }
function handleEdit(row: any) { editingId.value = row.id; formData.value = { ...row }; dialogTitle.value = '编辑'; showDialog.value = true }
async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定要删除该记录吗？', '提示', { type: 'warning' })
    const fn = (api as any)['delete${pascal(filename)}'] || (api as any).deleteFaq
    if (fn) await fn(row.id)
    ElMessage.success('删除成功'); loadData()
  } catch {}
}
async function handleSave() {
${requiredChecks}
  try {
    const cfn = (api as any)['create${pascal(filename)}'] || (api as any).createFaq
    const ufn = (api as any)['update${pascal(filename)}'] || (api as any).updateFaq
    if (editingId.value) { if (ufn) await ufn(editingId.value, formData.value); ElMessage.success('更新成功') }
    else { if (cfn) await cfn(formData.value); ElMessage.success('创建成功') }
    showDialog.value = false; loadData()
  } catch {}
}` : ''}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="card-panel">
      <div class="section-header">
        <div class="section-title">${title}</div>
        ${hasForm ? '<el-button type="primary" @click="handleAdd">新增</el-button>' : ''}
      </div>
      <div class="filter-bar">
        <el-input v-model="searchKeyword" placeholder="搜索..." clearable style="width: 240px" @keyup.enter="handleSearch" />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
      <el-table :data="dataList" stripe>
${tableCols}
${hasForm ? `        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>` : ''}
      </el-table>
      <div class="table-footer" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange" @size-change="handleSizeChange" />
      </div>
    </div>
${hasForm ? `    <el-dialog v-model="showDialog" :title="dialogTitle" width="600px" :close-on-click-modal="false">
      <el-form label-width="100px">
${formItems}
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>` : ''}
  </div>
</template>
`;

  fs.writeFileSync(path.join(dirPath, filename + '.vue'), tpl, 'utf-8');
}

function pascal(str) {
  return str.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join('');
}

// Pages definition: [dir, filename, title, mockImport, mockFn, columns, formFields]
const pages = [
  ['answer-kb', 'faq', 'FAQ知识库', '@/mock/answer-kb', 'getFaqList', [{prop:'question',label:'问题',minWidth:200},{prop:'answerType',label:'答案类型',width:100},{prop:'status',label:'状态',width:80,tag:true},{prop:'hitCount',label:'命中次数',width:100},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'question',label:'问题',type:'input',required:true},{field:'answer',label:'答案',type:'textarea',required:true},{field:'answerType',label:'答案类型',type:'select',options:'ANSWER_TYPE_OPTIONS'},{field:'status',label:'状态',type:'select',options:'FAQ_STATUS_OPTIONS'}]],
  ['answer-kb', 'templates', '应答模板管理', '@/mock/answer-kb', 'getTemplateList', [{prop:'name',label:'模板名称',minWidth:200},{prop:'category',label:'分类',width:120},{prop:'usageCount',label:'使用次数',width:100},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'模板名称',type:'input',required:true},{field:'content',label:'模板内容',type:'textarea',required:true},{field:'category',label:'分类',type:'input'}]],
  ['answer-kb', 'keywords', '关键词配置', '@/mock/answer-kb', 'getKeywordList', [{prop:'faqQuestion',label:'关联问题',minWidth:200},{prop:'keywords',label:'关键词',minWidth:200,tags:true},{prop:'matchMode',label:'匹配模式',width:100},{prop:'weight',label:'权重',width:80},{prop:'createdAt',label:'创建时间',width:160}], [{field:'faqQuestion',label:'关联问题',type:'input',required:true},{field:'keywords',label:'关键词(逗号分隔)',type:'input',required:true},{field:'matchMode',label:'匹配模式',type:'select',options:'MATCH_MODE_OPTIONS'},{field:'weight',label:'权重',type:'number'}]],
  ['answer-kb', 'attributes', '属性管理', '@/mock/answer-kb', 'getAttributeList', [{prop:'name',label:'属性名称',minWidth:150},{prop:'type',label:'类型',width:100},{prop:'required',label:'必填',width:80},{prop:'description',label:'描述',minWidth:200},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'属性名称',type:'input',required:true},{field:'type',label:'类型',type:'select',options:'ATTRIBUTE_TYPE_OPTIONS',required:true},{field:'description',label:'描述',type:'input'},{field:'required',label:'是否必填',type:'switch'}]],
  ['answer-kb', 'tables', '表格管理', '@/mock/answer-kb', 'getTableList', [{prop:'name',label:'表格名称',minWidth:200},{prop:'description',label:'描述',minWidth:200},{prop:'updatedAt',label:'更新时间',width:160}], [{field:'name',label:'表格名称',type:'input',required:true},{field:'description',label:'描述',type:'textarea'}]],
  ['answer-kb', 'documents', '文档管理', '@/mock/answer-kb', 'getDocumentList', [{prop:'name',label:'文档名称',minWidth:200},{prop:'type',label:'类型',width:80},{prop:'size',label:'大小',width:100},{prop:'status',label:'状态',width:100,tag:true},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'文档名称',type:'input',required:true},{field:'type',label:'类型',type:'select',options:'DOC_TYPE_OPTIONS'}]],
  ['search-center', 'smart-search', '智能搜索', '@/mock/search-center', 'search', [{prop:'title',label:'标题',minWidth:200},{prop:'score',label:'相关度',width:100},{prop:'source',label:'来源',width:120},{prop:'type',label:'类型',width:80},{prop:'category',label:'分类',width:120}], []],
  ['search-center', 'multimodal', '多模态检索', '@/mock/search-center', 'getMultimodalConfigs', [{prop:'name',label:'配置名称',minWidth:150},{prop:'modality',label:'模态',width:100},{prop:'model',label:'模型',width:150},{prop:'enabled',label:'启用',width:80,switch:true},{prop:'threshold',label:'阈值',width:80},{prop:'maxResults',label:'最大结果数',width:100}], [{field:'name',label:'名称',type:'input'},{field:'threshold',label:'阈值',type:'number'},{field:'maxResults',label:'最大结果数',type:'number'}]],
  ['search-center', 'preferences', '检索偏好设置', '@/mock/search-center', 'getSearchPreference', [{prop:'defaultSort',label:'默认排序',width:120},{prop:'resultCount',label:'结果数量',width:100},{prop:'highlightMode',label:'高亮模式',width:120},{prop:'language',label:'语言',width:100}], [{field:'resultCount',label:'结果数量',type:'number'}]],
  ['search-center', 'tag-search', '知识标签检索', '@/mock/search-center', 'getTagTree', [{prop:'name',label:'标签名称',minWidth:200},{prop:'knowledgeCount',label:'知识数量',width:120}], []],
  ['search-center', 'search-logs', '检索日志分析', '@/mock/search-center', 'getSearchLogs', [{prop:'username',label:'用户',width:120},{prop:'keyword',label:'搜索词',minWidth:150},{prop:'resultCount',label:'结果数',width:100},{prop:'searchTime',label:'耗时(ms)',width:100},{prop:'source',label:'来源',width:120},{prop:'timestamp',label:'时间',width:160}], []],
  ['search-center', 'push', '知识推送', '@/mock/search-center', 'getPushList', [{prop:'name',label:'推送名称',minWidth:150},{prop:'targetType',label:'目标类型',width:100},{prop:'triggerCondition',label:'触发条件',minWidth:150},{prop:'status',label:'状态',width:80,tag:true},{prop:'pushCount',label:'推送次数',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'推送名称',type:'input',required:true},{field:'triggerCondition',label:'触发条件',type:'input'},{field:'status',label:'状态',type:'select',options:"[{label:'启用',value:'active'},{label:'暂停',value:'paused'}]"}]],
  ['search-center', 'model-training', '检索模型训练', '@/mock/search-center', 'getTrainingList', [{prop:'name',label:'任务名称',minWidth:150},{prop:'modelType',label:'模型类型',width:120},{prop:'status',label:'状态',width:100,tag:true},{prop:'progress',label:'进度',width:120,progress:true},{prop:'dataset',label:'数据集',width:120},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'任务名称',type:'input',required:true},{field:'modelType',label:'模型类型',type:'select',options:"[{label:'嵌入模型',value:'embedding'},{label:'重排序',value:'rerank'},{label:'纠错',value:'correction'}]"},{field:'dataset',label:'数据集',type:'input',required:true}]],
  ['search-center', 'query-rewrite', '查询重写/扩写', '@/mock/search-center', 'getRewriteList', [{prop:'name',label:'规则名称',minWidth:150},{prop:'type',label:'类型',width:80},{prop:'sourcePattern',label:'源模式',minWidth:150},{prop:'targetPattern',label:'目标模式',minWidth:150},{prop:'enabled',label:'启用',width:80,switch:true},{prop:'hitCount',label:'命中次数',width:100}], [{field:'name',label:'规则名称',type:'input',required:true},{field:'type',label:'类型',type:'select',options:"[{label:'重写',value:'rewrite'},{label:'扩写',value:'expand'}]",required:true},{field:'sourcePattern',label:'源模式',type:'input',required:true},{field:'targetPattern',label:'目标模式',type:'input',required:true}]],
  ['search-center', 'strategy', '检索策略', '@/mock/search-center', 'getStrategyList', [{prop:'name',label:'策略名称',minWidth:150},{prop:'mode',label:'检索模式',width:100},{prop:'topK',label:'TopK',width:80},{prop:'scoreThreshold',label:'阈值',width:80},{prop:'rerankEnabled',label:'重排序',width:80,switch:true},{prop:'isDefault',label:'默认',width:80,switch:true}], [{field:'name',label:'策略名称',type:'input',required:true},{field:'mode',label:'检索模式',type:'select',options:"[{label:'向量',value:'vector'},{label:'全文',value:'fulltext'},{label:'混合',value:'hybrid'}]",required:true},{field:'topK',label:'TopK',type:'number'},{field:'scoreThreshold',label:'阈值',type:'number'}]],
  ['reference', 'bindings', '引用关系管理', '@/mock/knowledge-reference', 'getReferenceList', [{prop:'sourceKnowledgeTitle',label:'源知识',minWidth:180},{prop:'targetKnowledgeTitle',label:'目标知识',minWidth:180},{prop:'refType',label:'引用类型',width:100},{prop:'status',label:'状态',width:80,tag:true},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'sourceKnowledgeTitle',label:'源知识',type:'input',required:true},{field:'targetKnowledgeTitle',label:'目标知识',type:'input',required:true},{field:'refType',label:'引用类型',type:'select',options:'REF_TYPE_OPTIONS',required:true}]],
  ['reference', 'tracing', '引用追溯', '@/mock/knowledge-reference', 'getTraceList', [{prop:'sourceTitle',label:'源知识',minWidth:180},{prop:'targetTitle',label:'目标知识',minWidth:180},{prop:'depth',label:'深度',width:80},{prop:'createdAt',label:'创建时间',width:160}], []],
  ['reference', 'permissions', '引用权限', '@/mock/knowledge-reference', 'getPermissionList', [{prop:'roleName',label:'角色',minWidth:150},{prop:'canCreate',label:'创建',width:80,switch:true},{prop:'canEdit',label:'编辑',width:80,switch:true},{prop:'canDelete',label:'删除',width:80,switch:true},{prop:'canView',label:'查看',width:80,switch:true},{prop:'canExport',label:'导出',width:80,switch:true}], []],
  ['reference', 'templates', '引用模板', '@/mock/knowledge-reference', 'getTemplateList', [{prop:'name',label:'模板名称',minWidth:200},{prop:'format',label:'格式',width:100},{prop:'usageCount',label:'使用次数',width:100},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'模板名称',type:'input',required:true},{field:'content',label:'模板内容',type:'textarea',required:true},{field:'format',label:'格式',type:'select',options:"[{label:'Markdown',value:'markdown'},{label:'HTML',value:'html'},{label:'纯文本',value:'text'}]"}]],
  ['reference', 'review', '引用审核', '@/mock/knowledge-reference', 'getReviewList', [{prop:'sourceTitle',label:'源知识',minWidth:150},{prop:'targetTitle',label:'目标知识',minWidth:150},{prop:'action',label:'操作',width:80},{prop:'status',label:'状态',width:80,tag:true},{prop:'reviewer',label:'审核人',width:100},{prop:'createdAt',label:'创建时间',width:160}], []],
  ['reference', 'reports', '引用报表', '@/mock/knowledge-reference', 'getReportList', [{prop:'name',label:'报表名称',minWidth:200},{prop:'type',label:'类型',width:120},{prop:'period',label:'周期',width:100},{prop:'generatedAt',label:'生成时间',width:160}], []],
  ['reference', 'feedback', '引用反馈', '@/mock/knowledge-reference', 'getFeedbackList', [{prop:'username',label:'用户',width:120},{prop:'rating',label:'评分',width:100},{prop:'comment',label:'评价',minWidth:250},{prop:'createdAt',label:'时间',width:160}], [{field:'rating',label:'评分(1-5)',type:'number',required:true},{field:'comment',label:'评价内容',type:'textarea'}]],
  ['reference', 'validity', '知识有效性评估', '@/mock/knowledge-reference', 'getValidityList', [{prop:'knowledgeTitle',label:'知识标题',minWidth:200},{prop:'referenceCount',label:'引用数',width:80},{prop:'validCount',label:'有效数',width:80},{prop:'expiredCount',label:'过期数',width:80},{prop:'conflictCount',label:'冲突数',width:80},{prop:'status',label:'状态',width:80,tag:true}], []],
  ['reference', 'recommendations', '引用推荐', '@/mock/knowledge-reference', 'getRecommendationList', [{prop:'sourceTitle',label:'源知识',minWidth:180},{prop:'recommendedTitle',label:'推荐知识',minWidth:180},{prop:'score',label:'推荐分',width:80},{prop:'reason',label:'推荐原因',minWidth:150},{prop:'status',label:'状态',width:80,tag:true}], []],
  ['reference', 'conflicts', '引用冲突检测', '@/mock/knowledge-reference', 'getConflictList', [{prop:'sourceTitle',label:'源知识',minWidth:180},{prop:'targetTitle',label:'目标知识',minWidth:180},{prop:'conflictType',label:'冲突类型',width:120},{prop:'description',label:'描述',minWidth:200},{prop:'status',label:'状态',width:100,tag:true},{prop:'detectedAt',label:'检测时间',width:160}], []],
  ['robot-config', 'basic', '基础信息', '@/mock/robot-config', 'getRobotBasicList', [{prop:'name',label:'机器人名称',minWidth:150},{prop:'description',label:'描述',minWidth:200},{prop:'modelName',label:'模型',width:120},{prop:'modelMode',label:'模式',width:100},{prop:'memoryTurns',label:'记忆轮数',width:100},{prop:'updatedAt',label:'更新时间',width:160}], [{field:'name',label:'名称',type:'input',required:true},{field:'description',label:'描述',type:'textarea'},{field:'persona',label:'人设',type:'textarea'},{field:'modelName',label:'模型',type:'input'},{field:'memoryTurns',label:'记忆轮数',type:'number'}]],
  ['robot-config', 'dialogue', '对话配置', '@/mock/robot-config', 'getDialogueConfig', [{prop:'openingMessage',label:'开场白',minWidth:300},{prop:'fallbackMessage',label:'兜底话术',minWidth:250},{prop:'afterReplySuggestions',label:'答复建议',width:100,switch:true}], [{field:'openingMessage',label:'开场白',type:'textarea',required:true},{field:'fallbackMessage',label:'兜底话术',type:'textarea',required:true},{field:'afterReplySuggestions',label:'答复后建议',type:'switch'}]],
  ['robot-config', 'strategy', '全局策略', '@/mock/robot-config', 'getGlobalStrategy', [{prop:'fallbackMessage',label:'兜底话术',minWidth:250},{prop:'unmatchedEnabled',label:'未匹配策略',width:100,switch:true},{prop:'unmatchedStrategy',label:'策略类型',width:120},{prop:'securityEnabled',label:'安全策略',width:100,switch:true}], [{field:'fallbackMessage',label:'兜底话术',type:'textarea'},{field:'unmatchedStrategy',label:'未匹配策略',type:'select',options:"[{label:'兜底话术',value:'fallback'},{label:'转人工',value:'transfer'},{label:'忽略',value:'ignore'}]"},{field:'securityEnabled',label:'启用安全策略',type:'switch'}]],
  ['robot-config', 'knowledge', '知识库配置', '@/mock/robot-config', 'getRobotKnowledgeList', [{prop:'knowledgeBaseName',label:'知识库',minWidth:200},{prop:'enabled',label:'启用',width:80,switch:true},{prop:'priority',label:'优先级',width:80},{prop:'maxResults',label:'最大结果',width:100},{prop:'scoreThreshold',label:'阈值',width:80}], [{field:'maxResults',label:'最大结果数',type:'number'},{field:'scoreThreshold',label:'阈值',type:'number'},{field:'priority',label:'优先级',type:'number'}]],
  ['robot-config', 'plugins', '插件配置', '@/mock/robot-config', 'getRobotPluginList', [{prop:'pluginName',label:'插件名称',minWidth:200},{prop:'enabled',label:'启用',width:80,switch:true}], [{field:'enabled',label:'启用',type:'switch'}]],
  ['robot-config', 'workflows', '工作流配置', '@/mock/robot-config', 'getRobotWorkflowList', [{prop:'workflowName',label:'工作流名称',minWidth:200},{prop:'triggerCondition',label:'触发条件',minWidth:200},{prop:'enabled',label:'启用',width:80,switch:true}], [{field:'triggerCondition',label:'触发条件',type:'input'},{field:'enabled',label:'启用',type:'switch'}]],
  ['robot-config', 'databases', '数据库配置', '@/mock/robot-config', 'getRobotDatabaseList', [{prop:'name',label:'数据库名称',minWidth:150},{prop:'type',label:'类型',width:100},{prop:'host',label:'主机',width:150},{prop:'port',label:'端口',width:80},{prop:'database',label:'数据库',width:120},{prop:'status',label:'状态',width:100,tag:true}], [{field:'name',label:'名称',type:'input',required:true},{field:'type',label:'类型',type:'select',options:"[{label:'MySQL',value:'mysql'},{label:'PostgreSQL',value:'postgresql'},{label:'MongoDB',value:'mongodb'}]",required:true},{field:'host',label:'主机',type:'input',required:true},{field:'port',label:'端口',type:'number',required:true},{field:'database',label:'数据库名',type:'input',required:true}]],
  ['robot-config', 'publish', '发布管理', '@/mock/robot-config', 'getPublishList', [{prop:'version',label:'版本',width:120},{prop:'status',label:'状态',width:100,tag:true},{prop:'scope',label:'发布范围',minWidth:150},{prop:'publishTime',label:'发布时间',width:160}], []],
  ['robot-config', 'monitor', '监控管理', '@/mock/robot-config', 'getMonitorData', [{prop:'date',label:'日期',width:120},{prop:'totalConversations',label:'会话数',width:100},{prop:'avgResponseTime',label:'平均响应(ms)',width:120},{prop:'satisfactionRate',label:'满意度',width:100},{prop:'hitRate',label:'命中率',width:100},{prop:'transferRate',label:'转人工率',width:100}], []],
  ['robot-config', 'test', '对话测试', '@/mock/robot-config', 'getTestCaseList', [{prop:'name',label:'用例名称',minWidth:150},{prop:'input',label:'输入',minWidth:200},{prop:'expectedOutput',label:'期望输出',minWidth:200},{prop:'passed',label:'通过',width:80}], [{field:'name',label:'用例名称',type:'input',required:true},{field:'input',label:'输入',type:'textarea',required:true},{field:'expectedOutput',label:'期望输出',type:'textarea',required:true}]],
  ['robot-config', 'debug', '对话调试', '@/mock/robot-config', 'getDebugLogs', [{prop:'level',label:'级别',width:80},{prop:'message',label:'消息',minWidth:300},{prop:'timestamp',label:'时间',width:160}], []],
  ['robot-config', 'optimize', '对话优化', '@/mock/robot-config', 'getOptimizeList', [{prop:'title',label:'建议标题',minWidth:200},{prop:'type',label:'类型',width:100},{prop:'impact',label:'影响',width:80},{prop:'status',label:'状态',width:80,tag:true},{prop:'createdAt',label:'创建时间',width:160}], []],
  ['dialog-knowledge', 'manage', '知识管理', '@/mock/dialog-knowledge', 'getDialogKnowledgeList', [{prop:'name',label:'名称',minWidth:200},{prop:'type',label:'类型',width:100},{prop:'status',label:'状态',width:80,tag:true},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'名称',type:'input',required:true},{field:'content',label:'内容',type:'textarea',required:true},{field:'type',label:'类型',type:'select',options:"[{label:'FAQ',value:'faq'},{label:'意图',value:'intent'},{label:'实体',value:'entity'}]"}]],
  ['dialog-knowledge', 'recommendation', '智能推荐', '@/mock/dialog-knowledge', 'getDialogKnowledgeList', [{prop:'name',label:'名称',minWidth:200},{prop:'type',label:'类型',width:100},{prop:'status',label:'状态',width:80,tag:true}], []],
  ['dialog-knowledge', 'flow', '对话流配置', '@/mock/dialog-knowledge', 'getDialogFlowList', [{prop:'name',label:'对话流名称',minWidth:200},{prop:'description',label:'描述',minWidth:250},{prop:'status',label:'状态',width:80,tag:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'名称',type:'input',required:true},{field:'description',label:'描述',type:'textarea'}]],
  ['dialog-knowledge', 'semantic', '语义理解', '@/mock/dialog-knowledge', 'getSemanticRuleList', [{prop:'name',label:'规则名称',minWidth:150},{prop:'intent',label:'意图',width:120},{prop:'enabled',label:'启用',width:80,switch:true},{prop:'hitCount',label:'命中次数',width:100}], [{field:'name',label:'规则名称',type:'input',required:true},{field:'intent',label:'意图',type:'input',required:true},{field:'response',label:'回复',type:'textarea',required:true}]],
  ['dialog-knowledge', 'variables', '全局变量', '@/mock/dialog-knowledge', 'getGlobalVariableList', [{prop:'name',label:'变量名',minWidth:150},{prop:'key',label:'键',width:150},{prop:'type',label:'类型',width:100},{prop:'defaultValue',label:'默认值',width:120},{prop:'scope',label:'作用域',width:100},{prop:'description',label:'描述',minWidth:200}], [{field:'name',label:'变量名',type:'input',required:true},{field:'key',label:'键名',type:'input',required:true},{field:'type',label:'类型',type:'select',options:'VARIABLE_TYPE_OPTIONS',required:true},{field:'defaultValue',label:'默认值',type:'input'},{field:'description',label:'描述',type:'input'},{field:'scope',label:'作用域',type:'select',options:"[{label:'全局',value:'global'},{label:'会话',value:'session'}]"}]],
  ['dialog-knowledge', 'entities', '实体管理', '@/mock/dialog-knowledge', 'getDialogEntityList', [{prop:'name',label:'实体名称',minWidth:150},{prop:'type',label:'类型',width:100},{prop:'description',label:'描述',minWidth:200},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'实体名称',type:'input',required:true},{field:'type',label:'类型',type:'select',options:'ENTITY_TYPE_OPTIONS',required:true},{field:'description',label:'描述',type:'input'}]],
  ['dialog-knowledge', 'api-plugins', 'API插件', '@/mock/dialog-knowledge', 'getApiPluginList', [{prop:'name',label:'插件名称',minWidth:150},{prop:'endpoint',label:'接口地址',minWidth:250},{prop:'method',label:'方法',width:80},{prop:'enabled',label:'启用',width:80,switch:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'插件名称',type:'input',required:true},{field:'endpoint',label:'接口地址',type:'input',required:true},{field:'method',label:'方法',type:'select',options:"[{label:'GET',value:'GET'},{label:'POST',value:'POST'},{label:'PUT',value:'PUT'},{label:'DELETE',value:'DELETE'}]",required:true}]],
  ['dialog-knowledge', 'model-settings', '模型设置', '@/mock/dialog-knowledge', 'getModelPresetList', [{prop:'name',label:'预设名称',minWidth:150},{prop:'modelName',label:'模型',width:120},{prop:'temperature',label:'温度',width:80},{prop:'maxTokens',label:'最大Token',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'预设名称',type:'input',required:true},{field:'modelName',label:'模型',type:'input',required:true},{field:'systemPrompt',label:'系统提示词',type:'textarea'},{field:'temperature',label:'温度',type:'number'},{field:'maxTokens',label:'最大Token',type:'number'}]],
  ['publish-eval', 'data', '评估数据', '@/mock/publish-eval', 'getEvalDatasetList', [{prop:'name',label:'数据集名称',minWidth:200},{prop:'category',label:'分类',width:120},{prop:'questionCount',label:'题目数',width:100},{prop:'status',label:'状态',width:80,tag:true},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'数据集名称',type:'input',required:true},{field:'description',label:'描述',type:'textarea'},{field:'category',label:'分类',type:'input'}]],
  ['publish-eval', 'tasks', '评估任务', '@/mock/publish-eval', 'getEvalTaskList', [{prop:'name',label:'任务名称',minWidth:150},{prop:'datasetName',label:'数据集',width:150},{prop:'modelName',label:'模型',width:120},{prop:'status',label:'状态',width:100,tag:true},{prop:'progress',label:'进度',width:120,progress:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'任务名称',type:'input',required:true},{field:'datasetId',label:'数据集ID',type:'input',required:true},{field:'modelId',label:'模型ID',type:'input',required:true}]],
  ['publish-eval', 'release', '机器人发布', '@/mock/publish-eval', 'getRobotReleaseList', [{prop:'robotName',label:'机器人',minWidth:150},{prop:'version',label:'版本',width:120},{prop:'environment',label:'环境',width:100},{prop:'status',label:'状态',width:100,tag:true},{prop:'releaseNotes',label:'发布说明',minWidth:200},{prop:'createdAt',label:'创建时间',width:160}], [{field:'robotName',label:'机器人名称',type:'input',required:true},{field:'version',label:'版本',type:'input',required:true},{field:'environment',label:'环境',type:'select',options:"[{label:'测试',value:'staging'},{label:'生产',value:'production'}]",required:true},{field:'releaseNotes',label:'发布说明',type:'textarea'}]],
  ['robot-operation', 'mining', '数据挖掘', '@/mock/robot-operation', 'getMiningList', [{prop:'name',label:'任务名称',minWidth:200},{prop:'type',label:'类型',width:100},{prop:'status',label:'状态',width:100,tag:true},{prop:'progress',label:'进度',width:120,progress:true},{prop:'resultCount',label:'结果数',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'任务名称',type:'input',required:true},{field:'type',label:'类型',type:'select',options:"[{label:'自动',value:'auto'},{label:'手动',value:'manual'}]"},{field:'description',label:'描述',type:'textarea'}]],
  ['robot-operation', 'optimize', '知识优化', '@/mock/robot-operation', 'getOptimizeList', [{prop:'knowledgeTitle',label:'知识标题',minWidth:200},{prop:'optimizeType',label:'优化类型',width:100},{prop:'suggestion',label:'建议',minWidth:250},{prop:'impact',label:'影响',width:80},{prop:'status',label:'状态',width:80,tag:true}], []],
  ['robot-operation', 'faq-analysis', 'FAQ知识分析', '@/mock/robot-operation', 'getFaqAnalysisList', [{prop:'question',label:'问题',minWidth:250},{prop:'hitCount',label:'命中数',width:80},{prop:'missCount',label:'未命中',width:80},{prop:'hitRate',label:'命中率',width:100},{prop:'avgSatisfaction',label:'满意度',width:100},{prop:'period',label:'周期',width:100}], []],
  ['robot-operation', 'multi-turn', '多轮对话分析', '@/mock/robot-operation', 'getMultiTurnList', [{prop:'sessionId',label:'会话ID',width:120},{prop:'topic',label:'主题',width:120},{prop:'turnCount',label:'轮次',width:80},{prop:'resolution',label:'解决状态',width:100,tag:true},{prop:'satisfaction',label:'满意度',width:100},{prop:'createdAt',label:'时间',width:160}], []],
  ['robot-operation', 'intent', '意图知识分析', '@/mock/robot-operation', 'getIntentList', [{prop:'intent',label:'意图',minWidth:150},{prop:'utteranceCount',label:'语句数',width:100},{prop:'accuracy',label:'准确率',width:100},{prop:'coverage',label:'覆盖率',width:100},{prop:'suggestion',label:'建议',minWidth:200}], []],
  ['knowledge-produce', 'images', '图片导入及生成', '@/mock/knowledge-produce', 'getMediaList', [{prop:'name',label:'文件名',minWidth:200},{prop:'size',label:'大小',width:100},{prop:'status',label:'状态',width:100,tag:true},{prop:'description',label:'描述',minWidth:200},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'文件名',type:'input',required:true},{field:'description',label:'描述',type:'textarea'}]],
  ['knowledge-produce', 'audio', '音频导入及生成', '@/mock/knowledge-produce', 'getMediaList', [{prop:'name',label:'文件名',minWidth:200},{prop:'size',label:'大小',width:100},{prop:'status',label:'状态',width:100,tag:true},{prop:'description',label:'描述',minWidth:200},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'文件名',type:'input',required:true},{field:'description',label:'描述',type:'textarea'}]],
  ['knowledge-produce', 'video', '视频导入', '@/mock/knowledge-produce', 'getMediaList', [{prop:'name',label:'文件名',minWidth:200},{prop:'size',label:'大小',width:100},{prop:'status',label:'状态',width:100,tag:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'文件名',type:'input',required:true},{field:'description',label:'描述',type:'textarea'}]],
  ['knowledge-produce', 'documents', '文档管理', '@/mock/knowledge-produce', 'getMediaList', [{prop:'name',label:'文件名',minWidth:200},{prop:'size',label:'大小',width:100},{prop:'status',label:'状态',width:100,tag:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'文件名',type:'input',required:true},{field:'description',label:'描述',type:'textarea'}]],
  ['knowledge-produce', 'channels', '多渠道知识获取', '@/mock/knowledge-produce', 'getChannelList', [{prop:'name',label:'渠道名称',minWidth:150},{prop:'type',label:'类型',width:100},{prop:'endpoint',label:'地址',minWidth:200},{prop:'enabled',label:'启用',width:80,switch:true},{prop:'itemCount',label:'条目数',width:100},{prop:'status',label:'状态',width:100,tag:true}], [{field:'name',label:'渠道名称',type:'input',required:true},{field:'type',label:'类型',type:'select',options:"[{label:'API接口',value:'api'},{label:'爬虫',value:'crawler'},{label:'文件导入',value:'import'},{label:'手动录入',value:'manual'}]",required:true},{field:'endpoint',label:'地址',type:'input'}]],
  ['knowledge-produce', 'sharing', '多部门知识共享', '@/mock/knowledge-produce', 'getSharingList', [{prop:'sourceDept',label:'源部门',width:120},{prop:'targetDept',label:'目标部门',width:120},{prop:'knowledgeBaseName',label:'知识库',minWidth:150},{prop:'permission',label:'权限',width:80},{prop:'status',label:'状态',width:80,tag:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'sourceDept',label:'源部门',type:'input',required:true},{field:'targetDept',label:'目标部门',type:'input',required:true},{field:'knowledgeBaseName',label:'知识库',type:'input',required:true},{field:'permission',label:'权限',type:'select',options:"[{label:'只读',value:'read'},{label:'读写',value:'write'}]"}]],
  ['knowledge-produce', 'qa-extract', '问答抽取', '@/mock/knowledge-produce', 'getExtractionList', [{prop:'sourceFile',label:'来源文件',minWidth:150},{prop:'question',label:'问题',minWidth:250},{prop:'answer',label:'答案',minWidth:250},{prop:'confidence',label:'置信度',width:100},{prop:'status',label:'状态',width:80,tag:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'question',label:'问题',type:'input',required:true},{field:'answer',label:'答案',type:'textarea',required:true}]],
  ['knowledge-storage', 'documents', '文档存储', '@/mock/knowledge-storage', 'getStorageList', [{prop:'name',label:'文件名',minWidth:200},{prop:'type',label:'类型',width:80},{prop:'size',label:'大小',width:100},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'文件名',type:'input',required:true}]],
  ['knowledge-storage', 'images', '图片存储', '@/mock/knowledge-storage', 'getStorageList', [{prop:'name',label:'文件名',minWidth:200},{prop:'size',label:'大小',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'文件名',type:'input',required:true}]],
  ['knowledge-storage', 'video', '视频存储', '@/mock/knowledge-storage', 'getStorageList', [{prop:'name',label:'文件名',minWidth:200},{prop:'size',label:'大小',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'文件名',type:'input',required:true}]],
  ['knowledge-storage', 'audio', '语音存储', '@/mock/knowledge-storage', 'getStorageList', [{prop:'name',label:'文件名',minWidth:200},{prop:'size',label:'大小',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'文件名',type:'input',required:true}]],
  ['knowledge-storage', 'maintenance', '知识库维护', '@/mock/knowledge-storage', 'getMaintenanceList', [{prop:'knowledgeBaseName',label:'知识库',minWidth:200},{prop:'action',label:'操作',width:100},{prop:'status',label:'状态',width:100,tag:true},{prop:'progress',label:'进度',width:120,progress:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'knowledgeBaseName',label:'知识库',type:'input',required:true},{field:'action',label:'操作',type:'select',options:"[{label:'重建索引',value:'reindex'},{label:'清理垃圾',value:'cleanup'},{label:'优化存储',value:'optimize'},{label:'数据备份',value:'backup'}]",required:true}]],
  ['knowledge-storage', 'groups', '知识库分组', '@/mock/knowledge-storage', 'getGroupList', [{prop:'name',label:'分组名称',minWidth:200},{prop:'description',label:'描述',minWidth:250},{prop:'memberCount',label:'成员数',width:100},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'分组名称',type:'input',required:true},{field:'description',label:'描述',type:'textarea'}]],
  ['knowledge-storage', 'tag-types', '标签类型管理', '@/mock/knowledge-storage', 'getTagTypeList', [{prop:'name',label:'类型名称',minWidth:200},{prop:'description',label:'描述',minWidth:250},{prop:'color',label:'颜色',width:80},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'类型名称',type:'input',required:true},{field:'description',label:'描述',type:'input'},{field:'color',label:'颜色',type:'input'}]],
  ['knowledge-storage', 'notes', '笔记管理', '@/mock/knowledge-storage', 'getNoteList', [{prop:'title',label:'标题',minWidth:200},{prop:'content',label:'内容',minWidth:300},{prop:'creator',label:'创建人',width:100},{prop:'updatedAt',label:'更新时间',width:160}], [{field:'title',label:'标题',type:'input',required:true},{field:'content',label:'内容',type:'textarea',required:true}]],
  ['knowledge-review', 'flows', '审核流程管理', '@/mock/knowledge-review', 'getReviewTaskList', [{prop:'knowledgeTitle',label:'知识标题',minWidth:200},{prop:'flowName',label:'审核流程',width:150},{prop:'currentStep',label:'当前步骤',width:100},{prop:'status',label:'状态',width:80,tag:true},{prop:'submitter',label:'提交人',width:100},{prop:'submitTime',label:'提交时间',width:160}], []],
  ['knowledge-review', 'flow-design', '审核流程设计', '@/mock/knowledge-review', 'getFlowList', [{prop:'name',label:'流程名称',minWidth:200},{prop:'description',label:'描述',minWidth:250},{prop:'status',label:'状态',width:80,tag:true},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'流程名称',type:'input',required:true},{field:'description',label:'描述',type:'textarea'}]],
  ['knowledge-review', 'publish', '知识发布管理', '@/mock/knowledge-review', 'getPublishList', [{prop:'knowledgeTitle',label:'知识标题',minWidth:200},{prop:'version',label:'版本',width:100},{prop:'status',label:'状态',width:100,tag:true},{prop:'operator',label:'操作人',width:100},{prop:'publishTime',label:'发布时间',width:160}], []],
  ['knowledge-review', 'reset', '知识重置管理', '@/mock/knowledge-review', 'getResetList', [{prop:'knowledgeTitle',label:'知识标题',minWidth:200},{prop:'fromVersion',label:'从版本',width:100},{prop:'toVersion',label:'到版本',width:100},{prop:'reason',label:'原因',minWidth:200},{prop:'operator',label:'操作人',width:100},{prop:'createdAt',label:'时间',width:160}], []],
  ['knowledge-review', 'listeners', '监听管理', '@/mock/knowledge-review', 'getListenerList', [{prop:'name',label:'监听器名称',minWidth:150},{prop:'url',label:'URL',minWidth:250},{prop:'events',label:'事件',minWidth:200},{prop:'enabled',label:'启用',width:80,switch:true},{prop:'status',label:'状态',width:80,tag:true},{prop:'triggerCount',label:'触发次数',width:100}], [{field:'name',label:'名称',type:'input',required:true},{field:'url',label:'URL',type:'input',required:true},{field:'events',label:'事件(逗号分隔)',type:'input',required:true}]],
  ['knowledge-review', 'compliance', '合规性检查', '@/mock/knowledge-review', 'getComplianceList', [{prop:'name',label:'规则名称',minWidth:150},{prop:'ruleType',label:'规则类型',width:100},{prop:'rule',label:'规则',minWidth:250},{prop:'enabled',label:'启用',width:80,switch:true},{prop:'hitCount',label:'命中次数',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'规则名称',type:'input',required:true},{field:'ruleType',label:'规则类型',type:'select',options:"[{label:'内容',value:'content'},{label:'格式',value:'format'},{label:'关键词',value:'keyword'},{label:'长度',value:'length'}]",required:true},{field:'rule',label:'规则描述',type:'textarea',required:true}]],
  ['knowledge-review', 'reports', '审核报告', '@/mock/knowledge-review', 'getReportList', [{prop:'name',label:'报告名称',minWidth:200},{prop:'period',label:'周期',width:100},{prop:'totalReviews',label:'审核总数',width:100},{prop:'approved',label:'通过',width:80},{prop:'rejected',label:'驳回',width:80},{prop:'avgReviewTime',label:'平均时长(h)',width:120},{prop:'generatedAt',label:'生成时间',width:160}], []],
  ['knowledge-review', 'quality', '质量评估', '@/mock/knowledge-review', 'getQualityScoreList', [{prop:'knowledgeTitle',label:'知识标题',minWidth:200},{prop:'totalScore',label:'总分',width:80},{prop:'level',label:'等级',width:80,tag:true},{prop:'evaluatedAt',label:'评估时间',width:160}], []],
  ['workflow', 'list', '业务流列表', '@/mock/workflow-mgmt', 'getWorkflowList', [{prop:'name',label:'业务流名称',minWidth:200},{prop:'description',label:'描述',minWidth:250},{prop:'nodeCount',label:'节点数',width:80},{prop:'status',label:'状态',width:80,tag:true},{prop:'version',label:'版本',width:80},{prop:'runCount',label:'运行次数',width:100},{prop:'updatedAt',label:'更新时间',width:160}], [{field:'name',label:'名称',type:'input',required:true},{field:'description',label:'描述',type:'textarea'}]],
  ['workflow', 'nodes', '节点管理', '@/mock/workflow-mgmt', 'getNodeDefList', [{prop:'name',label:'节点名称',minWidth:150},{prop:'type',label:'类型',width:120},{prop:'category',label:'分类',width:100},{prop:'description',label:'描述',minWidth:250}], []],
  ['workflow', 'templates', '配置模板', '@/mock/workflow-mgmt', 'getWorkflowTemplateList', [{prop:'name',label:'模板名称',minWidth:200},{prop:'description',label:'描述',minWidth:250},{prop:'category',label:'分类',width:100},{prop:'usageCount',label:'使用次数',width:100},{prop:'creator',label:'创建人',width:100},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'模板名称',type:'input',required:true},{field:'description',label:'描述',type:'textarea'},{field:'category',label:'分类',type:'input'}]],
  ['workflow', 'test', '对话测试', '@/mock/workflow-mgmt', 'getWorkflowList', [{prop:'name',label:'业务流',minWidth:200},{prop:'status',label:'状态',width:80,tag:true},{prop:'runCount',label:'测试次数',width:100}], []],
  ['workflow', 'debug', '对话调试', '@/mock/workflow-mgmt', 'getWorkflowList', [{prop:'name',label:'业务流',minWidth:200},{prop:'status',label:'状态',width:80,tag:true},{prop:'version',label:'版本',width:80}], []],
  ['workflow', 'monitor', '监控管理', '@/mock/workflow-mgmt', 'getWorkflowList', [{prop:'name',label:'业务流',minWidth:200},{prop:'status',label:'状态',width:80,tag:true},{prop:'runCount',label:'运行次数',width:100},{prop:'lastRunAt',label:'最后运行',width:160}], []],
  ['workflow', 'permissions', '权限管理', '@/mock/workflow-mgmt', 'getWorkflowList', [{prop:'name',label:'业务流',minWidth:200},{prop:'creator',label:'创建人',width:120},{prop:'status',label:'状态',width:80,tag:true}], []],
  ['workflow', 'migration', '配置迁移', '@/mock/workflow-mgmt', 'getMigrationList', [{prop:'workflowName',label:'业务流',minWidth:200},{prop:'fromEnv',label:'源环境',width:120},{prop:'toEnv',label:'目标环境',width:120},{prop:'status',label:'状态',width:100,tag:true},{prop:'progress',label:'进度',width:120,progress:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'workflowName',label:'业务流名称',type:'input',required:true},{field:'fromEnv',label:'源环境',type:'input',required:true},{field:'toEnv',label:'目标环境',type:'input',required:true}]],
  ['workflow', 'knowledge-update', '知识更新', '@/mock/workflow-mgmt', 'getWorkflowList', [{prop:'name',label:'业务流',minWidth:200},{prop:'status',label:'状态',width:80,tag:true},{prop:'updatedAt',label:'更新时间',width:160}], []],
  ['plugin-db', 'plugins', '插件管理', '@/mock/plugin-db', 'getPluginList', [{prop:'name',label:'插件名称',minWidth:200},{prop:'description',label:'描述',minWidth:250},{prop:'type',label:'类型',width:100},{prop:'version',label:'版本',width:100},{prop:'status',label:'状态',width:80,tag:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'插件名称',type:'input',required:true},{field:'description',label:'描述',type:'textarea'},{field:'type',label:'类型',type:'select',options:"[{label:'自定义',value:'custom'},{label:'上传',value:'uploaded'},{label:'JSON导入',value:'json_import'}]"}]],
  ['plugin-db', 'databases', '数据库管理', '@/mock/plugin-db', 'getDatabaseList', [{prop:'name',label:'数据库名称',minWidth:200},{prop:'description',label:'描述',minWidth:200},{prop:'type',label:'类型',width:120},{prop:'host',label:'主机',width:150},{prop:'port',label:'端口',width:80},{prop:'status',label:'状态',width:100,tag:true},{prop:'createdAt',label:'创建时间',width:160}], [{field:'name',label:'名称',type:'input',required:true},{field:'description',label:'描述',type:'textarea'},{field:'type',label:'类型',type:'select',options:"[{label:'MySQL',value:'mysql'},{label:'PostgreSQL',value:'postgresql'},{label:'MongoDB',value:'mongodb'},{label:'Redis',value:'redis'}]",required:true},{field:'host',label:'主机',type:'input',required:true},{field:'port',label:'端口',type:'number',required:true},{field:'database',label:'数据库名',type:'input',required:true}]],
];

pages.forEach(p => genPage(...p));
console.log(`Generated ${pages.length} view files`);
