package com.fastrag.module.platform.controller;

/**
 * 数据字典管理控制器
 * <p>
 * 提供系统数据字典的增删改查功能，支持按字典类型（type）分组查询。
 * 字典数据用于管理系统中各类枚举值、下拉选项等配置型数据。
 * </p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>GET /api/dictionaries — 查询字典列表，支持按 type 过滤，返回按类型分组的格式化数据</li>
 *   <li>GET /api/dictionaries/types — 获取所有字典类型列表</li>
 *   <li>POST /api/dictionaries — 创建字典条目（type、key、value）</li>
 *   <li>PUT /api/dictionaries/{id} — 更新字典条目</li>
 *   <li>DELETE /api/dictionaries/{id} — 删除字典条目</li>
 * </ul>
 *
 * <p>查询接口对返回数据做了格式化适配，将 {@link com.fastrag.module.platform.entity.SysDictionary}
 * 实体转换为前端所需的 id/key/label/value/enabled/remark 结构。</p>
 *
 * @see com.fastrag.module.platform.service.DictionaryService
 * @see com.fastrag.module.platform.entity.SysDictionary
 */
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.platform.entity.SysDictionary;
import com.fastrag.module.platform.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String type) {
        try {
            Map<String, List<SysDictionary>> grouped;
            if (type != null && !type.isEmpty()) {
                grouped = Map.of(type, dictionaryService.list(type));
            } else {
                grouped = dictionaryService.listAllGroupedByType();
            }

            Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
            for (Map.Entry<String, List<SysDictionary>> entry : grouped.entrySet()) {
                List<Map<String, Object>> items = new ArrayList<>();
                for (SysDictionary d : entry.getValue()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", String.valueOf(d.getId()));
                    item.put("key", d.getDictKey() != null ? d.getDictKey() : "");
                    // label 为空回退 dict_key，兼容存量数据
                    item.put("label", d.getLabel() != null && !d.getLabel().isEmpty()
                            ? d.getLabel() : (d.getDictKey() != null ? d.getDictKey() : ""));
                    item.put("value", d.getDictValue() != null ? d.getDictValue() : "");
                    item.put("enabled", d.getEnabled() == null || d.getEnabled() == 1);
                    item.put("remark", d.getRemark() != null ? d.getRemark() : "");
                    items.add(item);
                }
                result.put(entry.getKey(), items);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.serverError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/types")
    public ApiResponse<?> getTypes() {
        return ApiResponse.success(dictionaryService.listDictTypes());
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, Object> body) {
        SysDictionary dict = new SysDictionary();
        dict.setDictType((String) body.get("type"));
        dict.setDictKey((String) body.get("key"));
        dict.setDictValue((String) body.get("value"));
        dict.setLabel((String) body.get("label"));
        dict.setEnabled(parseEnabled(body.get("enabled"), 1));
        dict.setRemark((String) body.get("remark"));
        dictionaryService.create(dict);
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SysDictionary dict = dictionaryService.getById(id);
        if (dict == null) return ApiResponse.notFound("字典条目不存在");
        dict.setDictType((String) body.getOrDefault("type", dict.getDictType()));
        dict.setDictKey((String) body.getOrDefault("key", dict.getDictKey()));
        dict.setDictValue((String) body.getOrDefault("value", dict.getDictValue()));
        if (body.containsKey("label")) dict.setLabel((String) body.get("label"));
        dict.setEnabled(parseEnabled(body.get("enabled"), dict.getEnabled() != null ? dict.getEnabled() : 1));
        if (body.containsKey("remark")) dict.setRemark((String) body.get("remark"));
        dictionaryService.update(dict);
        return ApiResponse.success();
    }

    /** enabled 入参兼容布尔/数字/字符串，缺省沿用 fallback */
    private static Integer parseEnabled(Object raw, Integer fallback) {
        if (raw instanceof Boolean b) return b ? 1 : 0;
        if (raw instanceof Number n) return n.intValue() == 0 ? 0 : 1;
        if (raw instanceof String s && !s.isBlank()) return Boolean.parseBoolean(s) ? 1 : 0;
        return fallback;
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        dictionaryService.delete(id);
        return ApiResponse.success();
    }
}
