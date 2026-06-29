package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbQaExtractTask; import java.util.*;
public interface QaExtractService {
    List<KbQaExtractTask> list(String kbId,String status);
    KbQaExtractTask get(String id);
    KbQaExtractTask start(KbQaExtractTask task);
    KbQaExtractTask stop(String id);
    KbQaExtractTask update(String id,KbQaExtractTask task);
    void delete(String id);
}
