package com.url.shortener.service;

import com.url.shortener.dtos.LoginRequest;
import com.url.shortener.dtos.UserProfileResponse;
import com.url.shortener.models.User;
import com.url.shortener.repository.UserRepository;
import com.url.shortener.security.jwt.JwtAuthenticationResponse;
import com.url.shortener.security.jwt.JwtUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@AllArgsConstructor
@Slf4j
public class UserService {
    private PasswordEncoder passwordEncoder;
    private UserRepository userRepository;
    private AuthenticationManager authenticationManager;
    private JwtUtils jwtUtils;

    public User registerUser(User user){
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        String email = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase();
        String password = user.getPassword() == null ? "" : user.getPassword();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username, email and password are required");
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public JwtAuthenticationResponse authenticateUser(LoginRequest loginRequest){
        String loginId = loginRequest.getUsername() == null ? "" : loginRequest.getUsername().trim();
        String password = loginRequest.getPassword() == null ? "" : loginRequest.getPassword();

        if (loginId.isEmpty() || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username/email and password are required");
        }

        // Try to find user by username first, then by email
        String username = userRepository.findByUsername(loginId)
                .map(User::getUsername)
                .orElseGet(() -> userRepository.findByEmail(loginId.toLowerCase())
                        .map(User::getUsername)
                        .orElse(loginId));

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            String jwt = jwtUtils.generateToken(userDetails);
            return new JwtAuthenticationResponse(jwt);
        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password");
        }
    }

    public User findByUsername(String name) {
        return userRepository.findByUsername(name).orElseThrow(
                () -> new UsernameNotFoundException("User not found with username: " + name)
        );
    }

    public UserProfileResponse getUserProfile(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }

        String normalizedLoginId = loginId.trim();
        User user = userRepository.findByUsername(normalizedLoginId)
                .or(() -> userRepository.findByEmail(normalizedLoginId.toLowerCase()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        log.info("Fetching profile for username={} email={}", user.getUsername(), user.getEmail());
        return new UserProfileResponse(user.getUsername(), user.getEmail());
    }
}