package com.url.shortener.controller;

import com.url.shortener.models.UrlMapping;
import com.url.shortener.service.UrlMappingService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@AllArgsConstructor
public class RedirectController {

    private UrlMappingService urlMappingService;

    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code){
        UrlMapping urlMapping = urlMappingService.getOriginalUrl(code);
        if (urlMapping != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(urlMapping.getOriginalUrl()))
                    .build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}