/** 知识库信息 */
export interface KnowledgeBase {
  id: string
  name: string
  description: string
  category: string
  tags: string[]
  embeddingModel: string
  dimension: number
  creator: string
  createdAt: string
  usedSize: string
  totalSize: string
  type: '团队' | '个人'
  /** 是否自动构建知识图谱（默认关闭） */
  graphAutoBuild?: number
  /** 知识图谱构建用 LLM 模型（parse strategy 未配置时的 fallback） */
  graphLlmModel?: string
}

/** 文档信息 */
export interface Document {
  id: string
  name: string
  type: 'folder' | 'document' | 'table' | 'qa'
  owner: string
  lastViewAt: string
  viewCount: number
  children?: Document[]
}

/** 分类节点 */
export interface CategoryNode {
  id: string
  name: string
  children?: CategoryNode[]
}

/** 文件类型配置 */
export interface FileTypeConfig {
  documents: boolean   // PDF、Word、Excel、PPT
  audio: boolean       // MP3、WAV
  video: boolean       // MP4、AVI
  images: boolean      // JPG、PNG（需要 OCR）
}

/** 知识库表单数据 */
export interface KnowledgeBaseForm {
  name: string
  category: string
  description: string
  tags: string[]
  permission: 'private' | 'team' | 'public'
  embeddingModel: string
  parseMode: 'auto' | 'manual'
  splitMode: 'auto' | 'custom'
  /** 是否自动构建知识图谱 */
  graphAutoBuild?: boolean
  /** 知识图谱构建用 LLM 模型 */
  graphLlmModel?: string
  fileTypeConfig: FileTypeConfig
  retrievalConfig: RetrievalSettingConfig
}

/** 检索设置配置 */
export interface RetrievalSettingConfig {
  /** 检索模式 */
  mode: 'vector' | 'fulltext' | 'hybrid'
  /** Top K */
  topK: number
  /** 是否启用分数阈值 */
  enableScoreThreshold?: boolean
  /** 分数阈值 */
  scoreThreshold?: number
  /** 是否启用 Rerank 模型 */
  enableRerank?: boolean
  /** 混合搜索重排策略 */
  rerankStrategy?: 'weighted' | 'rerank_model'
  /** 语义权重 (0-1) */
  semanticWeight?: number
  /** 相似度阈值 0-1（低于此阈值的结果被过滤） */
  similarityThreshold?: number

  // ===== 检索预处理（可选，默认开启）=====
  /** 自动纠错（错别字 + 拼音） */
  enableAutoCorrection?: boolean
  /** 查询重写规则（术语归一） */
  enableQueryRewrite?: boolean
  /** 图谱扩展（知识图谱增强检索） */
  enableGraphExpansion?: boolean
  /** 图谱扩展深度（1-2） */
  graphExpansionDepth?: number
  /** 图谱最大扩展实体数 */
  graphMaxEntities?: number
  /** 同义词联想 */
  enableSynonymExpansion?: boolean
  /** 图谱检索通道（后端图谱召回参与 RRF 融合，默认开） */
  enableGraphExpand?: boolean
  /** 关键词匹配：用户输入命中问答对触发关键词时优先返回 QA 结果 */
  enableKeywordMatch?: boolean

  // ===== 多路召回（可选，默认关闭）=====
  /** 是否启用多路召回（关闭则只走单一 mode 通道） */
  enableMultiRetrieval?: boolean
  /** 向量召回数量（多路模式下的单通道召回数） */
  vectorRecallCount?: number
  /** 全文召回数量 */
  fulltextRecallCount?: number
  /** 图谱子图召回数量 */
  graphRecallCount?: number
  /** QA 对召回数量（从问答知识库直接匹配） */
  qaRecallCount?: number
  /** 融合策略 */
  fusionStrategy?: 'rrf' | 'weighted' | 'interleave'

