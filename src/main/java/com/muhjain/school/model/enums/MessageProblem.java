package com.muhjain.school.model.enums;

/** Why a message failed, when the provider says. Null while it is still QUEUED or once it is SENT. */
public enum MessageProblem {

	PHONE_OFF, WRONG_NUMBER, NOT_ON_WHATSAPP, DND, PROVIDER_ERROR, OTHER

}
