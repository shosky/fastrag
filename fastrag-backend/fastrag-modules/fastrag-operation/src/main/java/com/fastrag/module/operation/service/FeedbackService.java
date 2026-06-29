package com.fastrag.module.operation.service;
import com.fastrag.common.response.PageResult; import com.fastrag.module.operation.entity.UserFeedback; import java.util.*;
public interface FeedbackService {
    PageResult<UserFeedback> page(String kbId,String feedback,String status,int page,int pageSize);
    List<UserFeedback> list(String kbId);
    void create(UserFeedback fb);
    UserFeedback update(Long id,UserFeedback fb);
    void delete(Long id);
    UserFeedback reply(Long id,String reply,String operator);
    Map<String,Object> statistics(String kbId);
}