  // ===== 上下文组装（可选，默认 concat）=====
  /**
   * 上下文组装策略：
   * - concat：直接返回命中的子分片
   * - parent_chunk：命中子分片时返回其所属父分片（按标题聚合的章节），单层文档退化为返回自身
   * - parent_document：返回命中分片所属的完整文档
   * - window：返回命中分片及其前后 N 个相邻分片
   */
  contextAssemblyStrategy?: 'concat' | 'parent_chunk' | 'parent_document' | 'window'
  /** 窗口模式：命中 chunk 的前后 N 个 chunk */
  contextWindowSize?: number
  /** context 最大 token 数 */
  maxContextTokens?: number
  /** context 排序方式 */
  contextOrder?: 'relevance' | 'document_order'

  // ===== 重排序细化（可选）=====
  /** Rerank 模型选择（从 models.ts 的 Rerank 模型中选） */
  rerankModel?: string
  /** 是否启用 LLM 重排序（用大模型对候选结果打分） */
  enableLLMRerank?: boolean
  /** 是否启用 MMR（Maximal Marginal Relevance）多样性控制 */
  enableMMR?: boolean
  /** MMR lambda 参数（0=最大多样性，1=最大相关性） */
  mmrLambda?: number

  // ===== BM25 细节（debug / 高级用户用）=====
  /** BM25 召回数量（全文检索/混合检索的 BM25 候选数） */
  bm25RecallCount: number
  /** 向量检索权重（混合模式） */
  vectorWeight: number
  /** BM25 权重（混合模式） */
  bm25Weight: number
}

/** 图谱扩展配置 */
export interface GraphExpansionConfig {
  /** 是否启用图谱扩展 */
  enabled: boolean
  /** 扩展深度（1-2） */
  depth: number
  /** 最大扩展实体数 */
  maxEntities: number
}

/** 图谱实体 */
export interface GraphEntity {
  id: string
  name: string
  type: string
}

/** 图谱关系 */
export interface GraphRelation {
  source: string
  target: string
  label: string
}

/** 图谱扩展结果 */
export interface GraphExpansionResult {
  entities: GraphEntity[]
  relations: GraphRelation[]
  expandedQuery: string
}

/** 扩展后的检索请求 */
export interface ExpandedSearchRequest {
  query: string
  originalQuery: string
  enableGraphExpansion: boolean
  topK: number
  similarityThreshold: number
  mode: 'vector' | 'fulltext' | 'hybrid'
}

/** 文件类型分类 */
export type FileCategory = 'document' | 'image' | 'audio' | 'video'

/** 文件处理状态 */
export type ProcessStatus = 'pending' | 'processing' | 'completed' | 'failed'

/** 知识库文件 */
export interface KnowledgeFile {
  id: string
  name: string
  category: FileCategory
  extension: string
  size: number
  url: string
  status: ProcessStatus
  /** 处理进度 0-100 */
  progress: number
  /** 当前处理阶段 */
  stage?: string
  /** 音视频时长（秒） */
  duration?: number
  /** 文档页数 */
  pages?: number
  /** 使用的解析策略ID */
  parseStrategyId?: string
  /** 解析策略名称（用于显示） */
  parseStrategyName?: string
  /** 当前绑定策略的高级配置（含文件级专属策略；分片策略设置对话框据此回填参数） */
  parseStrategyAdvanced?: {
    parse?: Record<string, unknown>
    chunk?: {
      strategy?: string
      chunkLength?: number
      overlap?: number
      delimiters?: string[]
      parentMaxChunkLength?: number
      parentAggLevel?: string
      semanticThreshold?: number
      embeddingModel?: string
      titlePrefix?: boolean
      headingPath?: boolean
    }
    index?: Record<string, unknown>
  }
  /** 切片数量 */
  chunkCount?: number
  /** 处理模式: chunk | qa */
  processingMode?: string
  /** 是否构建知识图谱 */
  enableGraphBuild?: number
  /** 所属文件夹 ID（根目录为 'root'） */
  folderId?: string
  /** 软删除时间（存在即表示已删除，在回收站中） */
  deletedAt?: string
  createdAt: string
  updatedAt: string
}

