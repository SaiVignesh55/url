package com.url.shortener.service;

import com.url.shortener.dtos.AnalyticsResponseDTO;
import com.url.shortener.dtos.AnalyticsPointDTO;
import com.url.shortener.dtos.ClickEventDTO;
import com.url.shortener.dtos.GeoData;
import com.url.shortener.dtos.UrlAnalyticsResponseDTO;
import com.url.shortener.dtos.UrlMappingDTO;
import com.url.shortener.models.ClickEvent;
import com.url.shortener.models.UrlMapping;
import com.url.shortener.models.User;
import com.url.shortener.repository.ClickEventRepository;
import com.url.shortener.repository.UrlMappingRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
@AllArgsConstructor
public class UrlMappingService {

    private final UrlMappingRepository urlMappingRepository;
    private final ClickEventRepository clickEventRepository;
    private final GeoApiService geoApiService;

    public UrlMappingDTO createShortUrl(String originalUrl, User user) {
        String shortUrl = generateShortUrl();
        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setOriginalUrl(originalUrl);
        urlMapping.setShortUrl(shortUrl);
        urlMapping.setUser(user);
        urlMapping.setCreatedDate(LocalDateTime.now());
        UrlMapping savedUrlMapping = urlMappingRepository.save(urlMapping);
        return convertToDto(savedUrlMapping);
    }

    private UrlMappingDTO convertToDto(UrlMapping urlMapping) {
        UrlMappingDTO urlMappingDTO = new UrlMappingDTO();
        urlMappingDTO.setId(urlMapping.getId());
        urlMappingDTO.setOriginalUrl(urlMapping.getOriginalUrl());
        urlMappingDTO.setShortUrl(urlMapping.getShortUrl());
        urlMappingDTO.setClickCount(urlMapping.getClickCount());
        urlMappingDTO.setCreatedDate(urlMapping.getCreatedDate());
        urlMappingDTO.setUsername(urlMapping.getUser().getUsername());
        return urlMappingDTO;
    }

    private String generateShortUrl() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder shortUrl = new StringBuilder(8);

        for (int i = 0; i < 8; i++) {
            shortUrl.append(characters.charAt(random.nextInt(characters.length())));
        }

