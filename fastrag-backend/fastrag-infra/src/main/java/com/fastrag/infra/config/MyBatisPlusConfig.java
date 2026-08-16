package com.fastrag.infra.config;

/**
 * MyBatis-Plus 配置类。
 *
 * <p>为 MyBatis-Plus 框架提供全局配置，主要包括：
 * <ul>
 *   <li>分页插件：注册 {@link PaginationInnerInterceptor}，使 MyBatis-Plus 的分页查询（Page 对象）在 MySQL 数据库下生效</li>
 *   <li>自动填充处理器：注册 {@link MetaObjectHandler}，在插入和更新时自动填充 createdAt / updatedAt 时间字段，
 *       对应实体类中使用 {@code @TableField(fill = FieldFill.INSERT)} 和
 *       {@code @TableField(fill = FieldFill.INSERT_UPDATE)} 注解的字段</li>
 * </ul>
 *
 * <p>生效方式：通过 {@code @Configuration} 注解被 Spring 自动扫描加载，
 * 产生的 Bean 会被 MyBatis-Plus 自动装配机制感知并应用。
 */
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;

@Configuration
public class MyBatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor i = new MybatisPlusInterceptor();
        i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return i;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
