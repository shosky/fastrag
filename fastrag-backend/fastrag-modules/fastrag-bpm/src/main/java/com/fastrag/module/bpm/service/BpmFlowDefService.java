package com.fastrag.module.bpm.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fastrag.module.bpm.dto.*;
import com.fastrag.module.bpm.entity.BpmFlowDef;

import java.util.List;

public interface BpmFlowDefService extends IService<BpmFlowDef> {
    /** 分页查询(支持关键字/分类/可见性/我的过滤) */
    PageResult<FlowDefVO> page(FlowDefPageReq req);
    /** 列表查询(精简,用于下拉) */
    List<FlowDefVO> listSimple(String keyword, String visibility);
    /** 详情(含当前版本号 + 最近 5 个版本) */
    FlowDefVO detail(String id);
    /** 创建(同时创建首个 draft 版本) */
    FlowDefVO create(FlowDefRequest req, String operatorId);
    /** 更新基本信息(不影响版本内容) */
    FlowDefVO update(String id, FlowDefRequest req);
    /** 删除(物理,带级联校验) */
    void delete(String id);
    /** 复制流程(深拷贝草稿,ownerId=current) */
    FlowDefVO copy(String id, String newName, String operatorId);
}