package com.muhjain.school.model.enums;

/**
 * Why a message was sent. The notification purposes (NOTICE, MARKETING, URGENT, STAFF, ONE_FAMILY) come from the
 * kind's policy: GREETING is sent as MARKETING, FEE_REMINDER as FEE_REMINDER.
 */
public enum MessagePurpose {

	BOARDING, FEE_RECEIPT, RECEIPT_CANCELLED, FEE_REMINDER, ENQUIRY, TEST, OTHER,
	NOTICE, MARKETING, URGENT, STAFF, ONE_FAMILY

}
