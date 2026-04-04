package com.url.shortener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.GeoDebugResponse;
import com.url.shortener.dtos.GeoData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
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
            log.error("Region lookup failed: original URL is null/blank");
            return GeoData.unknown();
        }

        try {
            log.info("URL: {}", url);
            String domain = extractDomain(url);
            log.info("DOMAIN: {}", domain);

            String resolvedIp = resolveIp(domain);
            log.info("RESOLVED IP: {}", resolvedIp);

            if (resolvedIp == null || resolvedIp.isBlank() || isLoopbackIp(resolvedIp)) {
                log.error("Region lookup failed: invalid resolved IP={} for domain={}", resolvedIp, domain);
                return GeoData.unknown();
            }

            GeoData geoData = getRegionByIp(resolvedIp);
            log.info("REGION: {}", geoData.getRegion());
            return geoData;
        } catch (Exception ex) {
            log.warn("Failed to resolve region from url={}: {}", url, ex.getMessage());
            return GeoData.unknown();
        }
    }

    public GeoDebugResponse debugRegionFromUrl(String url) {
        GeoDebugResponse debug = new GeoDebugResponse();
        debug.setUrl(url);
        debug.setSuccess(false);

        if (url == null || url.isBlank()) {
            debug.setFallbackReason("URL is null or blank");
            debug.setCountry("UNKNOWN");
            debug.setRegion("UNKNOWN");
            debug.setCity("UNKNOWN");
            return debug;
        }

        try {
            String domain = extractDomain(url);
            debug.setDomain(domain);

            String resolvedIp = resolveIp(domain);
            debug.setResolvedIp(resolvedIp);
            debug.setLoopbackIp(isLoopbackIp(resolvedIp));

            if (resolvedIp == null || resolvedIp.isBlank()) {
                debug.setFallbackReason("DNS resolved empty IP");
                applyUnknown(debug);
                return debug;
            }
            if (debug.isLoopbackIp()) {
                debug.setFallbackReason("DNS resolved loopback IP");
                applyUnknown(debug);
                return debug;
            }

            if (apiToken == null || apiToken.isBlank()) {
                debug.setTokenPresent(false);
                debug.setFallbackReason("ipinfo token missing");
                applyUnknown(debug);
                return debug;
            }

            debug.setTokenPresent(true);
            String requestUrl = UriComponentsBuilder
                    .fromUriString(IPINFO_URL)
                    .queryParam("token", apiToken)
                    .buildAndExpand(resolvedIp.trim())
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();
            debug.setIpinfoRequestUrl(requestUrl.replace(apiToken, "XXX"));

            ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.GET, null, String.class);
            HttpStatusCode statusCode = response.getStatusCode();
            debug.setHttpStatus(statusCode.toString());
            debug.setRawResponse(response.getBody());

            if (!statusCode.is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                debug.setFallbackReason("ipinfo response was non-2xx or empty");
                applyUnknown(debug);
                return debug;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            debug.setCountry(textOrUnknown(root, "country"));
            debug.setRegion(textOrUnknown(root, "region"));
            debug.setCity(textOrUnknown(root, "city"));

            if ("UNKNOWN".equals(debug.getRegion())) {
                debug.setFallbackReason("region field missing/blank in ipinfo response");
            }
            if ("UNKNOWN".equals(debug.getCountry()) && debug.getFallbackReason() == null) {
                debug.setFallbackReason("country field missing/blank in ipinfo response");
            }

            debug.setSuccess(!"UNKNOWN".equals(debug.getRegion()));
            return debug;
        } catch (Exception ex) {
            debug.setFallbackReason(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            applyUnknown(debug);
            return debug;
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

            log.info("IPINFO REQUEST: {}", requestUrl.replace(apiToken, "XXX"));
            log.info("IPINFO TOKEN PRESENT: {}", apiToken != null && !apiToken.isBlank());

            ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.GET, null, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                log.error("ipinfo response invalid for ip={} status={}", ip, response.getStatusCode());
                return GeoData.unknown();
            }

            log.info("IPINFO RAW RESPONSE: {}", response.getBody());

            JsonNode root = objectMapper.readTree(response.getBody());
            String country = textOrUnknown(root, "country");
            String region = textOrUnknown(root, "region");
            String city = textOrUnknown(root, "city");

            if ("UNKNOWN".equals(region)) {
                log.error("Region missing/null in ipinfo response for ip={}", ip);
            }
            if ("UNKNOWN".equals(country)) {
                log.error("Country missing/null in ipinfo response for ip={}", ip);
            }

            return new GeoData(country, region, city);
        } catch (Exception ex) {
            log.warn("ipinfo lookup failed for ip={}: {}", ip, ex.getMessage());
            return GeoData.unknown();
        }
    }

    private String extractDomain(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String host = uri.getHost();

        if (host == null || host.isBlank()) {
            uri = new URI("https://" + url);
            host = uri.getHost();
        }

        if (host == null || host.isBlank()) {
            log.error("Domain extraction failed for URL={}", url);
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

    private boolean isLoopbackIp(String ip) {
        return ip.startsWith("127.") || "0.0.0.0".equals(ip) || "::1".equals(ip);
    }

    private void applyUnknown(GeoDebugResponse debug) {
        debug.setCountry("UNKNOWN");
        debug.setRegion("UNKNOWN");
        debug.setCity("UNKNOWN");
        debug.setSuccess(false);
    }
}

