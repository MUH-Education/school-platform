package com.muhjain.school.model.dto.response;

import com.muhjain.school.model.enums.NotificationKind;
import com.muhjain.school.model.enums.RuleEvent;
import com.muhjain.school.model.enums.RuleTrigger;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One line of the Settings screen's "Automatic messages" table.
 * Example: 1, FEE_REMINDER_10TH "Fee reminder 10th", FEE_REMINDER, template 41 "Fee tone 1", CRON MONTHLY 10th 10:00,
 * on, last run 10 Oct 10:00, last 30 days: 1 notification to 14 families.
 *
 * @param schedule CRON rules: the choice the cron stands for (null for a cron typed by hand in the database)
 * @param festivals rules with a date list (FESTIVAL): the dates and their names; null for the others
 * @param lastMonthNotifications notifications this rule made in the last 30 days
 * @param lastMonthRecipients the people they went to (counted at send time)
 */
public record NotificationRuleResponse(Long id, String code, String name, NotificationKind kind, Long templateId,
		String templateName, Audience audience, RuleTrigger triggerType, String cron, RuleSchedule schedule,
		RuleEvent event, List<FestivalDay> festivals, Integer delayMinutes, boolean enabled, OffsetDateTime lastRunAt,
		Long updatedBy, String updatedByName, long lastMonthNotifications, long lastMonthRecipients) {

}
