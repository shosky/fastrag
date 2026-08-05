package com.fastrag.module.knowledge.model;
import lombok.Data; import java.util.*;
@Data public class FolderNodeDto { private String id,label; private String createdAt; private String updatedAt; private List<FolderNodeDto> children = new ArrayList<>(); }
