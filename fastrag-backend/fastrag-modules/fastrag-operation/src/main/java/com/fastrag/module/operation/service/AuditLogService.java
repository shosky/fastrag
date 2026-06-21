package com.fastrag.module.operation.service;
import com.fastrag.module.operation.entity.SysAuditLog; import java.util.*;
public interface AuditLogService { List<SysAuditLog> list(String module,Integer limit); }
