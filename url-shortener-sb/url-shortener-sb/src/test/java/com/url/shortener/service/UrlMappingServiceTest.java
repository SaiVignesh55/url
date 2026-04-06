package com.url.shortener.service;

import com.url.shortener.dtos.AnalyticsResponseDTO;
import com.url.shortener.dtos.ClickEventDTO;
import com.url.shortener.dtos.GeoData;
import com.url.shortener.dtos.UrlAnalyticsResponseDTO;
import com.url.shortener.dtos.UrlMappingDTO;
import com.url.shortener.exception.AliasAlreadyTakenException;
import com.url.shortener.exception.InvalidAliasException;
import com.url.shortener.models.ClickEvent;
import com.url.shortener.models.UrlMapping;
import com.url.shortener.models.User;
import com.url.shortener.repository.ClickEventRepository;
import com.url.shortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

class UrlMappingServiceTest {

    private UrlMappingRepository urlMappingRepository;
    private ClickEventRepository clickEventRepository;
    private GeoApiService geoApiService;
    private UrlMappingService service;

    @BeforeEach
    void setUp() {
        urlMappingRepository = mock(UrlMappingRepository.class);
        clickEventRepository = mock(ClickEventRepository.class);
        geoApiService = mock(GeoApiService.class);
        when(geoApiService.getRegionFromIp(any())).thenReturn(new GeoData("UNKNOWN", "UNKNOWN", "UNKNOWN"));
        service = new UrlMappingService(urlMappingRepository, clickEventRepository, geoApiService);
    }

