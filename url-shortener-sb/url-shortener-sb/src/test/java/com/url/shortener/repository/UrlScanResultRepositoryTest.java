package com.url.shortener.repository;

import com.url.shortener.models.UrlScanResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class UrlScanResultRepositoryTest {

    @Autowired
    private UrlScanResultRepository urlScanResultRepository;

    @Test
    void shouldInsertScanResultRow() {
        UrlScanResult result = new UrlScanResult();
        result.setUrl("https://example.com");
        result.setFinalVerdict("SAFE");
        result.setScore(5);
        result.setMalwareScore(0);
        result.setPhishingScore(0);
        result.setSpamScore(5);
        result.setRedirectRisk(3);
        result.setDomainRisk(2);
        result.setCreatedAt(LocalDateTime.now());

        UrlScanResult saved = urlScanResultRepository.save(result);

        assertNotNull(saved.getId());
        assertEquals(1L, urlScanResultRepository.count());
    }
}

