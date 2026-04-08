package com.url.shortener.controller;

import com.url.shortener.security.IpAddressResolver;
import com.url.shortener.security.jwt.JwtUtils;
import com.url.shortener.service.GeoApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GeoDebugController.class)
@AutoConfigureMockMvc(addFilters = false)
class GeoDebugControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeoApiService geoApiService;

    @MockitoBean
    private IpAddressResolver ipAddressResolver;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void shouldReturnDebugIpApiPayload() throws Exception {
        when(ipAddressResolver.getClientIp(org.mockito.ArgumentMatchers.any())).thenReturn("8.8.8.8");
        when(geoApiService.getCityFromIp("8.8.8.8")).thenReturn("Ashburn");

        mockMvc.perform(get("/api/debug/debug-ipapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ip").value("8.8.8.8"))
                .andExpect(jsonPath("$.city").value("Ashburn"));
    }
}

