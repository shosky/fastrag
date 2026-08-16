package com.fastrag.module.retrieval.service;

/**
 * 检索日志服务接口。
 *
 * <p>定义检索日志的记录、更新、分页查询和统计分析能力。
 * 日志按组织隔离，仅返回当前用户有权访问的知识库日志，平台级 API Token 不过滤。</p>
 *
 * @see KbRetrievalLog
 */
import com.fastrag.common.response.PageResult; import com.fastrag.module.retrieval.entity.KbRetrievalLog; import java.util.*;
public interface RetrievalLogService {
    void log(KbRetrievalLog log);
    void update(KbRetrievalLog log);
    PageResult<KbRetrievalLog> page(String kbId,Boolean hasResult,int page,int pageSize);
    Map<String,Object> analysis(String kbId);
}
