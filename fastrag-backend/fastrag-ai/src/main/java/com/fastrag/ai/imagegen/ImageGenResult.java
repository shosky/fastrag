package com.fastrag.ai.imagegen;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenResult {
    private byte[] imageBytes;
    private String contentType;   // e.g. image/png
    private String filename;      // e.g. img_xxx.png
    private String mimeType;
}