/** 检索配置（向后兼容别名，统一使用 RetrievalSettingConfig） */
export type RetrievalConfig = RetrievalSettingConfig

/** 处理引擎配置 */
export interface ProcessingConfig {
  /** OCR 引擎 */
  ocrEngine: string
  /* ASR 引擎 */
  asrEngine: string
  /** 视频处理策略 */
  videoStrategy: 'keyframe_asr' | 'asr_only' | 'uniform_sample'
  /** 关键帧间隔（秒） */
  keyframeInterval?: number
}

/** 解析策略类型（文档类型，与后端 ParseMethodRegistry 对齐） */
export type ParseMethodType = 'default' | 'pdf' | 'doc' | 'docx' | 'pptx' | 'xlsx' | 'video' | 'audio' | 'image'

/** 分片策略类型 */
export type ChunkStrategyType = 'rule_fixed' | 'rule_recursive' | 'structure_aware' | 'semantic' | 'parent_child'

/** 分片策略元信息 */
export interface ChunkStrategyOption {
  value: ChunkStrategyType
  label: string
  icon: string
  description: string
  tag: string
}

/** 分片策略选项列表 */
export const CHUNK_STRATEGY_OPTIONS: ChunkStrategyOption[] = [
  {
    value: 'rule_fixed',
    label: '固定大小切分',
    icon: 'Grid',
    description: '按字符/词/Token 数量固定切开，加入少量 Overlap。适合通用知识库。',
    tag: '规则·固定',
  },
  {
    value: 'rule_recursive',
    label: '递归字符切分',
    icon: 'Sort',
    description: '优先按段落切，再退化到换行、句子、字符。适合通用知识库。',
    tag: '规则·递归',
  },
  {
    value: 'structure_aware',
    label: '结构感知切片',
    icon: 'Files',
    description: '按 Markdown 标题、HTML DOM、PDF 版面、代码函数、表格、图片等结构切分，自动附加章节标题。',
    tag: '结构感知',
  },
  {
    value: 'semantic',
    label: '语义切片',
    icon: 'MagicStick',
    description: '计算相邻句子的向量相似度，在语义突变处断开。适合长文本、叙事内容、高质量知识库。',
    tag: '语义切片',
  },
  {
    value: 'parent_child',
    label: '父子切片',
    icon: 'Share',
    description: '子分片用于精确召回，父分片按标题自动聚合用于上下文生成。',
    tag: '父子切片',
  },
]

/** 策略标签颜色映射 */
export const STRATEGY_TAG_TYPE_MAP: Record<ChunkStrategyType, string> = {
  rule_fixed: 'info',
  rule_recursive: 'info',
  structure_aware: 'primary',
  semantic: 'success',
  parent_child: 'warning',
}

/** 策略值 → 元信息快速查找表 */
export const STRATEGY_TYPE_MAP = Object.fromEntries(
  CHUNK_STRATEGY_OPTIONS.map((opt) => [opt.value, opt]),
) as Record<ChunkStrategyType, ChunkStrategyOption>

/** 父分片聚合层级选项（仅 parent_child 策略使用） */
export const PARENT_AGG_LEVEL_OPTIONS = [
  { label: '一级标题（H1）', value: 'H1' },
  { label: '二级标题（H2）', value: 'H2' },
  { label: '三级标题（H3）', value: 'H3' },
  { label: '自动（智能选择）', value: 'auto' },
] as const

/** 表格处理模式 */
export type TableMode = 'structured' | 'markdown' | 'ignore'

/** 解析配置组 */
export interface ParseStrategyParseConfig {
  /** 表格处理模式 */
  tableMode: TableMode
  /** 视频关键帧采样间隔（秒），仅 parseMethod=video 生效（audio 走纯 ASR） */
  keyframeIntervalSeconds?: number | null
  /** 关键帧 pHash 哈希阈值 */
  keyframeHashThreshold?: number | null
}

