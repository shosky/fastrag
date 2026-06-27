package com.fastrag.common.handler;

import java.util.Map;

/**
 * 知识图谱构建处理接口
 */
public interface GraphBuildHandler {
    void handleGraphBuild(Map<String, Object> message);
}
