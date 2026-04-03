package com.url.shortener.controller;

import com.url.shortener.models.User;
import com.url.shortener.models.UrlScanResult;
import com.url.shortener.repository.UserRepository;
import com.url.shortener.repository.UrlScanResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UrlScanUserIdPersistenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlScanResultRepository urlScanResultRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void ensureUserIdFiveExists() {
        if (userRepository.findById(5L).isPresent()) {
            return;
        }

        while (userRepository.findById(5L).isEmpty()) {
            long marker = System.nanoTime();
            User user = new User();
            user.setEmail("scan-user-" + marker + "@example.com");
            user.setUsername("scan_user_" + marker);
            user.setPassword(passwordEncoder.encode("password"));
            user.setRole("ROLE_USER");
            userRepository.save(user);
        }
    }

    @Test
    void testScanSaveEndpointShouldPersistNonNullUserId() throws Exception {
        mockMvc.perform(post("/api/test-scan-save")
                        .with(user("tester").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("saved"));

        UrlScanResult latest = urlScanResultRepository.findAll().stream()
                .max(Comparator.comparing(UrlScanResult::getId))
                .orElseThrow();

        assertNotNull(latest.getUser(), "Saved scan should have a linked user");
        assertNotNull(latest.getUser().getId(), "Saved scan user id should not be null");
        assertEquals(5L, latest.getUser().getId());
    }
}

