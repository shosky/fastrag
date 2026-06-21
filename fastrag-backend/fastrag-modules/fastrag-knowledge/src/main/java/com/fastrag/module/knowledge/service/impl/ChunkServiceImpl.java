package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.module.knowledge.entity.KbChunk; import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.service.ChunkService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class ChunkServiceImpl implements ChunkService {
    private final KbChunkMapper mapper;
    @Override public Map<String,Object> list(String kbId,String fileId,int page,int pageSize) {
        var w=new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId,kbId); if(fileId!=null&&!fileId.isBlank())w.eq(KbChunk::getFileId,fileId);
        w.orderByAsc(KbChunk::getChunkIndex); var r=mapper.selectPage(new Page<>(page,pageSize),w);
        var result=new HashMap<String,Object>(); result.put("list",r.getRecords()); result.put("total",r.getTotal()); result.put("page",page); result.put("pageSize",pageSize); return result;
    }
    @Override public long getCount(String kbId) { return mapper.selectCount(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId,kbId)); }
}
