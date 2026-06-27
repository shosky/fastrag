package com.fastrag.common.handler;

import java.util.Map;

/**
 * 文档摄入处理接口
 */
public interface IngestionHandler {
    void handleIngestion(Map<String, Object> message);
}
