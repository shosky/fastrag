package com.fastrag.infra.neo4j;

/**
 * Neo4j 图数据库驱动配置类，负责创建和管理 Neo4j Java Driver 实例。
 *
 * <p>配置的组件：Neo4j {@link org.neo4j.driver.Driver} Bean，作为 Neo4j 数据库的连接驱动。
 *
 * <p>装配语义（ADR-0002）：Neo4j 是知识图谱构建/检索链路的必需依赖，本 Bean 无条件创建，
 * 且在启动时通过 {@code verifyConnectivity()} 校验连通性——Neo4j 不可达时应用启动失败（fail-fast），
 * 错误信息给出可操作的修复指引，替代原先「静默降级 MySQL 图存储」的隐式行为。
 *
 * <p>配置参数（来自 application.yml 或环境变量）：
 * <ul>
 *   <li>{@code neo4j.uri} — Neo4j 连接地址，默认 {@code bolt://localhost:7687}</li>
 *   <li>{@code neo4j.user} — 用户名，默认 {@code neo4j}</li>
 *   <li>{@code neo4j.password} — 密码，默认为空（无密码时使用 {@link GraphDatabase#driver(String)} 直连）</li>
 * </ul>
 *
 * <p>与其他模块的交互：产生的 Driver Bean 被 {@link Neo4jGraphStore} 注入使用。
 */
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${neo4j.user:neo4j}")
    private String user;

    @Value("${neo4j.password:}")
    private String password;

    @Bean
    public Driver neo4jDriver() {
        Driver driver = (password == null || password.isBlank())
                ? GraphDatabase.driver(uri)
                : GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        try {
            // fail-fast：启动即校验连通性，Neo4j 不可达时不允许应用带病运行
            driver.session(SessionConfig.forDatabase("system")).close();
        } catch (Exception e) {
            driver.close();
            throw new IllegalStateException(
                    "Neo4j 连接失败（" + uri + "）：" + e.getMessage()
                            + "。知识图谱为必需依赖（ADR-0002），请启动 docker-compose 中的 fastrag-neo4j 服务"
                            + "或检查 neo4j.uri/user/password 配置后重启。",
                    e);
        }
        return driver;
    }
}
