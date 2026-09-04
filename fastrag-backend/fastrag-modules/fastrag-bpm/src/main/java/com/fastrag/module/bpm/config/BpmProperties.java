package com.fastrag.module.bpm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** bpm.* 配置项(对应 application.yml) */
@Data @Component @ConfigurationProperties(prefix = "bpm")
public class BpmProperties {
    private Executor executor = new Executor();
    private Scheduler scheduler = new Scheduler();
    private Instance instance = new Instance();
    private Spel spel = new Spel();
    private Async async = new Async();

    @Data public static class Executor {
        private int poolSize = 8;
        private int queueCapacity = 200;
        private String streamName = "bpm:stream:instance";
        private String consumerGroup = "bpm-workers";
        private long pendingTimeoutMs = 60000;
    }
    @Data public static class Scheduler {
        private String timeoutScanCron = "0/30 * * * * *";
        private boolean enabled = true;
    }
    @Data public static class Instance {
        private int maxDurationHours = 24;
        private int maxRunning = 200;
        private int maxNodesPerFlow = 50;
        private int maxEdgesPerFlow = 200;
        private int logRetentionDays = 180;
    }
    @Data public static class Spel {
        private boolean enableTypeAccess = false;
    }
    @Data public static class Async {
        private boolean enabled = false;
    }
}