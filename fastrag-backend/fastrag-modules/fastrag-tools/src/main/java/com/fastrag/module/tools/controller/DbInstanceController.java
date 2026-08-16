package com.fastrag.module.tools.controller;

/**
 * 数据库实例管理控制器。
 *
 * <p>提供外部数据库实例的 CRUD 操作及辅助功能，用于管理和查询已注册的数据库连接。</p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>{@code GET    /api/databases} - 分页查询数据库实例列表（支持 keyword/dbType 筛选）</li>
 *   <li>{@code GET    /api/databases/{id}} - 获取单个数据库实例详情</li>
 *   <li>{@code POST   /api/databases} - 创建数据库实例</li>
 *   <li>{@code PUT    /api/databases/{id}} - 更新数据库实例配置</li>
 *   <li>{@code DELETE /api/databases/{id}} - 删除数据库实例</li>
 *   <li>{@code GET    /api/databases/{id}/tables} - 获取数据库表列表</li>
 *   <li>{@code POST   /api/databases/{id}/tables} - 创建数据库表</li>
 *   <li>{@code POST   /api/databases/{id}/test-conn} - 测试数据库连接</li>
 *   <li>{@code POST   /api/databases/{id}/query} - 执行 SQL 查询</li>
 *   <li>{@code POST   /api/databases/{id}/sync-tables} - 同步数据库表结构</li>
 * </ul>
 *
 * @see DbInstanceService
 */
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
