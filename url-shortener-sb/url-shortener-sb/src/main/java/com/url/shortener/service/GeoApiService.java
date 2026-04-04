package com.url.shortener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.GeoData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class GeoApiService {

    private static final String IPINFO_URL = "https://ipinfo.io/{ip}";

    @Value("${ipinfo.token:}")
    private String apiToken;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public GeoApiService(ObjectMapper objectMapper, @Qualifier("scannerRestTemplate") RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public GeoData getRegionFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return GeoData.unknown();
        }

        try {
            log.info("URL: {}", url);
            String domain = extractDomain(url);
            log.info("DOMAIN: {}", domain);

            String resolvedIp = resolveIp(domain);
            log.info("RESOLVED IP: {}", resolvedIp);

            GeoData geoData = getRegionByIp(resolvedIp);
            log.info("REGION: {}", geoData.getRegion());
            return geoData;
        } catch (Exception ex) {
            log.warn("Failed to resolve region from url={}: {}", url, ex.getMessage());
            return GeoData.unknown();
        }
    }

    private GeoData getRegionByIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return GeoData.unknown();
        }

        if (apiToken == null || apiToken.isBlank()) {
            log.warn("ipinfo token is not configured. Returning UNKNOWN for ip={}", ip);
            return GeoData.unknown();
        }

        try {
            String requestUrl = UriComponentsBuilder
                    .fromUriString(IPINFO_URL)
                    .queryParam("token", apiToken)
                    .buildAndExpand(ip.trim())
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();

            ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.GET, null, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return GeoData.unknown();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            return new GeoData(
                    textOrUnknown(root, "country"),
                    textOrUnknown(root, "region"),
                    textOrUnknown(root, "city")
            );
        } catch (Exception ex) {
            log.warn("ipinfo lookup failed for ip={}: {}", ip, ex.getMessage());
            return GeoData.unknown();
        }
    }

    private String extractDomain(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();

        if (host == null || host.isBlank()) {
            uri = URI.create("https://" + url);
            host = uri.getHost();
        }

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Unable to extract domain");
        }

        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private String resolveIp(String domain) throws Exception {
        InetAddress inet = InetAddress.getByName(domain);
        return inet.getHostAddress();
    }

    private String textOrUnknown(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return "UNKNOWN";
        }
        return node.asText();
    }
}

