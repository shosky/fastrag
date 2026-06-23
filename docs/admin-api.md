# 管理模块接口文档

## 目录
1. [敏感词管理](#1-敏感词管理)
2. [团队管理](#2-团队管理)
3. [字典管理](#3-字典管理)
4. [已有接口对接清单](#4-已有接口对接清单)

---

## 1. 敏感词管理

**基础路径**: `/api/sensitive-words`

### 1.1 获取敏感词列表

```
GET /api/sensitive-words
```

**请求头**: `Authorization: Bearer <token>`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": "1",
      "word": "敏感词",
      "reply": "该内容涉及敏感词",
      "blockInput": true,
      "blockSearch": false,
      "replaceAnswer": false
    }
  ],
  "message": "success"
}
```

### 1.2 创建敏感词

```
POST /api/sensitive-words
```

**请求体**:
```json
{
  "word": "敏感词",
  "reply": "指定回复内容",
  "blockInput": true,
  "blockSearch": false,
  "replaceAnswer": false
}
```

**响应**:
```json
{
  "code": 200,
  "data": null,
  "message": "success"
}
```

### 1.3 更新敏感词

```
PUT /api/sensitive-words/{id}
```

**请求体**: 同创建

**响应**:
```json
{
  "code": 200,
  "data": null,
  "message": "success"
}
```

### 1.4 删除敏感词

```
DELETE /api/sensitive-words/{id}
```

**响应**:
```json
{
  "code": 200,
  "data": null,
  "message": "success"
}
```

---

## 2. 团队管理

**基础路径**: `/api/teams`

### 2.1 获取团队列表

```
GET /api/teams
```

**请求头**: `Authorization: Bearer <token>`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": "team_001",
      "name": "项目管理团队",
      "description": "负责项目管理",
      "memberCount": 8
    }
  ],
  "message": "success"
}
```

### 2.2 获取团队详情

```
GET /api/teams/{id}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "id": "team_001",
    "name": "项目管理团队",
    "description": "负责项目管理",
    "createdAt": "2026-06-22T10:00:00"
  },
  "message": "success"
}
```

### 2.3 创建团队

```
POST /api/teams
```

**请求体**:
```json
{
  "name": "团队名称",
  "description": "团队描述"
}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "id": "team_002",
    "name": "团队名称",
    "description": "团队描述"
  },
  "message": "success"
}
```

### 2.4 更新团队

```
PUT /api/teams/{id}
```

**请求体**:
```json
{
  "name": "新名称",
  "description": "新描述"
}
```

### 2.5 删除团队

```
DELETE /api/teams/{id}
```

**说明**: 同时删除团队成员关联

### 2.6 获取团队成员

```
GET /api/teams/{id}/members
```

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": "user_001",
      "name": "张三",
      "username": "zhangsan"
    }
  ],
  "message": "success"
}
```

### 2.7 添加团队成员

```
POST /api/teams/{id}/members
```

**请求体**:
```json
{
  "userId": "user_001"
}
```

### 2.8 移除团队成员

```
DELETE /api/teams/{id}/members/{userId}
```

---

## 3. 字典管理

**基础路径**: `/api/dictionaries`

### 3.1 获取字典列表（按类型分组）

```
GET /api/dictionaries
```

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 否 | 按类型筛选 |

**响应**:
```json
{
  "code": 200,
  "data": {
    "系统信息": [
      {
        "id": "1",
        "key": "system_name",
        "label": "system_name",
        "value": "AIS 智能知识服务平台",
        "enabled": true,
        "remark": ""
      }
    ],
    "业务配置": [
      {
        "id": "2",
        "key": "default_model",
        "label": "default_model",
        "value": "qwen3-32b",
        "enabled": true,
        "remark": ""
      }
    ]
  },
  "message": "success"
}
```

### 3.2 获取字典类型列表

```
GET /api/dictionaries/types
```

**响应**:
```json
{
  "code": 200,
  "data": ["系统信息", "业务配置", "登录安全"],
  "message": "success"
}
```

### 3.3 创建字典条目

```
POST /api/dictionaries
```

**请求体**:
```json
{
  "type": "系统信息",
  "key": "system_name",
  "value": "AIS 智能知识服务平台"
}
```

### 3.4 更新字典条目

```
PUT /api/dictionaries/{id}
```

**请求体**:
```json
{
  "type": "系统信息",
  "key": "system_name",
  "value": "新名称"
}
```

### 3.5 删除字典条目

```
DELETE /api/dictionaries/{id}
```

---

## 4. 已有接口对接清单

### 4.1 认证模块

| 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|
| 登录 | POST | `/api/auth/login` | ✅ 已对接 |
| 获取用户信息 | GET | `/api/auth/userinfo` | ✅ 已对接 |
| 登出 | POST | `/api/auth/logout` | ✅ 已对接 |

### 4.2 知识库模块

| 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|
| 知识库列表 | GET | `/api/kb` | ✅ 已对接 |
| 知识库分类 | GET | `/api/kb/categories` | ✅ 已对接 |
| 知识库详情 | GET | `/api/kb/{id}` | ✅ 已对接 |
| 创建知识库 | POST | `/api/kb` | ✅ 已对接 |
| 更新知识库 | PUT | `/api/kb/{id}` | ✅ 已对接 |
| 删除知识库 | DELETE | `/api/kb/{id}` | ✅ 已对接 |
| 文件列表 | GET | `/api/kb/{kbId}/files` | ✅ 已对接 |
| 上传文件 | POST | `/api/kb/{kbId}/files` | ✅ 已对接 |
| Chunks列表 | GET | `/api/kb/{kbId}/chunks` | ✅ 已对接 |
| QA对列表 | GET | `/api/kb/{kbId}/qa-pairs` | ✅ 已对接 |
| 解析策略 | GET | `/api/kb/{kbId}/parse-strategies` | ✅ 已对接 |

### 4.3 检索模块

| 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|
| 检索搜索 | POST | `/api/retrieval/search` | ✅ 已对接 |
| 查询建议 | POST | `/api/query/suggest` | ✅ 已对接 |
| 同义词扩展 | POST | `/api/query/expand-synonyms` | ✅ 已对接 |
| 图谱扩展 | POST | `/api/graph/expand` | ✅ 已对接 |

### 4.4 应用模块

| 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|
| 应用列表 | GET | `/api/apps` | ✅ 已对接 |
| 应用详情 | GET | `/api/apps/{id}` | ✅ 已对接 |
| 创建应用 | POST | `/api/apps` | ✅ 已对接 |
| 删除应用 | DELETE | `/api/apps/{id}` | ✅ 已对接 |
| 应用模板 | GET | `/api/apps/templates` | ✅ 已对接 |

### 4.5 工具/技能/MCP

| 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|
| 工具列表 | GET | `/api/tools` | ✅ 已对接 |
| 技能列表 | GET | `/api/skills` | ✅ 已对接 |
| MCP服务列表 | GET | `/api/mcp-services` | ✅ 已对接 |

### 4.6 平台配置

| 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|
| 模型列表 | GET | `/api/models` | ✅ 已对接 |
| 查询规则 | GET | `/api/query-rules` | ✅ 已对接 |
| 术语库 | GET | `/api/terminology/libraries` | ✅ 已对接 |
| 敏感词 | GET | `/api/sensitive-words` | ✅ 已对接 |
| 字典 | GET | `/api/dictionaries` | ✅ 已对接 |

### 4.7 账号权限

| 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|
| 角色列表 | GET | `/api/roles` | ✅ 已对接 |
| 组织树 | GET | `/api/org/tree` | ✅ 已对接 |
| 人员列表 | GET | `/api/personnel` | ✅ 已对接 |
| 团队列表 | GET | `/api/teams` | ✅ 已对接 |

### 4.8 运营分析

| 接口 | 方法 | 路径 | 状态 |
|------|------|------|------|
| 首页数据 | GET | `/api/home` | ✅ 已对接 |
| 知识资产分析 | GET | `/api/analytics/kb` | ✅ 已对接 |
| 审计日志 | GET | `/api/audit/system-log` | ✅ 已对接 |
| 登录日志 | GET | `/api/audit/login-log` | ✅ 已对接 |
| 审核列表 | GET | `/api/reviews` | ✅ 已对接 |

---

## 5. 通用说明

### 5.1 认证方式
所有接口（除登录外）需要在请求头中携带：
```
Authorization: Bearer <token>
```

### 5.2 响应格式
```json
{
  "code": 200,       // 200成功，400参数错误，401未授权，500服务异常
  "data": {},        // 响应数据
  "message": "success" // 错误信息
}
```

### 5.3 分页参数
```
?page=1&pageSize=20
```

### 5.4 分页响应
```json
{
  "code": 200,
  "data": {
    "list": [],
    "total": 100,
    "page": 1,
    "pageSize": 20
  }
}
```
