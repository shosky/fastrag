package com.fastrag.module.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.agent.entity.AgentRun;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AgentRunMapper extends BaseMapper<AgentRun> {

    @Select("SELECT * FROM agent_run WHERE request_id = #{requestId} LIMIT 1")
    AgentRun selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM agent_run WHERE thread_id = #{threadId} AND status IN ('pending', 'running') LIMIT 1")
    AgentRun selectByThreadIdAndStatus(@Param("threadId") String threadId);
}
