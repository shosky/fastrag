package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.service.FileService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}/files") @RequiredArgsConstructor
public class FileController {
    private final FileService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId) { return ApiResponse.success(svc.list(kbId)); }
    @GetMapping("/deleted") public ApiResponse<?> deleted(@PathVariable String kbId) { return ApiResponse.success(svc.listDeleted(kbId)); }
    @PostMapping public ApiResponse<?> upload(@PathVariable String kbId,@RequestParam("file") MultipartFile file) { return ApiResponse.success(svc.upload(kbId,file)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String kbId,@PathVariable String id,@RequestBody Map<String,Object> p) { return ApiResponse.success(svc.update(kbId,id,p)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) { svc.delete(kbId,id); return ApiResponse.success(); }
    @PostMapping("/{id}/restore") public ApiResponse<?> restore(@PathVariable String kbId,@PathVariable String id) { svc.restore(kbId,id); return ApiResponse.success(); }
    @DeleteMapping("/{id}/permanent") public ApiResponse<?> permDelete(@PathVariable String kbId,@PathVariable String id) { svc.permanentDelete(kbId,id); return ApiResponse.success(); }
    @DeleteMapping("/recycle-bin") public ApiResponse<?> emptyBin(@PathVariable String kbId) { svc.emptyRecycleBin(kbId); return ApiResponse.success(); }
    @PostMapping("/{id}/copy") public ApiResponse<?> copy(@PathVariable String kbId,@PathVariable String id) { return ApiResponse.success(svc.copy(kbId,id)); }
    @GetMapping("/{id}/processing-status") public ApiResponse<?> status(@PathVariable String kbId,@PathVariable String id) { return ApiResponse.success(svc.getProcessingStatus(kbId,id)); }
}
