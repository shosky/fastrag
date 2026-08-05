/** API 文档数据类型定义 */

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

/** API 参数定义 */
export interface ApiParam {
  /** 参数名称 */
  name: string
  /** 参数类型 */
  type: string
  /** 是否必填 */
  required: boolean
  /** 参数描述 */
  description: string
  /** 示例值 */
  example?: string
}

/** API 接口定义 */
export interface ApiEndpoint {
  /** HTTP 方法 */
  method: HttpMethod
  /** 接口路径模板，如 /api/kb/{kbId}/files */
  path: string
  /** 接口名称（简短） */
  summary: string
  /** 接口详细描述 */
  description: string
  /** 路径参数 */
  pathParams?: ApiParam[]
  /** Query 参数 */
  queryParams?: ApiParam[]
  /** 请求体参数（POST/PUT） */
  body?: ApiParam[]
  /** 响应示例 JSON */
  responseExample?: string
  /** 请求示例 JSON（POST/PUT） */
  requestExample?: string
  /** 是否支持在线调试（复杂请求如文件上传不支持） */
  debuggable?: boolean
}

/** API 分组 */
export interface ApiGroup {
  /** 分组标识 */
  key: string
  /** 分组名称 */
  name: string
  /** Element Plus 图标名称 */
  icon: string
  /** 分组内的接口列表 */
  endpoints: ApiEndpoint[]
}

/** API Token 信息 */
export interface ApiToken {
  id: string
  name: string
  /** Token 值（仅创建时返回） */
  token?: string
  /** 权限范围：read | write */
  permission: 'read' | 'write'
  /** 创建时间 */
  createdAt: string
  /** 过期时间 */
  expiresAt: string | null
  /** 是否已过期 */
  expired?: boolean
}
