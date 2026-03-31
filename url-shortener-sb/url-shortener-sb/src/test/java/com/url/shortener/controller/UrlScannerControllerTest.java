package com.url.shortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.dtos.UrlScanAsyncStatusResponse;
import com.url.shortener.dtos.UrlScanRequest;
import com.url.shortener.dtos.UrlScanResponse;
import com.url.shortener.service.UrlScannerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UrlScannerController.class)
@AutoConfigureMockMvc(addFilters = false)
class UrlScannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UrlScannerService urlScannerService;

    @Test
    void shouldSubmitAsyncScanAndReturnAccepted() throws Exception {
        when(urlScannerService.submitAsyncScan("https://example.com")).thenReturn("job-123");

        UrlScanRequest request = new UrlScanRequest();
        request.setUrl("  https://example.com  ");

        mockMvc.perform(post("/api/scan/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(urlScannerService, times(1)).submitAsyncScan(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("https://example.com", captor.getValue());
    }

    @Test
    void shouldReturnBadRequestForInvalidAsyncScanBody() throws Exception {
        UrlScanRequest request = new UrlScanRequest();
        request.setUrl("   ");

        mockMvc.perform(post("/api/scan/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.jobId").doesNotExist())
                .andExpect(jsonPath("$.status").value("INVALID_REQUEST"));

        verify(urlScannerService, never()).submitAsyncScan(anyString());
    }

    @Test
    void shouldReturnAsyncJobStatus() throws Exception {
        UrlScanResponse result = new UrlScanResponse(
                "SAFE",
                "Scan completed",
                "https://example.com",
                0,
                List.of("No threat match found")
        );
        UrlScanAsyncStatusResponse statusResponse = new UrlScanAsyncStatusResponse(
                "job-123",
                "COMPLETED",
                result,
                null
        );
        when(urlScannerService.getAsyncScanStatus("job-123")).thenReturn(statusResponse);

        mockMvc.perform(get("/api/scan/async/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.scannedUrl").value("https://example.com"));
    }

    @Test
    void shouldPropagateNotFoundForMissingAsyncJob() throws Exception {
        when(urlScannerService.getAsyncScanStatus("missing-job"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Async scan job not found"));

        mockMvc.perform(get("/api/scan/async/missing-job"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldPropagateTooManyRequestsOnAsyncOverload() throws Exception {
        when(urlScannerService.submitAsyncScan("https://example.com"))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many async scan jobs"));

        UrlScanRequest request = new UrlScanRequest();
        request.setUrl("https://example.com");

        mockMvc.perform(post("/api/scan/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }
}

