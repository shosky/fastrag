# fastrag-platform -- 平台基础配置与模型管理模块

平台基础模块，负责系统配置管理、模型全生命周期、敏感词过滤、数据字典及术语库维护等核心平台能力。

## 模块职责

- 管理系统配置项（SysConfig）及其变更历史（SysConfigHistory），支持发布策略与安全策略的读写
- 模型注册、训练、测试、导出全生命周期管理（ModelRecord / ModelTraining / ModelTestReport / ModelCallLog）
- 敏感词库的增删改查与实时过滤（SensitiveWord）
- 数据字典维护，为系统下拉框等提供统一数据源（SysDictionary）
- 查询规则管理，支持 RAG 检索阶段的查询改写与扩展（QueryRule）
- 术语库与术语记录管理，保障领域术语一致性（TermLibrary / TermRecord）
- 系统通知的发布与查询（SysNotification）
- 文件上传通用端点

## 对外 REST 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/models/{id}/train` | 触发模型训练 |
| POST | `/api/models/{id}/test` | 触发模型测试 |
| GET  | `/api/models/{id}/trainings` | 查询模型训练记录 |
| GET  | `/api/models/{id}/test-reports` | 查询模型测试报告 |
| GET  | `/api/publish-strategy` | 获取发布策略 |
| PUT  | `/api/publish-strategy` | 更新发布策略 |
| GET  | `/api/security-policy` | 获取安全策略 |
| PUT  | `/api/security-policy` | 更新安全策略 |
| GET  | `/api/models` | 模型列表/分页查询 |
| POST | `/api/models` | 新增模型 |
| PUT  | `/api/models/{id}` | 更新模型 |
| DELETE | `/api/models/{id}` | 删除模型 |
| GET  | `/api/dictionaries` | 字典列表 |
| POST | `/api/dictionaries` | 新增字典 |
| PUT  | `/api/dictionaries/{id}` | 更新字典 |
| DELETE | `/api/dictionaries/{id}` | 删除字典 |
| GET  | `/api/sensitive-words` | 敏感词列表 |
| POST | `/api/sensitive-words` | 新增敏感词 |
| PUT  | `/api/sensitive-words/{id}` | 更新敏感词 |
| DELETE | `/api/sensitive-words/{id}` | 删除敏感词 |
| GET  | `/api/query-rules` | 查询规则列表 |
| POST | `/api/query-rules` | 新增查询规则 |
| PUT  | `/api/query-rules/{id}` | 更新查询规则 |
| DELETE | `/api/query-rules/{id}` | 删除查询规则 |
| GET  | `/api/terms/libraries` | 术语库列表 |
| POST | `/api/terms/libraries` | 新增术语库 |
| GET  | `/api/terms/records` | 术语记录列表 |
| POST | `/api/terms/records` | 新增术语记录 |
| GET  | `/api/notifications` | 通知列表 |
| POST | `/api/upload` | 通用文件上传 |

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | fastrag-common | ApiResponse、@Loggable 注解、ActionType/LogCategory 枚举 |
| 依赖 | fastrag-security | 认证鉴权拦截、权限校验 |
| 依赖 | fastrag-infra | 文件存储、消息队列等基础设施 |
| 依赖 | fastrag-ai | 模型训练/测试/调用等 AI 能力封装 |
| 被依赖 | fastrag-operation | 操作审计日志依赖平台事件 |
| 被依赖 | fastrag-publish | 发布模块读取发布策略 |
| 被依赖 | fastrag-knowledge | 知识库模块使用模型与查询规则 |

## 关键类说明

### 控制器

| 类名 | 路径前缀 | 职责 |
|------|----------|------|
| ConfigManageController | `/api` | 模型训练/测试、发布策略、安全策略的统一管理 |
| ModelController | `/api/models` | 模型 CRUD 全生命周期 |
| DictionaryController | `/api/dictionaries` | 数据字典管理 |
| SensitiveWordController | `/api/sensitive-words` | 敏感词管理 |
| QueryRuleController | `/api/query-rules` | 查询规则管理 |
| TermController | `/api/terms` | 术语库与术语记录管理 |
| NotificationController | `/api/notifications` | 系统通知管理 |
| UploadController | `/api/upload` | 通用文件上传 |

### 实体

| 类名 | 说明 |
|------|------|
| ModelRecord | 模型注册信息 |
| ModelTraining | 模型训练记录 |
| ModelTestReport | 模型测试报告 |
| ModelCallLog | 模型调用日志 |
| SensitiveWord | 敏感词 |
| SysDictionary | 数据字典 |
| SysConfig | 系统配置 |
| SysConfigHistory | 配置变更历史 |
| SysPublishStrategy | 发布策略 |
| SysSecurityPolicy | 安全策略 |
| SysNotification | 系统通知 |
| QueryRule | 查询规则 |
| TermLibrary | 术语库 |
| TermRecord | 术语记录 |

### 服务层

| 接口 | 实现类 | 职责 |
|------|--------|------|
| ConfigManageService | ConfigManageServiceImpl | 训练/测试触发、策略读写 |
| ModelService | ModelServiceImpl | 模型 CRUD |
| DictionaryService | DictionaryServiceImpl | 字典 CRUD |
| SensitiveWordService | SensitiveWordServiceImpl | 敏感词 CRUD 与过滤 |
| QueryRuleService | QueryRuleServiceImpl | 查询规则 CRUD |
| TermService | TermServiceImpl | 术语库/记录 CRUD |
