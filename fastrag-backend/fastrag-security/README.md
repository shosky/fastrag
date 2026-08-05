# fastrag-security

> 安全认证模块 — 提供 JWT 认证、知识库级 RBAC 权限控制、Redis 会话管理。

## 模块职责

- **JWT 认证**：签发/验证 Bearer Token，支持 Token 黑名单（Redis）
- **知识库 RBAC**：通过 `@KbAuth` 注解实现知识库级 owner > editor > viewer 权限层级
- **Security 过滤链**：配置无状态会话、公开端点白名单、JWT Filter 注入
- **上下文工具**：`SecurityUtil` 提供当前用户/权限的静态访问
- **Redis 配置**：提供 `StringRedisTemplate` Bean

## 对外 REST 端点

无。本模块不暴露任何 HTTP 端点。

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | fastrag-common | 使用 `KBRole` 枚举、`BusinessException` |
| 被依赖 | iam, knowledge, retrieval, graph-eval, application, tools, agent, platform, operation, bootstrap（10 个模块） |

## 关键类说明

### 认证过滤器

| 类 | 说明 |
|----|------|
| `JwtAuthFilter` | `OncePerRequestFilter`，从 `Authorization: Bearer xxx` 提取 Token，检查 Redis 黑名单，解析 JWT 并设置 `SecurityContext`。跳过 ASYNC dispatch（SSE 场景） |
| `LoginUser` | 认证主体 DTO，含 `userId`、`username`、`roles`、`permissions`，提供 `hasPermission()`/`hasAnyPermission()` 方法 |

### 安全配置

| 类 | 说明 |
|----|------|
| `SecurityConfig` | 配置 CSRF 禁用、无状态会话、公开端点白名单、JWT Filter 注入位置。提供 `BCryptPasswordEncoder` Bean |
| `RedisConfig` | 注册 `StringRedisTemplate` Bean |

### 知识库权限

| 类 | 说明 |
|----|------|
| `@KbAuth` | 方法级注解，声明所需最低 KB 角色（默认 viewer） |
| `KbAuthAspect` | AOP 切面，从 URI `/api/kb/{kbId}/...` 提取 kbId，查 Redis `kb:acl:{kbId}:{userId}` 获取用户角色，按层级校验 |

### 工具类

| 类 | 说明 |
|----|------|
| `JwtUtil` | JWT 令牌生成/解析/验证，使用 HMAC-SHA 签名 |
| `SecurityUtil` | 静态工具，获取当前 `LoginUser`、`userId`、检查权限 |

## 公开端点白名单

```
/api/auth/login
/api/auth/send-code
/api/auth/register
/api/auth/reset-password
/api/auth/wechat/*
/swagger-ui/**
/v3/api-docs/**
/actuator/**
```