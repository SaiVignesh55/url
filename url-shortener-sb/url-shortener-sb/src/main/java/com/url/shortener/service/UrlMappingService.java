package com.url.shortener.service;

import com.url.shortener.dtos.AnalyticsResponseDTO;
import com.url.shortener.dtos.AnalyticsPointDTO;
import com.url.shortener.dtos.ClickEventDTO;
import com.url.shortener.dtos.GeoData;
import com.url.shortener.dtos.UrlAnalyticsResponseDTO;
import com.url.shortener.dtos.UrlMappingDTO;
import com.url.shortener.exception.AliasAlreadyTakenException;
import com.url.shortener.exception.InvalidAliasException;
import com.url.shortener.exception.InvalidUrlException;
import com.url.shortener.models.ClickEvent;
import com.url.shortener.models.UrlMapping;
import com.url.shortener.models.User;
import com.url.shortener.repository.ClickEventRepository;
import com.url.shortener.repository.UrlMappingRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.Optional;

@Service
@Slf4j
@AllArgsConstructor
public class UrlMappingService {

    private static final Pattern CUSTOM_ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9-]+$");
    private static final int CUSTOM_ALIAS_MIN_LENGTH = 3;
    private static final int CUSTOM_ALIAS_MAX_LENGTH = 30;
    private static final int RANDOM_CODE_LENGTH = 8;
    private static final int RANDOM_CODE_MAX_RETRY = 20;
    private static final Set<String> RESERVED_ALIASES = new HashSet<>(Arrays.asList("admin", "login", "api", "dashboard"));

    private final UrlMappingRepository urlMappingRepository;
    private final ClickEventRepository clickEventRepository;
    private final GeoApiService geoApiService;

    @Transactional
    public UrlMappingDTO createShortUrl(String originalUrl, String customAlias, User user) {
        String normalizedUrl = normalizeLongUrl(originalUrl);
        String normalizedAlias = normalizeAlias(customAlias);

        if (normalizedAlias != null) {
            validateCustomAlias(normalizedAlias);
            if (urlMappingRepository.existsByShortUrl(normalizedAlias)) {
                throw new AliasAlreadyTakenException("Alias already taken");
            }
            UrlMapping savedUrlMapping = saveWithShortCode(normalizedUrl, normalizedAlias, user, true);
            logGeneratedShortUrl(savedUrlMapping.getShortUrl());
            return convertToDto(savedUrlMapping);
        }

        UrlMapping savedUrlMapping = saveWithGeneratedShortCode(normalizedUrl, user);
        logGeneratedShortUrl(savedUrlMapping.getShortUrl());
        return convertToDto(savedUrlMapping);
    }

    private UrlMapping saveWithGeneratedShortCode(String normalizedUrl, User user) {
        for (int attempt = 0; attempt < RANDOM_CODE_MAX_RETRY; attempt++) {
            String shortUrl = generateShortUrl();
            if (urlMappingRepository.existsByShortUrl(shortUrl)) {
                continue;
            }

            try {
                return saveWithShortCode(normalizedUrl, shortUrl, user, false);
            } catch (AliasAlreadyTakenException ignored) {
                // Retry with another generated code when a race condition happens.
            }
        }
        throw new IllegalStateException("Unable to generate unique short URL");
    }

    private UrlMapping saveWithShortCode(String normalizedUrl, String shortCode, User user, boolean isCustomAlias) {
        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setOriginalUrl(normalizedUrl);
        urlMapping.setShortUrl(shortCode);
        urlMapping.setUser(user);
        urlMapping.setCreatedDate(LocalDateTime.now());

        try {
            return urlMappingRepository.save(urlMapping);
        } catch (DataIntegrityViolationException ex) {
            String conflictMessage = isCustomAlias ? "Alias already taken" : "Generated short code already exists";
            throw new AliasAlreadyTakenException(conflictMessage);
        }
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
        StringBuilder shortUrl = new StringBuilder(RANDOM_CODE_LENGTH);

        for (int i = 0; i < RANDOM_CODE_LENGTH; i++) {
            shortUrl.append(characters.charAt(random.nextInt(characters.length())));
        }

        return shortUrl.toString();
    }

    private String normalizeLongUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new InvalidUrlException("longUrl is required");
        }
        return originalUrl.trim();
    }

    private String normalizeAlias(String customAlias) {
        if (customAlias == null || customAlias.isBlank()) {
            return null;
        }
        return customAlias.trim();
    }

    private void validateCustomAlias(String alias) {
        if (alias.length() < CUSTOM_ALIAS_MIN_LENGTH || alias.length() > CUSTOM_ALIAS_MAX_LENGTH) {
            throw new InvalidAliasException("Alias length must be between 3 and 30 characters");
        }
        if (!CUSTOM_ALIAS_PATTERN.matcher(alias).matches()) {
            throw new InvalidAliasException("Alias can only contain letters, numbers, and hyphens");
        }
        if (RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new InvalidAliasException("Alias is reserved and cannot be used");
        }
    }

    @Transactional(readOnly = true)
    public List<UrlMappingDTO> getUrlsByUser(User user) {
        return urlMappingRepository.findByUser(user).stream()
                .map(this::convertToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<UrlMapping> getUrlByShortCodeForUser(String shortCode, User user) {
        return urlMappingRepository.findByShortUrlAndUser(shortCode, user);
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

        GeoData geo = geoApiService.getRegionFromIp(ipAddress);
        clickEvent.setCountry(trimValue(geo.getCountry(), 100));
        clickEvent.setRegion(trimValue(geo.getRegion(), 100));
        clickEvent.setCity(trimValue(geo.getCity(), 100));
        log.info("PRE-SAVE CLICK GEO: shortUrl={} ipAddress={} country={} region={} city={}",
                shortUrl,
                clickEvent.getIpAddress(),
                clickEvent.getCountry(),
                clickEvent.getRegion(),
                clickEvent.getCity());
        log.info("COUNTRY SAVED: {}", clickEvent.getCountry());
        System.out.println("Saving city: " + clickEvent.getCity());

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

    private void logGeneratedShortUrl(String slug) {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
        }

        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String shortUrl = normalizedBase + "/r/" + slug;
        System.out.println("Generated URL: " + shortUrl);
    }
}