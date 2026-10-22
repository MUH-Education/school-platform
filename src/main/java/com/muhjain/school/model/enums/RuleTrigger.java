package com.muhjain.school.model.enums;

/**
 * When an automatic rule fires. CRON: at a time (the fee reminder at 10:00 on the 10th). EVENT: when something
 * happens in the school (an admission is saved, the attendant taps "running late").
 */
public enum RuleTrigger {

	CRON, EVENT

}
