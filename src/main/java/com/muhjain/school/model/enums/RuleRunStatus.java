package com.muhjain.school.model.enums;

/**
 * What one {@code RuleService.fire} did.
 * <ul>
 * <li>CREATED: a notification was made (it is sent by the Phase 11 dispatcher).</li>
 * <li>NOBODY: the run is saved, but nobody was in the audience (no birthday today), so no notification.</li>
 * <li>ALREADY_RAN: this run key was used before. Nothing done.</li>
 * <li>OFF: the rule is off, has no template, or nobody turned it on. Nothing done, nothing saved.</li>
 * </ul>
 */
public enum RuleRunStatus {

	CREATED, NOBODY, ALREADY_RAN, OFF

}
