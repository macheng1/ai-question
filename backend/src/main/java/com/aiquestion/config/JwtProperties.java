package com.aiquestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="security.jwt")
public record JwtProperties(String secret,String expiresIn,String refreshSecret,String refreshExpiresIn) {}
