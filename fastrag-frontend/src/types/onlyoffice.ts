/**
 * OnlyOffice Document Server 集成相关类型定义。
 *
 * <p>完整定义见 FastRAG 后端 {@code OnlyOfficeController} 与 OnlyOffice 官方 API 文档。
 * 前端只关心 config 对象的字段形状，以及事件回调 payload 的最小可用子集。
 */

/** OnlyOffice 文档类型（documentType 字段） */
export type OnlyOfficeDocumentType = 'word' | 'cell' | 'slide'

/** OnlyOffice fileType 字段（OO 文档类型识别） */
export type OnlyOfficeFileType = 'doc' | 'docx' | 'xls' | 'xlsx' | 'ppt' | 'pptx'

/** document 对象：OO 配置里的文件描述 */
export interface OnlyOfficeDocument {
  /** 文件类型，必须严格按 OO 约定（如 'docx'） */
  fileType: OnlyOfficeFileType
  /** 文档唯一 key；后端基于 fileId + updatedAt 哈希生成，文件变更时强制 reload */
  key: string
  /** 文档显示名 */
  title: string
  /** 文档直链（指向 FastRAG 的 /onlyoffice/raw 接口） */
  url: string
  /** 权限：edit 决定编辑器是 view 还是 edit 模式 */
  permissions: {
    edit: boolean
    download?: boolean
    print?: boolean
    copy?: boolean
  }
}

/** editorConfig：编辑器行为与用户信息 */
export interface OnlyOfficeEditorConfig {
  /** FastRAG 后端的回调地址（status=2 时 OO POST 新文件 URL） */
  callbackUrl: string
  /** 'edit' 编辑 / 'view' 只读；viewer 角色会传 view */
  mode: 'edit' | 'view'
  /** 当前编辑用户（OO 用于 co-authoring 显示） */
  user: {
    id: string
    name: string
  }
  /** 界面语言 */
  lang?: string
  /** 个性化配置 */
  customization?: {
    autosave?: boolean
    /** 强制保存（自动触发 callback.url） */
    forcesave?: boolean
    compactHeader?: boolean
    toolbar?: boolean
    [key: string]: unknown
  }
  /** 插件：autostart 为插件 GUID，pluginsData 为 plugin.json 地址（7.4 文档编辑器外层无选区能力，靠插件桥捕获） */
  plugins?: {
    autostart?: string[]
    /** plugin.json 完整 URL 列表 */
    pluginsData?: string[]
  }
}

/** 完整 OO editor config（直接喂给 DocsAPI.DocEditor） */
export interface OnlyOfficeEditorConfigResponse {
  document: OnlyOfficeDocument
  editorConfig: OnlyOfficeEditorConfig
  documentType: OnlyOfficeDocumentType
  /** OO 配置签名 token（OO Document Server 会校验） */
  token: string
  width?: string | number
  height?: string | number
}

/**
 * 选区变化事件 payload。
 * OO 在 spreadsheet 中返回 sheet+cellRange，word 中返回 paragraphIndex+text。
 * 这里只关心是否有选区与选区文本（通过 {@code getSelectedText()} 异步获取）。
 */
export interface OnlyOfficeSelectionChangedEvent {
  /** sheet 名（cell 才有） */
  sheet?: string
  /** cell 范围（cell 才有） */
  range?: string
  /** 段落索引（word/slide 才有） */
  paragraphIndex?: number
}

/** 全局 OO 文档状态 */
export interface OnlyOfficeDocumentState {
  /** 文档是否处于已修改未保存状态 */
  isModified: boolean
}

/** OO 公共回调事件映射 */
export interface OnlyOfficeEvents {
  onAppReady?: () => void
  onDocumentReady?: () => void
  /** 文档状态变更（修改/未修改） */
  onDocumentStateChange?: (event: OnlyOfficeDocumentState) => void
  /** 选区变化 */
  onSelectionChanged?: (event: OnlyOfficeSelectionChangedEvent) => void
  /** 错误 */
  onError?: (event: { errorCode: number; errorDescription: string }) => void
  /** 文档保存开始（OO 在调用 callbackUrl 前） */
  onSave?: () => void
}

/** 全局 DocsAPI 类型声明（OO 注入 window.DocsAPI） */
export interface OnlyOfficeDocsAPI {
  DocEditor: new (placeholderId: string, config: OnlyOfficeEditorConfigResponse) => OnlyOfficeDocEditorInstance
}

/** DocEditor 实例方法 */
export interface OnlyOfficeDocEditorInstance {
  destroyEditor(): void
  /** 获取当前选区文本（OO 7.0+ 支持） */
  getSelectedText?(): Promise<string>
  /** 滚动到指定页码（OO 7.0+） */
  scrollToPage?(pageNumber: number): void
  /** 设置可见区域（OO 7.0+） */
  setVisibleArea?(rect: { x: number; y: number; width: number; height: number }): void
  /** 高亮文本（OO 7.4+ 内部 API，不保证稳定） */
  SearchAndHighlight?(text: string): void
}

declare global {
  interface Window {
    DocsAPI?: OnlyOfficeDocsAPI
    Asc?: {
      plugin?: {
        editor?: unknown
        event?: {
          register(name: string, handler: (...args: any[]) => void): void
        }
        info?: {
          fileKey: string
          [key: string]: unknown
        }
      }
    }
  }
}

/**
 * FastRAG 后端 OnlyOfficeController 配置接口（与 ApiResponse 解包后的 data 形状对应）。
 */
export type OnlyOfficeConfig = OnlyOfficeEditorConfigResponse
