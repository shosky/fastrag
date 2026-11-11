package com.fastrag.module.application.widget.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 应用-Widget 绑定（app_widget_binding 表，第六类绑定）：N:N，enabled=0 禁用即两条链路
 * （清单注入+卡片渲染）失效。同 (app_id, widget_id) 唯一（uk_app_widget）。
 */
@Data
@TableName("app_widget_binding")
public class AppWidgetBinding {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String appId;

    private String widgetId;

    /** 0=禁用：不注入清单、不渲染卡片，等同未绑定 */
    private Integer enabled;

    private java.time.LocalDateTime createdAt;
}
