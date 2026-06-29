package com.fastrag.module.knowledge.service;
import com.fastrag.common.response.PageResult; import com.fastrag.module.knowledge.entity.*; import java.util.*;
public interface SmartSearchService {
    PageResult<KbSearchAssociation> pageAssociations(String kbId,String dimension,String keyword,int page,int pageSize);
    List<KbSearchAssociation> listAssociations(String kbId,String dimension);
    KbSearchAssociation createAssociation(KbSearchAssociation association);
    KbSearchAssociation updateAssociation(String id,KbSearchAssociation association);
    void deleteAssociation(String id);
    Map<String,Object> search(String kbId,String q,String dimension,int limit);
    Map<String,Object> judge(String kbId,String dimension,String query,String targetText);
    Map<String,Object> autoCorrect(String kbId,String q);
    // 纠错规则
    List<KbAutoCorrection> listCorrections(String kbId);
    KbAutoCorrection createCorrection(KbAutoCorrection correction);
    KbAutoCorrection updateCorrection(String id,KbAutoCorrection correction);
    void deleteCorrection(String id);
    List<KbAutoCorrection> batchImportCorrections(String kbId,List<KbAutoCorrection> items);
}
