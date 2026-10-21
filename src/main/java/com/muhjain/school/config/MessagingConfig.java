package com.muhjain.school.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@code app.messaging.provider.*} and {@code app.messaging.webhook.*}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ ProviderProperties.class, WebhookProperties.class })
public class MessagingConfig {

}
