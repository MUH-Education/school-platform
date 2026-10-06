package com.muhjain.school.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ JwtProperties.class, OtpProperties.class })
class AuthPropertiesConfig {

}
