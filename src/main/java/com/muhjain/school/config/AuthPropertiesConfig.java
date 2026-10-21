package com.muhjain.school.config;

import com.muhjain.school.security.JwtProperties;
import com.muhjain.school.security.SessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ JwtProperties.class, OtpProperties.class, SessionProperties.class })
public class AuthPropertiesConfig {

}
