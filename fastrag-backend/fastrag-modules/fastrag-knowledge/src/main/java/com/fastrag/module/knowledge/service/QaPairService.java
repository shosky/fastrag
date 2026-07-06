package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.model.*; import java.util.*;
public interface QaPairService { 
    List<QaPairDto> list(String kbId); 
    List<QaPairDto> list(String kbId, String fileId); 
    QaPairDto create(String kbId, QaCreateRequest req); 
    QaPairDto update(String kbId, String id, Map<String, Object> patch); 
    void delete(String kbId, String id); 
    void confirm(String kbId, String id); 
}
