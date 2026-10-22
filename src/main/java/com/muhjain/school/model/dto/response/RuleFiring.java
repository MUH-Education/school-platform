package com.muhjain.school.model.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * One reason to fire a rule, given to {@code RuleService.fire}.
 * Example: the BUS_LATE tap → run key {@code bus-late:4:2026-10-07}, values {"Minutes": "20"}, filter route 4, now.
 *
 * @param runKey the same key never runs twice: CRON (BIRTHDAY too) {@code 2026-10-10}, EVENT {@code student:129},
 * Run now {@code manual:<timestamp>}
 * @param fixedValues {box} values the same for everyone, added to the rule's own ({@code {Minutes}}, {@code {Festival}})
 * @param filter replaces the rule's audience filter when not null (WELCOME: that family; BUS_LATE: that route)
 * @param sendAt null = now; a time = scheduled for then
 * @param manual "Run now" from Settings: also fires a rule that is off (it still needs a template)
 */
public record RuleFiring(String runKey, Map<String, String> fixedValues, AudienceFilter filter, Instant sendAt,
		boolean manual) {

	public RuleFiring {
		fixedValues = (fixedValues == null) ? Map.of() : Map.copyOf(fixedValues);
	}

	/** A plain run: the rule's own audience, now. */
	public static RuleFiring of(String runKey) {
		return new RuleFiring(runKey, Map.of(), null, null, false);
	}

}
