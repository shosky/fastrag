/** 通用 API 响应结构 */
interface ApiResponse<T = unknown> {
  code: number
  data: T
  message: string
}

/** 分页请求参数 */
interface PageParams {
  page: number
  pageSize: number
}

/** 分页响应 */
interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/** 通用筛选参数 */
interface FilterParams {
  keyword?: string
  [key: string]: unknown
}
