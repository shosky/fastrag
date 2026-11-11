package com.fastrag.module.application.widget;

import java.util.List;

/**
 * 官方 Widget 模板内置常量（快照 #8：代码内置、启动播种、DB 无记录时插入不覆盖）。
 * 对标腾讯云 ADP 官方模板库按四场景组织，name 为播种幂等键（creator=system 内唯一）。
 * 全部模板必须通过 WidgetTemplateValidator 三档校验（WidgetBuiltinSeederTest 守护）。
 */
public final class BuiltinWidgetTemplates {

    private BuiltinWidgetTemplates() {}

    public record Spec(String name, String description, String template, String schemaJson, String defaultJson) {}

    public static final List<Spec> ALL = List.of(
            // ===== 信息确认 =====
            new Spec("订单确认卡",
                    "【信息确认】点单/订单信息确认，用户一键确认或取消",
                    """
                    {"type":"Card","props":{"status":"待确认"},"children":[
                      {"type":"Title","props":{"value":"订单 {{order_no}}"}},
                      {"type":"Text","props":{"value":"共 {{count}} 件商品，合计 ¥{{total}}"}},
                      {"type":"Form","props":{"name":"confirm_form"},"children":[
                        {"type":"RadioGroup","props":{"name":"dine_in","label":"用餐方式","options":[{"label":"堂食","value":true},{"label":"外带","value":false}]}},
                        {"type":"Input","props":{"name":"note","label":"备注","placeholder":"口味偏好等"}}
                      ]},
                      {"type":"Button","props":{"label":"确认订单","action":{"type":"sys.submit"}}}
                    ]}""",
                    """
                    {"type":"object","properties":{
                      "order_no":{"type":"string"},"count":{"type":"number"},
                      "total":{"type":"number"},"dine_in":{"type":"boolean"},"note":{"type":"string"}},
                      "required":["order_no","count","total"]}""",
                    """
                    {"order_no":"A-1024","count":2,"total":42,"dine_in":true,"note":""}"""),

            // ===== 过程反馈 =====
            new Spec("任务进度卡",
                    "【过程反馈】长任务进度推进（同 widgetKey 替换实现进度更新）",
                    """
                    {"type":"Card","props":{"status":"{{stage}}"},"children":[
                      {"type":"Title","props":{"value":"任务进行中"}},
                      {"type":"Text","props":{"value":"{{detail}}"}},
                      {"type":"Caption","props":{"value":"进度 {{percent}}%"}}
                    ]}""",
                    """
                    {"type":"object","properties":{
                      "stage":{"type":"string"},"detail":{"type":"string"},"percent":{"type":"number"}},
                      "required":["stage","percent"]}""",
                    """
                    {"stage":"处理中","detail":"正在生成音频","percent":30}"""),

            // ===== 澄清询问 =====
            new Spec("创作需求澄清卡",
                    "【澄清询问】一次性收集创作偏好，减少多轮追问",
                    """
                    {"type":"Card","props":{"status":"需要你的选择"},"children":[
                      {"type":"Title","props":{"value":"确认创作方向"}},
                      {"type":"Text","props":{"value":"{{question}}"}},
                      {"type":"Form","props":{"name":"clarify_form"},"children":[
                        {"type":"RadioGroup","props":{"name":"style","label":"风格","options":[{"label":"{{style_a}}","value":"a"},{"label":"{{style_b}}","value":"b"}]}},
                        {"type":"Checkbox","props":{"name":"with_rhyme","label":"需要押韵"}}
                      ]},
                      {"type":"Button","props":{"label":"就这么定","action":{"type":"sys.submit"}}}
                    ]}""",
                    """
                    {"type":"object","properties":{
                      "question":{"type":"string"},"style_a":{"type":"string"},"style_b":{"type":"string"},"style":{"type":"string"}},
                      "required":["question","style_a","style_b"]}""",
                    """
                    {"question":"歌词想要哪种感觉？","style_a":"民谣叙事","style_b":"流行轻快"}"""),

            // ===== 结果展示 =====
            new Spec("天气结果卡",
                    "【结果展示】结构化展示查询结果",
                    """
                    {"type":"Card","props":{"status":"{{date}}"},"children":[
                      {"type":"Title","props":{"value":"{{city}}天气"}},
                      {"type":"Text","props":{"value":"{{condition}}，{{temp_low}}~{{temp_high}}°C"}},
                      {"type":"Divider","props":{}},
                      {"type":"Row","props":{},"children":[
                        {"type":"Col","props":{},"children":[{"type":"Caption","props":{"value":"湿度 {{humidity}}%"}}]},
                        {"type":"Col","props":{},"children":[{"type":"Caption","props":{"value":"{{wind}}"}}]}
                      ]}
                    ]}""",
                    """
                    {"type":"object","properties":{
                      "city":{"type":"string"},"date":{"type":"string"},"condition":{"type":"string"},
                      "temp_low":{"type":"number"},"temp_high":{"type":"number"},
                      "humidity":{"type":"number"},"wind":{"type":"string"}},
                      "required":["city","condition"]}""",
                    """
                    {"city":"深圳","date":"今天","condition":"多云","temp_low":22,"temp_high":29,"humidity":65,"wind":"东风 3 级"}"""),

            // ===== 澄清询问（表单提交） =====
            new Spec("基础表单收集卡",
                    "【澄清询问】基础信息表单收集（姓名/手机号），一次填完减少追问",
                    """
                    {"type":"Card","props":{"status":"信息收集"},"children":[
                      {"type":"Title","props":{"value":"请完善以下信息"}},
                      {"type":"Form","props":{"name":"profile_form"},"children":[
                        {"type":"Input","props":{"name":"name","label":"姓名","placeholder":"请输入姓名"}},
                        {"type":"Input","props":{"name":"phone","label":"手机号","placeholder":"请输入手机号"}}
                      ]},
                      {"type":"Button","props":{"label":"提交","action":{"type":"sys.submit"}}}
                    ]}""",
                    """
                    {"type":"object","properties":{
                      "name":{"type":"string"},"phone":{"type":"string"}},
                      "required":["name","phone"]}""",
                    """
                    {"name":"","phone":""}"""),

            new Spec("单选澄清卡",
                    "【澄清询问】下拉单选澄清（如视频清晰度偏好）",
                    """
                    {"type":"Card","props":{"status":"需要确认"},"children":[
                      {"type":"Title","props":{"value":"请选择你期望的{{subject}}"}},
                      {"type":"Form","props":{"name":"pick_form"},"children":[
                        {"type":"Select","props":{"name":"choice","label":"请选择","options":[{"label":"{{option_a}}","value":"a"},{"label":"{{option_b}}","value":"b"},{"label":"{{option_c}}","value":"c"}]}}
                      ]},
                      {"type":"Button","props":{"label":"提交","action":{"type":"sys.submit"}}}
                    ]}""",
                    """
                    {"type":"object","properties":{
                      "subject":{"type":"string"},"option_a":{"type":"string"},"option_b":{"type":"string"},
                      "option_c":{"type":"string"},"choice":{"type":"string"}},
                      "required":["subject","option_a","option_b","option_c"]}""",
                    """
                    {"subject":"视频清晰度","option_a":"1080P","option_b":"720P","option_c":"480P","choice":"720P"}"""),

            new Spec("多选澄清卡",
                    "【澄清询问】多选 + 补充说明（确认/跳过双动作）",
                    """
                    {"type":"Card","props":{"status":"多选澄清"},"children":[
                      {"type":"Title","props":{"value":"{{question}}"}},
                      {"type":"Form","props":{"name":"multi_form"},"children":[
                        {"type":"Checkbox","props":{"name":"opt_video","label":"视频作品"}},
                        {"type":"Checkbox","props":{"name":"opt_album","label":"专辑"}},
                        {"type":"Checkbox","props":{"name":"opt_audio","label":"音频"}},
                        {"type":"Checkbox","props":{"name":"opt_software","label":"软件"}},
                        {"type":"Input","props":{"name":"note","label":"补充说明","placeholder":"请输入"}}
                      ]},
                      {"type":"Row","props":{},"children":[
                        {"type":"Col","props":{},"children":[{"type":"Button","props":{"label":"确认","action":{"type":"sys.submit"}}}]},
                        {"type":"Col","props":{},"children":[{"type":"Button","props":{"label":"跳过","action":{"type":"sys.submit","payload":{"skipped":true}}}}]}
                      ]}
                    ]}""",
                    """
                    {"type":"object","properties":{
                      "question":{"type":"string"},"opt_video":{"type":"boolean"},"opt_album":{"type":"boolean"},
                      "opt_audio":{"type":"boolean"},"opt_software":{"type":"boolean"},"note":{"type":"string"}},
                      "required":["question"]}""",
                    """
                    {"question":"您想了解哪些类型的内容？","opt_video":false,"opt_album":false,"opt_audio":false,"opt_software":false,"note":""}"""),

            // ===== 结果展示（列表信息） =====
            new Spec("歌曲列表卡",
                    "【结果展示】列表信息展示（歌名 + 歌手，条目可点）",
                    """
                    {"type":"Card","props":{"status":"共 {{total}} 首"},"children":[
                      {"type":"Title","props":{"value":"歌曲列表"}},
                      {"type":"ListView","props":{},"children":[
                        {"type":"ListViewItem","props":{"gap":"12px"},"children":[
                          {"type":"Text","props":{"value":"{{song1}}","weight":"semibold"}},
                          {"type":"Caption","props":{"value":"{{artist1}}"}},
                          {"type":"Icon","props":{"name":"VideoPlay"}}
                        ]},
                        {"type":"ListViewItem","props":{"gap":"12px"},"children":[
                          {"type":"Text","props":{"value":"{{song2}}","weight":"semibold"}},
                          {"type":"Caption","props":{"value":"{{artist2}}"}},
                          {"type":"Icon","props":{"name":"VideoPlay"}}
                        ]}
                      ]}
                    ]}""",
                    """
                    {"type":"object","properties":{
                      "total":{"type":"number"},"song1":{"type":"string"},"artist1":{"type":"string"},
                      "song2":{"type":"string"},"artist2":{"type":"string"}},
                      "required":["total","song1","song2"]}""",
                    """
                    {"total":2,"song1":"虚空回响","artist1":"星野源","song2":"量子脉动","artist2":"以太阳"}"""),

            new Spec("日程创建确认卡",
                    "【信息确认】日程条目确认，勾选提醒后一键创建",
                    """
                    {"type":"Card","props":{"status":"待确认"},"children":[
                      {"type":"Title","props":{"value":"{{date}} 日程"}},
                      {"type":"ListView","props":{},"children":[
                        {"type":"ListViewItem","props":{"gap":"12px"},"children":[
                          {"type":"Text","props":{"value":"{{item1_time}}","weight":"semibold"}},
                          {"type":"Text","props":{"value":"{{item1_title}}"}}
                        ]},
                        {"type":"ListViewItem","props":{"gap":"12px"},"children":[
                          {"type":"Text","props":{"value":"{{item2_time}}","weight":"semibold"}},
                          {"type":"Text","props":{"value":"{{item2_title}}"}}
                        ]}
                      ]},
                      {"type":"Form","props":{"name":"schedule_form"},"children":[
                        {"type":"Checkbox","props":{"name":"remind","label":"开始前提醒我"}}
                      ]},
                      {"type":"Button","props":{"label":"添加到日程","action":{"type":"sys.submit"}}}
                    ]}""",
                    """
                    {"type":"object","properties":{
                      "date":{"type":"string"},"item1_time":{"type":"string"},"item1_title":{"type":"string"},
                      "item2_time":{"type":"string"},"item2_title":{"type":"string"},"remind":{"type":"boolean"}},
                      "required":["date","item1_title","item2_title"]}""",
                    """
                    {"date":"周五 28","item1_time":"12:00-12:45","item1_title":"午传",
                     "item2_time":"12:45-17:00","item2_title":"产品创建排审","remind":true}"""),

            // ===== 简单信息 =====
            new Spec("身份信息介绍卡",
                    "【简单信息】人物/身份介绍（头像占位 + 简介 + 标签）",
                    """
                    {"type":"Card","props":{},"children":[
                      {"type":"Row","props":{},"children":[
                        {"type":"Col","props":{},"children":[
                          {"type":"Icon","props":{"name":"UserFilled"}},
                          {"type":"Title","props":{"value":"{{name}}"}},
                          {"type":"Caption","props":{"value":"{{job}}"}}
                        ]},
                        {"type":"Col","props":{},"children":[
                          {"type":"Markdown","props":{"value":"{{bio}}"}},
                          {"type":"Row","props":{},"children":[
                            {"type":"Col","props":{},"children":[{"type":"Badge","props":{"value":"{{tag1}}"}}]},
                            {"type":"Col","props":{},"children":[{"type":"Badge","props":{"value":"{{tag2}}"}}]}
                          ]}
                        ]}
                      ]}
                    ]}""",
                    """
                    {"type":"object","properties":{
                      "name":{"type":"string"},"job":{"type":"string"},"bio":{"type":"string"},
                      "tag1":{"type":"string"},"tag2":{"type":"string"}},
                      "required":["name","bio"]}""",
                    """
                    {"name":"董敏言","job":"产品经理","bio":"热衷于 AIGC 与智能体技术的结合，为智能碰撞创造更多可能。",
                     "tag1":"人工智能","tag2":"人类发展"}""")
    );
}
