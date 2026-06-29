<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import BasicConfig from './components/BasicConfig.vue'
import KnowledgeConfig from './components/KnowledgeConfig.vue'
import DialogConfig from './components/DialogConfig.vue'
import GlobalConfig from './components/GlobalConfig.vue'
import DatabaseConfig from './components/DatabaseConfig.vue'
import KnowledgeUpdate from './components/KnowledgeUpdate.vue'
import DialogTest from './components/DialogTest.vue'
import DialogOptimize from './components/DialogOptimize.vue'
import MonitorPublishConfig from './components/MonitorPublishConfig.vue'
import * as api from '@/api'

const route = useRoute()
const router = useRouter()
const appId = route.params.id as string
const activeMenu = ref('basic')

// 模型列表（从 API 加载）
const llmModels = ref<any[]>([])
const embeddingModels = ref<any[]>([])
const rerankModels = ref<any[]>([])

// 选中的模型
const selectedLlmModel = ref('')
const selectedEmbeddingModel = ref('')
const selectedRerankModel = ref('')

onMounted(async () => {
  try {
    const [llmRes, embRes, rerankRes] = await Promise.all([
      api.getModels({ purpose: 'LLM' }).catch(() => []),
      api.getModels({ purpose: 'Embedding' }).catch(() => []),
      api.getModels({ purpose: 'Rerank' }).catch(() => []),
    ])
    llmModels.value = (llmRes as any)?.list || llmRes || []
    embeddingModels.value = (embRes as any)?.list || embRes || []
    rerankModels.value = (rerankRes as any)?.list || rerankRes || []

    // 默认选中第一个
    if (llmModels.value.length) selectedLlmModel.value = llmModels.value[0].code || llmModels.value[0].name
    if (embeddingModels.value.length) selectedEmbeddingModel.value = embeddingModels.value[0].code || embeddingModels.value[0].name
    if (rerankModels.value.length) {
      selectedRerankModel.value = rerankModels.value[0].code || rerankModels.value[0].name
      retrievalParams.value.rerankModelName = selectedRerankModel.value
    }
  } catch {
    // 加载失败
  }
})

const appInfo = ref({
  id: appId,
  name: '智能问答助手',
  type: 'ChatBot',
  description: '基于知识库的智能问答应用',
  accessToken: 'sk-ais-xxxxxxxxxxxx',
})

const menuGroups = reactive([
  {
    name: '配置',
    expanded: true,
    items: [
      { key: 'basic', label: '基础配置', icon: 'Setting' },
      { key: 'kb', label: '知识库配置', icon: 'Collection' },
      { key: 'skill', label: '技能配置', icon: 'MagicStick' },
      { key: 'tool', label: '工具配置', icon: 'SetUp' },
      { key: 'mcp', label: 'MCP配置', icon: 'Connection' },
      { key: 'vm', label: '虚拟机配置', icon: 'Monitor' },
	      { key: 'ui', label: '界面配置', icon: 'Monitor' },
	      { key: 'dialog', label: '对话配置', icon: 'ChatDotRound' },
      { key: 'nav', label: '导航配置', icon: 'Menu' },
      { key: 'param', label: '参数配置', icon: 'Tools' },
      { key: 'prompt', label: 'Prompt编写', icon: 'EditPen' },
	      { key: 'debug', label: '对话调试', icon: 'ChatDotRound' },
	      { key: 'member', label: '成员管理', icon: 'User' },
	      { key: 'global', label: '全局策略', icon: 'Lock' },
	      { key: 'db', label: '数据库配置', icon: 'Coin' },
	      { key: 'kb-update', label: '知识更新', icon: 'Refresh' },
	      { key: 'dialog-test', label: '对话测试', icon: 'CircleCheck' },
	      { key: 'dialog-optimize', label: '对话优化', icon: 'TrendCharts' },
	      { key: 'monitor-publish', label: '发布监控', icon: 'DataBoard' },
    ],
  },
  {
    name: '发布',
    expanded: true,
    items: [
      { key: 'integration', label: '企业集成', icon: 'Connection' },
      { key: 'publish', label: '分享&发布', icon: 'Share' },
    ],
  },
  {
    name: '运营',
    expanded: true,
    items: [
      { key: 'chat-log', label: '对话记录', icon: 'ChatLineRound' },
      { key: 'feedback', label: '反馈记录', icon: 'ChatDotSquare' },
    ],
  },
  {
    name: '其它',
    expanded: true,
    items: [
      { key: 'home', label: '应用首页', icon: 'HomeFilled' },
    ],
  },
])

// 扁平化菜单项（用于查找）
const menuItems = menuGroups.flatMap(group => group.items)

// 展开/折叠分组
function toggleMenuGroup(groupName: string) {
  const group = menuGroups.find(g => g.name === groupName)
  if (group) {
    group.expanded = !group.expanded
  }
}

// 基础配置
const basicForm = ref({
  name: appInfo.value.name,
  type: appInfo.value.type,
  description: appInfo.value.description,
  icon: '',
  tags: [] as string[],
})

const availableTags = ['测试', '应用中心', '写作', '创作', '客服', '机器人', '问答', '文档']
const iconDialogVisible = ref(false)

