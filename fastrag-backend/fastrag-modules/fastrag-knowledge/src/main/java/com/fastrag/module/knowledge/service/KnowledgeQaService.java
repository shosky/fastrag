package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.*; import java.util.*;
public interface KnowledgeQaService {
    // 多轮问答
    List<KbMultiTurnQa> listMultiTurnQa(String kbId);
    KbMultiTurnQa createMultiTurnQa(KbMultiTurnQa qa);
    KbMultiTurnQa updateMultiTurnQa(String id,KbMultiTurnQa qa);
    void deleteMultiTurnQa(String id);
    KbMultiTurnQa getMultiTurnQa(String id);
    // 多模态问答
    List<KbMultimodalQa> listMultimodalQa(String kbId);
    KbMultimodalQa createMultimodalQa(KbMultimodalQa qa);
    KbMultimodalQa updateMultimodalQa(String id,KbMultimodalQa qa);
    void deleteMultimodalQa(String id);
    KbMultimodalQa getMultimodalQa(String id);
    // 文档导读
    List<KbDocGuide> listDocGuides(String kbId);
    KbDocGuide createDocGuide(KbDocGuide guide);
    KbDocGuide updateDocGuide(String id,KbDocGuide guide);
    void deleteDocGuide(String id);
    KbDocGuide getDocGuide(String id);
    KbDocGuide indexDocGuide(String id);
    // 多模态检索
    Map<String,Object> multimodalSearch(String kbId,String modality,String query,int topK);
    Map<String,Object> multimodalSort(String kbId,String modality,List<String> ids,String sortBy);
}
