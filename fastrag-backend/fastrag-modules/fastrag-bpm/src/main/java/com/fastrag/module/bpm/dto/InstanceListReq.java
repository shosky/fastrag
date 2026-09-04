package com.fastrag.module.bpm.dto;
import lombok.Data;

@Data
public class InstanceListReq {
    private Integer page = 1;
    private Integer size = 20;
    private String flowDefId;
    private String status;
    private String keyword;
    private String startUserId;
}