/** 分片配置组 */
export interface ParseStrategyChunkConfig {
  /** 分片策略类型 */
  strategy: ChunkStrategyType
  /**
   * 子分片长度（父子分片模式下为子分片目标长度；
   * 父分片按标题层级自动聚合，最大长度 = 该值 × 2，超过按子分片边界二次切分）
   */
  chunkLength: number
  /** 相邻子分片重叠字符数 */
  overlap: number
  /** content 内嵌 Markdown 标题前缀 */
  titlePrefix: boolean
  /** 生成 title / headingPath 元数据 */
  headingPath: boolean
  /** 纯文本兜底路径的分隔符 */
  delimiters: string[]
  /** 父分片最大长度（仅 parent_child 策略生效），聚合后超过按句子边界二次切分 */
  parentMaxChunkLength?: number
  /** 父分片聚合标题层级（仅 parent_child 策略生效）：H1 / H2 / H3 / auto */
  parentAggLevel?: 'H1' | 'H2' | 'H3' | 'auto'
  /** 语义切片阈值（0~100，对应 0.0~1.0，仅 semantic 策略生效） */
  semanticThreshold?: number
  /** 语义切片 Embedding 模型 code（仅 semantic 策略生效，空 = 系统默认） */
  embeddingModel?: string
}

/** 索引配置组 */
export interface ParseStrategyIndexConfig {
  /** embedding 输入拼接字段（一期固定，二期开放配置） */
  embedFields: string[]
}

/** 解析策略高级参数（分组类型化结构，替代原平铺 JSON） */
export interface ParseStrategyAdvanced {
  /** 解析配置 */
  parse: ParseStrategyParseConfig
  /** 分片配置 */
  chunk: ParseStrategyChunkConfig
  /** 索引配置（二期开放，一期后端固定策略消费，可省略） */
  index?: ParseStrategyIndexConfig
}

/** 默认高级参数（与后端系统默认值对齐：子分片长度=1000, overlap=100, delimiters=["\n\n"]） */
export const DEFAULT_ADVANCED: ParseStrategyAdvanced = {
  parse: {
    tableMode: 'structured',
    keyframeIntervalSeconds: null,
    keyframeHashThreshold: null,
  },
  chunk: {
    strategy: 'rule_fixed',
    chunkLength: 1000,
    overlap: 100,
    titlePrefix: true,
    headingPath: true,
    delimiters: ['\n\n'],
    parentMaxChunkLength: 2000,
    parentAggLevel: 'auto' as const,
    semanticThreshold: 30,
    embeddingModel: '',
  },
  index: {
    embedFields: ['content', 'headingPath', 'fileName'],
  },
}

/** 表格处理模式选项 */
export const TABLE_MODE_OPTIONS: { label: string; value: TableMode; desc: string }[] = [
  { label: '结构化保留', value: 'structured', desc: '保留表格行列结构，输出为结构化数据' },
  { label: 'Markdown 表格', value: 'markdown', desc: '转换为 Markdown 表格格式' },
  { label: '忽略表格', value: 'ignore', desc: '跳过表格内容，仅处理正文' },
]

/** 解析策略 */
export interface ParseStrategy {
  id: string
  /** 策略名称 */
  name: string
  /** 策略描述 */
  description: string
  /** 适用的文件扩展名列表 */
  extensions: string[]
  /** 解析方法类型 */
  parseMethod: ParseMethodType
  /** 是否为默认策略 */
  isDefault: boolean
  /** 创建时间 */
  createdAt: string
  /** 更新时间 */
  updatedAt: string
  /** 高级参数（可选，旧数据可能没有） */
  advanced?: ParseStrategyAdvanced
  /** 解析用 LLM 模型（智能分段、内容提取） */
  llmModel?: string
  /** 引用该策略的未删除文件数（删除确认提示影响面：删除后这些文件回退自动匹配） */
  fileCount?: number
}

