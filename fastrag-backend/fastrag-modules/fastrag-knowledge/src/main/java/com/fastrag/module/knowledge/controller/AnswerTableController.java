package com.fastrag.module.knowledge.controller;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbAnswerTable;
import com.fastrag.module.knowledge.entity.KbAnswerTableColumn;
import com.fastrag.module.knowledge.entity.KbAnswerTableRow;
import com.fastrag.module.knowledge.mapper.KbAnswerTableColumnMapper;
import com.fastrag.module.knowledge.mapper.KbAnswerTableMapper;
import com.fastrag.module.knowledge.mapper.KbAnswerTableRowMapper;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 表格型应答知识：新增/编辑/删除表格、表格增加列、表格内容行编辑 */
@RestController @RequestMapping("/api/kb/{kbId}/answer-tables") @RequiredArgsConstructor
public class AnswerTableController {
    private final KbAnswerTableMapper tableMapper;
    private final KbAnswerTableColumnMapper columnMapper;
    private final KbAnswerTableRowMapper rowMapper;

    @GetMapping
    public ApiResponse<?> list(@PathVariable String kbId) {
        List<Map<String,Object>> out = new ArrayList<>();
        for (var t : tableMapper.selectList(new LambdaQueryWrapper<KbAnswerTable>().eq(KbAnswerTable::getKbId,kbId).orderByDesc(KbAnswerTable::getCreatedAt))) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",t.getId()); m.put("name",t.getName()); m.put("description",t.getDescription());
            m.put("createdAt",t.getCreatedAt()); m.put("updatedAt",t.getUpdatedAt());
            m.put("columnCount",columnMapper.selectCount(new LambdaQueryWrapper<KbAnswerTableColumn>().eq(KbAnswerTableColumn::getTableId,t.getId())));
            m.put("rowCount",rowMapper.selectCount(new LambdaQueryWrapper<KbAnswerTableRow>().eq(KbAnswerTableRow::getTableId,t.getId())));
            out.add(m);
        }
        return ApiResponse.success(out);
    }

    @PostMapping
    public ApiResponse<?> create(@PathVariable String kbId,@RequestBody Map<String,Object> body) {
        var t = new KbAnswerTable();
        t.setKbId(kbId); t.setName((String) body.get("name")); t.setDescription((String) body.get("description"));
        tableMapper.insert(t);
        Object cols = body.get("columns");
        if (cols instanceof List<?> list) {
            int sort = 0;
            for (Object o : list) {
                if (o instanceof Map<?,?> c) insertColumn(t.getId(), str(c.get("name")), str(c.get("key")), strOr(c.get("type"),"text"), sort++);
            }
        }
        return ApiResponse.success(detail(kbId, t.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String kbId,@PathVariable String id) { return ApiResponse.success(detail(kbId, id)); }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String kbId,@PathVariable String id,@RequestBody Map<String,Object> body) {
        var t = tableMapper.selectById(id); if (t == null) throw new RuntimeException("表格不存在");
        if (body.containsKey("name")) t.setName((String) body.get("name"));
        if (body.containsKey("description")) t.setDescription((String) body.get("description"));
        tableMapper.updateById(t);
        return ApiResponse.success(detail(kbId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) {
        columnMapper.delete(new LambdaQueryWrapper<KbAnswerTableColumn>().eq(KbAnswerTableColumn::getTableId,id));
        rowMapper.delete(new LambdaQueryWrapper<KbAnswerTableRow>().eq(KbAnswerTableRow::getTableId,id));
        tableMapper.deleteById(id);
        return ApiResponse.success();
    }

    // ===== 表格增加列 =====
    @PostMapping("/{id}/columns")
    public ApiResponse<?> addColumn(@PathVariable String kbId,@PathVariable String id,@RequestBody Map<String,Object> body) {
        int sort = Math.toIntExact(columnMapper.selectCount(new LambdaQueryWrapper<KbAnswerTableColumn>().eq(KbAnswerTableColumn::getTableId,id)));
        insertColumn(id, str(body.get("name")), str(body.get("key")), strOr(body.get("type"),"text"), sort);
        return ApiResponse.success(detail(kbId, id));
    }

    @DeleteMapping("/{id}/columns/{colId}")
    public ApiResponse<?> deleteColumn(@PathVariable String kbId,@PathVariable String id,@PathVariable String colId) {
        columnMapper.deleteById(colId);
        return ApiResponse.success(detail(kbId, id));
    }

    // ===== 表格内容（行） =====
    @PostMapping("/{id}/rows")
    public ApiResponse<?> addRow(@PathVariable String kbId,@PathVariable String id,@RequestBody Map<String,Object> body) {
        var r = new KbAnswerTableRow();
        r.setTableId(id);
        r.setContentMap(body.get("content"));
        r.setSortOrder(Math.toIntExact(rowMapper.selectCount(new LambdaQueryWrapper<KbAnswerTableRow>().eq(KbAnswerTableRow::getTableId,id))));
        rowMapper.insert(r);
        return ApiResponse.success(detail(kbId, id));
    }

    @PutMapping("/{id}/rows/{rowId}")
    public ApiResponse<?> updateRow(@PathVariable String kbId,@PathVariable String id,@PathVariable String rowId,@RequestBody Map<String,Object> body) {
        var r = rowMapper.selectById(rowId); if (r == null) throw new RuntimeException("行不存在");
        if (body.containsKey("content")) r.setContentMap(body.get("content"));
        rowMapper.updateById(r);
        return ApiResponse.success(detail(kbId, id));
    }

    @DeleteMapping("/{id}/rows/{rowId}")
    public ApiResponse<?> deleteRow(@PathVariable String kbId,@PathVariable String id,@PathVariable String rowId) {
        rowMapper.deleteById(rowId);
        return ApiResponse.success(detail(kbId, id));
    }

    private Map<String,Object> detail(String kbId,String id) {
        var t = tableMapper.selectById(id); if (t == null) throw new RuntimeException("表格不存在");
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",t.getId()); m.put("kbId",t.getKbId()); m.put("name",t.getName()); m.put("description",t.getDescription());
        m.put("createdAt",t.getCreatedAt()); m.put("updatedAt",t.getUpdatedAt());
        m.put("columns",columnMapper.selectList(new LambdaQueryWrapper<KbAnswerTableColumn>().eq(KbAnswerTableColumn::getTableId,id).orderByAsc(KbAnswerTableColumn::getSortOrder)));
        m.put("rows",rowMapper.selectList(new LambdaQueryWrapper<KbAnswerTableRow>().eq(KbAnswerTableRow::getTableId,id).orderByAsc(KbAnswerTableRow::getSortOrder)));
        return m;
    }

    private void insertColumn(String tableId,String name,String key,String type,int sort) {
        var c = new KbAnswerTableColumn();
        c.setTableId(tableId); c.setName(name);
        c.setColKey(key != null && !key.isBlank() ? key : "col_" + UUID.randomUUID().toString().substring(0, 8));
        c.setColType(type != null ? type : "text"); c.setSortOrder(sort);
        columnMapper.insert(c);
    }
    private String str(Object o) { return o == null ? null : o.toString(); }
    private String strOr(Object o,String def) { return o == null || o.toString().isBlank() ? def : o.toString(); }
}
