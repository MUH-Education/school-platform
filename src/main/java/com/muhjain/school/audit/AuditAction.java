package com.muhjain.school.audit;

/** What happened to a row. Matches the check on {@code audit_log.action}. */
public enum AuditAction {

	CREATED, UPDATED, DELETED, LOGIN

}
