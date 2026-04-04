package com.url.shortener.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "click_event", indexes = {
        @Index(name = "idx_click_event_url_date", columnList = "url_mapping_id, click_date"),
        @Index(name = "idx_click_event_click_date", columnList = "click_date")
})
@Data
public class ClickEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "click_date", nullable = false)
    private LocalDateTime clickDate;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "city", length = 100)
    private String city;

    @ManyToOne(optional = false)
    @JoinColumn(name = "url_mapping_id", nullable = false)
    private UrlMapping urlMapping;
}