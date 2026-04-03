package com.url.shortener.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "url_scan_results", indexes = {
        @Index(name = "idx_url_scan_results_url", columnList = "url"),
        @Index(name = "idx_url_scan_results_scanned_url", columnList = "scanned_url"),
        @Index(name = "idx_url_scan_results_created_at", columnList = "created_at")
})
@Data
public class UrlScanResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "scanned_url", nullable = false, length = 2048)
    private String scannedUrl;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "message", length = 512)
    private String message;

    @Column(name = "reasons", columnDefinition = "TEXT")
    private String reasons;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Column(name = "final_verdict", nullable = false, length = 32)
    private String finalVerdict;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "malware_score", nullable = false)
    private Integer malwareScore;

    @Column(name = "phishing_score", nullable = false)
    private Integer phishingScore;

    @Column(name = "spam_score", nullable = false)
    private Integer spamScore;

    @Column(name = "redirect_risk", nullable = false)
    private Integer redirectRisk;

    @Column(name = "domain_risk", nullable = false)
    private Integer domainRisk;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

