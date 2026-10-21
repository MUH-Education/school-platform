package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.Role;

/**
 * Who a session belongs to, enough to build the request's authorities without a database call.
 * Internal carrier between {@code SessionService} and {@code UserJwtConverter}.
 */
public record SessionPrincipal(Long userId, Role role, Long staffId) {

}
