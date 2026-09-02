package com.fastrag.module.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.platform.entity.SysDictionary;
import com.fastrag.module.platform.mapper.SysDictionaryMapper;
import com.fastrag.module.platform.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements DictionaryService {

    private final SysDictionaryMapper dictionaryMapper;

    @Override
    public List<SysDictionary> list(String dictType) {
        LambdaQueryWrapper<SysDictionary> query = new LambdaQueryWrapper<>();
        if (dictType != null && !dictType.isEmpty()) {
            query.eq(SysDictionary::getDictType, dictType);
        }
        query.orderByAsc(SysDictionary::getSortOrder).orderByAsc(SysDictionary::getId);
        return dictionaryMapper.selectList(query);
    }

    @Override
    public Map<String, List<SysDictionary>> listAllGroupedByType() {
        List<SysDictionary> all = dictionaryMapper.selectList(
                new LambdaQueryWrapper<SysDictionary>()
                        .orderByAsc(SysDictionary::getSortOrder)
                        .orderByAsc(SysDictionary::getId));
        return all.stream().collect(
                Collectors.groupingBy(d -> d.getDictType() != null ? d.getDictType() : "未分类",
                        LinkedHashMap::new, Collectors.toList())
        );
    }

    @Override
    public List<String> listDictTypes() {
        List<SysDictionary> all = dictionaryMapper.selectList(null);
        return all.stream()
                .map(SysDictionary::getDictType)
                .filter(t -> t != null && !t.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public String getDictValue(String dictType, String dictKey) {
        SysDictionary dict = dictionaryMapper.selectOne(
                new LambdaQueryWrapper<SysDictionary>()
                        .eq(SysDictionary::getDictType, dictType)
                        .eq(SysDictionary::getDictKey, dictKey)
                        .last("LIMIT 1")
        );
        return dict != null ? dict.getDictValue() : null;
    }

    @Override
    public Map<String, String> getDictMap(String dictType) {
        List<SysDictionary> list = list(dictType);
        Map<String, String> map = new LinkedHashMap<>();
        for (SysDictionary d : list) {
            if (d.getDictKey() != null) {
                map.put(d.getDictKey(), d.getDictValue() != null ? d.getDictValue() : "");
            }
        }
        return map;
    }

    @Override
    public void create(SysDictionary dict) {
        dictionaryMapper.insert(dict);
    }

    @Override
    public void update(SysDictionary dict) {
        dictionaryMapper.updateById(dict);
    }

    @Override
    public void delete(Long id) {
        dictionaryMapper.deleteById(id);
    }

    @Override
    public SysDictionary getById(Long id) {
        return dictionaryMapper.selectById(id);
    }
}
