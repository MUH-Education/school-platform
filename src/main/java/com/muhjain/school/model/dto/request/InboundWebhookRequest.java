package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.ButtonAction;

import java.time.OffsetDateTime;

/**
 * Provider callback: a WhatsApp message that came in to the school number. Our own neutral shape (question C1).
 * {@code text} or {@code buttonAction} (a tap on a REPLY button), at least one. {@code inReplyTo} = the provider's id
 * of our message they answered. Example: {@code {"providerRef":"wamid.IN1","phone":"+919811100003","text":"STOP",
 * "receivedAt":"2026-10-07T18:29:00+05:30"}}.
 */
public record InboundWebhookRequest(String providerRef, String phone, String text, ButtonAction buttonAction,
		String inReplyTo, OffsetDateTime receivedAt) {
}
