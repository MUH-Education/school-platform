package com.muhjain.school.auth;

import java.time.Clock;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * One shared secret (APP_JWT_SECRET) signs and checks every token with HS256.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
	}

	/** Checks the signature and the expiry. "Now" comes from the app clock. */
	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		JwtTimestampValidator expiry = new JwtTimestampValidator();
		expiry.setClock(clock);
		decoder.setJwtValidator(expiry);
		return decoder;
	}

	private static SecretKey secretKey(JwtProperties properties) {
		return new SecretKeySpec(properties.secretBytes(), "HmacSHA256");
	}

}
