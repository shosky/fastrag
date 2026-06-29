package com.fastrag.module.retrieval.service;
import com.fastrag.module.retrieval.entity.KbUpdateRemind; import java.util.*;
public interface UpdateRemindService {
    List<KbUpdateRemind> list(String kbId);
    KbUpdateRemind get(String kbId);
    KbUpdateRemind save(KbUpdateRemind remind);
    void delete(String id);
    Map<String,Object> remind(String kbId);
}