function handleSaveBasic() {
  ElMessage.success('基本信息保存成功')
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('复制成功')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

// 知识库配置
const kbSearchKeyword = ref('')
const categorySearchKeyword = ref('')
const selectedCategory = ref('all')
const bindPersonalKB = ref('no')
const bindTeamKB = ref(true)

const categories = ref([
  { id: 'all', name: '全部', children: [] },
  { id: 'market', name: '市场运营', children: [] },
  { id: 'project', name: '项目管理', children: [] },
  { id: 'product', name: '产品研发', children: [] },
  { id: 'research', name: '市场研究', children: [] },
  { id: 'rag', name: 'RAG测试集', children: [] },
  { id: 'learning', name: '学习资源', children: [] },
  { id: 'enterprise', name: '企业管理', children: [] },
])

const availableKBs = ref([
  { id: '1', name: '物产定制化功能操作手册', embeddingModel: 'text-embedding-v4', dimension: 1024, category: 'product', selected: false },
  { id: '2', name: '企业资质管理', embeddingModel: 'bge-m3', dimension: 1024, category: 'enterprise', selected: true },
  { id: '3', name: '心愿汇', embeddingModel: 'bge-m3', dimension: 1024, category: 'project', selected: false },
  { id: '4', name: 'SaaP攻略2026', embeddingModel: 'bge-m3', dimension: 1024, category: 'market', selected: false },
  { id: '5', name: '数字员工宣发', embeddingModel: 'bge-m3', dimension: 1024, category: 'market', selected: false },
  { id: '6', name: '2025年会', embeddingModel: 'bge-m3', dimension: 1024, category: 'enterprise', selected: false },
  { id: '7', name: '2024年度年会珍贵记录', embeddingModel: 'bge-m3', dimension: 1024, category: 'enterprise', selected: true },
  { id: '8', name: '深港科创项目知识库', embeddingModel: 'bge-m3', dimension: 1024, category: 'project', selected: false },
  { id: '9', name: '市场营销和商机知识库', embeddingModel: 'bge-m3', dimension: 1024, category: 'market', selected: false },
  { id: '10', name: '数字员工开发库', embeddingModel: 'bge-m3', dimension: 1024, category: 'product', selected: false },
  { id: '11', name: '中汇项目管理知识库', embeddingModel: 'bge-m3', dimension: 1024, category: 'project', selected: false },
])

const filteredKBs = computed(() => {
  let list = availableKBs.value
  if (selectedCategory.value !== 'all') {
    list = list.filter(kb => kb.category === selectedCategory.value)
  }
  if (kbSearchKeyword.value) {
    list = list.filter(kb => kb.name.includes(kbSearchKeyword.value))
  }
  return list
})

const selectedKBCount = computed(() => availableKBs.value.filter(kb => kb.selected).length)
const allSelected = computed(() => filteredKBs.value.length > 0 && filteredKBs.value.every(kb => kb.selected))

function handleSelectAll() {
  const newVal = !allSelected.value
  filteredKBs.value.forEach(kb => {
    kb.selected = newVal
  })
}

function handleSaveKB() {
  ElMessage.success('知识库配置保存成功')
}

// 技能配置
const availableSkills = ref([
  { id: '1', name: '联网搜索', description: '当用户询问实时信息时，自动搜索互联网获取最新内容', enabled: true },
  { id: '2', name: '代码生成', description: '根据用户需求生成代码片段', enabled: true },
  { id: '3', name: '数据分析', description: '对用户提供的数据进行统计分析', enabled: false },
  { id: '4', name: '文档翻译', description: '将文档翻译为目标语言', enabled: false },
  { id: '5', name: '会议纪要', description: '自动生成会议纪要摘要', enabled: true },
])
const selectedSkills = ref(['1', '2', '5'])

// 工具配置
const availableTools = ref([
  { id: '1', name: '安装技能', identifier: 'install_skill', description: '安装新的 Skill 到当前用户的私有空间', enabled: true },
  { id: '2', name: 'Tavily 网页搜索', identifier: 'tavily_search', description: 'A search engine optimized for comprehensive results', enabled: true },
  { id: '3', name: '展示交付物', identifier: 'present_artifacts', description: '将已经生成好的结果文件展示给用户', enabled: true },
  { id: '4', name: '向用户提问', identifier: 'ask_user_question', description: '在执行过程中向用户提问', enabled: false },
  { id: '5', name: 'Query Kb', identifier: 'query_kb', description: '在指定知识库中检索内容', enabled: true },
])
const selectedTools = ref(['1', '2', '3', '5'])

// MCP配置
const availableMcp = ref([
  { id: '1', name: 'leowang', mcpUrl: 'https://mcp.api-inference.modelscope.net/75cb7553dc8c4f/mcp', tools: ['bing_search', 'crawl_webpage'], enabled: true },
  { id: '2', name: 'code_executor', mcpUrl: 'https://mcp.example.com/code-exec', tools: ['execute_python', 'execute_javascript'], enabled: true },
  { id: '3', name: 'database_query', mcpUrl: 'https://mcp.example.com/db-query', tools: ['query_sql', 'query_nosql'], enabled: false },
])
const selectedMcp = ref(['1', '2'])

// 虚拟机配置
const vmConfig = ref({
  enabled: false,
  image: 'ubuntu-22.04',
  cpu: 2,
  memory: 4,
  disk: 20,
  networkEnabled: true,
  autoShutdown: true,
  shutdownTimeout: 30,
  preInstalledPackages: ['python3', 'nodejs', 'git'],
  environmentVars: [
    { key: 'ENV', value: 'production' },
    { key: 'LOG_LEVEL', value: 'info' },
  ],
})

const vmImages = [
  { label: 'Ubuntu 22.04 LTS', value: 'ubuntu-22.04' },
  { label: 'Ubuntu 20.04 LTS', value: 'ubuntu-20.04' },
  { label: 'CentOS 7', value: 'centos-7' },
  { label: 'Debian 11', value: 'debian-11' },
  { label: 'Windows Server 2022', value: 'windows-2022' },
]

function addEnvVar() {
  vmConfig.value.environmentVars.push({ key: '', value: '' })
}

function removeEnvVar(index: number) {
  vmConfig.value.environmentVars.splice(index, 1)
}

const newPackage = ref('')

// 界面配置
const uiConfig = ref({
  // 文本配置
  welcome: '您好,有什么我可以帮助您',
  signature: '',
  title: '标题',
  placeholder: '请输入您的问题',
  textInfo: '帮助中心',
  brandText: 'Powered by AIS',
  // 颜色配置
  colorTemplate: 'default',
  chatWindowColor: 'rgb(1, 103, 229)',
  userMessageColor: 'rgba(1, 103, 229, 0.12)',
  questionFontColor: 'rgb(51, 51, 51)',
  replyMessageColor: 'rgb(238, 238, 238)',
  replyFontColor: 'rgb(51, 51, 51)',
})

const colorTemplates = [
  { label: '默认', value: 'default', color: 'rgb(1, 103, 229)' },
  { label: '生态绿', value: 'green', color: 'rgb(34, 139, 34)' },
  { label: '蜜蜡黄', value: 'yellow', color: 'rgb(218, 165, 32)' },
  { label: '琥珀橙', value: 'orange', color: 'rgb(255, 140, 0)' },
  { label: '中国红', value: 'red', color: 'rgb(220, 20, 60)' },
  { label: '炫酷黑', value: 'black', color: 'rgb(30, 30, 30)' },
  { label: '自定义', value: 'custom', color: '' },
]

// 导航配置
const navSearchKeyword = ref('')
const navSearchQA = ref('')
const navDateRange = ref<[Date, Date] | null>(null)
const showAddNavDialog = ref(false)
const navForm = ref({
  title: '',
  isQA: 'yes',
  sort: 1,
})

const navList = ref([
  { id: '1', title: '请假审批流程', isQA: true, sort: 1 },
  { id: '2', title: '报销制度说明', isQA: true, sort: 2 },
  { id: '3', title: '员工手册', isQA: false, sort: 3 },
  { id: '4', title: '入职流程', isQA: true, sort: 4 },
])

const filteredNavList = computed(() => {
  let list = navList.value
  if (navSearchKeyword.value) {
    list = list.filter(item => item.title.includes(navSearchKeyword.value))
  }
  if (navSearchQA.value) {
    list = list.filter(item => navSearchQA.value === 'yes' ? item.isQA : !item.isQA)
  }
  return list
})

function handleAddNav() {
  if (!navForm.value.title) {
    ElMessage.warning('请输入问题标题')
    return
  }
  navList.value.push({
    id: String(Date.now()),
    title: navForm.value.title,
    isQA: navForm.value.isQA === 'yes',
    sort: navForm.value.sort,
  })
  showAddNavDialog.value = false
  navForm.value = { title: '', isQA: 'yes', sort: 1 }
  ElMessage.success('添加成功')
}

function handleDeleteNav(id: string) {
  navList.value = navList.value.filter(item => item.id !== id)
  ElMessage.success('删除成功')
}

function handleNavQuery() {
  ElMessage.success('查询成功')
}

function handleNavReset() {
  navSearchKeyword.value = ''
  navSearchQA.value = ''
  navDateRange.value = null
}

// 参数配置
const activeParamTab = ref('retrieval')

const retrievalParams = ref({
  contextCount: 20,
  recallCount: 20,
  knowledgeAlpha: 0.50,
  documentAggregation: true,
  rerankModel: true,
  rerankModelName: '',
  adjacentTextCount: 0,
  qaQms: 0.91,
  knowledgeKms: 0.51,
  replyMode: 'llm',
  conversationMode: 'multi',
  conversationRounds: 5,
})

// Prompt配置
const activePromptStep = ref('system')
const promptSteps = ref([
  { id: 'system', title: '系统指令（System）', description: '定义AI助手的基本角色和行为准则', expanded: true },
  { id: 'rewrite', title: '查询重写', description: '将用户查询转换为更适合检索的格式', expanded: false },
  { id: 'intent', title: '意图拆解', description: '充分理解用户意图，以便更精准的查询知识', expanded: false },
  { id: 'generate', title: '用户生成', description: '基于检索的知识，对用户的问题进行生成', expanded: false },
])

const promptContent = ref({
  system: '# 角色与目标 (ROLE AND GOAL)\n\n你是一个专业的RAG（检索增强生成）系统回答引擎。你的核心职能是基于提供的检索上下文，为用户提供准确、有据可查的回答。你必须严格依据上下文内容，并规范地标注所有信息来源。\n\n# 核心指令 (CORE DIRECTIVE)\n\n分析用户问题和提供的上下文信息，基于可用知识提供准确回答。所有事实陈述都',
  rewrite: '',
  intent: '',
  generate: '',
})

const promptTemplates = ref([
  { id: '1', name: '通用问答模板', content: '' },
  { id: '2', name: '客服助手模板', content: '' },
  { id: '3', name: '知识库问答模板', content: '' },
])

const timeVars = [
  { label: '${time}', desc: '当前时间' },
  { label: '${yesterday}', desc: '昨天' },
  { label: '${tomorrow}', desc: '明天' },
]

function insertVariable(varStr: string) {
  // 在光标位置插入变量
  promptContent.value.system += varStr
}

function handleSavePrompt() {
  ElMessage.success('Prompt配置保存成功')
}

function handleResetPrompt() {
  promptContent.value.system = ''
  ElMessage.success('已重置')
}

// 对话调试
const debugMessages = ref<any[]>([
  { role: 'assistant', content: '您好,有什么我可以帮助您' },
])
const debugInput = ref('')
const debugLoading = ref(false)

async function handleDebugSend() {
  if (!debugInput.value) return
  debugMessages.value.push({ role: 'user', content: debugInput.value })
  const q = debugInput.value
  debugInput.value = ''
  debugLoading.value = true
  await new Promise(r => setTimeout(r, 1500))
  debugMessages.value.push({
    role: 'assistant',
    content: `抱歉，AI服务暂时不可用，请稍后再试`,
    time: '1.5s',
  })
  debugLoading.value = false
}

function handleDebugClear() {
  debugMessages.value = [{ role: 'assistant', content: '您好,有什么我可以帮助您' }]
}

// ===== 成员管理 =====
const memberSearchKeyword = ref('')
const showMemberDialog = ref(false)
const showRoleDialog = ref(false)
const editingMember = ref<any>(null)
const memberForm = ref({ name: '', account: '', role: '查看者' })
const memberRoleForm = ref({ role: '查看者' })

const memberList = ref([
  { id: '1', name: '张三', account: 'zhangsan@company.com', role: '管理员', status: '启用' },
  { id: '2', name: '李四', account: 'lisi@company.com', role: '编辑', status: '启用' },
  { id: '3', name: '王五', account: 'wangwu@company.com', role: '查看者', status: '启用' },
  { id: '4', name: '赵六', account: 'zhaoliu@company.com', role: '查看者', status: '禁用' },
])

const filteredMemberList = computed(() => {
  if (!memberSearchKeyword.value) return memberList.value
  return memberList.value.filter(m => m.name.includes(memberSearchKeyword.value) || m.account.includes(memberSearchKeyword.value))
})

function handleEditMember(row: any) {
  editingMember.value = row
  memberForm.value = { name: row.name, account: row.account, role: row.role }
  showMemberDialog.value = true
}

function handleAssignRole(row: any) {
  editingMember.value = row
  memberRoleForm.value = { role: row.role }
  showRoleDialog.value = true
}

function handleSaveMember() {
  if (!memberForm.value.name || !memberForm.value.account) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (editingMember.value) {
    Object.assign(editingMember.value, memberForm.value)
    ElMessage.success('成员信息已更新')
  } else {
    memberList.value.push({
      id: String(Date.now()),
      name: memberForm.value.name,
      account: memberForm.value.account,
      role: memberForm.value.role,
      status: '启用',
    })
    ElMessage.success('成员添加成功')
  }
  showMemberDialog.value = false
  editingMember.value = null
  memberForm.value = { name: '', account: '', role: '查看者' }
}

function handleDeleteMember(id: string) {
  memberList.value = memberList.value.filter(m => m.id !== id)
  ElMessage.success('成员已移除')
}

function handleSaveRole() {
  if (editingMember.value) {
    editingMember.value.role = memberRoleForm.value.role
    ElMessage.success('角色分配成功')
  }
  showRoleDialog.value = false
  editingMember.value = null
}

// ===== 企业集成 =====
const showApiKeyDialog = ref(false)
const apiKeyForm = ref({ name: '', expiresIn: 'never' })

const apiKeys = ref<any[]>([
  { id: '1', name: '生产环境密钥', key: 'sk-prod-a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5', showKey: false, createdAt: '2026-01-15 10:30:00' },
  { id: '2', name: '测试环境密钥', key: 'sk-test-p9o8i7u6y5t4r3e2w1q0', showKey: false, createdAt: '2026-03-20 14:20:00' },
])

const webhookConfig = ref({
  url: '',
  events: [] as string[],
  enabled: false,
})

function handleCreateApiKey() {
  if (!apiKeyForm.value.name) {
    ElMessage.warning('请输入密钥名称')
    return
  }
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
  const key = 'sk-' + Array.from({ length: 32 }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
  apiKeys.value.push({
    id: String(Date.now()),
    name: apiKeyForm.value.name,
    key,
    showKey: false,
    createdAt: new Date().toLocaleString(),
  })
  showApiKeyDialog.value = false
  apiKeyForm.value = { name: '', expiresIn: 'never' }
  ElMessage.success('密钥创建成功')
}

function deleteApiKey(id: string) {
  apiKeys.value = apiKeys.value.filter(k => k.id !== id)
  ElMessage.success('密钥已删除')
}

function handleSaveWebhook() {
  ElMessage.success('Webhook 配置已保存')
}

// ===== 分享&发布 =====
const activePublishTab = ref('share')
const publishConfig = ref({
  shareEnabled: false,
  accessPassword: '',
  expiresIn: 'never',
  selectedChannels: [] as string[],
})

const embedOptions = ref({ width: 380, height: 560, themeColor: '#0167e5' })

const publishChannels = [
  { key: 'wechat', label: '微信', icon: 'ChatDotRound', desc: '集成到微信公众号' },
  { key: 'web', label: '网站', icon: 'Monitor', desc: '嵌入到网页' },
  { key: 'app', label: '移动App', icon: 'Iphone', desc: '集成到iOS/Android' },
  { key: 'dingtalk', label: '钉钉', icon: 'ChatLineRound', desc: '集成到钉钉' },
  { key: 'feishu', label: '飞书', icon: 'ChatSquare', desc: '集成到飞书' },
  { key: 'api', label: 'API接口', icon: 'Connection', desc: '通过API调用' },
]

const shareUrl = computed(() => `https://app.ais.com/share/${appInfo.value.id || appId}${publishConfig.value.accessPassword ? '?pwd=' + publishConfig.value.accessPassword : ''}`)
const embedCode = computed(() => `<iframe src="${shareUrl.value}" width="${embedOptions.value.width}" height="${embedOptions.value.height}" style="border:none;border-radius:8px" allow="clipboard-write" />`)

function togglePublishChannel(key: string) {
  const idx = publishConfig.value.selectedChannels.indexOf(key)
  if (idx >= 0) publishConfig.value.selectedChannels.splice(idx, 1)
  else publishConfig.value.selectedChannels.push(key)
}

function handleSavePublish() {
  ElMessage.success('发布配置已保存')
}

// ===== 对话记录 =====
const chatLogKeyword = ref('')
const chatLogDateRange = ref<[Date, Date] | null>(null)
const chatLogRating = ref('')
const showChatDetail = ref(false)
const chatDetail = ref<any>(null)

const chatLogs = ref([
  { id: '1', user: '匿名用户A', sessionId: 'sess_abc123', question: '你们的服务有哪些功能？', answer: '我们提供AI知识库问答...', rating: 5, tokens: 256, time: '2026-06-27 14:32:10', messages: [
    { role: 'user', content: '你们的服务有哪些功能？', tokens: 12, latency: '0.1s' },
    { role: 'assistant', content: '我们提供AI知识库问答、智能客服、文档助手等功能，支持多种模型和知识库配置。', tokens: 244, latency: '1.2s' },
  ]},
  { id: '2', user: '匿名用户B', sessionId: 'sess_def456', question: '如何配置知识库？', answer: '在知识库配置页面...', rating: 4, tokens: 189, time: '2026-06-27 15:10:45', messages: [
    { role: 'user', content: '如何配置知识库？', tokens: 10, latency: '0.1s' },
    { role: 'assistant', content: '在知识库配置页面，您可以绑定已有知识库或创建新的知识库。', tokens: 179, latency: '0.9s' },
  ]},
  { id: '3', user: '匿名用户C', sessionId: 'sess_ghi789', question: '支持哪些模型？', answer: '我们支持多种大语言模型...', rating: 3, tokens: 312, time: '2026-06-27 16:20:30', messages: [
    { role: 'user', content: '支持哪些模型？', tokens: 8, latency: '0.1s' },
    { role: 'assistant', content: '我们支持GPT-4、通义千问、文心一言等多种大语言模型。', tokens: 304, latency: '1.5s' },
  ]},
  { id: '4', user: '匿名用户D', sessionId: 'sess_jkl012', question: '应用发布后如何更新？', answer: '在编辑器中修改配置后...', rating: 5, tokens: 178, time: '2026-06-28 09:05:12', messages: [
    { role: 'user', content: '应用发布后如何更新？', tokens: 11, latency: '0.1s' },
    { role: 'assistant', content: '在编辑器中修改配置后，点击发布按钮即可更新已发布的应用。', tokens: 167, latency: '0.8s' },
  ]},
])

const filteredChatLogs = computed(() => {
  let list = chatLogs.value
  if (chatLogKeyword.value) {
    list = list.filter(l => l.question.includes(chatLogKeyword.value) || l.answer.includes(chatLogKeyword.value))
  }
  if (chatLogRating.value) {
    list = list.filter(l => l.rating === Number(chatLogRating.value))
  }
  return list
})

function handleChatLogQuery() {
  ElMessage.success('查询完成')
}

function handleChatLogReset() {
  chatLogKeyword.value = ''
  chatLogDateRange.value = null
  chatLogRating.value = ''
}

function handleViewChatDetail(row: any) {
  chatDetail.value = row
  showChatDetail.value = true
}

function handleDeleteChatLog(id: string) {
  chatLogs.value = chatLogs.value.filter(l => l.id !== id)
  ElMessage.success('对话记录已删除')
}

function handleExportChatLog() {
  ElMessage.success('对话记录导出中，请稍候...')
}

// ===== 反馈记录 =====
const feedbackKeyword = ref('')
const feedbackTypeFilter = ref('')
const feedbackStatusFilter = ref('')
const showFeedbackDetail = ref(false)
const selectedFeedback = ref<any>(null)
const feedbackProcessing = ref(false)
const feedbackReply = ref('')

const feedbacks = ref([
  { id: '1', user: '匿名用户A', type: 'good', typeLabel: '好评', content: '回答非常准确，帮助很大！', rating: 5, status: 'resolved', statusLabel: '已处理', time: '2026-06-27 14:32:10', reply: '感谢您的反馈，我们会继续努力！' },
  { id: '2', user: '匿名用户B', type: 'bad', typeLabel: '差评', content: '回答与问题不符，完全没有解决我的问题。', rating: 1, status: 'pending', statusLabel: '待处理', time: '2026-06-27 15:10:45', reply: '' },
  { id: '3', user: '匿名用户C', type: 'suggestion', typeLabel: '建议', content: '希望能增加更多领域的知识库模板，比如医疗和法律方面的。', rating: 4, status: 'processing', statusLabel: '处理中', time: '2026-06-27 16:20:30', reply: '' },
  { id: '4', user: '匿名用户D', type: 'good', typeLabel: '好评', content: '界面很友好，使用起来很流畅。', rating: 5, status: 'resolved', statusLabel: '已处理', time: '2026-06-28 09:05:12', reply: '感谢您的支持！' },
  { id: '5', user: '匿名用户E', type: 'complaint', typeLabel: '投诉', content: '回复速度太慢了，等了很久。', rating: 2, status: 'pending', statusLabel: '待处理', time: '2026-06-28 10:30:00', reply: '' },
])

const feedbackStats = computed(() => {
  const total = feedbacks.value.length
  const good = feedbacks.value.filter(f => f.type === 'good').length
  const bad = feedbacks.value.filter(f => f.type === 'bad').length
  const pending = feedbacks.value.filter(f => f.status === 'pending').length
  return { total, good, bad, pending }
})

const filteredFeedbacks = computed(() => {
  let list = feedbacks.value
  if (feedbackKeyword.value) {
    list = list.filter(f => f.content.includes(feedbackKeyword.value))
  }
  if (feedbackTypeFilter.value) {
    list = list.filter(f => f.type === feedbackTypeFilter.value)
  }
  if (feedbackStatusFilter.value) {
    list = list.filter(f => f.status === feedbackStatusFilter.value)
  }
  return list
})

function handleFeedbackQuery() {
  ElMessage.success('查询完成')
}

function handleFeedbackReset() {
  feedbackKeyword.value = ''
  feedbackTypeFilter.value = ''
  feedbackStatusFilter.value = ''
}

function handleViewFeedback(row: any) {
  selectedFeedback.value = row
  feedbackProcessing.value = false
  feedbackReply.value = ''
  showFeedbackDetail.value = true
}

function handleProcessFeedback(row: any) {
  selectedFeedback.value = row
  feedbackProcessing.value = true
  feedbackReply.value = row.reply || ''
  showFeedbackDetail.value = true
}

function handleSubmitFeedbackReply() {
  if (selectedFeedback.value) {
    selectedFeedback.value.reply = feedbackReply.value
    selectedFeedback.value.status = 'resolved'
    selectedFeedback.value.statusLabel = '已处理'
    ElMessage.success('回复已提交')
  }
  showFeedbackDetail.value = false
  feedbackProcessing.value = false
  feedbackReply.value = ''
}

function handleDeleteFeedback(id: string) {
  feedbacks.value = feedbacks.value.filter(f => f.id !== id)
  ElMessage.success('反馈已删除')
}

function handleExportFeedback() {
  ElMessage.success('反馈数据导出中，请稍候...')
}

// ===== 应用首页 =====
const previewMode = ref('chat')

function handleOpenApp() {
  window.open(`/app/${appInfo.value.id || appId}`, '_blank')
}

function handlePublishConfig() {
  ElMessage.success('配置已发布')
}

function handleSave() {
  ElMessage.success('保存成功')
}

function handlePublish() {
  ElMessage.success('发布成功')
}
</script>

<template>
  <div class="app-editor">
    <!-- 左侧菜单 -->
    <div class="editor-sidebar">
      <div class="sidebar-header">
        <el-button link @click="router.push('/application')">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <span>{{ appInfo.name }}</span>
      </div>
      <el-scrollbar class="menu-scrollbar">
        <div class="menu-groups">
          <div v-for="group in menuGroups" :key="group.name" class="menu-group">
            <div class="group-header" @click="toggleMenuGroup(group.name)">
              <span class="group-name">{{ group.name }}</span>
              <el-icon class="group-expand-icon">
                <ArrowDown v-if="group.expanded" />
                <ArrowRight v-else />
              </el-icon>
            </div>
            <div v-show="group.expanded" class="group-items">
              <div
                v-for="item in group.items"
                :key="item.key"
                class="menu-item"
                :class="{ active: activeMenu === item.key }"
                @click="activeMenu = item.key"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-scrollbar>
    </div>

    <!-- 右侧内容区 -->
    <div class="editor-content">
	      <!-- 基础配置 -->
	      <BasicConfig v-if="activeMenu === 'basic'" :app-info="appInfo" />

	      <!-- 对话配置 -->
	      <DialogConfig v-if="activeMenu === 'dialog'" :app-info="appInfo" />
	      <GlobalConfig v-if="activeMenu === 'global'" :app-info="appInfo" />
	      <DatabaseConfig v-if="activeMenu === 'db'" :app-info="appInfo" />
	      <KnowledgeUpdate v-if="activeMenu === 'kb-update'" :app-info="appInfo" />
	      <DialogTest v-if="activeMenu === 'dialog-test'" :app-info="appInfo" />
	      <DialogOptimize v-if="activeMenu === 'dialog-optimize'" :app-info="appInfo" />
	      <MonitorPublishConfig v-if="activeMenu === 'monitor-publish'" :app-info="appInfo" />

      <!-- 知识库配置 -->
      <KnowledgeConfig v-if="activeMenu === 'kb'" />

      <!-- 技能配置 -->
      <div v-if="activeMenu === 'skill'" class="config-section">
        <h3>技能配置</h3>
        <p class="desc">配置应用可用的技能，扩展智能体的能力。</p>
        <el-checkbox-group v-model="selectedSkills">
          <div v-for="skill in availableSkills" :key="skill.id" class="skill-option">
            <div class="skill-info">
              <el-checkbox :value="skill.id">
                <span class="skill-name">{{ skill.name }}</span>
              </el-checkbox>
              <span class="skill-desc">{{ skill.description }}</span>
            </div>
            <el-switch
              v-model="skill.enabled"
              size="small"
              @click.stop
            />
          </div>
        </el-checkbox-group>
        <div class="selected-count">已选 {{ selectedSkills.length }} 个技能</div>
      </div>

      <!-- 工具配置 -->
      <div v-if="activeMenu === 'tool'" class="config-section">
        <h3>工具配置</h3>
        <p class="desc">配置应用可用的HTTP工具，扩展智能体的执行能力。</p>
        <el-checkbox-group v-model="selectedTools">
          <div v-for="tool in availableTools" :key="tool.id" class="tool-option">
            <div class="tool-info">
              <el-checkbox :value="tool.id">
                <span class="tool-name">{{ tool.name }}</span>
                <span class="tool-identifier">{{ tool.identifier }}</span>
              </el-checkbox>
              <span class="tool-desc">{{ tool.description }}</span>
            </div>
            <el-switch
              v-model="tool.enabled"
              size="small"
              @click.stop
            />
          </div>
        </el-checkbox-group>
        <div class="selected-count">已选 {{ selectedTools.length }} 个工具</div>
      </div>

      <!-- MCP配置 -->
      <div v-if="activeMenu === 'mcp'" class="config-section">
        <h3>MCP配置</h3>
        <p class="desc">配置应用可用的MCP服务，扩展智能体的外部能力。</p>
        <el-checkbox-group v-model="selectedMcp">
          <div v-for="mcp in availableMcp" :key="mcp.id" class="mcp-option">
            <div class="mcp-info">
              <el-checkbox :value="mcp.id">
                <span class="mcp-name">{{ mcp.name }}</span>
              </el-checkbox>
              <span class="mcp-url">{{ mcp.mcpUrl }}</span>
              <div class="mcp-tools">
                <el-tag v-for="tool in mcp.tools" :key="tool" size="small" type="info">{{ tool }}</el-tag>
              </div>
            </div>
            <el-switch
              v-model="mcp.enabled"
              size="small"
              @click.stop
            />
          </div>
        </el-checkbox-group>
        <div class="selected-count">已选 {{ selectedMcp.length }} 个MCP服务</div>
      </div>

      <!-- 虚拟机配置 -->
      <div v-if="activeMenu === 'vm'" class="config-section">
        <h3>虚拟机配置</h3>
        <p class="desc">配置应用运行时的虚拟机环境，用于执行代码和运行服务。</p>

        <el-card class="vm-config-card">
          <div class="vm-header">
            <span>启用虚拟机</span>
            <el-switch v-model="vmConfig.enabled" />
          </div>

          <template v-if="vmConfig.enabled">
            <el-divider />

            <el-form label-width="120px" style="max-width: 600px">
              <el-form-item label="镜像系统">
                <el-select v-model="vmConfig.image" style="width: 100%">
                  <el-option v-for="img in vmImages" :key="img.value" :label="img.label" :value="img.value" />
                </el-select>
              </el-form-item>

              <el-form-item label="CPU (核)">
                <el-input-number v-model="vmConfig.cpu" :min="1" :max="16" :step="1" />
              </el-form-item>

              <el-form-item label="内存 (GB)">
                <el-input-number v-model="vmConfig.memory" :min="1" :max="64" :step="1" />
              </el-form-item>

              <el-form-item label="磁盘 (GB)">
                <el-input-number v-model="vmConfig.disk" :min="10" :max="500" :step="10" />
              </el-form-item>

              <el-form-item label="网络访问">
                <el-switch v-model="vmConfig.networkEnabled" />
                <span class="vm-tip">允许虚拟机访问外部网络</span>
              </el-form-item>

              <el-form-item label="自动关机">
                <el-switch v-model="vmConfig.autoShutdown" />
              </el-form-item>

              <el-form-item v-if="vmConfig.autoShutdown" label="超时时间">
                <el-input-number v-model="vmConfig.shutdownTimeout" :min="5" :max="120" :step="5" />
                <span class="vm-unit">分钟</span>
              </el-form-item>
            </el-form>

            <el-divider content-position="left">预装软件包</el-divider>
            <div class="vm-packages">
              <el-tag
                v-for="(pkg, index) in vmConfig.preInstalledPackages"
                :key="index"
                closable
                @close="vmConfig.preInstalledPackages.splice(index, 1)"
              >
                {{ pkg }}
              </el-tag>
              <el-input
                v-model="newPackage"
                size="small"
                style="width: 150px"
                placeholder="添加包名"
                @keyup.enter="() => { if (newPackage) { vmConfig.preInstalledPackages.push(newPackage); newPackage = '' } }"
              >
                <template #append>
                  <el-button @click="() => { if (newPackage) { vmConfig.preInstalledPackages.push(newPackage); newPackage = '' } }">
                    <el-icon><Plus /></el-icon>
                  </el-button>
                </template>
              </el-input>
            </div>

            <el-divider content-position="left">环境变量</el-divider>
            <el-table :data="vmConfig.environmentVars" size="small" border style="width: 500px">
              <el-table-column label="变量名" min-width="150">
                <template #default="{ row }">
                  <el-input v-model="row.key" size="small" placeholder="KEY" />
                </template>
              </el-table-column>
              <el-table-column label="变量值" min-width="150">
                <template #default="{ row }">
                  <el-input v-model="row.value" size="small" placeholder="VALUE" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" size="small" @click="removeEnvVar($index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" link @click="addEnvVar" style="margin-top: 8px">
              <el-icon><Plus /></el-icon>添加环境变量
            </el-button>
          </template>
        </el-card>
      </div>

      <!-- 界面配置 -->
      <div v-if="activeMenu === 'ui'" class="config-section">
        <h3>界面配置</h3>
        <div class="ui-layout">
          <div class="ui-form-area">
            <!-- 文本配置 -->
            <div class="config-group">
              <h4 class="group-title">文本配置</h4>
              <el-form label-width="100px" style="width: 100%">
                <el-form-item label="欢迎语：">
                  <el-input v-model="uiConfig.welcome" placeholder="请输入欢迎语" />
                </el-form-item>
                <el-form-item label="签名：">
                  <el-input v-model="uiConfig.signature" placeholder="请输入" />
                </el-form-item>
                <el-form-item label="标题：">
                  <el-input v-model="uiConfig.title" placeholder="标题" />
                </el-form-item>
                <el-form-item label="占位文本：">
                  <el-input v-model="uiConfig.placeholder" placeholder="请输入您的问题" />
                </el-form-item>
                <el-form-item label="文本信息：">
                  <el-input v-model="uiConfig.textInfo" placeholder="帮助中心" />
                </el-form-item>
                <el-form-item label="技术品牌：">
                  <el-input v-model="uiConfig.brandText" placeholder="Powered by AIS" />
                </el-form-item>
              </el-form>
            </div>

            <!-- 颜色配置 -->
            <div class="config-group">
              <h4 class="group-title">颜色配置</h4>
              <el-form label-width="100px" style="width: 100%">
                <el-form-item label="模板：">
                  <div class="color-templates">
                    <div
                      v-for="tpl in colorTemplates"
                      :key="tpl.value"
                      class="color-template-item"
                      :class="{ active: uiConfig.colorTemplate === tpl.value }"
                      @click="uiConfig.colorTemplate = tpl.value"
                    >
                      <div
                        class="template-color"
                        :style="{ background: tpl.color || '#ccc' }"
                      ></div>
                      <span>{{ tpl.label }}</span>
                    </div>
                  </div>
                </el-form-item>
                <el-form-item label="聊天窗口颜色：">
                  <el-input v-model="uiConfig.chatWindowColor" :disabled="uiConfig.colorTemplate !== 'custom'" />
                </el-form-item>
                <el-form-item label="用户消息颜色：">
                  <el-input v-model="uiConfig.userMessageColor" :disabled="uiConfig.colorTemplate !== 'custom'" />
                </el-form-item>
                <el-form-item label="提问字体颜色：">
                  <el-input v-model="uiConfig.questionFontColor" :disabled="uiConfig.colorTemplate !== 'custom'" />
                </el-form-item>
                <el-form-item label="回复消息颜色：">
                  <el-input v-model="uiConfig.replyMessageColor" :disabled="uiConfig.colorTemplate !== 'custom'" />
                </el-form-item>
                <el-form-item label="回复字体颜色：">
                  <el-input v-model="uiConfig.replyFontColor" :disabled="uiConfig.colorTemplate !== 'custom'" />
                </el-form-item>
              </el-form>
            </div>
          </div>

          <!-- 预览区域 -->
          <div class="ui-preview">
            <div class="preview-chat-widget">
              <div class="preview-widget-header" :style="{ background: uiConfig.chatWindowColor }">
                <span>{{ uiConfig.title || '标题' }}</span>
                <div class="header-actions">
                  <el-icon><RefreshRight /></el-icon>
                  <el-icon><Close /></el-icon>
                </div>
              </div>
              <div class="preview-widget-body">
                <div class="bot-message">
                  <div class="bot-avatar">
                    <el-icon :size="16" color="#fff"><ChatDotRound /></el-icon>
                  </div>
                  <div class="message-bubble bot">{{ uiConfig.welcome }}</div>
                </div>
                <div class="user-message">
                  <div class="message-bubble user">你好</div>
                </div>
              </div>
              <div class="preview-widget-footer" :style="{ background: uiConfig.chatWindowColor }">
                <div class="input-placeholder">{{ uiConfig.placeholder }}</div>
                <div class="send-icon">▶</div>
              </div>
              <div class="widget-brand">{{ uiConfig.brandText }}</div>
            </div>
            <div class="help-center-link">{{ uiConfig.textInfo }}</div>
          </div>
        </div>
      </div>

      <!-- Prompt编写 -->
      <div v-if="activeMenu === 'prompt'" class="config-section">
        <div class="prompt-config-layout">
          <!-- 左侧步骤导航 -->
          <div class="prompt-steps">
            <div
              v-for="(step, index) in promptSteps"
              :key="step.id"
              class="prompt-step-item"
              :class="{ active: activePromptStep === step.id }"
              @click="activePromptStep = step.id"
            >
              <div class="step-number">{{ index + 1 }}</div>
              <div class="step-info">
                <div class="step-title">{{ step.title }}</div>
                <div class="step-desc">{{ step.description }}</div>
              </div>
            </div>
          </div>

          <!-- 右侧配置区 -->
          <div class="prompt-config-content">
            <!-- 系统指令 -->
            <div v-if="activePromptStep === 'system'" class="prompt-config-card">
              <div class="config-card-header">
                <div class="header-info">
                  <h4>系统指令（System）</h4>
                  <p>定义AI助手的基本角色和行为准则</p>
                </div>
                <div class="header-actions">
                  <el-button size="small" @click="handleResetPrompt">重 置</el-button>
                  <el-button size="small" type="primary" @click="handleSavePrompt">保 存</el-button>
                  <el-icon class="expand-icon" @click="promptSteps[0].expanded = !promptSteps[0].expanded">
                    <ArrowUp v-if="promptSteps[0].expanded" />
                    <ArrowDown v-else />
                  </el-icon>
                </div>
              </div>

              <div v-show="promptSteps[0].expanded" class="config-card-body">
                <div class="prompt-toolbar">
                  <span class="toolbar-label">点击变量标签可插入到光标位置：</span>
                  <el-button link type="primary" size="small">
                    <el-icon><MagicStick /></el-icon>Prompt模版
                  </el-button>
                </div>

                <div class="variable-tags">
                  <el-tag
                    v-for="v in timeVars"
                    :key="v.label"
                    size="small"
                    class="var-tag"
                    @click="insertVariable(v.label)"
                  >
                    {{ v.label }}（{{ v.desc }}）
                  </el-tag>
                </div>

                <el-input
                  v-model="promptContent.system"
                  type="textarea"
                  :rows="12"
                  placeholder="请输入系统指令..."
                />
              </div>
            </div>

            <!-- 查询重写 -->
            <div v-if="activePromptStep === 'rewrite'" class="prompt-config-card collapsed">
              <div class="config-card-header" @click="promptSteps[1].expanded = !promptSteps[1].expanded">
                <div class="header-info">
                  <h4>查询重写</h4>
                  <p>将用户查询转换为更适合检索的格式</p>
                </div>
                <el-icon class="expand-icon">
                  <ArrowUp v-if="promptSteps[1].expanded" />
                  <ArrowDown v-else />
                </el-icon>
              </div>
              <div v-show="promptSteps[1].expanded" class="config-card-body">
                <el-input v-model="promptContent.rewrite" type="textarea" :rows="8" placeholder="请输入查询重写指令..." />
              </div>
            </div>

            <!-- 意图拆解 -->
            <div v-if="activePromptStep === 'intent'" class="prompt-config-card collapsed">
              <div class="config-card-header" @click="promptSteps[2].expanded = !promptSteps[2].expanded">
                <div class="header-info">
                  <h4>意图拆解</h4>
                  <p>充分理解用户意图，以便更精准的查询知识</p>
                </div>
                <el-icon class="expand-icon">
                  <ArrowUp v-if="promptSteps[2].expanded" />
                  <ArrowDown v-else />
                </el-icon>
              </div>
              <div v-show="promptSteps[2].expanded" class="config-card-body">
                <el-input v-model="promptContent.intent" type="textarea" :rows="8" placeholder="请输入意图拆解指令..." />
              </div>
            </div>

            <!-- 用户生成 -->
            <div v-if="activePromptStep === 'generate'" class="prompt-config-card collapsed">
              <div class="config-card-header" @click="promptSteps[3].expanded = !promptSteps[3].expanded">
                <div class="header-info">
                  <h4>用户生成</h4>
                  <p>基于检索的知识，对用户的问题进行生成</p>
                </div>
                <el-icon class="expand-icon">
                  <ArrowUp v-if="promptSteps[3].expanded" />
                  <ArrowDown v-else />
                </el-icon>
              </div>
              <div v-show="promptSteps[3].expanded" class="config-card-body">
                <el-input v-model="promptContent.generate" type="textarea" :rows="8" placeholder="请输入用户生成指令..." />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 对话调试 -->
      <div v-if="activeMenu === 'debug'" class="config-section chat-debug">
        <div class="debug-layout">
          <!-- 左侧提示 -->
          <div class="debug-sidebar">
            <div class="debug-tip">
              <p>当前参数调整后问答对话调试验证效果，满意后点击发布配置生效；若不发布不影响原有应用参数配置，离开当前页面后所做临时调整将丢失。</p>
              <el-button type="primary" @click="handlePublishConfig">发布配置</el-button>
            </div>
          </div>

          <!-- 右侧聊天窗口 -->
          <div class="debug-chat-widget">
            <div class="widget-header">
              <span>标题</span>
              <el-icon class="refresh-btn" @click="handleDebugClear"><RefreshRight /></el-icon>
            </div>

            <div class="widget-messages">
              <div v-for="(msg, idx) in debugMessages" :key="idx" :class="['message', msg.role]">
                <div v-if="msg.role === 'assistant'" class="bot-avatar">
                  <el-icon :size="16" color="#fff"><ChatDotRound /></el-icon>
                </div>
                <div class="message-content">{{ msg.content }}</div>
                <div v-if="msg.role === 'user'" class="message-actions">
                  <el-icon><CopyDocument /></el-icon>
                  <el-icon><RefreshRight /></el-icon>
                </div>
              </div>
            </div>

            <div class="widget-input-area">
              <el-input
                v-model="debugInput"
                placeholder="请输入您的问题"
                @keyup.enter="handleDebugSend"
              >
                <template #append>
                  <el-button class="send-btn" :loading="debugLoading" @click="handleDebugSend">
                    <el-icon v-if="!debugLoading"><Promotion /></el-icon>
                  </el-button>
                </template>
              </el-input>
              <div class="widget-brand">Powered by AIS</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 导航配置 -->
      <div v-if="activeMenu === 'nav'" class="config-section">
        <h3>导航配置</h3>
        <p class="desc">管理常见问题导航内容并保持最新排序</p>

        <div class="nav-toolbar">
          <div class="nav-filters">
            <el-input v-model="navSearchKeyword" placeholder="搜索问题标题" clearable style="width: 200px">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="navSearchQA" placeholder="是否QA" clearable style="width: 150px">
              <el-option label="是" value="yes" />
              <el-option label="否" value="no" />
            </el-select>
            <el-date-picker
              v-model="navDateRange"
              type="daterange"
              range-separator="→"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 300px"
            />
            <el-button type="primary" @click="handleNavQuery">查 询</el-button>
            <el-button @click="handleNavReset">重 置</el-button>
          </div>
          <el-button type="primary" @click="showAddNavDialog = true">新增问题</el-button>
        </div>

        <el-table :data="filteredNavList" style="width: 100%; margin-top: 16px">
          <el-table-column prop="title" label="问题标题" min-width="300" />
          <el-table-column prop="isQA" label="是否QA" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isQA ? 'success' : 'info'" size="small">{{ row.isQA ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="100" align="center" />
          <el-table-column label="操作" width="150" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDeleteNav(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 新增问题弹窗 -->
        <el-dialog v-model="showAddNavDialog" title="新增问题" width="500px">
          <el-form label-width="80px">
            <el-form-item label="问题标题" required>
              <el-input v-model="navForm.title" placeholder="请输入问题标题" />
            </el-form-item>
            <el-form-item label="是否QA">
              <el-radio-group v-model="navForm.isQA">
                <el-radio label="yes">是</el-radio>
                <el-radio label="no">否</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="navForm.sort" :min="1" :max="100" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showAddNavDialog = false">取消</el-button>
            <el-button type="primary" @click="handleAddNav">确定</el-button>
          </template>
        </el-dialog>
      </div>

      <!-- 参数配置 -->
      <div v-if="activeMenu === 'param'" class="config-section">
        <div class="param-tabs">
          <div
            v-for="tab in [{ key: 'model', label: '大模型' }, { key: 'retrieval', label: '检索配置' }, { key: 'prompt', label: 'Prompt编写' }]"
            :key="tab.key"
            class="param-tab-item"
            :class="{ active: activeParamTab === tab.key }"
            @click="activeParamTab = tab.key"
          >
            {{ tab.label }}
          </div>
        </div>

        <div v-if="activeParamTab === 'retrieval'" class="retrieval-config">
          <el-form label-width="140px" style="max-width: 700px">
            <el-form-item label="上下文数量：" required>
              <div class="slider-input">
                <el-slider v-model="retrievalParams.contextCount" :min="1" :max="50" style="flex: 1" />
                <el-input-number v-model="retrievalParams.contextCount" :min="1" :max="50" size="small" controls-position="right" style="width: 80px" />
              </div>
            </el-form-item>

            <el-form-item label="召回数量：" required>
              <div class="slider-input">
                <el-slider v-model="retrievalParams.recallCount" :min="1" :max="50" style="flex: 1" />
                <el-input-number v-model="retrievalParams.recallCount" :min="1" :max="50" size="small" controls-position="right" style="width: 80px" />
              </div>
            </el-form-item>

            <el-form-item label="知识库alpha：">
              <div class="slider-input">
                <el-slider v-model="retrievalParams.knowledgeAlpha" :min="0" :max="1" :step="0.01" style="flex: 1" />
                <el-input-number v-model="retrievalParams.knowledgeAlpha" :min="0" :max="1" :step="0.01" size="small" controls-position="right" style="width: 80px" />
              </div>
            </el-form-item>

            <el-form-item label="文档聚合排序：">
              <el-tooltip content="对检索结果进行文档级别的聚合排序" placement="top">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
              <el-switch v-model="retrievalParams.documentAggregation" />
            </el-form-item>

            <el-form-item label="重排模型：">
              <el-tooltip content="启用重排模型对检索结果重新排序" placement="top">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
              <el-switch v-model="retrievalParams.rerankModel" />
            </el-form-item>

            <el-form-item label="选择重排模型：" v-if="retrievalParams.rerankModel">
              <el-select v-model="retrievalParams.rerankModelName" style="width: 100%">
                <el-option
                  v-for="m in rerankModels"
                  :key="m.id"
                  :label="`${m.name} (${m.brand || m.code})`"
                  :value="m.code || m.name"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="拼接邻近文本片数量：">
              <el-tooltip content="在检索结果前后拼接的文本片数量" placement="top">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
              <div class="slider-input">
                <el-slider v-model="retrievalParams.adjacentTextCount" :min="0" :max="5" style="flex: 1" />
                <el-input-number v-model="retrievalParams.adjacentTextCount" :min="0" :max="5" size="small" controls-position="right" style="width: 80px" />
              </div>
            </el-form-item>

            <el-form-item label="QA对qms：" required>
              <el-tooltip content="QA对匹配阈值" placement="top">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
              <div class="slider-input">
                <el-slider v-model="retrievalParams.qaQms" :min="0" :max="1" :step="0.01" style="flex: 1" />
                <el-input-number v-model="retrievalParams.qaQms" :min="0" :max="1" :step="0.01" size="small" controls-position="right" style="width: 80px" />
              </div>
            </el-form-item>

            <el-form-item label="知识库kms：" required>
              <el-tooltip content="知识库匹配阈值" placement="top">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
              <div class="slider-input">
                <el-slider v-model="retrievalParams.knowledgeKms" :min="0" :max="1" :step="0.01" style="flex: 1" />
                <el-input-number v-model="retrievalParams.knowledgeKms" :min="0" :max="1" :step="0.01" size="small" controls-position="right" style="width: 80px" />
              </div>
            </el-form-item>

            <el-form-item label="回复方式：" required>
              <el-radio-group v-model="retrievalParams.replyMode">
                <el-radio label="custom">自定义回复</el-radio>
                <el-radio label="llm">LLM辅助回答</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="对话方式：">
              <el-select v-model="retrievalParams.conversationMode" style="width: 100%">
                <el-option label="单轮对话" value="single" />
                <el-option label="多轮对话" value="multi" />
              </el-select>
            </el-form-item>

            <el-form-item label="多轮对话轮次：">
              <div class="slider-input">
                <el-slider v-model="retrievalParams.conversationRounds" :min="1" :max="20" style="flex: 1" />
                <el-input-number v-model="retrievalParams.conversationRounds" :min="1" :max="20" size="small" controls-position="right" style="width: 80px" />
              </div>
            </el-form-item>
          </el-form>

          <div class="param-actions">
            <el-button @click="() => { ElMessage.success('已重置') }">重 置</el-button>
            <el-button type="primary" @click="handleSave">保 存</el-button>
          </div>
        </div>

        <div v-if="activeParamTab === 'model'" class="config-sub">
          <el-form label-width="120px">
            <el-form-item label="对话模型">
              <el-select v-model="selectedLlmModel" style="width: 100%">
                <el-option
                  v-for="m in llmModels"
                  :key="m.id"
                  :label="`${m.name} (${m.brand || m.code})`"
                  :value="m.code || m.name"
                />
              </el-select>
              <div class="form-tip">选择用于生成回答的大语言模型</div>
            </el-form-item>
            <el-form-item label="向量模型">
              <el-select v-model="selectedEmbeddingModel" style="width: 100%">
                <el-option
                  v-for="m in embeddingModels"
                  :key="m.id"
                  :label="`${m.name} (${m.brand || m.code})`"
                  :value="m.code || m.name"
                />
              </el-select>
              <div class="form-tip">选择用于知识库向量化的 Embedding 模型</div>
            </el-form-item>
            <el-form-item label="重排模型">
              <el-select v-model="selectedRerankModel" style="width: 100%" clearable>
                <el-option label="不使用重排" value="" />
                <el-option
                  v-for="m in rerankModels"
                  :key="m.id"
                  :label="`${m.name} (${m.brand || m.code})`"
                  :value="m.code || m.name"
                />
              </el-select>
              <div class="form-tip">选择用于检索结果重排序的 Rerank 模型（可选）</div>
            </el-form-item>
          </el-form>
          <div class="param-actions">
            <el-button type="primary" @click="handleSave">保 存</el-button>
          </div>
        </div>

        <div v-if="activeParamTab === 'prompt'" class="config-sub">
          <div class="prompt-vars">
            <span>可插入变量：</span>
            <el-tag size="small" @click="promptContent.system += '${context}'">${context}</el-tag>
            <el-tag size="small" @click="promptContent.system += '${question}'">${question}</el-tag>
          </div>
          <el-input v-model="promptContent.system" type="textarea" :rows="12" style="margin-top: 12px" />
          <div class="param-actions">
            <el-button type="primary" @click="handleSave">保 存</el-button>
          </div>
        </div>
      </div>

      <!-- ========== 成员管理 ========== -->
      <div v-if="activeMenu === 'member'" class="config-section">
        <div class="section-group">
          <div class="section-title">成员管理</div>
          <p class="desc">管理应用的成员及其角色权限</p>
          <div style="margin-bottom:16px;display:flex;gap:8px;justify-content:space-between;align-items:center">
            <el-button type="primary" size="small" @click="showMemberDialog = true">
              <el-icon><Plus /></el-icon>添加成员
            </el-button>
            <el-input v-model="memberSearchKeyword" placeholder="搜索成员..." prefix-icon="Search" style="width:260px" size="small" clearable />
          </div>
          <el-table :data="filteredMemberList" border stripe size="small" style="width:100%">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="name" label="成员名称" min-width="140" />
            <el-table-column prop="account" label="账号" min-width="160" />
            <el-table-column prop="role" label="角色" width="120">
              <template #default="{ row }">
                <el-tag :type="row.role === '管理员' ? 'danger' : row.role === '编辑' ? 'warning' : 'info'" size="small">{{ row.role }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEditMember(row)">编辑</el-button>
                <el-button type="warning" link size="small" @click="handleAssignRole(row)">角色</el-button>
                <el-popconfirm title="确定移除该成员?" @confirm="handleDeleteMember(row.id)">
                  <template #reference>
                    <el-button type="danger" link size="small">移除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <!-- 添加/编辑成员对话框 -->
        <el-dialog v-model="showMemberDialog" :title="editingMember ? '编辑成员' : '添加成员'" width="480px">
          <el-form :model="memberForm" label-width="100px">
            <el-form-item label="成员名称" required>
              <el-input v-model="memberForm.name" placeholder="请输入成员名称" />
            </el-form-item>
            <el-form-item label="账号" required>
              <el-input v-model="memberForm.account" placeholder="请输入账号" />
            </el-form-item>
            <el-form-item label="角色" required>
              <el-select v-model="memberForm.role" style="width:100%">
                <el-option label="管理员" value="管理员" />
                <el-option label="编辑" value="编辑" />
                <el-option label="查看者" value="查看者" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showMemberDialog = false">取消</el-button>
            <el-button type="primary" @click="handleSaveMember">保存</el-button>
          </template>
        </el-dialog>
        <!-- 角色分配对话框 -->
        <el-dialog v-model="showRoleDialog" title="分配角色" width="400px">
          <el-form label-width="100px">
            <el-form-item label="成员">
              <span>{{ editingMember?.name }}</span>
            </el-form-item>
            <el-form-item label="角色" required>
              <el-select v-model="memberRoleForm.role" style="width:100%">
                <el-option label="管理员" value="管理员" />
                <el-option label="编辑" value="编辑" />
                <el-option label="查看者" value="查看者" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showRoleDialog = false">取消</el-button>
            <el-button type="primary" @click="handleSaveRole">保存</el-button>
          </template>
        </el-dialog>
      </div>

      <!-- ========== 企业集成 ========== -->
      <div v-if="activeMenu === 'integration'" class="config-section">
        <div class="section-group">
          <div class="section-title">企业集成</div>
          <p class="desc">配置 API 密钥、Webhook 和与企业系统的集成端点</p>
          <!-- API密钥 -->
          <div style="margin-bottom:24px">
            <h4 style="margin:0 0 12px;font-size:14px;font-weight:600">API 密钥</h4>
            <el-table :data="apiKeys" border stripe size="small" style="width:100%">
              <el-table-column prop="name" label="名称" min-width="140" />
              <el-table-column prop="key" label="密钥" min-width="240">
                <template #default="{ row }">
                  <div style="display:flex;align-items:center;gap:8px">
                    <span v-if="!row.showKey">sk-****{{ row.key.slice(-8) }}</span>
                    <span v-else style="font-family:monospace;font-size:12px">{{ row.key }}</span>
                    <el-button link size="small" @click="row.showKey = !row.showKey">
                      <el-icon><View v-if="!row.showKey" /><Hide v-else /></el-icon>
                    </el-button>
                    <el-button link size="small" @click="copyToClipboard(row.key)">复制</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="160" />
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-popconfirm title="确定删除此密钥?" @confirm="deleteApiKey(row.id)">
                    <template #reference>
                      <el-button type="danger" link size="small">删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" style="margin-top:8px" @click="showApiKeyDialog = true">
              <el-icon><Plus /></el-icon>新建密钥
            </el-button>
          </div>
          <!-- Webhook 配置 -->
          <div style="margin-bottom:24px">
            <h4 style="margin:0 0 12px;font-size:14px;font-weight:600">Webhook 配置</h4>
            <el-form label-width="140px" style="max-width:600px">
              <el-form-item label="Webhook URL">
                <el-input v-model="webhookConfig.url" placeholder="https://hooks.example.com/..." />
              </el-form-item>
              <el-form-item label="触发事件">
                <el-checkbox-group v-model="webhookConfig.events">
                  <el-checkbox label="message_sent" value="message_sent">消息发送</el-checkbox>
                  <el-checkbox label="message_received" value="message_received">消息接收</el-checkbox>
                  <el-checkbox label="feedback_created" value="feedback_created">反馈提交</el-checkbox>
                  <el-checkbox label="app_published" value="app_published">应用发布</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-form-item label="启用">
                <el-switch v-model="webhookConfig.enabled" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="small" @click="handleSaveWebhook">保存配置</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
        <el-dialog v-model="showApiKeyDialog" title="新建 API 密钥" width="450px">
          <el-form label-width="100px">
            <el-form-item label="密钥名称" required>
              <el-input v-model="apiKeyForm.name" placeholder="输入密钥名称" />
            </el-form-item>
            <el-form-item label="过期时间">
              <el-select v-model="apiKeyForm.expiresIn" style="width:100%">
                <el-option label="永不过期" value="never" />
                <el-option label="30天" value="30d" />
                <el-option label="90天" value="90d" />
                <el-option label="1年" value="1y" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showApiKeyDialog = false">取消</el-button>
            <el-button type="primary" @click="handleCreateApiKey">创建</el-button>
          </template>
        </el-dialog>
      </div>

      <!-- ========== 分享&发布 ========== -->
      <div v-if="activeMenu === 'publish'" class="config-section">
        <div class="section-group">
          <div class="section-title">分享 & 发布</div>
          <p class="desc">将应用发布到各个渠道，或生成分享链接/嵌入代码</p>
          <el-tabs v-model="activePublishTab">
            <el-tab-pane label="分享链接" name="share">
              <el-form label-width="140px" style="max-width:600px;margin-top:16px">
                <el-form-item label="分享状态">
                  <el-switch v-model="publishConfig.shareEnabled" active-text="已开启" inactive-text="已关闭" />
                </el-form-item>
                <el-form-item label="分享链接" v-if="publishConfig.shareEnabled">
                  <div style="display:flex;gap:8px;width:100%">
                    <el-input :model-value="shareUrl" readonly>
                      <template #prepend><el-icon><Link /></el-icon></template>
                    </el-input>
                    <el-button @click="copyToClipboard(shareUrl)">复制</el-button>
                  </div>
                  <div class="form-tip">任何人拥有此链接即可访问应用</div>
                </el-form-item>
                <el-form-item label="访问密码" v-if="publishConfig.shareEnabled">
                  <el-input v-model="publishConfig.accessPassword" placeholder="设置访问密码（可选）" style="width:200px" />
                </el-form-item>
                <el-form-item label="有效期">
                  <el-select v-model="publishConfig.expiresIn" style="width:200px">
                    <el-option label="永久有效" value="never" />
                    <el-option label="7天" value="7d" />
                    <el-option label="30天" value="30d" />
                    <el-option label="90天" value="90d" />
                  </el-select>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleSavePublish">保存分享设置</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
            <el-tab-pane label="嵌入代码" name="embed">
              <div style="margin-top:16px">
                <h4 style="margin:0 0 8px;font-size:14px;font-weight:500">HTML 嵌入代码</h4>
                <p class="desc">将以下代码复制到您的网站页面中</p>
                <el-input type="textarea" :model-value="embedCode" :rows="10" readonly style="font-family:monospace;font-size:12px" />
                <el-button size="small" style="margin-top:8px" @click="copyToClipboard(embedCode)">复制代码</el-button>
              </div>
              <div style="margin-top:24px">
                <h4 style="margin:0 0 8px;font-size:14px;font-weight:500">嵌入选项</h4>
                <el-form label-width="120px" style="max-width:400px">
                  <el-form-item label="宽">
                    <el-input v-model.number="embedOptions.width" size="small">
                      <template #append>px</template>
                    </el-input>
                  </el-form-item>
                  <el-form-item label="高">
                    <el-input v-model.number="embedOptions.height" size="small">
                      <template #append>px</template>
                    </el-input>
                  </el-form-item>
                  <el-form-item label="主题色">
                    <el-color-picker v-model="embedOptions.themeColor" show-alpha />
                  </el-form-item>
                </el-form>
              </div>
            </el-tab-pane>
            <el-tab-pane label="发布渠道" name="channel">
              <div class="config-group" style="margin-top:16px">
                <div class="group-title">选择发布渠道</div>
                <div style="display:flex;flex-wrap:wrap;gap:16px">
                  <el-card v-for="ch in publishChannels" :key="ch.key" :class="{ 'channel-selected': publishConfig.selectedChannels.includes(ch.key) }" shadow="hover" style="width:180px;cursor:pointer" @click="togglePublishChannel(ch.key)">
                    <div style="text-align:center;padding:8px">
                      <el-icon :size="32" :color="publishConfig.selectedChannels.includes(ch.key) ? '#409eff' : '#999'"><component :is="ch.icon" /></el-icon>
                      <div style="margin-top:8px;font-weight:500">{{ ch.label }}</div>
                      <div style="font-size:12px;color:#999;margin-top:4px">{{ ch.desc }}</div>
                    </div>
                  </el-card>
                </div>
                <div style="margin-top:16px">
                  <el-button type="primary" @click="handleSavePublish">保存发布配置</el-button>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>

      <!-- ========== 对话记录 ========== -->
      <div v-if="activeMenu === 'chat-log'" class="config-section">
        <div class="section-group">
          <div class="section-title">对话记录</div>
          <p class="desc">查看和搜索用户与应用的对话历史记录</p>
          <!-- 搜索筛选栏 -->
          <div style="display:flex;gap:12px;margin-bottom:16px;flex-wrap:wrap;align-items:center">
            <el-input v-model="chatLogKeyword" placeholder="搜索对话内容..." prefix-icon="Search" style="width:260px" size="small" clearable />
            <el-date-picker v-model="chatLogDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" size="small" style="width:260px" />
            <el-select v-model="chatLogRating" placeholder="评分过滤" size="small" style="width:130px" clearable>
              <el-option label="全部" value="" />
              <el-option label="好评" value="good" />
              <el-option label="中评" value="neutral" />
              <el-option label="差评" value="bad" />
            </el-select>
            <el-button size="small" @click="handleChatLogQuery">查询</el-button>
            <el-button size="small" @click="handleChatLogReset">重置</el-button>
            <div style="flex:1" />
            <el-button size="small" @click="handleExportChatLog">
              <el-icon><Download /></el-icon>导出记录
            </el-button>
          </div>
          <!-- 对话列表 -->
          <el-table :data="filteredChatLogs" border stripe size="small" style="width:100%">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="user" label="用户" width="120" />
            <el-table-column prop="sessionId" label="会话ID" width="160" />
            <el-table-column label="消息摘要" min-width="300">
              <template #default="{ row }">
                <div style="display:flex;flex-direction:column;gap:2px">
                  <div style="font-size:12px;color:#666">
                    <el-tag size="small" type="info" round>Q</el-tag>
                    <span style="margin-left:4px">{{ row.question }}</span>
                  </div>
                  <div style="font-size:12px;color:#333">
                    <el-tag size="small" type="primary" round>A</el-tag>
                    <span style="margin-left:4px">{{ row.answer }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="rating" label="评分" width="80" align="center">
              <template #default="{ row }">
                <el-rate v-model="row.rating" disabled size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="tokens" label="Token消耗" width="110" />
            <el-table-column prop="time" label="时间" width="160" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click="handleViewChatDetail(row)">详情</el-button>
                <el-popconfirm title="确定删除此对话?" @confirm="handleDeleteChatLog(row.id)">
                  <template #reference>
                    <el-button link size="small" type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <div style="display:flex;justify-content:space-between;align-items:center;margin-top:16px">
            <span style="font-size:13px;color:#999">共 {{ filteredChatLogs.length }} 条记录</span>
            <el-pagination background layout="prev, pager, next" :total="filteredChatLogs.length" :page-size="15" size="small" />
          </div>
        </div>
        <!-- 对话详情抽屉 -->
        <el-drawer v-model="showChatDetail" title="对话详情" size="500px">
          <div v-if="chatDetail">
            <div style="margin-bottom:16px">
              <div style="font-size:13px;color:#999;margin-bottom:8px">用户：{{ chatDetail.user }} | 会话ID：{{ chatDetail.sessionId }} | 时间：{{ chatDetail.time }}</div>
            </div>
            <div v-for="(msg, i) in chatDetail.messages" :key="i" :class="['detail-msg', msg.role]">
              <div class="detail-msg-label">
                <el-tag :type="msg.role === 'user' ? 'info' : 'primary'" size="small" round>{{ msg.role === 'user' ? '用户' : 'AI' }}</el-tag>
              </div>
              <div class="detail-msg-content">{{ msg.content }}</div>
              <div class="detail-msg-meta" v-if="msg.tokens">Token: {{ msg.tokens }} | 耗时: {{ msg.latency }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </el-drawer>
      </div>

      <!-- ========== 反馈记录 ========== -->
      <div v-if="activeMenu === 'feedback'" class="config-section">
        <div class="section-group">
          <div class="section-title">反馈记录</div>
          <p class="desc">查看用户对应用回复的反馈和评价</p>
          <!-- 筛选栏 -->
          <div style="display:flex;gap:12px;margin-bottom:16px;flex-wrap:wrap;align-items:center">
            <el-input v-model="feedbackKeyword" placeholder="搜索反馈内容..." prefix-icon="Search" style="width:260px" size="small" clearable />
            <el-select v-model="feedbackTypeFilter" placeholder="反馈类型" size="small" style="width:130px" clearable>
              <el-option label="全部" value="" />
              <el-option label="好评" value="good" />
              <el-option label="差评" value="bad" />
              <el-option label="建议" value="suggestion" />
              <el-option label="投诉" value="complaint" />
            </el-select>
            <el-select v-model="feedbackStatusFilter" placeholder="处理状态" size="small" style="width:130px" clearable>
              <el-option label="全部" value="" />
              <el-option label="待处理" value="pending" />
              <el-option label="处理中" value="processing" />
              <el-option label="已处理" value="resolved" />
              <el-option label="已关闭" value="closed" />
            </el-select>
            <el-button size="small" @click="handleFeedbackQuery">查询</el-button>
            <el-button size="small" @click="handleFeedbackReset">重置</el-button>
            <div style="flex:1" />
            <el-button size="small" @click="handleExportFeedback">
              <el-icon><Download /></el-icon>导出反馈
            </el-button>
          </div>
          <!-- 统计卡片 -->
          <div style="display:flex;gap:16px;margin-bottom:16px">
            <el-card shadow="never" style="flex:1">
              <div style="text-align:center;padding:8px 0">
                <div style="font-size:28px;font-weight:600;color:#409eff">{{ feedbackStats.total }}</div>
                <div style="font-size:13px;color:#999;margin-top:4px">总反馈数</div>
              </div>
            </el-card>
            <el-card shadow="never" style="flex:1">
              <div style="text-align:center;padding:8px 0">
                <div style="font-size:28px;font-weight:600;color:#67c23a">{{ feedbackStats.good }}</div>
                <div style="font-size:13px;color:#999;margin-top:4px">好评数</div>
              </div>
            </el-card>
            <el-card shadow="never" style="flex:1">
              <div style="text-align:center;padding:8px 0">
                <div style="font-size:28px;font-weight:600;color:#e6a23c">{{ feedbackStats.pending }}</div>
                <div style="font-size:13px;color:#999;margin-top:4px">待处理</div>
              </div>
            </el-card>
            <el-card shadow="never" style="flex:1">
              <div style="text-align:center;padding:8px 0">
                <div style="font-size:28px;font-weight:600;color:#f56c6c">{{ feedbackStats.bad }}</div>
                <div style="font-size:13px;color:#999;margin-top:4px">差评数</div>
              </div>
            </el-card>
          </div>
          <!-- 反馈列表 -->
          <el-table :data="filteredFeedbacks" border stripe size="small" style="width:100%">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="user" label="用户" width="120" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag :type="row.type === 'good' ? 'success' : row.type === 'bad' ? 'danger' : row.type === 'suggestion' ? 'warning' : 'info'" size="small">
                  {{ row.typeLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="反馈内容" min-width="280" show-overflow-tooltip />
            <el-table-column prop="rating" label="评分" width="80" align="center">
              <template #default="{ row }">
                <el-rate v-model="row.rating" disabled size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="status" label="处理状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'resolved' ? 'success' : row.status === 'processing' ? 'warning' : row.status === 'closed' ? 'info' : 'danger'" size="small">
                  {{ row.statusLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="time" label="提交时间" width="160" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click="handleViewFeedback(row)">查看</el-button>
                <el-button v-if="row.status === 'pending'" link size="small" type="warning" @click="handleProcessFeedback(row)">处理</el-button>
                <el-popconfirm title="确定删除此反馈?" @confirm="handleDeleteFeedback(row.id)">
                  <template #reference>
                    <el-button link size="small" type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <div style="display:flex;justify-content:space-between;align-items:center;margin-top:16px">
            <span style="font-size:13px;color:#999">共 {{ filteredFeedbacks.length }} 条记录</span>
            <el-pagination background layout="prev, pager, next" :total="filteredFeedbacks.length" :page-size="15" size="small" />
          </div>
        </div>
        <!-- 反馈详情/处理对话框 -->
        <el-dialog v-model="showFeedbackDetail" :title="feedbackProcessing ? '处理反馈' : '反馈详情'" width="560px">
          <div v-if="selectedFeedback">
            <div style="margin-bottom:16px">
              <div><strong>用户：</strong>{{ selectedFeedback.user }}</div>
              <div><strong>类型：</strong>
                <el-tag :type="selectedFeedback.type === 'good' ? 'success' : selectedFeedback.type === 'bad' ? 'danger' : selectedFeedback.type === 'suggestion' ? 'warning' : 'info'" size="small">{{ selectedFeedback.typeLabel }}</el-tag>
              </div>
              <div><strong>评分：</strong><el-rate v-model="selectedFeedback.rating" disabled size="small" style="display:inline-flex;vertical-align:middle" /></div>
              <div><strong>提交时间：</strong>{{ selectedFeedback.time }}</div>
            </div>
            <div style="margin-bottom:16px;padding:12px;background:#f5f7fa;border-radius:4px">
              <div style="font-weight:500;margin-bottom:4px">反馈内容：</div>
              <div>{{ selectedFeedback.content }}</div>
            </div>
            <div v-if="selectedFeedback.reply">
              <div style="font-weight:500;margin-bottom:4px">回复内容：</div>
              <div style="padding:12px;background:#ecf5ff;border-radius:4px">{{ selectedFeedback.reply }}</div>
            </div>
            <div v-if="feedbackProcessing" style="margin-top:16px">
              <el-input v-model="feedbackReply" type="textarea" :rows="4" placeholder="输入回复内容..." />
            </div>
          </div>
          <template #footer>
            <el-button @click="showFeedbackDetail = false">关闭</el-button>
            <el-button v-if="feedbackProcessing" type="primary" @click="handleSubmitFeedbackReply">提交回复</el-button>
          </template>
        </el-dialog>
      </div>

      <!-- ========== 应用首页 ========== -->
      <div v-if="activeMenu === 'home'" class="config-section">
        <div class="section-group">
          <div class="section-title">应用首页预览</div>
          <p class="desc">预览应用在最终用户端的显示效果</p>
          <div style="display:flex;gap:24px">
            <!-- 预览方式选择 -->
            <div style="width:200px;flex-shrink:0">
              <el-menu :default-active="previewMode" @select="(v: string) => previewMode = v" style="border-right:none">
                <el-menu-item index="chat">
                  <el-icon><ChatDotRound /></el-icon>
                  <span>对话预览</span>
                </el-menu-item>
                <el-menu-item index="embed">
                  <el-icon><Monitor /></el-icon>
                  <span>嵌入视图</span>
                </el-menu-item>
                <el-menu-item index="mobile">
                  <el-icon><Iphone /></el-icon>
                  <span>移动端预览</span>
                </el-menu-item>
              </el-menu>
              <div style="margin-top:16px;padding:12px;background:#f5f7fa;border-radius:4px">
                <div style="font-size:13px;font-weight:500;margin-bottom:8px">快捷操作</div>
                <el-button size="small" style="width:100%;margin-bottom:8px" @click="handleOpenApp">
                  <el-icon><Link /></el-icon>打开应用
                </el-button>
                <el-button size="small" style="width:100%;margin-bottom:8px" @click="activeMenu = 'publish'">
                  <el-icon><Share /></el-icon>分享应用
                </el-button>
                <el-button size="small" style="width:100%" @click="activeMenu = 'ui'">
                  <el-icon><Monitor /></el-icon>界面配置
                </el-button>
              </div>
            </div>
            <!-- 预览区域 -->
            <div style="flex:1;display:flex;justify-content:center;align-items:flex-start">
              <!-- 对话预览 -->
              <div v-if="previewMode === 'chat'" class="preview-chat-widget" style="width:380px;border:1px solid #e4e7ed;border-radius:8px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08)">
                <div class="preview-widget-header" :style="{ background: uiConfig.chatWindowColor || '#0167e5' }" style="padding:12px 16px;color:#fff;display:flex;justify-content:space-between;align-items:center">
                  <span>{{ appInfo.name }}</span>
                  <div class="header-actions">
                    <el-icon><Refresh /></el-icon>
                  </div>
                </div>
                <div class="preview-widget-body" style="min-height:320px;padding:12px;background:#f5f5f5">
                  <div class="bot-message" style="display:flex;gap:8px;margin-bottom:12px">
                    <div class="bot-avatar" :style="{ background: uiConfig.chatWindowColor || '#0167e5' }" style="width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;flex-shrink:0">
                      <el-icon :size="18" color="#fff"><ChatDotRound /></el-icon>
                    </div>
                    <div class="message-bubble bot" :style="{ background: '#fff', color: uiConfig.replyFontColor || '#333', borderRadius: '8px' }" style="padding:8px 12px;max-width:260px;font-size:13px">
                      {{ uiConfig.welcome || '您好,有什么我可以帮助您' }}
                    </div>
                  </div>
                  <div class="user-message" style="display:flex;justify-content:flex-end;margin-bottom:12px">
                    <div class="message-bubble user" :style="{ background: uiConfig.userMessageColor || 'rgba(1,103,229,0.12)', color: uiConfig.questionFontColor || '#333', borderRadius: '8px' }" style="padding:8px 12px;max-width:260px;font-size:13px">
                      你好，请介绍一下你们的服务
                    </div>
                  </div>
                  <div class="bot-message" style="display:flex;gap:8px;margin-bottom:12px">
                    <div class="bot-avatar" :style="{ background: uiConfig.chatWindowColor || '#0167e5' }" style="width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;flex-shrink:0">
                      <el-icon :size="18" color="#fff"><ChatDotRound /></el-icon>
                    </div>
                    <div class="message-bubble bot" :style="{ background: '#fff', color: uiConfig.replyFontColor || '#333', borderRadius: '8px' }" style="padding:8px 12px;max-width:260px;font-size:13px">
                      您好！我们提供基于AI的知识库问答服务，可以快速搭建智能客服、文档助手等应用。
                    </div>
                  </div>
                </div>
                <div class="preview-widget-footer" :style="{ background: uiConfig.chatWindowColor || '#0167e5' }" style="padding:12px 16px;display:flex;align-items:center;gap:8px">
                  <el-input size="small" placeholder="请输入您的问题..." style="flex:1" :style="{ '--el-input-bg-color': 'rgba(255,255,255,0.2)' }" />
                  <el-icon style="color:#fff;cursor:pointer"><Promotion /></el-icon>
                </div>
                <div class="widget-brand" style="text-align:center;padding:4px;font-size:11px;color:#999;background:#fff">
                  {{ uiConfig.brandText || 'Powered by AIS' }}
                </div>
              </div>
              <!-- 嵌入视图 -->
              <div v-if="previewMode === 'embed'" style="width:100%;max-width:600px;border:1px solid #e4e7ed;border-radius:8px;overflow:hidden">
                <div style="padding:8px 12px;background:#f5f7fa;border-bottom:1px solid #e4e7ed;display:flex;align-items:center;gap:8px">
                  <el-icon :size="14"><Monitor /></el-icon>
                  <span style="font-size:12px;color:#666">嵌入预览 — 在网页中显示的聊天控件</span>
                </div>
                <div style="position:relative;min-height:400px;background:#f0f2f5;display:flex;justify-content:center;align-items:center">
                  <div style="width:320px;border:1px solid #e4e7ed;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.06)">
                    <div style="padding:8px 12px;background:#0167e5;color:#fff;font-size:13px;display:flex;justify-content:space-between;align-items:center">
                      <span>{{ appInfo.name }}</span>
                      <el-icon size="14"><Close /></el-icon>
                    </div>
                    <div style="min-height:200px;padding:12px;background:#f5f5f5">
                      <div style="font-size:12px;color:#999;text-align:center;margin-top:80px">点击此处开始对话</div>
                    </div>
                    <div style="padding:8px 12px;background:#0167e5;display:flex;gap:4px">
                      <el-input size="small" placeholder="输入问题..." style="flex:1" />
                      <el-icon style="color:#fff;cursor:pointer"><Promotion /></el-icon>
                    </div>
                  </div>
                </div>
              </div>
              <!-- 移动端预览 -->
              <div v-if="previewMode === 'mobile'" style="width:280px;border:2px solid #333;border-radius:24px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.15);background:#fff">
                <div style="padding:8px 12px;background:#0167e5;color:#fff;font-size:12px;text-align:center;position:relative">
                  <span>{{ new Date().toLocaleTimeString() }}</span>
                  <div style="position:absolute;right:12px;top:8px;display:flex;gap:4px">
                    <div style="width:8px;height:8px;border-radius:50%;background:rgba(255,255,255,0.8)" />
                    <div style="width:14px;height:8px;border:1px solid rgba(255,255,255,0.8);border-radius:2px" />
                  </div>
                </div>
                <div style="padding:8px;min-height:400px;background:#f5f5f5">
                  <div style="display:flex;gap:6px;margin-bottom:8px">
                    <div style="width:24px;height:24px;border-radius:50%;background:#0167e5;display:flex;align-items:center;justify-content:center;flex-shrink:0">
                      <el-icon size="12" color="#fff"><ChatDotRound /></el-icon>
                    </div>
                    <div style="padding:6px 10px;background:#fff;border-radius:6px;font-size:12px;max-width:200px">{{ uiConfig.welcome || '您好,有什么我可以帮助您' }}</div>
                  </div>
                  <div style="display:flex;justify-content:flex-end;margin-bottom:8px">
                    <div style="padding:6px 10px;background:#0167e5;color:#fff;border-radius:6px;font-size:12px;max-width:200px">你们有什么功能？</div>
                  </div>
                  <div style="display:flex;gap:6px;margin-bottom:8px">
                    <div style="width:24px;height:24px;border-radius:50%;background:#0167e5;display:flex;align-items:center;justify-content:center;flex-shrink:0">
                      <el-icon size="12" color="#fff"><ChatDotRound /></el-icon>
                    </div>
                    <div style="padding:6px 10px;background:#fff;border-radius:6px;font-size:12px;max-width:200px">我们提供AI知识库问答服务！</div>
                  </div>
                </div>
                <div style="padding:8px;display:flex;gap:4px;border-top:1px solid #eee">
                  <el-input size="small" placeholder="输入..." style="flex:1" />
                  <el-icon style="color:#0167e5;cursor:pointer"><Promotion /></el-icon>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="editor-footer">
        <el-button @click="handleSave">保存</el-button>
        <el-button type="primary" @click="handlePublish">发布配置</el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;
@use 'sass:color';

.app-editor {
  display: flex;
  height: 100%;
}

.editor-sidebar {
  width: 200px;
  background: $bg-white;
  border-right: 1px solid $border-lighter;
  display: flex;
  flex-direction: column;

  .sidebar-header {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-base;
    border-bottom: 1px solid $border-lighter;
    font-weight: 600;
  }

  .menu-scrollbar {
    flex: 1;
  }
}

.menu-groups {
  padding: $spacing-sm 0;
}

.menu-group {
  margin-bottom: $spacing-xs;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm $spacing-base;
  cursor: pointer;
  user-select: none;

  &:hover {
    background: $bg-hover;
  }

  .group-name {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
  }

  .group-expand-icon {
    font-size: 12px;
    color: $text-secondary;
    transition: transform 0.3s;
  }
}

.group-items {
  .menu-item {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    padding-left: $spacing-xl;
    font-size: 13px;
    color: $text-regular;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: $bg-hover;
      color: $text-primary;
    }

    &.active {
      background: $bg-active;
      color: $color-primary;
      font-weight: 500;
    }

    .el-icon {
      font-size: 16px;
    }
  }
}

.editor-content {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-lg;
}

.config-section {
  h3 { margin: 0 0 $spacing-lg; }
  .desc { color: $text-secondary; margin-bottom: $spacing-base; }
}

.section-group {
  margin-bottom: $spacing-xl;
  padding-bottom: $spacing-xl;
  border-bottom: 1px solid $border-lighter;

  &:last-child {
    border-bottom: none;
  }

  .section-title {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: $spacing-lg;
  }
}

.icon-upload {
  display: flex;
  align-items: center;
  gap: $spacing-base;
}

.icon-preview {
  width: 64px;
  height: 64px;
  border-radius: $radius-base;
  background: rgba(64, 158, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-upload-btn {
  width: 64px;
  height: 64px;
  border-radius: $radius-base;
  border: 2px dashed $border-base;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: $color-primary;
    color: $color-primary;
  }
}

.icon-tip {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}

// 知识库配置样式
.kb-config-options {
  margin-bottom: $spacing-lg;
  padding: $spacing-base;
  background: $bg-hover;
  border-radius: $radius-base;
}

.kb-option-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;

  &:last-child {
    margin-bottom: 0;
  }

  .option-label {
    font-size: 14px;
    color: $text-primary;
  }
}

.kb-binding-section {
  margin-bottom: $spacing-lg;
}

.kb-binding-header {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-base;

  .section-label {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
  }
}

.kb-binding-content {
  display: flex;
  gap: $spacing-base;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  min-height: 400px;
}

.kb-categories {
  width: 200px;
  border-right: 1px solid $border-lighter;
  padding: $spacing-base;

  .category-list {
    margin-top: $spacing-sm;
  }

  .category-item {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    padding: $spacing-sm $spacing-base;
    cursor: pointer;
    border-radius: $radius-sm;
    font-size: 13px;
    color: $text-regular;

    &:hover {
      background: $bg-hover;
    }

    &.active {
      background: $bg-active;
      color: $color-primary;
      font-weight: 500;
    }

    .expand-icon {
      font-size: 12px;
    }
  }
}

.kb-list {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.kb-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-base;
  border-bottom: 1px solid $border-lighter;
}

.kb-list-actions {
  display: flex;
  align-items: center;
  gap: $spacing-base;

  .selected-info {
    font-size: 13px;
    color: $text-secondary;
  }
}

.kb-list-body {
  flex: 1;
  overflow-y: auto;
}

.kb-item {
  display: flex;
  align-items: center;
  padding: $spacing-base;
  border-bottom: 1px solid $border-extra-light;

  &:hover {
    background: $bg-hover;
  }

  .kb-name {
    flex: 1;
    font-size: 14px;
    color: $text-primary;
    margin-left: $spacing-sm;
  }

  .kb-meta {
    font-size: 12px;
    color: $text-secondary;
  }
}

.kb-footer {
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
}

// 界面配置样式
.ui-layout {
  display: flex;
  gap: $spacing-xl;
}

.ui-form-area {
  flex: 1;
}

.config-group {
  margin-bottom: $spacing-xl;

  .group-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: $spacing-base;
    padding-bottom: $spacing-sm;
    border-bottom: 1px solid $border-lighter;
  }
}

.color-templates {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.color-template-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  cursor: pointer;
  padding: $spacing-sm;
  border-radius: $radius-sm;
  border: 2px solid transparent;

  &:hover {
    background: $bg-hover;
  }

  &.active {
    border-color: $color-primary;
    background: $bg-active;
  }

  .template-color {
    width: 40px;
    height: 40px;
    border-radius: $radius-sm;
    border: 1px solid $border-lighter;
  }

  span {
    font-size: 12px;
    color: $text-secondary;
  }
}

.ui-preview {
  width: 320px;
  flex-shrink: 0;
}

.preview-chat-widget {
  border: 1px solid $border-lighter;
  border-radius: $radius-lg;
  overflow: hidden;
  box-shadow: $shadow-base;
}

.preview-widget-header {
  padding: $spacing-base;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .header-actions {
    display: flex;
    gap: $spacing-sm;
    cursor: pointer;
  }
}

.preview-widget-body {
  min-height: 300px;
  padding: $spacing-base;
  background: #f5f5f5;
}

.bot-message {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-base;
}

.bot-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: $color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-bubble {
  padding: $spacing-sm $spacing-base;
  border-radius: $radius-base;
  max-width: 200px;
  font-size: 13px;

  &.bot {
    background: #fff;
    color: $text-primary;
  }

  &.user {
    background: $color-primary;
    color: #fff;
    margin-left: auto;
  }
}

.user-message {
  display: flex;
  justify-content: flex-end;
  margin-bottom: $spacing-base;
}

.preview-widget-footer {
  padding: $spacing-base;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .input-placeholder {
    font-size: 13px;
    opacity: 0.8;
  }

  .send-icon {
    cursor: pointer;
  }
}

.widget-brand {
  text-align: center;
  padding: $spacing-sm;
  font-size: 11px;
  color: $text-secondary;
  background: #fff;
}

.help-center-link {
  text-align: right;
  margin-top: $spacing-sm;
  font-size: 12px;
  color: $color-primary;
  cursor: pointer;
}

// 导航配置样式
.nav-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: $spacing-base;
}

.nav-filters {
  display: flex;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

// 参数配置样式
.param-tabs {
  display: flex;
  gap: $spacing-lg;
  margin-bottom: $spacing-lg;
  border-bottom: 1px solid $border-lighter;
  padding-bottom: $spacing-base;
}

.param-tab-item {
  font-size: 14px;
  color: $text-secondary;
  cursor: pointer;
  padding-bottom: $spacing-sm;

  &:hover {
    color: $color-primary;
  }

  &.active {
    color: $color-primary;
    font-weight: 500;
    border-bottom: 2px solid $color-primary;
  }
}

.retrieval-config {
  .slider-input {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    width: 100%;
  }
}

.param-actions {
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
  border-top: 1px solid $border-lighter;
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
}

.form-tip {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}

// Prompt配置样式
.prompt-config-layout {
  display: flex;
  gap: $spacing-xl;
}

.prompt-steps {
  width: 280px;
  flex-shrink: 0;
}

.prompt-step-item {
  display: flex;
  gap: $spacing-base;
  padding: $spacing-base;
  cursor: pointer;
  border-radius: $radius-base;
  transition: all 0.2s;

  &:hover {
    background: $bg-hover;
  }

  &.active {
    background: $bg-active;

    .step-number {
      background: $color-primary;
      color: #fff;
    }

    .step-title {
      color: $color-primary;
    }
  }
}

.step-number {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: $border-lighter;
  color: $text-secondary;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
}

.step-info {
  flex: 1;
}

.step-title {
  font-size: 14px;
  font-weight: 500;
  color: $text-primary;
  margin-bottom: 4px;
}

.step-desc {
  font-size: 12px;
  color: $text-secondary;
}

.prompt-config-content {
  flex: 1;
  min-width: 0;
}

.prompt-config-card {
  background: $bg-white;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;

  &.collapsed {
    margin-bottom: $spacing-sm;
  }
}

.config-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-base;
  border-bottom: 1px solid $border-lighter;
  background: $bg-hover;

  .header-info {
    h4 {
      margin: 0 0 4px;
      font-size: 14px;
      font-weight: 500;
      color: $text-primary;
    }

    p {
      margin: 0;
      font-size: 12px;
      color: $text-secondary;
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  .expand-icon {
    cursor: pointer;
    color: $text-secondary;
  }
}

.config-card-body {
  padding: $spacing-base;
}

.prompt-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-sm;

  .toolbar-label {
    font-size: 13px;
    color: $text-secondary;
  }
}

.variable-tags {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-base;

  .var-tag {
    cursor: pointer;

    &:hover {
      color: $color-primary;
    }
  }
}

// 对话调试样式
.chat-debug {
  height: calc(100vh - 180px);
}

.debug-layout {
  display: flex;
  gap: $spacing-xl;
  height: 100%;
}

.debug-sidebar {
  width: 300px;
  flex-shrink: 0;

  .debug-tip {
    background: #fff7e6;
    border: 1px solid #ffd591;
    border-radius: $radius-base;
    padding: $spacing-base;

    p {
      font-size: 13px;
      color: $text-primary;
      margin: 0 0 $spacing-base;
      line-height: 1.6;
    }
  }
}

.debug-chat-widget {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid $border-lighter;
  border-radius: $radius-lg;
  overflow: hidden;
  background: $bg-white;
}

.widget-header {
  background: $color-primary;
  color: #fff;
  padding: $spacing-base $spacing-lg;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 15px;
  font-weight: 500;

  .refresh-btn {
    cursor: pointer;
    font-size: 18px;

    &:hover {
      opacity: 0.8;
    }
  }
}

.widget-messages {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-lg;
  background: #f5f5f5;
}

.message {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-base;

  &.user {
    justify-content: flex-end;

    .message-content {
      background: #fff;
      color: $text-primary;
      border-radius: $radius-base $radius-base 4px $radius-base;
    }

    .message-actions {
      display: flex;
      gap: $spacing-xs;
      align-items: center;
      color: $text-secondary;
      font-size: 14px;

      .el-icon {
        cursor: pointer;

        &:hover {
          color: $color-primary;
        }
      }
    }
  }

  &.assistant {
    .message-content {
      background: $color-primary;
      color: #fff;
      border-radius: $radius-base $radius-base $radius-base 4px;
    }
  }
}

.bot-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: $color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-content {
  padding: $spacing-base;
  font-size: 14px;
  line-height: 1.6;
  max-width: 400px;
}

.widget-input-area {
  padding: $spacing-base;
  border-top: 1px solid $border-lighter;
  background: $color-primary;

  .el-input {
    border-radius: $radius-base;
  }

  .send-btn {
    background: $color-primary;
    color: #fff;
    border: none;

    &:hover {
      background: color.adjust($color-primary, $lightness: -10%);
    }
  }

  .widget-brand {
    text-align: center;
    font-size: 11px;
    color: rgba(255, 255, 255, 0.7);
    margin-top: $spacing-sm;
  }
}

.kb-option {
  padding: $spacing-sm 0;
  border-bottom: 1px solid $border-extra-light;
}

.selected-count {
  margin-top: $spacing-base;
  color: $text-secondary;
  font-size: 13px;
}

.skill-option {
  padding: $spacing-base;
  border-bottom: 1px solid $border-extra-light;
  display: flex;
  align-items: center;
  justify-content: space-between;

  &:hover {
    background: $bg-hover;
  }
}

.skill-info {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.skill-name {
  font-weight: 500;
}

.skill-desc {
  font-size: 12px;
  color: $text-secondary;
  margin-left: 24px;
}

.tool-option {
  padding: $spacing-base;
  border-bottom: 1px solid $border-extra-light;
  display: flex;
  align-items: center;
  justify-content: space-between;

  &:hover {
    background: $bg-hover;
  }
}

.tool-info {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.tool-name {
  font-weight: 500;
}

.tool-identifier {
  font-size: 12px;
  color: $text-secondary;
  margin-left: $spacing-sm;
}

.tool-desc {
  font-size: 12px;
  color: $text-secondary;
  margin-left: 24px;
}

.mcp-option {
  padding: $spacing-base;
  border-bottom: 1px solid $border-extra-light;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  &:hover {
    background: $bg-hover;
  }
}

.mcp-info {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.mcp-name {
  font-weight: 500;
}

.mcp-url {
  font-size: 12px;
  color: $text-secondary;
  margin-left: 24px;
  word-break: break-all;
}

.mcp-tools {
  display: flex;
  gap: $spacing-xs;
  margin-left: 24px;
  margin-top: $spacing-xs;
}

.vm-config-card {
  max-width: 700px;
}

.vm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 500;
}

.vm-tip {
  margin-left: $spacing-sm;
  font-size: 12px;
  color: $text-secondary;
}

.vm-unit {
  margin-left: $spacing-sm;
  font-size: 12px;
  color: $text-secondary;
}

.vm-packages {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  align-items: center;
}

.ui-layout {
  display: flex;
  gap: $spacing-xl;
}

.ui-preview {
  width: 320px;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
  overflow: hidden;

  .preview-header {
    background: $color-primary;
    color: #fff;
    padding: $spacing-sm $spacing-base;
    font-size: 13px;
  }

  .preview-chat {
    padding: $spacing-lg;
    min-height: 200px;
  }

  .preview-welcome {
    background: $bg-hover;
    padding: $spacing-base;
    border-radius: $radius-base;
    font-size: 13px;
  }
}

.prompt-vars {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  .el-tag { cursor: pointer; }
}

.chat-debug .chat-area {
  height: calc(100vh - 280px);
  display: flex;
  flex-direction: column;
  border: 1px solid $border-lighter;
  border-radius: $radius-base;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-base;
}

.welcome {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: $text-secondary;
}

.msg {
  margin-bottom: $spacing-base;

  &.user .msg-content {
    background: #ecf5ff;
    margin-left: 80px;
  }

  &.assistant .msg-content {
    background: $bg-hover;
    margin-right: 80px;
  }
}

.msg-content {
  padding: $spacing-base;
  border-radius: $radius-base;
  line-height: 1.6;
}

.msg-meta {
  font-size: 12px;
  color: $text-secondary;
  margin-top: $spacing-xs;
}

.chat-input {
  padding: $spacing-base;
  border-top: 1px solid $border-lighter;
}

.editor-footer {
  margin-top: $spacing-lg;
  padding-top: $spacing-base;
  border-top: 1px solid $border-lighter;
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
}
</style>
