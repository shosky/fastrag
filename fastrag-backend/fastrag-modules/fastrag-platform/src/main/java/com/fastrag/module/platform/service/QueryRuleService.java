package com.fastrag.module.platform.service;
import com.fastrag.module.platform.entity.QueryRule; import java.util.*;
public interface QueryRuleService { List<QueryRule> list(String type); QueryRule create(Map<String,Object> form); void delete(String id); void toggle(String id); }
