package com.fastrag.module.application.widget;

import com.fastrag.module.application.widget.entity.Widget;
import com.fastrag.module.application.widget.mapper.WidgetMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S3 官方模板播种幂等（tdd/widget.md S3；REQ-WG-B01）：
 * 首启全量插入 / 重启存在即跳过不覆盖 / 部分缺失只补缺。
 */
@ExtendWith(MockitoExtension.class)
class WidgetBuiltinSeederTest {

    @Mock
    private WidgetMapper widgetMapper;

    @Test
    @DisplayName("[播种] 空库启动时 [全量插入]")
    void seedsAllWhenEmpty() {
        when(widgetMapper.selectCount(any())).thenReturn(0L);
        new WidgetBuiltinSeeder(widgetMapper).run();
        ArgumentCaptor<Widget> captor = ArgumentCaptor.forClass(Widget.class);
        verify(widgetMapper, times(BuiltinWidgetTemplates.ALL.size())).insert(captor.capture());
        List<Widget> inserted = captor.getAllValues();
        assertThat(inserted).allSatisfy(w -> {
            assertThat(w.getIsBuiltin()).isEqualTo(1);
            assertThat(w.getCreator()).isEqualTo("system");
        });
        assertThat(inserted).extracting(Widget::getName)
                .containsExactlyInAnyOrderElementsOf(BuiltinWidgetTemplates.ALL.stream().map(BuiltinWidgetTemplates.Spec::name).toList());
    }

    @Test
    @DisplayName("[播种] 重启时 [存在即跳过不覆盖]")
    void skipsExistingOnRestart() {
        when(widgetMapper.selectCount(any())).thenReturn(1L);
        new WidgetBuiltinSeeder(widgetMapper).run();
        verify(widgetMapper, never()).insert(any(Widget.class));
        verify(widgetMapper, never()).updateById(any(Widget.class));
    }

    @Test
    @DisplayName("[播种] 部分缺失时 [只补缺失]")
    void seedsOnlyMissing() {
        // 第一条存在、其余缺失：selectCount 第一次返回 1，之后返回 0
        when(widgetMapper.selectCount(any())).thenReturn(1L, 0L, 0L, 0L);
        new WidgetBuiltinSeeder(widgetMapper).run();
        ArgumentCaptor<Widget> captor = ArgumentCaptor.forClass(Widget.class);
        verify(widgetMapper, times(BuiltinWidgetTemplates.ALL.size() - 1)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(Widget::getName)
                .doesNotContain(BuiltinWidgetTemplates.ALL.get(0).name());
    }

    @Test
    @DisplayName("[播种] 全部内置模板 [通过三档校验守护]")
    void allBuiltinSpecsPassThreeTierValidation() {
        // 官方模板是门面：任一条模板坏档（JSON/白名单外组件/变量不一致）直接测试失败
        WidgetTemplateValidator validator = new WidgetTemplateValidator();
        for (BuiltinWidgetTemplates.Spec spec : BuiltinWidgetTemplates.ALL) {
            var errors = validator.validate(spec.name(), spec.template(), spec.schemaJson(), spec.defaultJson());
            assertThat(errors).as("内置模板「%s」应通过三档校验", spec.name()).isEmpty();
        }
    }
}
