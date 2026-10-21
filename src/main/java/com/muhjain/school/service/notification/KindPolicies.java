package com.muhjain.school.service.notification;

import com.muhjain.school.exception.ApiException;
import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.enums.AudienceGroup;
import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every {@link KindPolicy} bean, by kind. Built from the Spring beans, never a {@code switch}: a new policy class is
 * found by itself. The app does not start if a kind has no policy or two.
 */
@Component
public class KindPolicies {

	private final Map<NotificationKind, KindPolicy> byKind;

	public KindPolicies(List<KindPolicy> policies) {
		Map<NotificationKind, KindPolicy> map = new EnumMap<>(NotificationKind.class);
		for (KindPolicy policy : policies) {
			KindPolicy before = map.put(policy.kind(), policy);
			if (before != null) {
				throw new IllegalStateException("Two policies for " + policy.kind() + ": "
						+ before.getClass().getSimpleName() + " and " + policy.getClass().getSimpleName());
			}
		}
		for (NotificationKind kind : NotificationKind.values()) {
			if (!map.containsKey(kind)) {
				throw new IllegalStateException("No KindPolicy for " + kind);
			}
		}
		this.byKind = Collections.unmodifiableMap(map);
	}

	public KindPolicy forKind(NotificationKind kind) {
		return byKind.get(kind);
	}

	/**
	 * The user may send this kind. Checked in the service, on top of {@code @PreAuthorize}.
	 *
	 * @param permissions the logged-in user's permissions (from the role, never from the request)
	 * @throws ApiException 403 KIND_NOT_ALLOWED_FOR_ROLE
	 */
	public void checkMaySend(NotificationKind kind, Set<Permission> permissions) {
		if (Collections.disjoint(forKind(kind).whoMaySend(), permissions)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "KIND_NOT_ALLOWED_FOR_ROLE",
					"You may not send a " + label(kind) + " message.");
		}
	}

	/**
	 * Every group of the audience is allowed for this kind, and there is at least one group.
	 * Example: a FEE_REMINDER to ENQUIRY_OPEN → 409 AUDIENCE_NOT_ALLOWED.
	 *
	 * @throws ApiException 409 AUDIENCE_NOT_ALLOWED
	 */
	public void checkAudience(NotificationKind kind, Audience audience) {
		if (audience == null || audience.groups().isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "AUDIENCE_NOT_ALLOWED", "Pick who gets this message.");
		}
		Set<AudienceGroup> allowed = forKind(kind).allowedAudiences();
		List<AudienceGroup> refused = audience.groups().stream().filter(g -> !allowed.contains(g)).distinct().toList();
		if (!refused.isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "AUDIENCE_NOT_ALLOWED",
					"A " + label(kind) + " message cannot go to "
							+ refused.stream().map(KindPolicies::label).collect(Collectors.joining(", ")) + ".");
		}
	}

	// FEE_REMINDER → "fee reminder"
	private static String label(Enum<?> value) {
		return value.name().toLowerCase().replace('_', ' ');
	}

}
