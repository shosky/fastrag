package com.fastrag.module.bpm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fastrag.module.bpm.dto.*;
import com.fastrag.module.bpm.entity.BpmFlowInstance;

public interface BpmInstanceService extends IService<BpmFlowInstance> {
    /** 触发一个新实例:创建记录,放入队列,返回 instanceId */
    String trigger(InstanceTriggerRequest req);
    /** 分页查询实例 */
    PageResult<InstanceVO> list(InstanceListReq req);
    /** 实例详情(含 inputs/outputs Map 转换) */
    InstanceVO detail(String instanceId);
    /** 实例事件列表(按时间 asc) */
    java.util.List<?> events(String instanceId);
    /** 暂停 */
    void pause(String instanceId);
    /** 恢复 */
    void resume(String instanceId);
    /** 终止 */
    void cancel(String instanceId, String reason);
    /** 用户提交输入(由 user_input 节点挂起时调用) */
    void submitUserInput(SubmitInputRequest req);
    /** 实际执行一个实例(由 Scheduler 调用) */
    void run(String instanceId);
}