package com.fastrag.module.platform.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.platform.service.TermService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class TermController {
    private final TermService svc;
    @GetMapping("/api/terminology/libraries") public ApiResponse<?> libs() { return ApiResponse.success(svc.listLibraries()); }
    @PostMapping("/api/terminology/libraries") public ApiResponse<?> createLib(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.createLibrary(f)); }
    @DeleteMapping("/api/terminology/libraries/{id}") public ApiResponse<?> deleteLib(@PathVariable String id) { svc.deleteLibrary(id); return ApiResponse.success(); }
    @GetMapping("/api/terminology/terms") public ApiResponse<?> terms(@RequestParam(required=false) String library) { return ApiResponse.success(svc.listTerms(library)); }
    @PostMapping("/api/terminology/terms") public ApiResponse<?> createTerm(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.createTerm(f)); }
    @DeleteMapping("/api/terminology/terms/{id}") public ApiResponse<?> deleteTerm(@PathVariable String id) { svc.deleteTerm(id); return ApiResponse.success(); }
}
