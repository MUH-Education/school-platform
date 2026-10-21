package com.muhjain.school.model.enums;

/** Why a receipt was cancelled. Required whenever a receipt's status is CANCELLED. */
public enum CancelReason {

	WRONG_AMOUNT, WRONG_STUDENT, CHEQUE_BOUNCED, PAID_TWICE, OTHER

}
