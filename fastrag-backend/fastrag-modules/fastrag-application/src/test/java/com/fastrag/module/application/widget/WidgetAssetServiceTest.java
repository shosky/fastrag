package com.fastrag.module.application.widget;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.application.widget.dto.WidgetSnapshot;
import com.fastrag.module.application.widget.entity.Widget;
import com.fastrag.module.application.widget.mapper.WidgetMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S2 资产生命周期 + S6 resolveSnapshot（tdd/widget.md；REQ-WG-A01~A05/D02/D03）：
 * 服务层公共接口 + mock WidgetMapper（MyBatis-Plus 边界）。
 */
@ExtendWith(MockitoExtension.class)
class WidgetAssetServiceTest {

    @Mock
    private WidgetMapper widgetMapper;

    private WidgetAssetServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // 复用件不 mock：WidgetTemplateValidator 直用真实现（S1 已单独覆盖）
        service = new WidgetAssetServiceImpl(widgetMapper, new WidgetTemplateValidator());
    }

    private Widget row(String id, String creator, int builtin) {
        Widget w = new Widget();
        w.setId(id);
        w.setName("订单确认卡");
        w.setTemplate("{\"type\":\"Card\",\"props\":{},\"children\":[]}");
        w.setSchemaJson("{\"type\":\"object\",\"properties\":{\"total\":{\"type\":\"number\"}},\"required\":[\"total\"]}");
        w.setDefaultJson("{\"total\": 42}");
        w.setIsBuiltin(builtin);
        w.setCreator(creator);
        return w;
    }

    // ===== S6 快照解析（fail-closed 家族） =====

    @Test
    @DisplayName("[执行] 绑定集合内合法调用时 [快照五要素定格]")
    void resolveSnapshotReturnsFrozenFiveElements() {
        when(widgetMapper.selectById("w1")).thenReturn(row("w1", "u1", 0));
        WidgetSnapshot snap = service.resolveSnapshot("w1", "order-key", Map.of("total", 42), Set.of("w1"));
        assertThat(snap.widgetId()).isEqualTo("w1");
        assertThat(snap.widgetKey()).isEqualTo("order-key");
        assertThat(snap.name()).isEqualTo("订单确认卡");
        assertThat(snap.template()).contains("Card");
        assertThat(snap.data()).containsEntry("total", 42);
    }

    @Test
    @DisplayName("[执行] 集合外 widgetId 时 [fail-closed]")
    void resolveSnapshotRejectsOutsideBindingSet() {
        assertThatThrownBy(() -> service.resolveSnapshot("w2", "k", Map.of("total", 1), Set.of("w1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未在本次对话绑定集合");
    }

    @Test
    @DisplayName("[执行] 资产执行时刻被删时 [fail-closed]")
    void resolveSnapshotRejectsDeletedAsset() {
        when(widgetMapper.selectById("w1")).thenReturn(null);
        assertThatThrownBy(() -> service.resolveSnapshot("w1", "k", Map.of("total", 1), Set.of("w1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已删除");
    }

    @Test
    @DisplayName("[执行] data 不合 schema 时 [回灌自纠错误]")
    void resolveSnapshotRejectsSchemaViolation() {
        when(widgetMapper.selectById("w1")).thenReturn(row("w1", "u1", 0));
        assertThatThrownBy(() -> service.resolveSnapshot("w1", "k", Map.of(), Set.of("w1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("校验失败")
                .hasMessageContaining("total");
    }

    @Test
    @DisplayName("[执行] data 超 32KB 时 [护栏拒绝]")
    void resolveSnapshotRejectsOversizeData() {
        when(widgetMapper.selectById("w1")).thenReturn(row("w1", "u1", 0));
        Map<String, Object> big = Map.of("total", 1, "blob", "字".repeat(20000));
        assertThatThrownBy(() -> service.resolveSnapshot("w1", "k", big, Set.of("w1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("32KB");
    }

    // ===== S2 生命周期 =====

    @Test
    @DisplayName("[创建] 合法草稿时 [落库归属当前用户]")
    void createAssignsCreatorAndNonBuiltin() {
        service.create("订单确认卡", "desc", null,
                "{\"type\":\"Card\",\"props\":{},\"children\":[{\"type\":\"Title\",\"props\":{\"value\":\"标题\"}}]}",
                "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}", null, "u1");
        org.mockito.ArgumentCaptor<Widget> captor = org.mockito.ArgumentCaptor.forClass(Widget.class);
        verify(widgetMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreator()).isEqualTo("u1");
        assertThat(captor.getValue().getIsBuiltin()).isZero();
    }

    @Test
    @DisplayName("[修改/删除] 官方模板行时 [只读拒绝]")
    void builtinRowIsReadOnly() {
        when(widgetMapper.selectById("w9")).thenReturn(row("w9", "system", 1));
        assertThatThrownBy(() -> service.update("w9", "n", null, null, "{}", "{}", null, "u1", false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("只读");
        assertThatThrownBy(() -> service.delete("w9", "u1", false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("只读");
    }

    @Test
    @DisplayName("[读取] 非属主读他人自建时 [拒绝]")
    void nonOwnerReadRejected() {
        when(widgetMapper.selectById("w1")).thenReturn(row("w1", "u1", 0));
        assertThatThrownBy(() -> service.get("w1", "u2", false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("无权访问");
    }

    @Test
    @DisplayName("[复制] 官方行时 [生成独立资产]")
    void copyProducesIndependentAsset() {
        when(widgetMapper.selectById("w9")).thenReturn(row("w9", "system", 1));
        service.copy("w9", "u2");
        org.mockito.ArgumentCaptor<Widget> captor = org.mockito.ArgumentCaptor.forClass(Widget.class);
        verify(widgetMapper).insert(captor.capture());
        Widget copy = captor.getValue();
        assertThat(copy.getIsBuiltin()).isZero();
        assertThat(copy.getCreator()).isEqualTo("u2");
        assertThat(copy.getTemplate()).contains("Card");
        assertThat(copy.getId()).isNull(); // ASSIGN_ID 落库时生成，非继承源行
    }

    @Test
    @DisplayName("[删除] 解绑后删除成功 [直通]")
    void deleteSucceedsWhenUnbound() {
        when(widgetMapper.selectById("w1")).thenReturn(row("w1", "u1", 0));
        service.delete("w1", "u1", false);
        verify(widgetMapper).deleteById("w1");
    }

    @Test
    @DisplayName("[列表] 管理员全量 [非管理员按属主过滤]")
    void listFiltersByOwnerForNonAdmin() {
        when(widgetMapper.selectList(any())).thenReturn(java.util.List.of());
        service.list(false, "u1", true);
        verify(widgetMapper).selectList(org.mockito.ArgumentMatchers.argThat(q ->
                q == null || !q.toString().contains("creator =")));
        service.list(false, "u1", false);
        verify(widgetMapper, org.mockito.Mockito.times(2)).selectList(any());
    }
}
