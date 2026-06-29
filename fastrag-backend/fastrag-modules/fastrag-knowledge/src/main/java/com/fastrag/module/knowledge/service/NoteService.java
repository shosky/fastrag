package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbNote; import java.util.*;
public interface NoteService {
    List<KbNote> list(String kbId,String keyword);
    KbNote get(String id);
    KbNote create(KbNote note);
    KbNote update(String id,KbNote note);
    void delete(String id);
    Map<String,Object> export(String kbId,String ids);
}
