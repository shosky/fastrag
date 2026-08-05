import type { ApiGroup } from './types'

export const documentsGroup: ApiGroup = {
  key: 'documents',
  name: '文档管理',
  icon: 'Document',
  endpoints: [
    {
      method: 'GET',
      path: '/api/kb/{kbId}/files',
      summary: '获取文件列表',
      description: '获取知识库下的文件列表，支持分页和按文件名搜索。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
      ],
      queryParams: [
        { name: 'keyword', type: 'string', required: false, description: '文件名搜索关键词' },
        { name: 'folderId', type: 'string', required: false, description: '按文件夹筛选' },
        { name: 'page', type: 'number', required: false, description: '页码' },
        { name: 'pageSize', type: 'number', required: false, description: '每页条数' },
      ],
      responseExample: '{"code":200,"data":{"records":[{"id":"file_001","name":"产品手册.pdf","size":2048000,"status":"completed","chunkCount":15,"createdAt":"2026-07-15T10:00:00Z"}],"total":1}}',
    },
    {
      method: 'POST',
      path: '/api/kb/{kbId}/files',
      summary: '上传文件',
      description: '上传文件到知识库。支持 PDF、Word、Excel、PPT、TXT、Markdown 等格式。请求需使用 multipart/form-data。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
      ],
      body: [
        { name: 'file', type: 'file', required: true, description: '要上传的文件（multipart/form-data）' },
        { name: 'folderId', type: 'string', required: false, description: '目标文件夹 ID' },
      ],
      debuggable: false,
      responseExample: '{"code":200,"data":{"id":"file_002","name":"新产品介绍.pdf","size":3072000,"status":"pending"}}',
    },
    {
      method: 'POST',
      path: '/api/kb/{kbId}/files/{id}/process',
      summary: '触发文件解析',
      description: '手动触发文件的解析处理，将文件拆分为知识库分片。可指定解析模式（chunk 逐段切分 / qa 问答对提取）。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'id', type: 'string', required: true, description: '文件 ID' },
      ],
      body: [
        { name: 'processingMode', type: 'string', required: false, description: '解析模式：chunk（默认）或 qa', example: 'chunk' },
      ],
      requestExample: '{"processingMode":"chunk"}',
      responseExample: '{"code":200,"message":"文件解析已启动"}',
    },
    {
      method: 'GET',
      path: '/api/kb/{kbId}/files/{id}/processing-status',
      summary: '查询文件解析状态',
      description: '查询文件当前的解析处理状态：pending（等待）、processing（处理中）、completed（完成）、failed（失败）。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'id', type: 'string', required: true, description: '文件 ID' },
      ],
      responseExample: '{"code":200,"data":{"status":"completed","chunkCount":15,"progress":100}}',
    },
    {
      method: 'GET',
      path: '/api/kb/{kbId}/files/{id}/preview',
      summary: '预览文件分片',
      description: '预览文件解析后的分片内容，用于查看解析效果。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'id', type: 'string', required: true, description: '文件 ID' },
      ],
      responseExample: '{"code":200,"data":[{"index":0,"content":"第一段内容...","page":1},{"index":1,"content":"第二段内容...","page":1}]}',
    },
    {
      method: 'GET',
      path: '/api/kb/{kbId}/files/{id}/download',
      summary: '下载文件',
      description: '下载文件的原始内容。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'id', type: 'string', required: true, description: '文件 ID' },
      ],
      debuggable: false,
      responseExample: '(二进制文件流，Content-Type 根据文件类型自动设置)',
    },
    {
      method: 'PUT',
      path: '/api/kb/{kbId}/files/{id}',
      summary: '更新文件',
      description: '更新文件信息（重命名、移动文件夹等）。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'id', type: 'string', required: true, description: '文件 ID' },
      ],
      body: [
        { name: 'name', type: 'string', required: false, description: '新文件名' },
        { name: 'folderId', type: 'string', required: false, description: '目标文件夹 ID' },
      ],
      requestExample: '{"name":"更新后的文件名.pdf"}',
      responseExample: '{"code":200,"message":"更新成功"}',
    },
    {
      method: 'DELETE',
      path: '/api/kb/{kbId}/files/{id}',
      summary: '删除文件（软删除）',
      description: '将文件移入回收站（软删除），可通过 restore 接口恢复。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'id', type: 'string', required: true, description: '文件 ID' },
      ],
      responseExample: '{"code":200,"message":"文件已移入回收站"}',
    },
    {
      method: 'POST',
      path: '/api/kb/{kbId}/files/{id}/restore',
      summary: '恢复文件',
      description: '从回收站恢复文件。',
      pathParams: [
        { name: 'kbId', type: 'string', required: true, description: '知识库 ID' },
        { name: 'id', type: 'string', required: true, description: '文件 ID' },
      ],
      responseExample: '{"code":200,"message":"文件已恢复"}',
    },
  ],
}
