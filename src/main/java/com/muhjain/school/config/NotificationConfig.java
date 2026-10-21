package com.muhjain.school.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ NotificationProperties.class, AiProperties.class })
public class NotificationConfig {

}
