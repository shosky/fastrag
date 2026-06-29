package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbEntity; import java.util.*;
public interface EntityService {
    List<KbEntity> list(String kbId,String keyword);
    KbEntity get(String id);
    KbEntity create(KbEntity entity);
    KbEntity update(String id,KbEntity entity);
    void delete(String id);
}
