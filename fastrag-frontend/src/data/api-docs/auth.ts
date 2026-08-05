import type { ApiGroup } from './types'

export const authGroup: ApiGroup = {
  key: 'auth',
  name: '认证与鉴权',
  icon: 'Lock',
  endpoints: [
    {
      method: 'POST',
      path: '/api/api-tokens',
      summary: '创建 API Token',
      description: '创建平台级 API Token，用于程序化访问所有知识库接口。可设置权限范围（read/write）和过期时间。',
      body: [
        { name: 'name', type: 'string', required: true, description: 'Token 名称，便于识别用途' },
        { name: 'permission', type: 'string', required: true, description: '权限范围：read（只读）或 write（读写）', example: 'read' },
        { name: 'expiresIn', type: 'number', required: false, description: '有效期（秒），86400=1天，null=永不过期', example: '86400' },
      ],
      requestExample: '{"name":"生产系统集成","permission":"read","expiresIn":86400}',
      responseExample: '{"id":"tok_abc123","token":"frag_3f2a8c...","name":"生产系统集成","permission":"read","createdAt":"2026-08-03T10:00:00Z","expiresAt":"2026-08-04T10:00:00Z"}',
    },
    {
      method: 'GET',
      path: '/api/api-tokens',
      summary: '获取 Token 列表',
      description: '查看平台下已创建的所有 API Token（token 值已脱敏不显示）。',
      responseExample: '{"records":[{"id":"tok_abc123","name":"生产系统集成","permission":"read","createdAt":"2026-08-03T10:00:00Z","expiresAt":"2026-08-04T10:00:00Z","expired":false}],"total":1}',
    },
    {
      method: 'DELETE',
      path: '/api/api-tokens/{tokenId}',
      summary: '撤销 Token',
      description: '撤销指定的 API Token，撤销后使用该 Token 的请求将返回 401 错误。',
      pathParams: [
        { name: 'tokenId', type: 'string', required: true, description: 'Token ID' },
      ],
      responseExample: '{"code":200,"message":"Token 已撤销"}',
    },
  ],
}
