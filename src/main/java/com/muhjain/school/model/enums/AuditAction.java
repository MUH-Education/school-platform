package com.muhjain.school.model.enums;

/** What happened to a row. Matches the check on {@code audit_log.action}. */
public enum AuditAction {

	CREATED, UPDATED, DELETED, LOGIN, OPENED, CANCELLED

}
