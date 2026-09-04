package com.fastrag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "com.fastrag",
        // fastrag-application 的旧 WorkflowController 与 fastrag-bpm/LegacyWorkflowCompatController
        // 在 /api/workflows/* 上有重复 mapping, 由 LegacyWorkflowCompatController 统一接管。
        // 旧 WorkflowService 仍可被其它 Bean 注入使用, 此处仅排除它的 @RestController 注册。
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.fastrag\\.module\\.application\\.controller\\.WorkflowController"))
@MapperScan("com.fastrag.module.*.mapper")
@EnableAsync
@EnableScheduling
public class FastRagApplication {
    public static void main(String[] args) {
        SpringApplication.run(FastRagApplication.class, args);
    }
}
