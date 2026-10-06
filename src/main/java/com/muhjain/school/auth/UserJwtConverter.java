package com.muhjain.school.auth;

import java.util.ArrayList;
import java.util.List;

import com.muhjain.school.user.AppUser;
import com.muhjain.school.user.Permission;
import com.muhjain.school.user.UserService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Runs on every request after the signature and expiry are checked.
 * <ol>
 * <li>Loads the user row by {@code sub}.</li>
 * <li>401 if the user is gone, turned off, or {@code ver} is not the user's {@code token_version}.</li>
 * <li>Authorities = {@code ROLE_<role>} plus each permission of the role <b>in the database</b>,
 * so a role change works on the next request.</li>
 * </ol>
 * The authentication name is the user id, for example "2".
 */
@Component
public class UserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserService userService;

	public UserJwtConverter(UserService userService) {
		this.userService = userService;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		AppUser user = userService.findById(userId(jwt))
			.filter(AppUser::isActive)
			.filter(found -> found.getTokenVersion() == version(jwt))
			.orElseThrow(() -> new InvalidBearerTokenException("The token is no longer valid"));

		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
		for (Permission permission : user.getRole().permissions()) {
			authorities.add(new SimpleGrantedAuthority(permission.name()));
		}
		return new JwtAuthenticationToken(jwt, authorities, String.valueOf(user.getId()));
	}

	private static Long userId(Jwt jwt) {
		try {
			return Long.valueOf(jwt.getSubject());
		}
		catch (NumberFormatException | NullPointerException ex) {
			throw new InvalidBearerTokenException("The token has no user id");
		}
	}

	private static int version(Jwt jwt) {
		if (jwt.getClaim(JwtService.CLAIM_VERSION) instanceof Number number) {
			return number.intValue();
		}
		throw new InvalidBearerTokenException("The token has no version");
	}

}
