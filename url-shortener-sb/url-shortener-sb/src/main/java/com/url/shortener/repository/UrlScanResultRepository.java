package com.url.shortener.repository;

import com.url.shortener.models.UrlScanResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlScanResultRepository extends JpaRepository<UrlScanResult, Long> {
	Page<UrlScanResult> findByStatusIgnoreCase(String status, Pageable pageable);
}

