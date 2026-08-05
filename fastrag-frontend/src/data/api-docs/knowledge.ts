import type { ApiGroup } from './types'

export const knowledgeGroup: ApiGroup = {
  key: 'knowledge',
  name: '知识库管理',
  icon: 'Notebook',
  endpoints: [
    {
      method: 'GET',
      path: '/api/kb',
      summary: '获取知识库列表',
      description: '获取当前用户可访问的知识库列表，支持按关键词、分类筛选和分页。',
      queryParams: [
        { name: 'keyword', type: 'string', required: false, description: '搜索关键词，匹配知识库名称' },
        { name: 'category', type: 'string', required: false, description: '按分类过滤' },
        { name: 'page', type: 'number', required: false, description: '页码，默认 1' },
        { name: 'pageSize', type: 'number', required: false, description: '每页条数，默认 10' },
      ],
      responseExample: '{"code":200,"data":{"records":[{"id":"kb_123","name":"产品文档库","description":"存放产品相关文档","embeddingModel":"text-embedding-v4","creator":"admin","createdAt":"2026-07-01T08:00:00Z"}],"total":5}}',
    },
    {
      method: 'GET',
      path: '/api/kb/{id}',
      summary: '获取知识库详情',
      description: '获取单个知识库的详细信息，包括检索配置、嵌入模型等。',
      pathParams: [
        { name: 'id', type: 'string', required: true, description: '知识库 ID' },
      ],
      responseExample: '{"code":200,"data":{"id":"kb_123","name":"产品文档库","description":"存放产品相关文档","embeddingModel":"text-embedding-v4","dimension":1024,"retrievalConfig":{"mode":"hybrid","topK":10}}}',
    },
    {
      method: 'POST',
      path: '/api/kb',
      summary: '创建知识库',
      description: '创建一个新的知识库，需指定名称、嵌入模型等配置。',
      body: [
        { name: 'name', type: 'string', required: true, description: '知识库名称' },
        { name: 'description', type: 'string', required: false, description: '知识库描述' },
        { name: 'embeddingModel', type: 'string', required: true, description: '嵌入模型名称', example: 'text-embedding-v4' },
        { name: 'dimension', type: 'number', required: false, description: '向量维度', example: '1024' },
      ],
      requestExample: '{"name":"新产品文档库","description":"新产品线的文档","embeddingModel":"text-embedding-v4","dimension":1024}',
      responseExample: '{"code":200,"data":{"id":"kb_456","name":"新产品文档库"}}',
    },
    {
      method: 'PUT',
      path: '/api/kb/{id}',
      summary: '更新知识库',
      description: '更新知识库的名称、描述或检索配置等。',
      pathParams: [
        { name: 'id', type: 'string', required: true, description: '知识库 ID' },
      ],
      body: [
        { name: 'name', type: 'string', required: false, description: '知识库名称' },
        { name: 'description', type: 'string', required: false, description: '知识库描述' },
        { name: 'retrievalConfig', type: 'object', required: false, description: '检索配置' },
      ],
      responseExample: '{"code":200,"message":"更新成功"}',
    },
    {
      method: 'DELETE',
      path: '/api/kb/{id}',
      summary: '删除知识库',
      description: '删除指定知识库及其所有文件和分片数据，此操作不可逆。',
      pathParams: [
        { name: 'id', type: 'string', required: true, description: '知识库 ID' },
      ],
      responseExample: '{"code":200,"message":"删除成功"}',
    },
    {
      method: 'GET',
      path: '/api/kb/categories',
      summary: '获取知识库分类列表',
      description: '获取所有可用的知识库分类。',
      responseExample: '{"code":200,"data":["产品","技术","运营"]}',
    },
    {
      method: 'GET',
      path: '/api/kb-tags',
      summary: '获取标签列表',
      description: '获取所有知识库标签，用于自动补全。',
      responseExample: '{"code":200,"data":["AI","RAG","向量检索","知识图谱"]}',
    },
  ],
}
