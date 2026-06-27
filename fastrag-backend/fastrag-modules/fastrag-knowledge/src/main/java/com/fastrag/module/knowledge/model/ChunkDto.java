package com.fastrag.module.knowledge.model;
import lombok.Data;
@Data public class ChunkDto { private String fileId,fileName,content; private int chunkIndex; private Double startTime,endTime; }
