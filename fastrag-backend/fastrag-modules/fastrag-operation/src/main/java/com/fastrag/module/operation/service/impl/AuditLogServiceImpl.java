package com.fastrag.module.operation.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.operation.entity.SysAuditLog; import com.fastrag.module.operation.mapper.SysAuditLogMapper;
import com.fastrag.module.operation.service.AuditLogService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {
    private final SysAuditLogMapper mapper;
    @Override public List<SysAuditLog> list(String module,Integer limit) {
        var w=new LambdaQueryWrapper<SysAuditLog>(); if(module!=null)w.eq(SysAuditLog::getModule,module);
        w.orderByDesc(SysAuditLog::getTimestamp).last(limit!=null?"LIMIT "+limit:"LIMIT 100");
        return mapper.selectList(w);
    }
}
