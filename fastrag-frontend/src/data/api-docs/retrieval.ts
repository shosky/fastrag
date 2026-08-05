import type { ApiGroup } from './types'

export const retrievalGroup: ApiGroup = {
  key: 'retrieval',
  name: '检索服务',
  icon: 'Search',
  endpoints: [
    {
      method: 'POST',
      path: '/api/retrieval/search',
      summary: '检索知识库',
      description: '从知识库中检索与查询相关的分片内容，支持向量检索、BM25 检索、混合检索和图谱扩展检索。',
      body: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'query', type: 'string', required: true, description: '检索查询文本' },
        { name: 'mode', type: 'string', required: false, description: '检索模式：vector（向量）、bm25、hybrid（混合，默认）', example: 'hybrid' },
        { name: 'topK', type: 'number', required: false, description: '返回结果数量，默认 10', example: '5' },
        { name: 'similarityThreshold', type: 'number', required: false, description: '相似度阈值（0-1），低于此分数的结果将被过滤', example: '0.5' },
        { name: 'enableGraphExpand', type: 'boolean', required: false, description: '是否启用知识图谱扩展检索', example: 'true' },
      ],
      requestExample: '{"kbId":"kb_123","query":"如何配置向量检索?","mode":"hybrid","topK":5,"similarityThreshold":0.5,"enableGraphExpand":true}',
      responseExample: '{"code":200,"data":{"results":[{"chunkId":"chunk_001","content":"向量检索的配置方法...","score":0.92,"source":"检索指南.pdf","fileId":"file_789","pageNumber":1}]}}',
    },
  ],
}
