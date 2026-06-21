/** 知识库类型 */
export const KB_TYPES = ['全部', '团队', '个人'] as const

/** 模型用途 */
export const MODEL_PURPOSES = ['大语言模型', 'Embedding模型', 'Rerank模型', 'OCR识别'] as const

/** 应用类型 */
export const APP_TYPES = ['ChatBot 智能问答', 'Editor 写作助手', 'LiteAgent 智能体'] as const

/** 触发器类型 */
export const TRIGGER_TYPES = ['Webhook', '定时触发'] as const

/** 动作分类 */
export const ACTION_CATEGORIES = ['知识库', 'AI', '检索', '逻辑/工具', '通知'] as const

/** 模型品牌列表 */
export const MODEL_BRANDS = [
  '火山引擎', '通义千问', 'DeepSeek', '月之暗面', '智谱AI',
  '零一万物', '百川智能', 'MiniMax', '开源中国', '文心一言',
] as const

/** 权限模式 */
export const PERMISSION_MODES = ['成员可见', '公开可见'] as const

/** 文件上传限制 */
export const UPLOAD_CONFIG = {
  maxSize: 1 * 1024 * 1024, // 1MB
  acceptTypes: ['image/jpeg', 'image/png', 'image/jpg'],
  excelTypes: ['.xls', '.xlsx'],
  excelMaxSize: 50 * 1024 * 1024, // 50MB
}
