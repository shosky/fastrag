package com.fastrag.module.application.controller;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.application.entity.AppSemanticConfig;
import com.fastrag.module.application.mapper.AppSemanticConfigMapper;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime; import java.util.LinkedHashMap; import java.util.List; import java.util.Map;

/** 语义理解/语义定制：应用级意图模式与定制同义词配置（GET 读取 / PUT 保存，另提供 judge 试判断） */
@RestController @RequestMapping("/api/apps/{appId}/semantic-config") @RequiredArgsConstructor
public class SemanticConfigController {
    private final AppSemanticConfigMapper mapper;

    @GetMapping
    public ApiResponse<?> get(@PathVariable String appId) {
        AppSemanticConfig c = first(appId);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("appId", appId);
        out.put("intentPatterns", parseList(c == null ? null : c.getIntentPatterns()));
        out.put("customSynonyms", parseList(c == null ? null : c.getCustomSynonyms()));
        return ApiResponse.success(out);
    }

    @PutMapping
    public ApiResponse<?> put(@PathVariable String appId,@RequestBody Map<String,Object> body) {
        AppSemanticConfig c = first(appId);
        if (c == null) { c = new AppSemanticConfig(); c.setAppId(appId); }
        if (body.containsKey("intentPatterns")) c.setIntentPatterns(toJson(body.get("intentPatterns")));
        if (body.containsKey("customSynonyms")) c.setCustomSynonyms(toJson(body.get("customSynonyms")));
        if (c.getCreatedAt() == null) { c.setCreatedAt(LocalDateTime.now()); mapper.insert(c); }
        else { c.setUpdatedAt(LocalDateTime.now()); mapper.updateById(c); }
        return ApiResponse.success(c);
    }

    /** 语义理解试判断：按意图模式关键词命中返回意图与置信度（供调试/预览） */
    @PostMapping("/judge")
    public ApiResponse<?> judge(@PathVariable String appId,@RequestBody Map<String,Object> body) {
        String query = body.get("query") == null ? "" : body.get("query").toString();
        AppSemanticConfig c = first(appId);
        List<Map<String,Object>> patterns = parseList(c == null ? null : c.getIntentPatterns());
        List<Map<String,Object>> hits = new java.util.ArrayList<>();
        for (Map<String,Object> p : patterns) {
            Object patterns0 = p.get("patterns");
            double threshold = p.get("threshold") instanceof Number n ? n.doubleValue() : 0.6;
            if (!(patterns0 instanceof List<?> keys)) continue;
            long hit = keys.stream().map(Object::toString).filter(query::contains).count();
            if (hit > 0) {
                double confidence = Math.min(1.0, 0.5 + 0.25 * hit);
                if (confidence >= threshold) {
                    Map<String,Object> h = new LinkedHashMap<>();
                    h.put("intent", p.get("intent")); h.put("confidence", Math.round(confidence * 100) / 100.0);
                    hits.add(h);
                }
            }
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("query", query); out.put("matched", !hits.isEmpty()); out.put("intents", hits);
        return ApiResponse.success(out);
    }

    private AppSemanticConfig first(String appId) {
        return mapper.selectOne(new LambdaQueryWrapper<AppSemanticConfig>().eq(AppSemanticConfig::getAppId, appId).last("LIMIT 1"));
    }
    private List<Map<String,Object>> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return JSONUtil.toList(json, (Class<Map<String,Object>>) (Class) Map.class); }
        catch (Exception e) { return List.of(); }
    }
    private String toJson(Object v) { return v instanceof String s ? s : JSONUtil.toJsonStr(v); }
}
