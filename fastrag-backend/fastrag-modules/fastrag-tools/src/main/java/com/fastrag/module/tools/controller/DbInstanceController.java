package com.fastrag.module.tools.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.tools.entity.DbInstance;
import com.fastrag.module.tools.entity.DbTable;
import com.fastrag.module.tools.service.DbInstanceService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
@RestController @RequestMapping("/api/databases") @RequiredArgsConstructor
public class DbInstanceController {
    private final DbInstanceService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String dbType) { return ApiResponse.success(svc.list(keyword,dbType)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @Loggable(category=LogCategory.operation,action=ActionType.tool_created)
    @PostMapping public ApiResponse<?> create(@RequestBody DbInstance db) { return ApiResponse.success(svc.create(db)); }
    @Loggable(category=LogCategory.operation,action=ActionType.tool_updated,target="#id")
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody DbInstance db) { return ApiResponse.success(svc.update(id,db)); }
    @Loggable(category=LogCategory.operation,action=ActionType.tool_deleted,target="#id")
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @GetMapping("/{id}/tables") public ApiResponse<?> tables(@PathVariable String id) { return ApiResponse.success(svc.listTables(id)); }
    @Loggable(category=LogCategory.operation,action=ActionType.tool_updated,target="#id")
    @PostMapping("/{id}/tables") public ApiResponse<?> createTable(@PathVariable String id, @RequestBody DbTable table) { table.setDbId(id); return ApiResponse.success(svc.createTable(table)); }
    @PostMapping("/{id}/test-conn") public ApiResponse<?> testConn(@PathVariable String id) { return ApiResponse.success(svc.testConnection(id)); }
    @PostMapping("/{id}/query") public ApiResponse<?> query(@PathVariable String id,@RequestBody Map<String,String> b) { return ApiResponse.success(svc.query(id,b.get("sql"))); }
    @Loggable(category=LogCategory.operation,action=ActionType.tool_updated,target="#id")
    @PostMapping("/{id}/sync-tables") public ApiResponse<?> syncTables(@PathVariable String id) { return ApiResponse.success(svc.syncTables(id)); }
}
