/** 用户信息 */
export interface UserInfo {
  id: string
  username: string
  realName: string
  avatar?: string
  phone?: string
  email?: string
  roles: string[]
  permissions: string[]
}

/** 登录参数 */
export interface LoginParams {
  username: string
  password: string
}

/** 登录响应 */
export interface LoginResult {
  token: string
  userInfo: UserInfo
}
