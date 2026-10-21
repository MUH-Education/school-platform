package com.muhjain.school.model.dto.response;

/**
 * The numbers of the "Who gets it" screen. {@code picked} − {@code noPhone} − {@code duplicates} − {@code stopped}
 * = {@code total}. Example: picked 252, noPhone 3, duplicates 2, stopped 1 → total 246.
 *
 * @param onWhatsApp how many of {@code total} get WhatsApp
 * @param onSms how many of {@code total} get an SMS (URGENT: everyone, on top of WhatsApp)
 */
public record AudienceCounts(int picked, int duplicates, int stopped, int noPhone, int total, int onWhatsApp,
		int onSms) {
}
