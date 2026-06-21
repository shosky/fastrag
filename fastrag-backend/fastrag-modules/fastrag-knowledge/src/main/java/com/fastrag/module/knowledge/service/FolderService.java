package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.model.*; import java.util.*;
public interface FolderService { List<FolderNodeDto> list(String kbId); FolderNodeDto create(String kbId,String name,String parentId); String getName(String kbId,String folderId); }
