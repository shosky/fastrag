package com.fastrag.module.tools.service;
import com.fastrag.module.tools.entity.*; import java.util.*;
public interface DbInstanceService {
    List<DbInstance> list(String keyword,String dbType);
    DbInstance get(String id); DbInstance create(DbInstance db); DbInstance update(String id,DbInstance db); void delete(String id);
    List<DbTable> listTables(String dbId); DbTable createTable(DbTable t);
    Map<String,Object> testConnection(String dbId); Map<String,Object> query(String dbId,String sql);
}
