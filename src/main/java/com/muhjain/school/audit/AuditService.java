package com.muhjain.school.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one {@code audit_log} row for every create, update and login.
 * Example: {@code audit.record("USER", 2L, AuditAction.CREATED, "User +91XXXXXX0002 added as OFFICE_ADMIN", null)}
 */
@Service
public class AuditService {

	private static final int MAX_SUMMARY = 300;

	private final AuditLogRepository auditLogs;

	private final Clock clock;

	public AuditService(AuditLogRepository auditLogs, Clock clock) {
		this.auditLogs = auditLogs;
		this.clock = clock;
	}

	/** The logged-in user is "changed by". No login (for example a start-up job) → null. */
	@Transactional
	public void record(String entityType, Long entityId, AuditAction action, String summary,
			Map<String, Object> details) {
		record(entityType, entityId, action, summary, details, currentUserId());
	}

	/** Same, with "changed by" given. Used for LOGIN, when nobody is logged in yet. */
	@Transactional
	public void record(String entityType, Long entityId, AuditAction action, String summary,
			Map<String, Object> details, Long changedBy) {
		String shortSummary = (summary.length() > MAX_SUMMARY) ? summary.substring(0, MAX_SUMMARY - 3) + "..."
				: summary;
		auditLogs.save(new AuditLog(entityType, entityId, action, shortSummary, details, changedBy,
				Instant.now(clock)));
	}

	/** Newest first. Feeds the "Change history" box. */
	@Transactional(readOnly = true)
	public List<AuditLog> history(String entityType, Long entityId) {
		return auditLogs.findByEntityTypeAndEntityIdOrderByChangedAtDescIdDesc(entityType, entityId);
	}

	// The token's subject is the user id, so the authentication name is "2" for user 2.
	private static Long currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
			return null;
		}
		try {
			return Long.valueOf(authentication.getName());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