/** 解析策略表单数据 */
export interface ParseStrategyForm {
  name: string
  description: string
  extensions: string[]
  parseMethod: ParseMethodType
  /** 高级参数（可选） */
  advanced?: ParseStrategyAdvanced
  /** 解析用 LLM 模型 */
  llmModel?: string
}

/** 解析方法元数据（文档类型，来自后端 /parse-strategies/meta，避免前后端口径漂移） */
export interface ParseMethodMeta {
  /** 方法代码（文档类型） */
  code: ParseMethodType
  /** 用户可见的类型名称 */
  label: string
  /** 该方法兼容的扩展名（带点，default 为预选文档类扩展名） */
  extensions: string[]
}

/** 解析方法元数据接口响应 */
export interface ParseStrategyMeta {
  methods: ParseMethodMeta[]
  /** 全部受支持扩展名（default 类型可选全集） */
  supportedExtensions: string[]
}

/** 切片长度选项 */
export const CHUNK_LENGTH_OPTIONS = [
  { label: '1,000', value: 1000 },
  { label: '500', value: 500 },
  { label: '200', value: 200 },
  { label: '自定义', value: 0 },
]

/** 父子切片专用子分片长度预设（子分片用于向量精确召回，长度更小） */
export const CHUNK_LENGTH_OPTIONS_PARENT_CHILD = [
  { label: '100', value: 100 },
  { label: '200', value: 200 },
  { label: '500', value: 500 },
  { label: '自定义', value: 0 },
]

/** 分隔符选项 */
export const DELIMITER_OPTIONS = [
  { label: '\\n 换行符', value: '\n' },
  { label: '\\n\\n 换行符x2', value: '\n\n' },
  { label: '句号', value: '。' },
  { label: '分号', value: '；' },
]

/** 文件类型图标映射 */
export const FILE_CATEGORY_ICONS = {
  document: '📄',
  image: '🖼️',
  audio: '🎵',
  video: '🎬',
} as const

/** 文件扩展名到分类的映射 */
export const EXTENSION_TO_CATEGORY = {
  '.pdf': 'document', '.docx': 'document', '.doc': 'document',
  '.xlsx': 'document', '.xls': 'document', '.pptx': 'document', '.ppt': 'document',
  '.md': 'document', '.txt': 'document', '.csv': 'document',
  '.jpg': 'image', '.jpeg': 'image', '.png': 'image', '.bmp': 'image', '.tiff': 'image', '.gif': 'image',
  '.mp3': 'audio', '.wav': 'audio', '.m4a': 'audio', '.aac': 'audio', '.ogg': 'audio',
  '.mp4': 'video', '.avi': 'video', '.mov': 'video', '.mkv': 'video', '.flv': 'video',
} as const

/** 获取文件分类 */
export function getFileCategory(filename: string): FileCategory {
  const ext = '.' + filename.split('.').pop()?.toLowerCase()
  return (EXTENSION_TO_CATEGORY as Record<string, FileCategory>)[ext] || 'document'
}

/** 知识库文件切片 */
export interface KbChunk {
  id?: string
  content: string
  chunkIndex: number
  fileName?: string
  startTime?: number
  endTime?: number
}

/** 格式化文件大小 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(k)), sizes.length - 1)
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/** 格式化时长 */
export function formatDuration(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

// ===========================================================================
// 问答对（QA Pair）
// ===========================================================================

/** 问答对来源 */
export type QaSource = 'manual' | 'ai'

/** 问答对状态 */
export type QaStatus = 'draft' | 'confirmed'

/** 问答对 */
export interface QaPair {
  id: string
  kbId: string
  /** 来源文件 ID（AI 抽取时记录） */
  fileId?: string
  /** 来源文件名（显示用） */
  fileName?: string
  question: string
  answer: string
  source: QaSource
  status: QaStatus
  createdAt: string
}
