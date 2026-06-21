package com.fastrag.common.base;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public abstract class BaseEntity implements Serializable {
    private String id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
