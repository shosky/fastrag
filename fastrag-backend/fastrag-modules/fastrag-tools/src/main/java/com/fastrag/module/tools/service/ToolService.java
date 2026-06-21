package com.fastrag.module.tools.service;
import com.fastrag.module.tools.entity.*; import java.util.*;
public interface ToolService { List<Tool> list(String keyword,String type); Tool get(String id); Tool create(Map<String,Object> form); Tool update(String id,Map<String,Object> form); void delete(String id); void toggleEnabled(String id); }
