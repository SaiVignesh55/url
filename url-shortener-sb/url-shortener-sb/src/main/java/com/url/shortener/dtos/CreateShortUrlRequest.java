package com.url.shortener.dtos;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateShortUrlRequest {

    @NotBlank(message = "longUrl is required")
    @JsonAlias("originalUrl")
    private String longUrl;

    private String customAlias;
}
