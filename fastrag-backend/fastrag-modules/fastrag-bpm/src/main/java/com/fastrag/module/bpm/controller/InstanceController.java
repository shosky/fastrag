package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.*;
import com.fastrag.module.bpm.service.BpmInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/bpm/instances") @RequiredArgsConstructor
public class InstanceController {
    private final BpmInstanceService svc;

    @PostMapping("/trigger") public ApiResponse<?> trigger(@RequestBody InstanceTriggerRequest req) {
        return ApiResponse.success(java.util.Map.of("instanceId", svc.trigger(req)));
    }
    @GetMapping public ApiResponse<?> list(InstanceListReq req) { return ApiResponse.success(svc.list(req)); }
    @GetMapping("/{id}") public ApiResponse<?> detail(@PathVariable String id) { return ApiResponse.success(svc.detail(id)); }
    @GetMapping("/{id}/events") public ApiResponse<?> events(@PathVariable String id) { return ApiResponse.success(svc.events(id)); }
    @PostMapping("/{id}/pause") public ApiResponse<?> pause(@PathVariable String id) { svc.pause(id); return ApiResponse.success(); }
    @PostMapping("/{id}/resume") public ApiResponse<?> resume(@PathVariable String id) { svc.resume(id); return ApiResponse.success(); }
    @PostMapping("/{id}/cancel") public ApiResponse<?> cancel(@PathVariable String id, @RequestBody(required = false) java.util.Map<String, String> body) {
        svc.cancel(id, body == null ? null : body.get("reason"));
        return ApiResponse.success();
    }
    @PostMapping("/input/submit") public ApiResponse<?> submit(@RequestBody SubmitInputRequest req) {
        svc.submitUserInput(req);
        return ApiResponse.success();
    }
}