    @Test
    void shouldCreateShortUrlWithCustomAliasWhenValid() {
        User user = new User();
        user.setUsername("alice");

        when(urlMappingRepository.existsByShortUrl("my-link")).thenReturn(false);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping persisted = invocation.getArgument(0);
            persisted.setId(100L);
            return persisted;
        });

        UrlMappingDTO result = service.createShortUrl("https://example.com", "my-link", user);

        assertNotNull(result);
        assertEquals("my-link", result.getShortUrl());
        assertEquals("https://example.com", result.getOriginalUrl());
    }

    @Test
    void shouldRejectReservedCustomAlias() {
        User user = new User();

        InvalidAliasException ex = assertThrows(
                InvalidAliasException.class,
                () -> service.createShortUrl("https://example.com", "admin", user)
        );

        assertEquals("Alias is reserved and cannot be used", ex.getMessage());
    }

    @Test
    void shouldRejectInvalidCustomAliasPattern() {
        User user = new User();

        InvalidAliasException ex = assertThrows(
                InvalidAliasException.class,
                () -> service.createShortUrl("https://example.com", "bad alias", user)
        );

        assertEquals("Alias can only contain letters, numbers, and hyphens", ex.getMessage());
    }

    @Test
    void shouldThrowConflictWhenCustomAliasAlreadyExists() {
        User user = new User();
        when(urlMappingRepository.existsByShortUrl("my-link")).thenReturn(true);

        AliasAlreadyTakenException ex = assertThrows(
                AliasAlreadyTakenException.class,
                () -> service.createShortUrl("https://example.com", "my-link", user)
        );

        assertEquals("Alias already taken", ex.getMessage());
    }

    @Test
    void shouldPersistClickEventAndIncrementCountOnRedirect() {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(1L);
        mapping.setShortUrl("abc123");
        mapping.setOriginalUrl("https://example.com");
        mapping.setClickCount(0);

        when(urlMappingRepository.findByShortUrl("abc123")).thenReturn(mapping);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clickEventRepository.save(any(ClickEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(geoApiService.getRegionFromIp("203.0.113.10")).thenReturn(new GeoData("India", "Andhra Pradesh", "Guntur"));

        UrlMapping resolved = service.getOriginalUrl("abc123", "203.0.113.10", "Mozilla/5.0");

        assertNotNull(resolved);
        assertEquals(1, resolved.getClickCount());

        verify(urlMappingRepository).save(mapping);
        ArgumentCaptor<ClickEvent> clickCaptor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository, atLeastOnce()).save(clickCaptor.capture());

        ClickEvent savedEvent = clickCaptor.getValue();
        assertEquals("India", savedEvent.getCountry());
        assertEquals("Andhra Pradesh", savedEvent.getRegion());
        assertEquals("Guntur", savedEvent.getCity());
    }

    @Test
    void shouldReturnNullForMissingShortCode() {
        when(urlMappingRepository.findByShortUrl("missing")).thenReturn(null);

        UrlMapping resolved = service.getOriginalUrl("missing", "203.0.113.10", "Mozilla/5.0");

        assertNull(resolved);
    }

    @Test
    void shouldAggregateClicksByDateForUser() {
        User user = new User();

        ClickEventRepository.DailyClickCountProjection projection = new ClickEventRepository.DailyClickCountProjection() {
            @Override
            public LocalDate getClickDate() {
                return LocalDate.of(2026, 4, 2);
            }

            @Override
            public Long getCount() {
                return 3L;
            }
        };

        when(clickEventRepository.aggregateDailyClicksByUser(eq(user), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(projection));
        when(clickEventRepository.countByUrlMappingUserAndClickDateGreaterThanEqualAndClickDateLessThan(
                eq(user), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(3L);

        AnalyticsResponseDTO response = service.getTotalClicksByUserAndDate(
                user,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3)
        );

        assertEquals(3L, response.getTotalClicks());
        assertEquals(1, response.getClicksByDate().size());

        ClickEventDTO clickEventDTO = response.getClicksByDate().get(0);
        assertEquals(LocalDate.of(2026, 4, 2), clickEventDTO.getClickDate());
        assertEquals(3L, clickEventDTO.getCount());

        verify(clickEventRepository).aggregateDailyClicksByUser(eq(user), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void shouldAggregateClicksByDateForSingleShortUrl() {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(7L);

        ClickEventRepository.DailyClickCountProjection projection = new ClickEventRepository.DailyClickCountProjection() {
            @Override
            public LocalDate getClickDate() {
                return LocalDate.of(2026, 4, 1);
            }

            @Override
            public Long getCount() {
                return 5L;
            }
        };

        when(urlMappingRepository.findByShortUrl("short007")).thenReturn(mapping);
        when(clickEventRepository.aggregateDailyClicksByUrlMappingId(eq(7L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(projection));

        List<ClickEventDTO> response = service.getClickEventsByDate(
                "short007",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3)
        );

        assertEquals(1, response.size());
        assertEquals(LocalDate.of(2026, 4, 1), response.get(0).getClickDate());
        assertEquals(5L, response.get(0).getCount());

        verify(clickEventRepository).aggregateDailyClicksByUrlMappingId(eq(7L), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void shouldReturnNullWhenAnalyticsShortCodeDoesNotExist() {
        when(urlMappingRepository.findByShortUrl("missing123")).thenReturn(null);

        UrlAnalyticsResponseDTO response = service.getUrlAnalyticsByShortCode(
                "missing123",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3)
        );

        assertNull(response);
    }

    @Test
    void shouldReturnZeroClicksAndEmptySeriesWhenNoClickRows() {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(21L);
        mapping.setShortUrl("abc12345");
        mapping.setOriginalUrl("https://example.com");

        when(urlMappingRepository.findByShortUrl("abc12345")).thenReturn(mapping);
        when(clickEventRepository.aggregateDailyClicksByUrlMappingId(eq(21L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        UrlAnalyticsResponseDTO response = service.getUrlAnalyticsByShortCode(
                "abc12345",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3)
        );

        assertNotNull(response);
        assertEquals("abc12345", response.getShortCode());
        assertEquals("https://example.com", response.getOriginalUrl());
        assertEquals(0L, response.getTotalClicks());
        assertEquals(0, response.getClicksOverTime().size());
    }
}

