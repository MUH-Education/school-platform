package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.dto.response.Audience;
import com.muhjain.school.model.dto.response.FestivalDay;
import com.muhjain.school.model.dto.response.RuleSchedule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * A change on the Settings screen's "Automatic messages" table. Only what is sent changes; null = keep.
 * Example (the switch): {@code {"enabled": true}}. Example (a full edit): {@code {"templateId": 41, "schedule":
 * {"type": "MONTHLY", "time": "10:00", "daysOfMonth": [10]}, "audience": {"groups": ["FAMILIES_WITH_DUES"],
 * "filter": {"minMonthsLate": 1}}}}.
 *
 * @param audience CRON rules only (WELCOME and BUS_LATE get their family or route from the event)
 * @param schedule CRON rules only
 * @param delayMinutes EVENT rules only: send this long after the event instead of the rule's own time
 * @param festivals rules with a date list only (FESTIVAL): the whole new list
 */
public record NotificationRuleRequest(Boolean enabled, Long templateId, @Valid Audience audience,
		@Valid RuleSchedule schedule, @Min(0) @Max(1440) Integer delayMinutes,
		@Valid List<@NotNull @Valid FestivalDay> festivals) {

}
