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
  /** 上下文组装策略 */
  contextAssemblyStrategy?: 'concat' | 'parent_document' | 'window'
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
  /** BM25 召回数量 */
  bm25RecallCount: number
  /** 向量检索权重（混合模式） */
  vectorWeight: number
  /** BM25 权重（混合模式） */
  bm25Weight: number
  /** BM25 稀疏项丢弃比例 */
  bm25SparseDropRate: number
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
  /** 切片数量 */
  chunkCount?: number
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

/** 解析策略类型 */
export type ParseMethodType = 'default' | 'pptx' | 'pdf' | 'video' | 'audio'

/** 表格处理模式 */
export type TableMode = 'structured' | 'markdown' | 'ignore'

/** 解析策略高级参数 */
export interface ParseStrategyAdvanced {
  /** 切片方式 */
  splitMethod: 'fixed' | 'delimiter'
  /** 固定切片长度 */
  chunkLength: number
  /** 分隔符列表 */
  delimiters: string[]
  /** 索引字段 */
  indexFields: string[]
  /** 是否启用文档摘要 */
  enableDocSummary: boolean
  /** PPT 整页解析 */
  enablePptWholePage: boolean
  /** 表格处理模式 */
  tableMode: TableMode
}

/** 默认高级参数 */
export const DEFAULT_ADVANCED: ParseStrategyAdvanced = {
  splitMethod: 'fixed',
  chunkLength: 2000,
  delimiters: ['\n', '\n\n'],
  indexFields: ['originalText', 'originalImage', 'imageOCR'],
  enableDocSummary: false,
  enablePptWholePage: true,
  tableMode: 'structured',
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
  /** 解析用 VLM 模型（图片/表格理解） */
  vlmModel?: string
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
  /** 解析用 VLM 模型 */
  vlmModel?: string
}

/** 解析方法类型选项 */
export const PARSE_METHOD_OPTIONS: { label: string; value: ParseMethodType }[] = [
  { label: '通用解析', value: 'default' },
  { label: 'PPT解析', value: 'pptx' },
  { label: 'PDF解析', value: 'pdf' },
  { label: '视频解析', value: 'video' },
  { label: '音频解析', value: 'audio' },
]

/** 切片长度选项 */
export const CHUNK_LENGTH_OPTIONS = [
  { label: '2,000', value: 2000 },
  { label: '1,000', value: 1000 },
  { label: '500', value: 500 },
  { label: '自定义', value: 0 },
]

/** 索引字段选项 */
export const INDEX_FIELD_OPTIONS = [
  { label: '原始文本', value: 'originalText' },
  { label: '原始图片', value: 'originalImage' },
  { label: '图片 OCR', value: 'imageOCR' },
  { label: '文档目录', value: 'documentTOC' },
  { label: '文档名称', value: 'documentName' },
]

/** 分隔符选项 */
export const DELIMITER_OPTIONS = [
  { label: '\\n 换行符', value: '\n' },
  { label: '\\n\\n 换行符x2', value: '\n\n' },
  { label: '句号', value: '。' },
  { label: '分号', value: '；' },
]

/** 文件扩展名选项 */
export const EXTENSION_OPTIONS = [
  { label: '.pdf', value: '.pdf' },
  { label: '.docx', value: '.docx' },
  { label: '.doc', value: '.doc' },
  { label: '.xlsx', value: '.xlsx' },
  { label: '.xls', value: '.xls' },
  { label: '.pptx', value: '.pptx' },
  { label: '.ppt', value: '.ppt' },
  { label: '.md', value: '.md' },
  { label: '.txt', value: '.txt' },
  { label: '.csv', value: '.csv' },
  { label: '.jpg', value: '.jpg' },
  { label: '.jpeg', value: '.jpeg' },
  { label: '.png', value: '.png' },
  { label: '.bmp', value: '.bmp' },
  { label: '.tiff', value: '.tiff' },
  { label: '.gif', value: '.gif' },
  { label: '.mp3', value: '.mp3' },
  { label: '.wav', value: '.wav' },
  { label: '.m4a', value: '.m4a' },
  { label: '.aac', value: '.aac' },
  { label: '.ogg', value: '.ogg' },
  { label: '.mp4', value: '.mp4' },
  { label: '.avi', value: '.avi' },
  { label: '.mov', value: '.mov' },
  { label: '.mkv', value: '.mkv' },
  { label: '.flv', value: '.flv' },
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
