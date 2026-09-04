package com.fastrag.module.bpm.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fastrag.module.bpm.dto.TestCaseRequest;
import com.fastrag.module.bpm.entity.BpmFlowTestCase;

import java.util.List;

public interface BpmTestCaseService extends IService<BpmFlowTestCase> {
    List<BpmFlowTestCase> listByFlow(String flowDefId);
    BpmFlowTestCase create(String flowDefId, TestCaseRequest req, String operatorId);
    void delete(String id);
    /** 同步触发测试用例,完成后回写 actual_output + match_result + last_instance_id */
    BpmFlowTestCase run(String id);
}