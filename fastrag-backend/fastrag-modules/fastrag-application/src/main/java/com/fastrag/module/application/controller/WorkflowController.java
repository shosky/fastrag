package com.fastrag.module.application.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.application.service.WorkflowService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/workflows") @RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService svc;
    @GetMapping public ApiResponse<?> list() { return ApiResponse.success(svc.list()); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.update(id,f)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/publish") public ApiResponse<?> publish(@PathVariable String id) { svc.publish(id); return ApiResponse.success(); }
    @PostMapping("/{id}/nodes") public ApiResponse<?> addNode(@PathVariable String id,@RequestBody Map<String,Object> b) { svc.addNode(id,(String)b.get("type"),(Double)b.get("x"),(Double)b.get("y")); return ApiResponse.success(); }
    @DeleteMapping("/{id}/nodes/{nid}") public ApiResponse<?> deleteNode(@PathVariable String id,@PathVariable String nid) { svc.deleteNode(id,nid); return ApiResponse.success(); }
    @PostMapping("/{id}/edges") public ApiResponse<?> addEdge(@PathVariable String id,@RequestBody Map<String,Object> e) { svc.addEdge(id,e); return ApiResponse.success(); }
    @DeleteMapping("/{id}/edges/{eid}") public ApiResponse<?> deleteEdge(@PathVariable String id,@PathVariable String eid) { svc.deleteEdge(id,eid); return ApiResponse.success(); }
}
