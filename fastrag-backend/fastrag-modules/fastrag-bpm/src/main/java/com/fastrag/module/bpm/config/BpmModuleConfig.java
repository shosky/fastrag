package com.fastrag.module.bpm.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** BPM 模块扫描配置 */
@Configuration @EnableScheduling @ComponentScan(basePackages = "com.fastrag.module.bpm")
public class BpmModuleConfig {}