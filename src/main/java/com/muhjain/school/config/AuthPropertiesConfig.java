package com.muhjain.school.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.muhjain.school.security.JwtProperties;
import com.muhjain.school.security.SessionProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ JwtProperties.class, OtpProperties.class, SessionProperties.class })
public class AuthPropertiesConfig {

}
