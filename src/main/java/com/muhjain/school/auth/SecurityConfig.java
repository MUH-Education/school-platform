package com.muhjain.school.auth;

import com.muhjain.school.common.ApiErrorResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Who may call which URL.
 * <ul>
 * <li>Open: {@code /api/v1/auth/otp/**} (ask for and check a code) and {@code /actuator/health}.</li>
 * <li>Open only while the API docs are switched on (dev and test, never prod): {@code /v3/api-docs/**} and
 * {@code /swagger-ui/**}. They are not open when {@code springdoc.*.enabled} is false.</li>
 * <li>Everything else needs {@code Authorization: Bearer <token>}.</li>
 * <li>Permissions are checked on each controller method with {@code @PreAuthorize}.</li>
 * </ul>
 * No session and no cookie: every request carries its token, so CSRF protection is not needed.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, UserJwtConverter userJwtConverter,
			AuthenticationEntryPoint unauthenticated, AccessDeniedHandler forbidden,
			@Value("${springdoc.api-docs.enabled:false}") boolean apiDocsOn,
			@Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUiOn) {
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> {
				auth.requestMatchers("/api/v1/auth/otp/**").permitAll();
				auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
				// The one exception (task 8.10): the API docs and the Swagger page can be read without a token, but only
				// when they are switched on (dev and test). Calls made from Swagger still need a real token.
				if (apiDocsOn) {
					auth.requestMatchers("/v3/api-docs/**").permitAll();
				}
				if (swaggerUiOn) {
					auth.requestMatchers("/swagger-ui/**").permitAll();
				}
				auth.anyRequest().authenticated();
			})
			.oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(userJwtConverter))
				.authenticationEntryPoint(unauthenticated)
				.accessDeniedHandler(forbidden))
			.exceptionHandling(errors -> errors.authenticationEntryPoint(unauthenticated).accessDeniedHandler(forbidden));
		return http.build();
	}

	// No token, bad token, old token, turned-off user →
	// 401 { "error": "UNAUTHENTICATED", "message": "Please log in first.", "fields": null }
	@Bean
	AuthenticationEntryPoint unauthenticatedEntryPoint(JsonMapper jsonMapper) {
		return (request, response, authException) -> {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			jsonMapper.writeValue(response.getOutputStream(),
					ApiErrorResponse.of("UNAUTHENTICATED", "Please log in first."));
		};
	}

	// Logged in, but the role does not have the permission →
	// 403 { "error": "FORBIDDEN", "message": "You are not allowed to do this.", "fields": null }
	@Bean
	AccessDeniedHandler forbiddenHandler(JsonMapper jsonMapper) {
		return (request, response, accessDeniedException) -> {
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			jsonMapper.writeValue(response.getOutputStream(),
					ApiErrorResponse.of("FORBIDDEN", "You are not allowed to do this."));
		};
	}

}
