package com.muhjain.school.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.notifications.*}.
 *
 * @param manualApproval true = the owner sets a template's approval by hand ({@code PUT
 * /notification-templates/{id}/approval}). Only in {@code local} and {@code test}: there is no WhatsApp provider yet
 * (question C1). In {@code prod} the provider's template-status webhook decides, and the URL answers 404.
 */
@ConfigurationProperties("app.notifications")
public record NotificationProperties(boolean manualApproval) {
}
