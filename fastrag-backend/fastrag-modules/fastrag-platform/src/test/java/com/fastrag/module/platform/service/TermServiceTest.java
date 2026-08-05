package com.fastrag.module.platform.service;

import com.fastrag.module.platform.entity.TermLibrary;
import com.fastrag.module.platform.entity.TermRecord;
import com.fastrag.module.platform.mapper.TermLibraryMapper;
import com.fastrag.module.platform.mapper.TermRecordMapper;
import com.fastrag.module.platform.service.impl.TermServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TermServiceTest {

    @Mock
    private TermLibraryMapper libMapper;
    @Mock
    private TermRecordMapper termMapper;

    private TermService termService;

    @BeforeEach
    void setUp() {
        termService = new TermServiceImpl(libMapper, termMapper);
    }

    private TermLibrary makeLibrary(String id, String name, int count) {
        TermLibrary lib = new TermLibrary();
        lib.setId(id);
        lib.setName(name);
        lib.setTermCount(count);
        return lib;
    }

    private TermRecord makeTerm(String id, String libId, String term, String alias) {
        TermRecord tr = new TermRecord();
        tr.setId(id);
        tr.setLibraryId(libId);
        tr.setTerm(term);
        tr.setAlias(alias);
        return tr;
    }

    @Test
    void testCreateTerm_incrementsTermCount() {
        TermLibrary lib = makeLibrary("lib1", "测试词库", 5);
        when(libMapper.selectById("lib1")).thenReturn(lib);
        when(termMapper.insert(any())).thenReturn(1);
        when(libMapper.updateById(any())).thenReturn(1);

        termService.createTerm(Map.of(
                "term", "AI",
                "alias", "人工智能",
                "libraryId", "lib1"
        ));

        verify(termMapper).insert(any(TermRecord.class));
        verify(libMapper).updateById(argThat(l -> l.getTermCount() == 6));
    }

    @Test
    void testDeleteTerm_decrementsTermCount() {
        TermRecord term = makeTerm("t1", "lib1", "AI", "人工智能");
        TermLibrary lib = makeLibrary("lib1", "测试词库", 5);
        when(termMapper.selectById("t1")).thenReturn(term);
        when(libMapper.selectById("lib1")).thenReturn(lib);
        when(termMapper.deleteById("t1")).thenReturn(1);
        when(libMapper.updateById(any())).thenReturn(1);

        termService.deleteTerm("t1");

        verify(termMapper).deleteById("t1");
        verify(libMapper).updateById(argThat(l -> l.getTermCount() == 4));
    }

    @Test
    void testExpandSynonyms_findsMatchingTerms() {
        when(termMapper.selectList(any())).thenReturn(List.of(
                makeTerm("t1", "lib1", "AI", "人工智能,机器智能"),
                makeTerm("t2", "lib1", "RAG", "检索增强生成")
        ));

        List<String> expanded = termService.expandSynonyms("什么是AI技术");
        assertEquals(2, expanded.size());
        assertTrue(expanded.contains("人工智能"));
        assertTrue(expanded.contains("机器智能"));
    }

    @Test
    void testExpandSynonyms_noMatch_returnsEmpty() {
        when(termMapper.selectList(any())).thenReturn(List.of(
                makeTerm("t1", "lib1", "AI", "人工智能")
        ));

        List<String> expanded = termService.expandSynonyms("什么是数据库");
        assertTrue(expanded.isEmpty());
    }

    @Test
    void testExpandSynonyms_reverseMatching() {
        when(termMapper.selectList(any())).thenReturn(List.of(
                makeTerm("t1", "lib1", "AI", "人工智能")
        ));

        List<String> expanded = termService.expandSynonyms("什么是人工智能");
        assertEquals(1, expanded.size());
        assertTrue(expanded.contains("AI"));
    }

    @Test
    void testExpandSynonyms_emptyQuery() {
        List<String> expanded = termService.expandSynonyms("");
        assertTrue(expanded.isEmpty());
    }

    @Test
    void testExpandSynonyms_nullQuery() {
        List<String> expanded = termService.expandSynonyms(null);
        assertTrue(expanded.isEmpty());
    }
}
