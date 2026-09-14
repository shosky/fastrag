package com.fastrag.module.retrieval.service;
import com.fastrag.module.retrieval.entity.KbKnowledgePush; import java.util.*;
public interface KnowledgePushService {
    List<KbKnowledgePush> list(String kbId, String status);
    KbKnowledgePush get(String id);
    KbKnowledgePush create(String kbId, KbKnowledgePush push);
    KbKnowledgePush update(String id, KbKnowledgePush push);
    void delete(String id);
    // 发送推送：状态置 sent 并按目标用户写系统通知（无目标则全员通知）
    Map<String,Object> send(String id, String operator);
}
