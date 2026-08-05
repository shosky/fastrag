# fastrag-bootstrap -- 应用启动与 Schema 初始化模块

应用启动入口模块，负责 Spring Boot 应用启动、组件扫描、Mapper 扫描及数据库 Schema 自动初始化。

## 模块职责



- 作为整个 fastrag-backend 的 Spring Boot 启动入口（FastRagApplication）

- 通过 @ComponentScan("com.fastrag") 扫描所有业务模块的组件

- 通过 @MapperScan("com.fastrag.module.*.mapper") 扫描所有模块的 MyBatis Mapper 接口

- 通过 @EnableRabbit 启用 RabbitMQ 消息队列支持

- 通过 @EnableAsync 启用异步方法执行

- 通过 @EnableScheduling 启用定时任务调度



- 通过 SchemaInitializer 在应用启动完成后自动创建缺失的数据库表和字段

## 模块依赖

| 方向 | 模块 | 说明 |

|------|------|------|

| 依赖 | fastrag-common | 基础工具与通用组件 |

| 依赖 | fastrag-security | 安全配置与认证 |

| 依赖 | fastrag-infra | 基础设施（数据库、缓存、MQ） |

| 依赖 | fastrag-platform | 平台基础配置 |

| 依赖 | fastrag-knowledge | 知识库管理 |

| 依赖 | fastrag-ai | AI 能力封装 |

| 依赖 | fastrag-application | 应用管理 |

| 依赖 | fastrag-publish | 发布服务 |

| 依赖 | fastrag-operation | 运营监控 |

| 依赖 | fastrag-agent | 智能体 |



## 关键类说明

### 启动类

| 类名 | 说明 |

|------|------|

| FastRagApplication | @SpringBootApplication 启动类，配置组件扫描、Mapper 扫描及功能注解 |

### 配置类

| 类名 | 说明 |

|------|------|

| SchemaInitializer | @EventListener(ApplicationReadyEvent) 监听应用就绪事件，自动创建缺失的数据库表和字段 |



### 启动注解

| 注解 | 作用 |

|------|------|

| @SpringBootApplication | 标记为 Spring Boot 启动类 |

| @ComponentScan("com.fastrag") | 扫描 com.fastrag 下所有组件 |

| @MapperScan("com.fastrag.module.*.mapper") | 扫描所有模块的 MyBatis Mapper |

| @EnableRabbit | 启用 RabbitMQ |

| @EnableAsync | 启用异步执行 |

| @EnableScheduling | 启用定时任务 |