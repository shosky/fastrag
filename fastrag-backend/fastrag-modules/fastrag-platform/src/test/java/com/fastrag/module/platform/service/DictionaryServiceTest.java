package com.fastrag.module.platform.service;

import com.fastrag.module.platform.entity.SysDictionary;
import com.fastrag.module.platform.mapper.SysDictionaryMapper;
import com.fastrag.module.platform.service.impl.DictionaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryServiceTest {

    @Mock
    private SysDictionaryMapper dictionaryMapper;

    private DictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        dictionaryService = new DictionaryServiceImpl(dictionaryMapper);
    }

    private SysDictionary makeDict(Long id, String type, String key, String value) {
        SysDictionary d = new SysDictionary();
        d.setId(id);
        d.setDictType(type);
        d.setDictKey(key);
        d.setDictValue(value);
        return d;
    }

    @Test
    void testGetDictValue_found() {
        SysDictionary d = makeDict(1L, "gender", "male", "男");
        when(dictionaryMapper.selectOne(any())).thenReturn(d);
        String result = dictionaryService.getDictValue("gender", "male");
        assertEquals("男", result);
    }

    @Test
    void testGetDictValue_notFound_returnsNull() {
        when(dictionaryMapper.selectOne(any())).thenReturn(null);
        String result = dictionaryService.getDictValue("gender", "unknown");
        assertNull(result);
    }

    @Test
    void testGetDictMap_returnsAllItems() {
        when(dictionaryMapper.selectList(any())).thenReturn(List.of(
                makeDict(1L, "status", "active", "启用"),
                makeDict(2L, "status", "inactive", "禁用")
        ));
        Map<String, String> map = dictionaryService.getDictMap("status");
        assertEquals(2, map.size());
        assertEquals("启用", map.get("active"));
        assertEquals("禁用", map.get("inactive"));
    }

    @Test
    void testListDictTypes_returnsDistinctTypes() {
        when(dictionaryMapper.selectList(any())).thenReturn(List.of(
                makeDict(1L, "gender", "m", "男"),
                makeDict(2L, "gender", "f", "女"),
                makeDict(3L, "status", "active", "启用")
        ));
        List<String> types = dictionaryService.listDictTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains("gender"));
        assertTrue(types.contains("status"));
    }

    @Test
    void testListAllGroupedByType() {
        when(dictionaryMapper.selectList(any())).thenReturn(List.of(
                makeDict(1L, "gender", "m", "男"),
                makeDict(2L, "status", "active", "启用")
        ));
        Map<String, List<SysDictionary>> grouped = dictionaryService.listAllGroupedByType();
        assertEquals(2, grouped.size());
        assertTrue(grouped.containsKey("gender"));
        assertTrue(grouped.containsKey("status"));
    }
}
