package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbMediaStorage;
import com.fastrag.module.knowledge.service.MediaStorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; import org.springframework.core.io.Resource;
import org.springframework.http.MediaType; import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
@RestController @RequestMapping("/api/kb/{kbId}") @RequiredArgsConstructor
public class MediaStorageController {
    private final MediaStorageService svc;
    // ===== M7 媒体导入/查看/导出（images/audios/videos） =====
    @GetMapping("/media/{mediaType}") public ApiResponse<?> list(@PathVariable String kbId,@PathVariable String mediaType,
        @RequestParam(required=false) String keyword,@RequestParam(required=false) String status,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.success(svc.page(kbId,mediaType,keyword,status,page,pageSize));
    }
    @PostMapping("/media/{mediaType}/import") public ApiResponse<?> importMedia(@PathVariable String kbId,@PathVariable String mediaType,@RequestBody List<KbMediaStorage> items) {
        return ApiResponse.success(svc.batchImport(kbId,mediaType,items));
    }
    @GetMapping("/media/{mediaType}/export") public ApiResponse<?> exportMedia(@PathVariable String kbId,@PathVariable String mediaType,
        @RequestParam(required=false) String ids,@RequestParam(required=false) String status,@RequestParam(required=false) String editor) {
        return ApiResponse.success(svc.list(kbId,mediaType,null));
    }
    // ===== M7 文档导入/查看/导出（复用 media 表 mediaType=document） =====
    @GetMapping("/documents") public ApiResponse<?> documents(@PathVariable String kbId,
        @RequestParam(required=false) String keyword,@RequestParam(required=false) String status,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.success(svc.page(kbId,"document",keyword,status,page,pageSize));
    }
    @PostMapping("/documents/import") public ApiResponse<?> importDocs(@PathVariable String kbId,@RequestBody List<KbMediaStorage> items) {
        return ApiResponse.success(svc.batchImport(kbId,"document",items));
    }
    @GetMapping("/documents/export") public void exportDocs(@PathVariable String kbId, HttpServletResponse resp) throws Exception {
        var list=svc.list(kbId,"document",null);
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=documents.csv");
        var w=new java.io.PrintWriter(resp.getWriter()); w.println("name,type,size,status,createdAt");
        for(var d:list){ w.printf("\"%s\",%s,%d,%s,%s%n",d.getName(),d.getExtension()!=null?d.getExtension():"file",d.getSize()!=null?d.getSize():0,d.getStatus(),d.getCreatedAt()); }
        w.flush();
    }
    // ===== M9 存储管理 CRUD =====
    @GetMapping("/storage/{mediaType}") public ApiResponse<?> storageList(@PathVariable String kbId,@PathVariable String mediaType,
        @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.success(svc.page(kbId,mediaType,keyword,null,page,pageSize));
    }
    @PostMapping("/storage/{mediaType}") public ApiResponse<?> createStorage(@PathVariable String kbId,@PathVariable String mediaType,@RequestBody KbMediaStorage media) {
        media.setKbId(kbId); media.setMediaType(mediaType); return ApiResponse.success(svc.create(media));
    }
    @GetMapping("/storage/{mediaType}/{id}") public ApiResponse<?> getStorage(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PutMapping("/storage/{mediaType}/{id}") public ApiResponse<?> updateStorage(@PathVariable String id,@RequestBody KbMediaStorage media) { return ApiResponse.success(svc.update(id,media)); }
    @PutMapping("/storage/{mediaType}/{id}/edit") public ApiResponse<?> editStorage(@PathVariable String id,@RequestBody KbMediaStorage media) { return ApiResponse.success(svc.update(id,media)); }
    @DeleteMapping("/storage/{mediaType}/{id}") public ApiResponse<?> deleteStorage(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }

    // ===== M9 文件上传/下载 =====
    @PostMapping("/storage/{mediaType}/upload")
    public ApiResponse<?> uploadFile(@PathVariable String kbId, @PathVariable String mediaType,
        @RequestParam("file") MultipartFile file, @RequestParam(required=false) String description,
        @RequestParam(required=false) String tags) {
        return ApiResponse.success(svc.uploadFile(kbId, mediaType, file, description, tags));
    }

    @GetMapping("/storage/{mediaType}/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String id) {
        var fileInfo = svc.getDownloadResource(id);
        if (fileInfo == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header("Content-Disposition", "attachment; filename=\"" + fileInfo.getFileName() + "\"")
            .body(fileInfo.getResource());
    }
}
