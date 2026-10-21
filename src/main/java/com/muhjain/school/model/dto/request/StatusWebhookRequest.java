package com.muhjain.school.model.dto.request;

import com.muhjain.school.model.enums.DeliveryEvent;

import java.time.OffsetDateTime;

/**
 * Provider callback: what happened to a message it took. Our own neutral shape (the provider is not chosen yet,
 * question C1); the provider's class maps its body to this. {@code problem} only with FAILED, one of PHONE_OFF,
 * WRONG_NUMBER, NOT_ON_WHATSAPP, DND, PROVIDER_ERROR, OTHER (anything else counts as OTHER).
 * Example: {@code {"providerRef":"wamid.HBgM1","status":"READ","at":"2026-10-07T10:02:11+05:30"}}.
 */
public record StatusWebhookRequest(String providerRef, DeliveryEvent status, OffsetDateTime at, String problem) {
}
