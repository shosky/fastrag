package com.fastrag.module.application.widget;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.application.widget.dto.WidgetCatalogEntry;
import com.fastrag.module.application.widget.dto.WidgetSnapshot;
import com.fastrag.module.application.widget.entity.Widget;
import com.fastrag.module.application.widget.mapper.WidgetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WidgetAssetService 实现：资产域全部行为（校验/白名单/属主过滤/播种辅助/快照组装）隐藏在此。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WidgetAssetServiceImpl implements WidgetAssetService {

    private final WidgetMapper widgetMapper;
    private final WidgetTemplateValidator validator;

    // ===== 深接口三方法 =====

    @Override
    public WidgetSnapshot resolveSnapshot(String widgetId, String widgetKey, Map<String, Object> data, Set<String> allowedWidgetIds) {
        // fail-closed：集合外 / 空白 / 执行时刻已删（查不到）一律拒绝（REQ-WG-D02）
        if (widgetId == null || widgetId.isBlank()
                || allowedWidgetIds == null || !allowedWidgetIds.contains(widgetId)) {
            throw new BusinessException(404, "Error: 卡片未在本次对话绑定集合中，请勿调用未绑定的 widgetId");
        }
        Widget widget = widgetMapper.selectById(widgetId);
        if (widget == null) {
            throw new BusinessException(404, "Error: 卡片不存在或已删除：" + widgetId);
        }
        Map<String, Object> safeData = data == null ? Map.of() : data;
        if (JSONUtil.toJsonStr(safeData).getBytes(StandardCharsets.UTF_8).length
                > WidgetComponentWhitelist.MAX_DATA_BYTES) {
            throw new BusinessException(400, "Error: 卡片数据超过 32KB 上限，请精简 data");
        }
        List<WidgetTemplateValidator.Error> errors = validator.validateData(widget.getSchemaJson(), safeData);
        if (!errors.isEmpty()) {
            throw new BusinessException(400, "Error: 卡片数据校验失败：" + joinLines(errors));
        }
        return new WidgetSnapshot(widget.getId(), widgetKey == null ? "" : widgetKey,
                widget.getName(), widget.getTemplate(), safeData);
    }

    @Override
    public List<WidgetCatalogEntry> buildCatalog(List<String> widgetIds) {
        List<WidgetCatalogEntry> catalog = new ArrayList<>();
        if (widgetIds == null || widgetIds.isEmpty()) {
            return catalog;
        }
        List<Widget> rows = widgetMapper.selectBatchIds(widgetIds);
        Map<String, Widget> byId = new LinkedHashMap<>();
        for (Widget w : rows) {
            byId.put(w.getId(), w);
        }
        for (String id : widgetIds) {
            Widget w = byId.get(id);
            if (w == null) {
                // 已删除/不可见：清单降级不报错（IF-004 契约）
                log.warn("[Widget] buildCatalog 跳过缺失资产：{}", id);
                continue;
            }
            catalog.add(new WidgetCatalogEntry(w.getId(), w.getName(), w.getDescription(),
                    w.getSchemaJson(), w.getDefaultJson()));
        }
        return catalog;
    }

    @Override
    public void validateDraft(String name, String template, String schemaJson, String defaultJson) {
        List<WidgetTemplateValidator.Error> errors = validator.validate(name, template, schemaJson, defaultJson);
        if (!errors.isEmpty()) {
            throw new BusinessException(400, joinLines(errors));
        }
    }

    // ===== 资产生命周期 =====

    @Override
    public List<Widget> list(boolean builtin, String userId, boolean admin) {
        LambdaQueryWrapper<Widget> qw = new LambdaQueryWrapper<Widget>().orderByDesc(Widget::getUpdatedAt);
        if (builtin) {
            qw.eq(Widget::getIsBuiltin, 1);
        } else if (!admin) {
            qw.eq(Widget::getCreator, userId);
        }
        return widgetMapper.selectList(qw);
    }

    @Override
    public Widget get(String id, String userId, boolean admin) {
        Widget w = requireWidget(id);
        requireReadable(w, userId, admin);
        return w;
    }

    @Override
    public Widget create(String name, String description, String icon,
                         String template, String schemaJson, String defaultJson, String userId) {
        validateDraft(name, template, schemaJson, defaultJson);
        Widget w = new Widget();
        w.setName(name);
        w.setDescription(description);
        w.setIcon(icon);
        w.setTemplate(template);
        w.setSchemaJson(schemaJson);
        w.setDefaultJson(defaultJson);
        w.setIsBuiltin(0);
        w.setCreator(userId);
        w.setCreatedAt(LocalDateTime.now());
        w.setUpdatedAt(LocalDateTime.now());
        widgetMapper.insert(w);
        return w;
    }

    @Override
    public void update(String id, String name, String description, String icon,
                       String template, String schemaJson, String defaultJson, String userId, boolean admin) {
        Widget w = requireWidget(id);
        requireBuiltinGuard(w);
        requireOwner(w, userId, admin);
        validateDraft(name, template, schemaJson, defaultJson);
        w.setName(name);
        w.setDescription(description);
        w.setIcon(icon);
        w.setTemplate(template);
        w.setSchemaJson(schemaJson);
        w.setDefaultJson(defaultJson);
        w.setUpdatedAt(LocalDateTime.now());
        widgetMapper.updateById(w);
    }

    @Override
    public void delete(String id, String userId, boolean admin) {
        Widget w = requireWidget(id);
        requireBuiltinGuard(w);
        requireOwner(w, userId, admin);
        widgetMapper.deleteById(id);
    }

    @Override
    public Widget copy(String id, String userId) {
        Widget source = requireWidget(id);
        Widget copy = new Widget();
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setIcon(source.getIcon());
        copy.setTemplate(source.getTemplate());
        copy.setSchemaJson(source.getSchemaJson());
        copy.setDefaultJson(source.getDefaultJson());
        copy.setIsBuiltin(0);
        copy.setCreator(userId);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        widgetMapper.insert(copy);
        return copy;
    }

    // ===== 内部 =====

    private Widget requireWidget(String id) {
        Widget w = widgetMapper.selectById(id);
        if (w == null) {
            throw new BusinessException(404, "Widget 不存在：" + id);
        }
        return w;
    }

    private void requireReadable(Widget w, String userId, boolean admin) {
        if (w.getIsBuiltin() != null && w.getIsBuiltin() == 1) {
            return; // 官方行全员可读
        }
        requireOwner(w, userId, admin);
    }

    private void requireBuiltinGuard(Widget w) {
        if (w.getIsBuiltin() != null && w.getIsBuiltin() == 1) {
            throw new BusinessException(409, "官方模板行为只读，仅可复制");
        }
    }

    private void requireOwner(Widget w, String userId, boolean admin) {
        boolean owner = userId != null && userId.equals(w.getCreator());
        if (!owner && !admin) {
            throw new BusinessException(403, "无权访问他人 Widget");
        }
    }

    private String joinLines(List<WidgetTemplateValidator.Error> errors) {
        StringBuilder sb = new StringBuilder();
        for (WidgetTemplateValidator.Error e : errors) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append('[').append(e.piece()).append("] ").append(e.message());
        }
        return sb.toString();
    }
}
