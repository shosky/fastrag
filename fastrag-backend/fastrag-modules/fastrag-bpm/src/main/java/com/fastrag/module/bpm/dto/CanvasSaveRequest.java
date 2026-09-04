package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.util.List;

@Data
public class CanvasSaveRequest {
    private String canvasData;
    private List<NodeRequest> nodes;
    private List<EdgeRequest> edges;
}