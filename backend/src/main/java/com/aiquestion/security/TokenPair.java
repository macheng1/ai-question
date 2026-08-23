package com.aiquestion.security;
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */

public record TokenPair(String accessToken,String refreshToken,String tokenType,long expiresInSeconds) {}
