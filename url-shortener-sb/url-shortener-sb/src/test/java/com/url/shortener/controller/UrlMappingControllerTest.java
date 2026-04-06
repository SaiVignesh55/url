package com.url.shortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.url.shortener.exception.AliasAlreadyTakenException;
import com.url.shortener.exception.InvalidAliasException;
import com.url.shortener.models.User;
import com.url.shortener.security.IpAddressResolver;
import com.url.shortener.security.jwt.JwtUtils;
import com.url.shortener.service.UrlMappingService;
import com.url.shortener.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UrlMappingController.class)
@AutoConfigureMockMvc(addFilters = false)
class UrlMappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UrlMappingService urlMappingService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private IpAddressResolver ipAddressResolver;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("tester");
        when(userService.findByUsername("tester")).thenReturn(user);
    }

    @Test
    @WithMockUser(username = "tester", roles = "USER")
    void shouldReturnBadRequestWhenAliasIsInvalid() throws Exception {
        Map<String, String> requestBody = new LinkedHashMap<>();
        requestBody.put("longUrl", "https://example.com");
        requestBody.put("customAlias", "bad alias");

        when(urlMappingService.createShortUrl(eq("https://example.com"), eq("bad alias"), any(User.class)))
                .thenThrow(new InvalidAliasException("Alias can only contain letters, numbers, and hyphens"));

        mockMvc.perform(post("/api/urls/shorten")
                        .principal(() -> "tester")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Alias can only contain letters, numbers, and hyphens"));

        verify(urlMappingService, times(1)).createShortUrl("https://example.com", "bad alias", user);
    }

    @Test
    @WithMockUser(username = "tester", roles = "USER")
    void shouldReturnConflictWhenAliasAlreadyExists() throws Exception {
        Map<String, String> requestBody = new LinkedHashMap<>();
        requestBody.put("longUrl", "https://example.com");
        requestBody.put("customAlias", "my-link");

        when(urlMappingService.createShortUrl(eq("https://example.com"), eq("my-link"), any(User.class)))
                .thenThrow(new AliasAlreadyTakenException("Alias already taken"));

        mockMvc.perform(post("/api/urls/shorten")
                        .principal(() -> "tester")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Alias already taken"));

        verify(urlMappingService, times(1)).createShortUrl("https://example.com", "my-link", user);
    }

    @Test
    @WithMockUser(username = "tester", roles = "USER")
    void shouldReturnBadRequestWhenLongUrlIsMissing() throws Exception {
        Map<String, String> requestBody = new LinkedHashMap<>();
        requestBody.put("customAlias", "my-link");

        mockMvc.perform(post("/api/urls/shorten")
                        .principal(() -> "tester")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("longUrl is required"));

        verifyNoInteractions(urlMappingService);
    }
}

