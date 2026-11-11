package com.fastrag.module.application.widget;

import com.fastrag.module.application.widget.dto.WidgetCatalogEntry;
import com.fastrag.module.application.widget.dto.WidgetSnapshot;
import com.fastrag.module.application.widget.entity.Widget;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Widget 资产域唯一深接口（IF-004）：快照解析、注入清单组装、三档保存校验 + 资产生命周期。
 * 调用方：render_widget 执行器、清单注入编排层、WidgetController、绑定服务层。
 * 错误模式（ERR-WG-*）由 docs/interfaces.md 声明，本接口以 BusinessException(code,message) 抛出。
 */
public interface WidgetAssetService {

    /** 快照解析：widgetId ∈ allowedWidgetIds（fail-closed）+ 数据结构校验（≤32KB）+ 定格五要素；
     *  widgetKey 由调用方（工具实参）透传（实现期修订：设计稿签名遗漏该形参，见模块档 Deviations） */
    WidgetSnapshot resolveSnapshot(String widgetId, String widgetKey, Map<String, Object> data, Set<String> allowedWidgetIds);

    /** 注入清单组装：已删除/不可见资产跳过并告警（清单降级不报错）；调用方负责 5000 字符截断渲染 */
    List<WidgetCatalogEntry> buildCatalog(List<String> widgetIds);

    /** 三档保存校验：JSON 合法 / 白名单+必填属性 / 变量一致 + default 可反序列化；失败抛逐项行级业务错误 */
    void validateDraft(String name, String template, String schemaJson, String defaultJson);

    // ===== 资产生命周期（WidgetController 服务面） =====

    /** 列表：builtin=true 官方模板视图（全员）；false=属主过滤（管理员全量） */
    List<Widget> list(boolean builtin, String userId, boolean admin);

    Widget get(String id, String userId, boolean admin);

    /** 创建：三档校验 + is_builtin=0 + creator=当前用户；返回落库行 */
    Widget create(String name, String description, String icon,
                  String template, String schemaJson, String defaultJson, String userId);

    /** 修改：属主校验 + 官方行只读（409）+ 三档校验；保存即生效 */
    void update(String id, String name, String description, String icon,
                String template, String schemaJson, String defaultJson, String userId, boolean admin);

    /** 删除：官方行只读（409）；被绑定拒删（409 ERR-WG-BOUND-DELETE） */
    void delete(String id, String userId, boolean admin);

    /** 复制：深拷贝三件套 → 新行 is_builtin=0、creator=当前用户；返回新行 */
    Widget copy(String id, String userId);
}
