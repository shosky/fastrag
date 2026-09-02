package com.fastrag.module.platform.service;
import com.fastrag.module.platform.entity.*; import java.util.*;
public interface TermService {
    List<TermLibrary> listLibraries();
    TermLibrary createLibrary(Map<String,Object> form);
    /** 更新术语库（name/description），返回更新后的实体 */
    TermLibrary updateLibrary(String id, Map<String,Object> form);
    void deleteLibrary(String id);
    List<TermRecord> listTerms(String libraryId);
    TermRecord createTerm(Map<String,Object> form);
    /** 更新术语（term/alias/definition/status/libraryId），换库时维护两端 termCount */
    TermRecord updateTerm(String id, Map<String,Object> form);
    void deleteTerm(String id);
    /**
     * Expand a query with synonyms from term libraries.
     * If the query contains a term, add its aliases as additional search terms.
     *
     * @return list of additional terms to append to the query
     */
    List<String> expandSynonyms(String query);
}
