package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.model.*; import java.util.*;
public interface QaPairService {
    List<QaPairDto> list(String kbId,String faqType,String keyword);
    QaPairDto create(String kbId,QaCreateRequest req);
    QaPairDto update(String kbId,String id,Map<String,Object> patch);
    void delete(String kbId,String id);
    void confirm(String kbId,String id);
    /** 问答抽取全部入库：将该知识库下全部待确认(draft)问答对批量置为已入库(confirmed) */
    int confirmAll(String kbId);
    List<QaPairDto> extractQa(String kbId,List<String> fileIds);
    /** 按应答添加知识：将 QA 对答案落为知识条目 */
    QaPairDto toKnowledge(String kbId,String id);
}
