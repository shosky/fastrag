package com.fastrag.module.application.widget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.application.widget.entity.Widget;
import com.fastrag.module.application.widget.mapper.WidgetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 官方 Widget 模板启动播种（快照 #8）：DB 无对应记录时插入、已存在即跳过不覆盖
 * （SkillDataInitializer 口径）。幂等键 = creator='system' + name。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WidgetBuiltinSeeder implements CommandLineRunner {

    public static final String SYSTEM_CREATOR = "system";

    private final WidgetMapper widgetMapper;

    @Override
    public void run(String... args) {
        int inserted = 0;
        for (BuiltinWidgetTemplates.Spec spec : BuiltinWidgetTemplates.ALL) {
            if (exists(spec.name())) {
                continue;
            }
            Widget w = new Widget();
            w.setName(spec.name());
            w.setDescription(spec.description());
            w.setTemplate(spec.template());
            w.setSchemaJson(spec.schemaJson());
            w.setDefaultJson(spec.defaultJson());
            w.setIsBuiltin(1);
            w.setCreator(SYSTEM_CREATOR);
            w.setCreatedAt(LocalDateTime.now());
            w.setUpdatedAt(LocalDateTime.now());
            widgetMapper.insert(w);
            inserted++;
        }
        if (inserted > 0) {
            log.info("[Widget] 官方模板播种完成：插入 {} 条", inserted);
        }
    }

    private boolean exists(String name) {
        return widgetMapper.selectCount(new LambdaQueryWrapper<Widget>()
                .eq(Widget::getCreator, SYSTEM_CREATOR)
                .eq(Widget::getName, name)) > 0;
    }
}
