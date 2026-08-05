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
    public void deleteLibrary(String id) {
        termMapper.delete(new LambdaQueryWrapper<TermRecord>().eq(TermRecord::getLibraryId, id));
        libMapper.deleteById(id);
    }

    @Override
    public List<TermRecord> listTerms(String libId) {
        return termMapper.selectList(new LambdaQueryWrapper<TermRecord>().eq(libId != null, TermRecord::getLibraryId, libId));
    }

    @Override
    public TermRecord createTerm(Map<String, Object> f) {
        var t = new TermRecord();
        String libraryId = (String) f.get("libraryId");
        t.setTerm((String) f.get("term"));
        t.setAlias((String) f.get("alias"));
        t.setLibraryId(libraryId);
        t.setCreatedAt(LocalDateTime.now());
        termMapper.insert(t);

        // Maintain termCount on the library
        if (libraryId != null && !libraryId.isEmpty()) {
            TermLibrary lib = libMapper.selectById(libraryId);
            if (lib != null) {
                lib.setTermCount((lib.getTermCount() != null ? lib.getTermCount() : 0) + 1);
                lib.setUpdatedAt(LocalDateTime.now());
                libMapper.updateById(lib);
            }
        }
        return t;
    }

    @Override
    public void deleteTerm(String id) {
        TermRecord term = termMapper.selectById(id);
        String libraryId = term != null ? term.getLibraryId() : null;
        termMapper.deleteById(id);

        // Maintain termCount on the library
        if (libraryId != null && !libraryId.isEmpty()) {
            TermLibrary lib = libMapper.selectById(libraryId);
            if (lib != null && lib.getTermCount() != null && lib.getTermCount() > 0) {
                lib.setTermCount(lib.getTermCount() - 1);
                lib.setUpdatedAt(LocalDateTime.now());
                libMapper.updateById(lib);
            }
        }
    }

    @Override
    public List<String> expandSynonyms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<TermRecord> allTerms = termMapper.selectList(null);
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
