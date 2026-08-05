package com.fastrag.module.platform.service;

import com.fastrag.module.platform.entity.SysDictionary;
import java.util.List;
import java.util.Map;

public interface DictionaryService {

    /** Query all dictionary entries, optionally filtered by type. */
    List<SysDictionary> list(String dictType);

    /** Get all entries grouped by dictType. */
    Map<String, List<SysDictionary>> listAllGroupedByType();

    /** Get all distinct dictionary types. */
    List<String> listDictTypes();

    /** Runtime lookup: get dictValue by dictType and dictKey. */
    String getDictValue(String dictType, String dictKey);

    /** Runtime lookup: get all items of a type as Map<dictKey, dictValue>. */
    Map<String, String> getDictMap(String dictType);

    /** Create a new dictionary entry. */
    void create(SysDictionary dict);

    /** Update an existing dictionary entry. */
    void update(SysDictionary dict);

    /** Delete a dictionary entry by id. */
    void delete(Long id);

    /** Get a single dictionary entry by id. */
    SysDictionary getById(Long id);
}
