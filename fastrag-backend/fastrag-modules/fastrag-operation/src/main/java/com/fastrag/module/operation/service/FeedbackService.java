package com.fastrag.module.operation.service;
import com.fastrag.module.operation.entity.UserFeedback; import java.util.*;
public interface FeedbackService { List<UserFeedback> list(String kbId); void create(UserFeedback fb); }
