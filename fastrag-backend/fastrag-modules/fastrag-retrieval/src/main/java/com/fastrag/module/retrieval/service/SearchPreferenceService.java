package com.fastrag.module.retrieval.service;
import com.fastrag.module.retrieval.entity.KbSearchPreference; import java.util.*;
public interface SearchPreferenceService {
    List<KbSearchPreference> list(String kbId, String userId, boolean mineOnly);
    KbSearchPreference get(String id);
    KbSearchPreference create(String kbId, KbSearchPreference p);
    KbSearchPreference update(String id, KbSearchPreference p);
    void delete(String id);
}
