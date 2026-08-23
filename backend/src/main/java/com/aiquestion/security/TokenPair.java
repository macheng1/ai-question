package com.aiquestion.security;

public record TokenPair(String accessToken,String refreshToken,String tokenType,long expiresInSeconds) {}
