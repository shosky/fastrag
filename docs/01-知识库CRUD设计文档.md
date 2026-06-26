# 知识库 CRUD 设计文档

## 1. 功能概述

知识库是 FastRAG 系统的核心数据组织单元，用于管理文档、切片、图谱等关联数据。

## 2. 数据模型

### 2.1 数据库表 `kb`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(32) PK | 知识库ID |
| name | VARCHAR(128) | 名称 |
| description | TEXT | 描述 |
| category | VARCHAR(64) | 分类 |
| tags | JSON | 标签数组 |
| embedding_model | VARCHAR(128) | 嵌入模型 |
| dimension | INT | 向量维度 |
| creator | VARCHAR(32) | 创建者ID |
| type | VARCHAR(16) | 类型(team/personal) |
| permission | VARCHAR(16) | 权限(private/team/public) |
| used_size | BIGINT | 已用空间(bytes) |
| total_size | BIGINT | 总空间(bytes) |
| retrieval_config | JSON | 检索配置 |
| file_type_config | JSON | 文件类型配置 |
| parse_mode | VARCHAR(16) | 解析模式 |
| split_mode | VARCHAR(16) | 切分模式 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## 3. API 接口

### 3.1 获取知识库列表

```
GET /api/kb
```

**查询参数：**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | string | 否 | 搜索关键词(匹配name/description) |
| category | string | 否 | 分类筛选 |
| page | int | 否 | 页码(默认1) |
| pageSize | int | 否 | 每页数量(默认20) |

**响应：**
```json
{
  "code": 200,
  "data": {
    "list": [...],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```

### 3.2 获取分类统计

```
GET /api/kb/categories
```

**响应：**
```json
{
  "code": 200,
  "data": [
    {"id": "技术文档", "name": "技术文档", "count": 5},
    {"id": "产品说明", "name": "产品说明", "count": 3}
  ]
}
```

### 3.3 获取知识库详情

```
GET /api/kb/{id}
```

### 3.4 创建知识库

```
POST /api/kb
```

**请求体：**
```json
{
  "name": "知识库名称",
  "category": "技术文档",
  "description": "描述",
  "permission": "private",
  "embeddingModel": "bge-m3",
  "parseMode": "auto",
  "splitMode": "auto",
  "tags": ["标签1", "标签2"]
}
```

### 3.5 更新知识库

```
PUT /api/kb/{id}
```

### 3.6 删除知识库

```
DELETE /api/kb/{id}
```

## 4. 前端页面

- **列表页** `/knowledge` — 展示所有知识库，支持搜索、分类筛选
- **创建页** `/knowledge/create` — 创建表单
- **编辑页** `/knowledge/:id/edit` — 编辑表单
- **详情页** `/knowledge/:id` — 知识库详情，包含文件管理、检索测试等Tab

## 5. 当前状态

**后端：** ✅ 完整实现
**前端：** ✅ 已对接 API
