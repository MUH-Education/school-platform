package com.muhjain.school.security;

import com.muhjain.school.model.enums.Role;

/**
 * The basic facts about who is making the current request, resolved once per request by
 * {@link UserJwtConverter} and read by {@link CurrentUser} or anything else that needs them,
 * without asking the database again.
 */
public record RequestContextData(Long userId, Role role, Long staffId, String sessionId) {

}
