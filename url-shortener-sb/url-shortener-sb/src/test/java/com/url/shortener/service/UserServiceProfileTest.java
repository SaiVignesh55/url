package com.url.shortener.service;

import com.url.shortener.dtos.UserProfileResponse;
import com.url.shortener.models.User;
import com.url.shortener.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceProfileTest {

	private UserRepository userRepository;
	private UserService userService;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		userService = new UserService(null, userRepository, null, null);
	}

	@Test
	void shouldReturnProfileByUsername() {
		User user = new User();
		user.setUsername("john");
		user.setEmail("john@example.com");

		when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

		UserProfileResponse response = userService.getUserProfile("john");

		assertEquals("john", response.getName());
		assertEquals("john@example.com", response.getEmail());
	}

	@Test
	void shouldReturn404WhenUserNotFound() {
		when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("missing")).thenReturn(Optional.empty());

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> userService.getUserProfile("missing"));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}
}

