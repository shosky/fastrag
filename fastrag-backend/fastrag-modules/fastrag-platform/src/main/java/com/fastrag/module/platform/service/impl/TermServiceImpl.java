package com.fastrag.module.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.platform.entity.*;
import com.fastrag.module.platform.mapper.*;
import com.fastrag.module.platform.service.TermService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TermServiceImpl implements TermService {
    private final TermLibraryMapper libMapper;
    private final TermRecordMapper termMapper;

    /** 启用词条缓存：术语变更频率极低、检索频率高，写失效读加载即可 */
    private volatile List<TermRecord> enabledCache;

    private void invalidate() {
        enabledCache = null;
    }

    private List<TermRecord> enabledTerms() {
        List<TermRecord> cached = enabledCache;
        if (cached == null) {
            cached = termMapper.selectList(new LambdaQueryWrapper<TermRecord>().eq(TermRecord::getStatus, 1));
            enabledCache = cached;
        }
        return cached;
    }

    @Override
    public List<TermLibrary> listLibraries() {
        return libMapper.selectList(null);
    }

    @Override
    public TermLibrary createLibrary(Map<String, Object> f) {
        var l = new TermLibrary();
        l.setName((String) f.get("name"));
        l.setDescription((String) f.get("description"));
        l.setTermCount(0);
        l.setCreatedAt(LocalDateTime.now());
        l.setUpdatedAt(LocalDateTime.now());
        libMapper.insert(l);
        return l;
    }

    @Override
    public TermLibrary updateLibrary(String id, Map<String, Object> f) {
        TermLibrary l = libMapper.selectById(id);
        if (l == null) return null;
        if (f.containsKey("name")) l.setName((String) f.get("name"));
        if (f.containsKey("description")) l.setDescription((String) f.get("description"));
        l.setUpdatedAt(LocalDateTime.now());
        libMapper.updateById(l);
        return l;
    }

    @Override
    public void deleteLibrary(String id) {
        termMapper.delete(new LambdaQueryWrapper<TermRecord>().eq(TermRecord::getLibraryId, id));
        libMapper.deleteById(id);
        invalidate();
    }

    @Override
    public List<TermRecord> listTerms(String libraryId) {
        return termMapper.selectList(new LambdaQueryWrapper<TermRecord>().eq(libraryId != null, TermRecord::getLibraryId, libraryId));
    }

    @Override
    public TermRecord createTerm(Map<String, Object> f) {
        var t = new TermRecord();
        String libraryId = (String) f.get("libraryId");
        t.setTerm((String) f.get("term"));
        t.setAlias((String) f.get("alias"));
        t.setDefinition((String) f.get("definition"));
        t.setStatus(parseStatus(f.get("status")));
        t.setLibraryId(libraryId);
        t.setCreatedAt(LocalDateTime.now());
        termMapper.insert(t);
        adjustTermCount(libraryId, 1);
        invalidate();
        return t;
    }

    @Override
    public TermRecord updateTerm(String id, Map<String, Object> f) {
        TermRecord t = termMapper.selectById(id);
        if (t == null) return null;
        if (f.containsKey("term")) t.setTerm((String) f.get("term"));
        if (f.containsKey("alias")) t.setAlias((String) f.get("alias"));
        if (f.containsKey("definition")) t.setDefinition((String) f.get("definition"));
        if (f.containsKey("status")) t.setStatus(parseStatus(f.get("status")));
        String newLibraryId = (String) f.get("libraryId");
        if (newLibraryId != null && !newLibraryId.isEmpty() && !newLibraryId.equals(t.getLibraryId())) {
            adjustTermCount(t.getLibraryId(), -1);
            adjustTermCount(newLibraryId, 1);
            t.setLibraryId(newLibraryId);
        }
        termMapper.updateById(t);
        invalidate();
        return t;
    }

    @Override
    public void deleteTerm(String id) {
        TermRecord term = termMapper.selectById(id);
        String libraryId = term != null ? term.getLibraryId() : null;
        termMapper.deleteById(id);
        adjustTermCount(libraryId, -1);
        invalidate();
    }

    private void adjustTermCount(String libraryId, int delta) {
        if (libraryId == null || libraryId.isEmpty()) return;
        TermLibrary lib = libMapper.selectById(libraryId);
        if (lib == null) return;
        int count = lib.getTermCount() != null ? lib.getTermCount() : 0;
        lib.setTermCount(Math.max(0, count + delta));
        lib.setUpdatedAt(LocalDateTime.now());
        libMapper.updateById(lib);
    }

    /** status 兼容数字与布尔入参，缺省启用 */
    private static Integer parseStatus(Object raw) {
        if (raw instanceof Number n) return n.intValue() == 0 ? 0 : 1;
        if (raw instanceof Boolean b) return b ? 1 : 0;
        return 1;
    }

    @Override
    public List<String> expandSynonyms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<TermRecord> allTerms = enabledTerms();
        Set<String> addedTerms = new LinkedHashSet<>();

        for (TermRecord tr : allTerms) {
            String term = tr.getTerm();
            if (term == null || term.isBlank()) continue;

            // If query contains the term, add its aliases
            if (query.contains(term)) {
                String alias = tr.getAlias();
                if (alias != null && !alias.isBlank()) {
                    for (String a : alias.split("[,，]")) {
                        String trimmed = a.trim();
                        if (!trimmed.isEmpty() && !query.contains(trimmed)) {
                            addedTerms.add(trimmed);
                        }
                    }
                }
            }

            // If query contains an alias, add the term itself
            String alias = tr.getAlias();
            if (alias != null && !alias.isBlank()) {
                for (String a : alias.split("[,，]")) {
                    String trimmed = a.trim();
                    if (!trimmed.isEmpty() && query.contains(trimmed) && !query.contains(term)) {
                        addedTerms.add(term);
                    }
                }
            }
        }

        return new ArrayList<>(addedTerms);
    }
}
