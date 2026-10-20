package com.muhjain.school.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.muhjain.school.security.JwtProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ JwtProperties.class, OtpProperties.class })
public class AuthPropertiesConfig {

}
