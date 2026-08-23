package com.aiquestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="aliyun.sms")
public record AliyunSmsProperties(String accessKeyId,String accessKeySecret,String endpoint,String signName,String templateCode) {}
