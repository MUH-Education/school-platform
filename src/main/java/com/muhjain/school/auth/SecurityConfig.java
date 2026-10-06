package com.muhjain.school.auth;

import com.muhjain.school.common.ApiErrorResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Temporary security for Phase 0: only /actuator/health is open, every other URL answers 401.
 * Phase 1 replaces this with JWT login.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint unauthenticated) {
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
			.exceptionHandling(errors -> errors.authenticationEntryPoint(unauthenticated));
		return http.build();
	}

	// No token → 401 { "error": "UNAUTHENTICATED", "message": "Please log in first.", "fields": null }
	@Bean
	AuthenticationEntryPoint unauthenticatedEntryPoint(JsonMapper jsonMapper) {
		return (request, response, authException) -> {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			jsonMapper.writeValue(response.getOutputStream(),
					ApiErrorResponse.of("UNAUTHENTICATED", "Please log in first."));
		};
	}

}
