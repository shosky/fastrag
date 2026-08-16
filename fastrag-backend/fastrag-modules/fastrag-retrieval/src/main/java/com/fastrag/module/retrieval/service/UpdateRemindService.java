package com.fastrag.module.retrieval.service;

/**
 * 知识库更新提醒服务接口。
 *
 * <p>定义知识库更新提醒的配置管理能力，包括提醒配置的增删改查，
 * 以及触发提醒时查询自上次提醒以来的更新次数。</p>
 *
 * @see KbUpdateRemind
 */
import com.fastrag.module.retrieval.entity.KbUpdateRemind; import java.util.*;
public interface UpdateRemindService {
    List<KbUpdateRemind> list(String kbId);
    KbUpdateRemind get(String kbId);
    KbUpdateRemind save(KbUpdateRemind remind);
    void delete(String id);
    Map<String,Object> remind(String kbId);
}
