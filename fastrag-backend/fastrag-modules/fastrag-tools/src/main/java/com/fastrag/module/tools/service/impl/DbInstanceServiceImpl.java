package com.fastrag.module.tools.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.DbInstance; import com.fastrag.module.tools.entity.DbTable;
import com.fastrag.module.tools.mapper.DbInstanceMapper; import com.fastrag.module.tools.mapper.DbTableMapper;
import com.fastrag.module.tools.service.DbInstanceService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class DbInstanceServiceImpl implements DbInstanceService {
    private final DbInstanceMapper mapper; private final DbTableMapper tableMapper;
    @Override public List<DbInstance> list(String keyword,String dbType) {
        var w=new LambdaQueryWrapper<DbInstance>();
        if(keyword!=null&&!keyword.isEmpty())w.like(DbInstance::getName,keyword);
        if(dbType!=null&&!dbType.isEmpty())w.eq(DbInstance::getDbType,dbType);
        return mapper.selectList(w);
    }
    @Override public DbInstance get(String id) { return mapper.selectById(id); }
    @Override public DbInstance create(DbInstance db) { if(db.getStatus()==null)db.setStatus("connected"); if(db.getReadOnly()==null)db.setReadOnly(1); mapper.insert(db); return db; }
    @Override public DbInstance update(String id,DbInstance db) { db.setId(id); mapper.updateById(db); return mapper.selectById(id); }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public List<DbTable> listTables(String dbId) { return tableMapper.selectList(new LambdaQueryWrapper<DbTable>().eq(DbTable::getDbId,dbId)); }
    @Override public DbTable createTable(DbTable t) { if(t.getEnabled()==null)t.setEnabled(1); tableMapper.insert(t); return t; }
    @Override public Map<String,Object> testConnection(String dbId) { Map<String,Object> r=new LinkedHashMap<>(); r.put("connected",true); r.put("latencyMs",15); return r; }
    @Override public Map<String,Object> query(String dbId,String sql) { Map<String,Object> r=new LinkedHashMap<>(); r.put("results",List.of(Map.of("col1","val1"))); r.put("rowCount",1); return r; }
}
