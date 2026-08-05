import type { ApiGroup } from './types'

export const chunksGroup: ApiGroup = {
  key: 'chunks',
  name: '分片管理',
  icon: 'Files',
  endpoints: [
    {
      method: 'GET',
      path: '/api/kb/{kbId}/chunks',
      summary: '获取分片列表',
      description: '获取知识库下的分片列表，支持按文件过滤和分页。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
      ],
      queryParams: [
        { name: 'fileId', type: 'string', required: false, description: '按文件 ID 过滤' },
        { name: 'keyword', type: 'string', required: false, description: '搜索分片内容' },
        { name: 'page', type: 'number', required: false, description: '页码' },
        { name: 'pageSize', type: 'number', required: false, description: '每页条数' },
      ],
      responseExample: '{"code":200,"data":{"records":[{"id":"chunk_001","content":"这是一段知识库分片内容...","fileId":"file_001","fileName":"产品手册.pdf","pageNumber":1,"index":0}],"total":15}}',
    },
    {
      method: 'GET',
      path: '/api/kb/{kbId}/chunks/count',
      summary: '获取分片总数',
      description: '获取知识库的分片总数，用于统计和分页控制。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
      ],
      responseExample: '{"code":200,"data":156}',
    },
    {
      method: 'GET',
      path: '/api/kb/{kbId}/chunks/{id}',
      summary: '获取分片详情',
      description: '获取单个分片的详细内容。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'id', type: 'string', required: true, description: '分片 ID' },
      ],
      responseExample: '{"code":200,"data":{"id":"chunk_001","content":"完整分片内容...","fileId":"file_001","pageNumber":1,"index":0,"embedding":["0.12","-0.34",...]}}',
    },
    {
      method: 'POST',
      path: '/api/kb/{kbId}/chunks',
      summary: '手动创建分片',
      description: '手动创建分片，用于程序化导入知识内容。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
      ],
      body: [
        { name: 'fileId', type: 'string', required: true, description: '关联的文件 ID' },
        { name: 'content', type: 'string', required: true, description: '分片文本内容' },
        { name: 'chunkType', type: 'string', required: false, description: '分片类型：text（默认）', example: 'text' },
        { name: 'pageNumber', type: 'number', required: false, description: '页码' },
      ],
      requestExample: '{"fileId":"file_001","content":"这是手动创建的知识分片内容","chunkType":"text","pageNumber":1}',
      responseExample: '{"code":200,"data":{"id":"chunk_099","content":"这是手动创建的知识分片内容"}}',
    },
    {
      method: 'PUT',
      path: '/api/kb/{kbId}/chunks/{id}',
      summary: '更新分片',
      description: '更新分片的文本内容。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'id', type: 'string', required: true, description: '分片 ID' },
      ],
      body: [
        { name: 'content', type: 'string', required: true, description: '更新后的分片内容' },
      ],
      requestExample: '{"content":"更新后的分片内容"}',
      responseExample: '{"code":200,"message":"更新成功"}',
    },
    {
      method: 'DELETE',
      path: '/api/kb/{kbId}/chunks/{id}',
      summary: '删除分片',
      description: '删除单个分片。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'id', type: 'string', required: true, description: '分片 ID' },
      ],
      responseExample: '{"code":200,"message":"删除成功"}',
    },
    {
      method: 'POST',
      path: '/api/kb/{kbId}/chunks/batch-delete',
      summary: '批量删除分片',
      description: '批量删除多个分片。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
      ],
      body: [
        { name: 'ids', type: 'string[]', required: true, description: '要删除的分片 ID 数组' },
      ],
      requestExample: '["chunk_001","chunk_002","chunk_003"]',
      responseExample: '{"code":200,"message":"成功删除 3 个分片"}',
    },
  ],
}
