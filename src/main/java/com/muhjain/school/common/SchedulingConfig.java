package com.muhjain.school.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs. Example: the nightly OTP clean-up. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {

}
