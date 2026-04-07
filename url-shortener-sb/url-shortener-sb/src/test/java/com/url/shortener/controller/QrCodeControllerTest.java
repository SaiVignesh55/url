package com.url.shortener.controller;

import com.url.shortener.models.UrlMapping;
import com.url.shortener.models.User;
import com.url.shortener.security.IpAddressResolver;
import com.url.shortener.security.jwt.JwtUtils;
import com.url.shortener.service.QrCodeService;
import com.url.shortener.service.UrlMappingService;
import com.url.shortener.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QrCodeController.class)
@AutoConfigureMockMvc(addFilters = false)
class QrCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlMappingService urlMappingService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private QrCodeService qrCodeService;

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
    void shouldReturnQrCodeResponse() throws Exception {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortUrl("my-link");

        String encoded = Base64.getEncoder().encodeToString("png".getBytes());

        when(urlMappingService.getUrlByShortCodeForUser(eq("my-link"), any(User.class))).thenReturn(Optional.of(mapping));
        when(qrCodeService.generateQRCodeBase64("http://localhost:8080/r/my-link")).thenReturn(encoded);

        mockMvc.perform(get("/api/qr/my-link").principal(() -> "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("my-link"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/r/my-link"))
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.base64Png").value(encoded));
    }

    @Test
    @WithMockUser(username = "tester", roles = "USER")
    void shouldReturnNotFoundWhenShortCodeDoesNotExist() throws Exception {
        when(urlMappingService.getUrlByShortCodeForUser(eq("missing"), any(User.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/qr/missing").principal(() -> "tester"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Short URL not found"));
    }
}

