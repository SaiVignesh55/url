package com.url.shortener.repository;

import com.url.shortener.models.UrlScanResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UrlScanResultRepository extends JpaRepository<UrlScanResult, Long> {
	Optional<UrlScanResult> findByScannedUrl(String scannedUrl);

	Optional<UrlScanResult> findTopByScannedUrlOrderByCreatedAtDesc(String scannedUrl);
}

