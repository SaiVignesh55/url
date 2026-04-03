package com.url.shortener.service;

import com.url.shortener.dtos.UrlScanResponse;
import com.url.shortener.models.UrlScanResult;
import com.url.shortener.repository.UrlScanResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlScanResultService {

    private final UrlScanResultRepository urlScanResultRepository;

    @Transactional
    public void saveScanResult(UrlScanResponse response) {
        UrlScanResult scanResult = new UrlScanResult();
        scanResult.setUrl(normalizeUrl(response.getScannedUrl(), response.getFinalUrl()));
        scanResult.setFinalVerdict(normalizeVerdict(response.getVerdict(), response.getStatus()));
        scanResult.setScore(response.getFinalScore());
        scanResult.setMalwareScore(response.getMalwareScore());
        scanResult.setPhishingScore(response.getPhishingScore());
        scanResult.setSpamScore(response.getSpamScore());
        scanResult.setRedirectRisk(response.getRedirectScore());
        scanResult.setDomainRisk(response.getDomainScore());
        scanResult.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));

        try {
            urlScanResultRepository.save(scanResult);
        } catch (Exception ex) {
            log.error("Failed to save url scan result for url={} verdict={}", scanResult.getUrl(), scanResult.getFinalVerdict(), ex);
            throw ex;
        }
    }

    private String normalizeUrl(String scannedUrl, String finalUrl) {
        if (scannedUrl != null && !scannedUrl.isBlank()) {
            return scannedUrl;
        }
        if (finalUrl != null && !finalUrl.isBlank()) {
            return finalUrl;
        }
        return "unknown";
    }

    private String normalizeVerdict(String verdict, String status) {
        if (verdict != null && !verdict.isBlank()) {
            return verdict;
        }
        if (status != null && !status.isBlank()) {
            return status;
        }
        return "UNKNOWN";
    }
}

