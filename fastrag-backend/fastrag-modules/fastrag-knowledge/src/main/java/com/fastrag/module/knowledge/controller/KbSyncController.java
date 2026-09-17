package com.fastrag.module.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbSyncConfig;
import com.fastrag.module.knowledge.mapper.KbSyncConfigMapper;
import com.fastrag.module.knowledge.service.KbSyncService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;

/** 知识库同步机制：同步配置 CRUD + 手动执行 + 同步记录查看 */
@RestController @RequestMapping("/api/kb-sync") @RequiredArgsConstructor
public class KbSyncController {
    private final KbSyncConfigMapper configMapper;
    private final KbSyncService syncService;

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String keyword) { return ApiResponse.success(syncService.listConfigs(keyword)); }

    @PostMapping
    public ApiResponse<?> create(@RequestBody KbSyncConfig config) {
        if (config.getSyncMode() == null) config.setSyncMode("incremental");
        if (config.getIntervalMinutes() == null) config.setIntervalMinutes(60);
        if (config.getEnabled() == null) config.setEnabled(1);
        configMapper.insert(config);
        return ApiResponse.success(config);
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody KbSyncConfig config) {
        config.setId(id);
        configMapper.updateById(config);
        return ApiResponse.success(configMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) { configMapper.deleteById(id); return ApiResponse.success(); }

    /** 手动执行同步（一键同步） */
    @PostMapping("/{id}/run")
    public ApiResponse<?> run(@PathVariable String id) { return ApiResponse.success(syncService.run(id)); }

    @GetMapping("/{id}/records")
    public ApiResponse<?> records(@PathVariable String id) { return ApiResponse.success(syncService.records(id)); }
}
