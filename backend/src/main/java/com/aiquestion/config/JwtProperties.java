package com.aiquestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="security.jwt")
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public record JwtProperties(String secret,String expiresIn,String refreshSecret,String refreshExpiresIn) {}
