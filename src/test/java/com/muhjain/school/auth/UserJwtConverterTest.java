package com.muhjain.school.auth;

import com.muhjain.school.AbstractIntegrationTest;
import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserJwtConverterTest extends AbstractIntegrationTest {

	@Autowired
	private UserJwtConverter converter;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Test
	void activeUserGetsRoleAndPermissionsFromTheDatabase() {
		AppUser user = addUser("+919812340002", Role.ADMISSIONS_DESK);
		Jwt jwt = jwtDecoder.decode(jwtService.issue(user).token());
		user.setRole(Role.OFFICE_ADMIN);
		userRepository.saveAndFlush(user);

		var authentication = converter.convert(jwt);

		assertThat(authentication.getName()).isEqualTo(String.valueOf(user.getId()));
		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.contains("ROLE_OFFICE_ADMIN", "STUDENTS_EDIT")
			.doesNotContain("ROLE_ADMISSIONS_DESK", "USERS_MANAGE");
	}

	@Test
	void turnedOffUserIsRejected() {
		AppUser user = addUser("+919812340002", Role.OFFICE_ADMIN);
		Jwt jwt = jwtDecoder.decode(jwtService.issue(user).token());
		user.setActive(false);
		userRepository.saveAndFlush(user);

		assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void oldTokenVersionIsRejected() {
		AppUser user = addUser("+919812340002", Role.OFFICE_ADMIN);
		Jwt jwt = jwtDecoder.decode(jwtService.issue(user).token());
		user.bumpTokenVersion();
		userRepository.saveAndFlush(user);

		assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

	@Test
	void deletedUserIsRejected() {
		AppUser user = addUser("+919812340002", Role.OFFICE_ADMIN);
		Jwt jwt = jwtDecoder.decode(jwtService.issue(user).token());
		userRepository.delete(user);

		assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class);
	}

}
