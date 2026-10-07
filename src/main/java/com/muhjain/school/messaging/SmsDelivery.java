package com.muhjain.school.messaging;

import java.time.OffsetDateTime;

/** SMS state with the time it was sent, null unless SENT or TEST_ONLY. Example: {@code SENT, 07:42}. */
public record SmsDelivery(SmsState state, OffsetDateTime sentAt) {

	public static final SmsDelivery NONE = new SmsDelivery(SmsState.NONE, null);

	public static final SmsDelivery NOT_FOR_CLASS = new SmsDelivery(SmsState.NOT_FOR_CLASS, null);

}
