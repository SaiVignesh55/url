package com.url.shortener.dtos;

import lombok.Data;

@Data
public class GeoDebugResponse {
    private String url;
    private String domain;
    private String resolvedIp;
    private boolean loopbackIp;
    private boolean tokenPresent;
    private String ipapiRequestUrl;
    private String httpStatus;
    private String rawResponse;
    private String country;
    private String region;
    private String city;
    private String fallbackReason;
    private boolean success;
}

