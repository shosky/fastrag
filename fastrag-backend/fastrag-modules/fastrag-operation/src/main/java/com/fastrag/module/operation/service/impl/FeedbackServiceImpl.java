package com.fastrag.module.operation.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.operation.entity.UserFeedback; import com.fastrag.module.operation.mapper.UserFeedbackMapper;
import com.fastrag.module.operation.service.FeedbackService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {
    private final UserFeedbackMapper mapper;
    @Override public List<UserFeedback> list(String kbId) { var w=new LambdaQueryWrapper<UserFeedback>(); if(kbId!=null)w.eq(UserFeedback::getKbId,kbId); return mapper.selectList(w.orderByDesc(UserFeedback::getCreatedAt)); }
    @Override public void create(UserFeedback fb) { mapper.insert(fb); }
}
