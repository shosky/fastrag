package com.fastrag.module.knowledge.controller;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.model.QaCreateRequest;
import com.fastrag.module.knowledge.service.QaPairService;
import com.fastrag.module.publish.service.LogService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}/qa-pairs") @RequiredArgsConstructor
public class QaPairController {
    private final QaPairService svc;
    private final LogService logService;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId, @RequestParam(required = false) String fileId) { return ApiResponse.success(svc.list(kbId, fileId)); }
    @Loggable(category = LogCategory.operation, action = ActionType.qa_pair_created, detail = "创建QA对")
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@Valid @RequestBody QaCreateRequest req) { return ApiResponse.success(svc.create(kbId,req)); }
    @Loggable(category = LogCategory.operation, action = ActionType.qa_pair_updated, detail = "更新QA对")
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String kbId,@PathVariable String id,@RequestBody Map<String,Object> p) { return ApiResponse.success(svc.update(kbId,id,p)); }
    @Loggable(category = LogCategory.operation, action = ActionType.qa_pair_deleted, detail = "删除QA对")
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) { svc.delete(kbId,id); return ApiResponse.success(); }
    @Loggable(category = LogCategory.operation, action = ActionType.qa_pair_confirmed, detail = "确认QA对")
    @PostMapping("/{id}/confirm") public ApiResponse<?> confirm(@PathVariable String kbId,@PathVariable String id) { svc.confirm(kbId,id); return ApiResponse.success(); }
}
