# API 通用规范

## 1. 认证方式

所有接口（除 `/api/auth/login` 外）需要在请求头中携带 JWT Token：

```
Authorization: Bearer <token>
```

Token 通过登录接口获取，有效期 24 小时。

## 2. 统一响应格式

```json
{
  "code": 200,
  "data": {},
  "message": "success"
}
```

### 2.1 成功响应

| code | 说明 |
|------|------|
| 200 | 成功 |

### 2.2 错误响应

| code | 说明 |
|------|------|
| 400 | 参数错误 |
| 401 | 未授权（Token 缺失或无效） |
| 403 | 禁止访问（权限不足） |
| 404 | 资源不存在 |
| 500 | 服务异常 |

### 2.3 分页响应

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

## 3. 分页参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码 |
| pageSize | int | 20 | 每页数量 |
| keyword | string | - | 搜索关键词 |

## 4. 错误处理

- 业务异常：返回 `code` + `message`
- 参数校验异常：返回字段级错误信息
- 未捕获异常：返回 `code=500, message="服务异常"`

## 5. CORS 配置

- 允许来源：`*`
- 允许方法：`GET, POST, PUT, DELETE, OPTIONS`
- 允许头部：`*`
- 允许凭证：`true`
