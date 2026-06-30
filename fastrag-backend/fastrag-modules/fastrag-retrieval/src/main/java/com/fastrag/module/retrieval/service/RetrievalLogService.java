package com.fastrag.module.retrieval.service;
import com.fastrag.common.response.PageResult; import com.fastrag.module.retrieval.entity.KbRetrievalLog; import java.util.*;
public interface RetrievalLogService {
    void log(KbRetrievalLog log);
    void update(KbRetrievalLog log);
    PageResult<KbRetrievalLog> page(String kbId,Boolean hasResult,int page,int pageSize);
    Map<String,Object> analysis(String kbId);
}
