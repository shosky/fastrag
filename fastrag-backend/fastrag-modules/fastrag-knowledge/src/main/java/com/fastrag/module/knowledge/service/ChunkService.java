package com.fastrag.module.knowledge.service;
import java.util.*;
public interface ChunkService { Map<String,Object> list(String kbId,String fileId,int page,int pageSize); long getCount(String kbId); }
