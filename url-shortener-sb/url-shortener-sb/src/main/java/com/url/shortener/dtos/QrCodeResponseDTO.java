package com.url.shortener.dtos;

import lombok.Data;

@Data
public class QrCodeResponseDTO {
    private String shortCode;
    private String shortUrl;
    private String mimeType;
    private String base64Png;
}

