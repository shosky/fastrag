package com.fastrag.module.bpm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** BPM 异步执行器池(异步队列启用时使用;M3 默认同步执行) */
@Configuration
public class BpmExecutorConfig {
    @Bean("bpmExecutor")
    public ExecutorService bpmExecutor(BpmProperties props) {
        BpmProperties.Executor cfg = props.getExecutor();
        return new ThreadPoolExecutor(
                cfg.getPoolSize(), cfg.getPoolSize(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(cfg.getQueueCapacity()),
                r -> {
                    Thread t = new Thread(r, "bpm-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}