        return shortUrl.toString();
    }

    public List<UrlMappingDTO> getUrlsByUser(User user) {
        return urlMappingRepository.findByUser(user).stream()
                .map(this::convertToDto)
                .toList();
    }

    public List<ClickEventDTO> getClickEventsByDate(String shortUrl, LocalDate startDate, LocalDate endDate) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null) {
            return List.of();
        }

        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEndExclusive = endDate.plusDays(1).atStartOfDay();

        return clickEventRepository.aggregateDailyClicksByUrlMappingId(urlMapping.getId(), rangeStart, rangeEndExclusive)
                .stream()
                .map(this::toClickEventDTO)
                .toList();
    }

    public UrlAnalyticsResponseDTO getUrlAnalyticsByShortCode(String shortCode, LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Analytics request received shortCode={} startDate={} endDate={}", shortCode, startDate, endDate);

            UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortCode);
            log.info("Analytics URL lookup result shortCode={} found={}", shortCode, urlMapping != null);

            if (urlMapping == null) {
                return null;
            }

            LocalDate resolvedStart = startDate;
            LocalDate resolvedEnd = endDate;
            if (resolvedStart.isAfter(resolvedEnd)) {
                LocalDate temp = resolvedStart;
                resolvedStart = resolvedEnd;
                resolvedEnd = temp;
                log.warn("Analytics date range swapped for shortCode={} startDate={} endDate={}", shortCode, resolvedStart, resolvedEnd);
            }

            LocalDateTime rangeStart = resolvedStart.atStartOfDay();
            LocalDateTime rangeEndExclusive = resolvedEnd.plusDays(1).atStartOfDay();

            List<ClickEventRepository.DailyClickCountProjection> projections =
                    clickEventRepository.aggregateDailyClicksByUrlMappingId(urlMapping.getId(), rangeStart, rangeEndExclusive);

            List<AnalyticsPointDTO> clicksOverTime = new ArrayList<>();
            long totalClicks = 0L;
            for (ClickEventRepository.DailyClickCountProjection projection : projections) {
                AnalyticsPointDTO point = new AnalyticsPointDTO();
                point.setDate(projection.getClickDate().toString());
                point.setCount(projection.getCount());
                clicksOverTime.add(point);
                totalClicks += projection.getCount();
            }

            log.info("Analytics click records fetched shortCode={} records={}", shortCode, clicksOverTime.size());

            UrlAnalyticsResponseDTO response = new UrlAnalyticsResponseDTO();
            response.setShortCode(urlMapping.getShortUrl());
            response.setOriginalUrl(urlMapping.getOriginalUrl());
            response.setTotalClicks(totalClicks);
            response.setClicksOverTime(clicksOverTime);
            return response;
        } catch (Exception ex) {
            log.error("Analytics processing failed for shortCode={}", shortCode, ex);
            throw new RuntimeException("Analytics processing failed", ex);
        }
    }

    public AnalyticsResponseDTO getTotalClicksByUserAndDate(User user, LocalDate start, LocalDate end) {
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEndExclusive = end.plusDays(1).atStartOfDay();

        List<ClickEventDTO> clicksByDate = clickEventRepository.aggregateDailyClicksByUser(user, rangeStart, rangeEndExclusive)
                .stream()
                .map(this::toClickEventDTO)
                .toList();

        long totalClicks = clickEventRepository.countByUrlMappingUserAndClickDateGreaterThanEqualAndClickDateLessThan(
                user,
                rangeStart,
                rangeEndExclusive
        );

        AnalyticsResponseDTO response = new AnalyticsResponseDTO();
        response.setTotalClicks(totalClicks);
        response.setClicksByDate(clicksByDate);
        return response;
    }

    @Transactional
    public UrlMapping getOriginalUrl(String shortUrl, String ipAddress, String userAgent) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null) {
            return null;
        }

        urlMapping.setClickCount(urlMapping.getClickCount() + 1);
        urlMappingRepository.save(urlMapping);

        ClickEvent clickEvent = new ClickEvent();
        clickEvent.setClickDate(LocalDateTime.now(ZoneOffset.UTC));
        clickEvent.setUrlMapping(urlMapping);
        clickEvent.setIpAddress(trimValue(ipAddress, 64));
        clickEvent.setUserAgent(trimValue(userAgent, 1024));

        GeoData geo = geoApiService.getRegionFromUrl(urlMapping.getOriginalUrl());
        clickEvent.setCountry(trimValue(geo.getCountry(), 100));
        clickEvent.setRegion(trimValue(geo.getRegion(), 100));
        clickEvent.setCity(trimValue(geo.getCity(), 100));
        log.info("PRE-SAVE CLICK GEO: shortUrl={} targetUrl={} country={} region={} city={}",
                shortUrl,
                urlMapping.getOriginalUrl(),
                clickEvent.getCountry(),
                clickEvent.getRegion(),
                clickEvent.getCity());

        clickEventRepository.save(clickEvent);
        log.info("SAVED TO DB");

        log.info("Saved click event for shortUrl={} urlMappingId={} timestamp={} ip={}",
                shortUrl,
                urlMapping.getId(),
                clickEvent.getClickDate(),
                clickEvent.getIpAddress());

        return urlMapping;
    }

    private ClickEventDTO toClickEventDTO(ClickEventRepository.DailyClickCountProjection projection) {
        ClickEventDTO clickEventDTO = new ClickEventDTO();
        clickEventDTO.setClickDate(projection.getClickDate());
        clickEventDTO.setCount(projection.getCount());
        return clickEventDTO;
    }

    private String trimValue(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}