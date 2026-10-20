package com.muhjain.school.auth;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.model.dto.response.IssuedToken;
import com.muhjain.school.model.entity.AppUser;
import com.muhjain.school.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.muhjain.school.security.JwtService;
import com.muhjain.school.security.UserJwtConverter;

class UserJwtConverterTest extends AbstractIntegrationTest {

	@Autowired
	private UserJwtConverter converter;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Test
	void activeUserGetsRoleAndPermissionsFromTheSession() {
		AppUser user = addUser("+919812340002", Role.ADMISSIONS_DESK);
		Jwt jwt = jwtDecoder.decode(tokenFor(user));

		var authentication = converter.convert(jwt);

		assertThat(authentication.getName()).isEqualTo(String.valueOf(user.getId()));
		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.contains("ROLE_ADMISSIONS_DESK", "STUDENTS_VIEW")
			.doesNotContain("STUDENTS_EDIT", "USERS_MANAGE");
	}

	@Test
	void revokedSessionIsRejected() {
		AppUser user = addUser("+919812340002", Role.OFFICE_ADMIN);
		IssuedToken issued = sessionService.create(user, "10.0.0.1");
		Jwt jwt = jwtDecoder.decode(issued.token());

		sessionService.revoke(jwt.getClaimAsString(JwtService.CLAIM_SESSION));

		assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void sessionStillWorksAfterRedisForgetsItIfTheUserIsStillGood() {
		AppUser user = addUser("+919812340002", Role.OFFICE_ADMIN);
		Jwt jwt = jwtDecoder.decode(tokenFor(user));
		redisTemplate.delete("session:" + jwt.getClaimAsString(JwtService.CLAIM_SESSION));

		var authentication = converter.convert(jwt);

		assertThat(authentication.getName()).isEqualTo(String.valueOf(user.getId()));
	}

	@Test
	void turnedOffUserIsRejectedEvenWhenRedisForgotTheSession() {
		AppUser user = addUser("+919812340002", Role.OFFICE_ADMIN);
		Jwt jwt = jwtDecoder.decode(tokenFor(user));
		redisTemplate.delete("session:" + jwt.getClaimAsString(JwtService.CLAIM_SESSION));
		user.setActive(false);
		userRepository.saveAndFlush(user);

		assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

}
