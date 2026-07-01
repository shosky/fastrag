package com.fastrag.module.application.service.impl;
import cn.hutool.core.util.StrUtil; import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.application.entity.*; import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.AppService;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service @RequiredArgsConstructor
public class AppServiceImpl implements AppService {
    private final AppMapper appMapper; private final AppConfigMapper configMapper; private final AppTemplateMapper tplMapper;

    @Override public List<App> list(String kw,String tag) { var w=new LambdaQueryWrapper<App>(); if(StrUtil.isNotBlank(kw))w.like(App::getName,kw); if(StrUtil.isNotBlank(tag))w.like(App::getTags,tag); return appMapper.selectList(w.orderByDesc(App::getCreatedAt)); }
    @Override public App get(String id) { return appMapper.selectById(id); }
    @Override public App create(Map<String,Object> f) { var a=new App(); a.setName((String)f.get("name")); a.setDescription((String)f.get("description")); a.setType((String)f.getOrDefault("type","ChatBot")); a.setStatus("draft"); appMapper.insert(a); return a; }
    @Override public App update(String id,Map<String,Object> f) { var a=appMapper.selectById(id); if(a!=null){if(f.containsKey("name"))a.setName((String)f.get("name")); if(f.containsKey("description"))a.setDescription((String)f.get("description")); appMapper.updateById(a);} return a; }
    @Override public void delete(String id) { appMapper.deleteById(id); }
    @Override public List<AppTemplate> getTemplates() { return tplMapper.selectList(null); }
    @Override public AppConfig getConfig(String id) { return configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,id)); }
    @Override public AppConfig saveConfig(String id,AppConfig config) { config.setAppId(id); var existing=configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,id)); if(existing!=null){config.setId(existing.getId());configMapper.updateById(config);}else{configMapper.insert(config);} return config; }

    /** 关键词 → 回答规则库 */
    private static final List<String[]> QA_RULES = List.of(
        new String[]{"请假", "关于请假制度，一般规定如下：\n1. 员工请假需提前提交申请，经直属主管审批同意后方可休假。\n2. 请假类型包括：事假、病假、年假、婚假、产假、丧假等。\n3. 请假天数根据类型和工龄有所不同，具体天数请参考公司考勤管理制度。\n4. 紧急情况下可电话请假，但需在返岗后 24 小时内补办手续。"},
        new String[]{"打卡", "关于打卡考勤：\n1. 工作日需在规定时间内完成上下班打卡。\n2. 迟到/早退按公司考勤制度处理。\n3. 因公外出无法打卡的，需提前在系统中报备并经主管确认。\n4. 忘记打卡的，需在当月内提交补卡申请。"},
        new String[]{"考勤", "关于考勤管理：\n1. 公司实行标准工时制，工作时间为周一至周五。\n2. 考勤方式包括指纹打卡、人脸识别或移动端打卡。\n3. 每月考勤数据由人事部门统一汇总核算。\n4. 连续旷工超过 3 天视为严重违纪，按公司制度处理。"},
        new String[]{"报销", "关于费用报销：\n1. 员工因公产生的费用，需在费用发生后 30 日内提交报销申请。\n2. 报销需提供正规发票或有效凭证。\n3. 单笔报销金额超过 5000 元的，需部门负责人审批。\n4. 报销款项将在审批通过后的下一个工资发放日一并发放。"},
        new String[]{"薪资,工资,薪酬", "关于薪资福利：\n1. 公司每月 15 日发放上月工资，遇节假日顺延。\n2. 薪资结构包括基本工资、绩效奖金、岗位津贴等。\n3. 年终奖根据公司业绩和个人绩效考核结果确定。\n4. 薪资信息属个人隐私，请勿互相打探。"},
        new String[]{"培训,学习", "关于培训发展：\n1. 公司定期组织内部培训和外部学习机会。\n2. 新员工入职后需完成为期一周的入职培训。\n3. 部门每月至少组织一次专业技能培训。\n4. 员工可申请外部培训，经审批后费用由公司承担。"},
        new String[]{"入职,试用期", "关于入职与试用期：\n1. 新员工入职需提交身份证、学历证明、体检报告等材料。\n2. 试用期为 1-6 个月，根据岗位和合同约定执行。\n3. 试用期内享有正式员工同等的劳动保护和基本福利。\n4. 试用期考核合格的，按期转正；不合格的可延长试用或解除合同。"},
        new String[]{"离职,辞职", "关于离职办理：\n1. 员工辞职需提前 30 日以书面形式通知公司。\n2. 试用期内辞职需提前 3 日通知。\n3. 离职前需完成工作交接、资产归还、费用结算等手续。\n4. 离职证明在完成所有交接手续后由人事部门开具。"},
        new String[]{"福利,社保,公积金", "关于福利保障：\n1. 公司按规定为员工缴纳五险一金（养老、医疗、失业、工伤、生育保险及住房公积金）。\n2. 每年提供一次免费健康体检。\n3. 节假日按国家规定放假，部分节日发放福利礼品。\n4. 员工享有带薪年假，天数根据工龄计算。"},
        new String[]{"绩效,考核", "关于绩效考核：\n1. 公司实行月度考核与年度综合考核相结合的机制。\n2. 考核维度包括工作业绩、工作态度、团队协作等。\n3. 考核结果与绩效奖金、晋升调薪直接挂钩。\n4. 对考核结果有异议的，可在结果公布后 5 个工作日内申诉。"},
        new String[]{"合同,签约", "关于劳动合同：\n1. 入职即签订劳动合同，合同期限根据岗位和协商确定。\n2. 合同到期前 30 日，双方协商是否续签。\n3. 合同变更需双方书面同意并签署补充协议。\n4. 劳动合同一式两份，公司和员工各执一份。"},
        new String[]{"流程,审批", "关于审批流程：\n1. 一般事项由直属主管审批。\n2. 涉及费用支出的，需经部门负责人和财务部双重审批。\n3. 重大事项需总经理或分管领导审批。\n4. 审批周期一般为 1-3 个工作日，紧急事项可加急处理。"},
        new String[]{"制度,规定,规范", "关于公司制度：\n1. 公司制定了完善的规章制度体系，包括人事、行政、财务、安全等方面。\n2. 制度文件发布后需全员知悉并遵照执行。\n3. 制度的修订需经相关部门会签和管理层审批。\n4. 如需了解具体某项制度的详细内容，请向人事行政部门咨询。"},
        new String[]{"你好,您好,hi,hello", "您好！我是智能问答助手，可以帮您查询公司各类规章制度和常见问题，例如请假、考勤、报销、薪资福利等。请问有什么可以帮到您的？"},
        new String[]{"谢谢,感谢", "不客气！如果您还有其他问题，随时可以继续提问。祝您工作愉快！"},
        new String[]{"你是谁,什么,功能", "我是企业智能问答助手，主要功能包括：\n1. 查询公司规章制度（请假、考勤、报销等）\n2. 解答人事相关政策（入职、离职、合同等）\n3. 提供薪资福利、培训发展等信息\n4. 解答日常办公流程问题\n您可以直接输入问题，我会尽力为您解答。"}
    );

    @Override
    public Map<String,Object> run(String id, String query) {
        var r = new HashMap<String,Object>();
        r.put("sessionId", UUID.randomUUID().toString());
        r.put("answer", matchAnswer(query));
        return r;
    }

    /** 根据用户输入匹配最相关的回答 */
    private String matchAnswer(String query) {
        if (StrUtil.isBlank(query)) return "请输入您的问题，我会尽力为您解答。";
        String lower = query.toLowerCase().trim();

        // 遍历规则库，找到匹配度最高的回答
        String bestAnswer = null;
        int bestScore = 0;
        for (String[] rule : QA_RULES) {
            String[] keywords = rule[0].split(",");
            int score = 0;
            for (String kw : keywords) {
                if (lower.contains(kw.trim().toLowerCase())) score += kw.length();
            }
            if (score > bestScore) {
                bestScore = score;
                bestAnswer = rule[1];
            }
        }

        if (bestAnswer != null) return bestAnswer;

        // 兜底：根据问题长度和类型生成通用回复
        if (lower.contains("?") || lower.contains("？") || lower.contains("吗") || lower.contains("怎么") || lower.contains("如何") || lower.contains("什么") || lower.contains("哪里") || lower.contains("能否")) {
            return "关于您提到的「" + query + "」，目前我的知识库中暂未收录相关内容。建议您：\n1. 尝试换一种表述方式重新提问\n2. 咨询相关部门或同事获取准确信息\n3. 在知识库中上传相关文档后，我可以为您提供更精准的回答。";
        }
        return "感谢您的提问。关于「" + query + "」，我目前暂时无法给出准确的回答。您可以尝试更具体地描述您的问题，或者联系相关部门获取帮助。";
    }
}
